package io.github.kreza6173pixel.cyberappmanager.install

import android.util.Base64
import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ExecResult
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.inventory.ProtectedPackages
import io.github.kreza6173pixel.cyberappmanager.inventory.Verdict
import io.github.kreza6173pixel.cyberappmanager.inventory.isValidPackageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

data class InspectResult(
    val entry: FileEntry,
    val info: ApkManifestInfo?,
    val sha256: String?,
    val abis: List<String>,
    val installed: InstalledVersion?,
    val xapk: XapkManifest?,
    val apkCount: Int,
    val raw: String,
)

data class InstallResult(
    val fileName: String,
    val packageName: String?,
    val verdict: Verdict,
    val versionBefore: InstalledVersion?,
    val versionAfter: InstalledVersion?,
    val obbCopied: Int,
    val note: String,
    val log: String,
)

data class ExtractResult(val pkg: String, val verdict: Verdict, val destination: String, val files: List<String>, val note: String, val log: String)

/**
 * A8 installer, ported from the owner's `pulse-install` module. Shell uid 2000 reads /sdcard itself and
 * streams every APK into a `pm install-create` session (`cat | pm install-write -S <size>`), which avoids
 * system_server's FUSE read restriction. APKM is out of scope.
 *
 * Read-back: the package and version are read from the APK's own manifest before install, and the installed
 * `lastUpdateTime` / `versionCode` are compared before and after the commit. APPLIED only when they changed.
 */
class InstallerRepository(
    private val bridge: ExecBridge,
    private val protectedReason: (String) -> String? = { null },
) {
    private val log = StringBuilder()

    private fun sh(cmd: String, timeout: Int = TIMEOUT_MS): ExecResult? {
        val r = when (val o = bridge.execBlocking(cmd, timeout)) {
            is ExecOutcome.Failed -> null
            is ExecOutcome.Completed -> o.result
        }
        log.append("$ ").append(cmd.lineSequence().first()).append('\n')
        log.append(r?.let { (it.stdout + it.stderr).trim().take(LOG_CHARS) + "\n(exit ${it.exitCode})" } ?: "(not run: user service unavailable)")
        log.append("\n\n")
        return r
    }

    private fun ExecResult?.ok(): Boolean = this != null && exitCode == 0 && !truncated
    private fun q(s: String) = ShellQuoting.quote(s)
    private fun takeLog(): String = log.toString().trim().also { log.setLength(0) }

    suspend fun unzipAvailable(): Boolean = withContext(Dispatchers.IO) {
        sh("command -v unzip").let { it.ok() && it!!.stdout.isNotBlank() }.also { log.setLength(0) }
    }

    suspend fun browse(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        val p = normalizePath(path).ifEmpty { "/" }
        val r = sh("ls -la ${q(p)} 2>&1")
        log.setLength(0)
        when {
            r == null -> Result.failure(IllegalStateException("not connected to the Shizuku user service"))
            r.exitCode != 0 -> Result.failure(IllegalStateException(r.stdout.ifBlank { r.stderr }.trim().take(300)))
            else -> Result.success(parseLsLa(p, r.stdout))
        }
    }

    /** `unzip -p <apk> AndroidManifest.xml`, gzip first so large manifests stay under the 64 KiB cap. */
    private fun readManifest(apk: String): ApkManifestInfo? {
        val gz = sh("unzip -p ${q(apk)} AndroidManifest.xml 2>/dev/null | gzip -c 2>/dev/null | base64")
        if (gz.ok() && gz!!.stdout.isNotBlank()) {
            runCatching {
                val bytes = GZIPInputStream(ByteArrayInputStream(Base64.decode(gz.stdout, Base64.DEFAULT))).readBytes()
                Axml.parse(bytes)
            }.getOrNull()?.let { return it }
        }
        val plain = sh("unzip -p ${q(apk)} AndroidManifest.xml 2>/dev/null | base64")
        if (!plain.ok() || plain!!.stdout.isBlank()) return null
        return runCatching { Axml.parse(Base64.decode(plain.stdout, Base64.DEFAULT)) }.getOrNull()
    }

    private fun readVersion(pkg: String?): InstalledVersion? {
        if (pkg == null || !isValidPackageName(pkg)) return null
        val r = sh("dumpsys package ${q(pkg)} | grep -E 'versionCode=|versionName=|lastUpdateTime=' | head -n 6")
        return if (r == null || r.exitCode != 0) null else parseInstalledVersion(r.stdout)
    }

    private class Unpacked(val workDir: String, val apks: List<String>, val manifest: XapkManifest?)

    private fun unpack(entry: FileEntry): Result<Unpacked> {
        val work = "$WORK_ROOT/" + workDirName(entry.name, System.currentTimeMillis())
        sh("mkdir -p ${q(work)}")
        val unzip = sh("unzip -o -q ${q(entry.path)} -d ${q(work)} 2>&1", LONG_TIMEOUT_MS)
        if (unzip == null || unzip.exitCode != 0) {
            sh("rm -rf ${q(work)}")
            return Result.failure(IllegalStateException("extraction failed: " + (unzip?.stdout?.trim()?.take(200) ?: "not run")))
        }
        val found = sh("find ${q(work)} -iname '*.apk'")?.stdout.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.sorted()
        if (found.isEmpty()) {
            sh("rm -rf ${q(work)}")
            return Result.failure(IllegalStateException("no .apk found inside the bundle"))
        }
        val manifest = if (entry.format == PackageFormat.XAPK) {
            parseXapkManifest(sh("find ${q(work)} -maxdepth 1 -iname 'manifest.json' -exec cat {} \\;")?.stdout)
        } else null
        return Result.success(Unpacked(work, found, manifest))
    }

    suspend fun inspect(entry: FileEntry): Result<InspectResult> = withContext(Dispatchers.IO) {
        log.setLength(0)
        val format = entry.format ?: return@withContext Result.failure(IllegalArgumentException("unsupported file"))
        var apk = entry.path
        var xapk: XapkManifest? = null
        var count = 1
        var work: String? = null
        if (format != PackageFormat.APK) {
            val u = unpack(entry).getOrElse { return@withContext Result.failure(IllegalStateException(it.message + "\n\n" + takeLog())) }
            work = u.workDir
            xapk = u.manifest
            count = u.apks.size
            apk = pickBaseApk(u.apks) ?: u.apks.first()
        }
        val info = readManifest(apk)
        val sha = sh("sha256sum ${q(entry.path)} 2>/dev/null | cut -d' ' -f1")?.stdout?.trim()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
        val abis = sh("unzip -l ${q(apk)} 2>/dev/null | sed -n 's#.*[[:space:]]lib/\\([^/]*\\)/.*#\\1#p' | sort -u")?.stdout.orEmpty()
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        val installed = readVersion(info?.packageName ?: xapk?.packageName)
        work?.let { sh("rm -rf ${q(it)}") }
        Result.success(InspectResult(entry, info, sha, abis, installed, xapk, count, takeLog()))
    }

    /** pulse-install `streamingInstall()`: create, write every file, commit; abandon on any write error. */
    private fun streamingInstall(paths: List<String>, opts: InstallOptions): Pair<Boolean, String> {
        val create = sh(buildInstallCreateCmd(opts))
        val session = extractSessionId(create?.stdout)
        if (create == null || create.exitCode != 0 || session == null) {
            return false to "could not create install session: " + (create?.let { (it.stderr + it.stdout).trim() } ?: "not run")
        }
        paths.forEachIndexed { i, path ->
            val w = sh("sz=$(stat -c%s ${q(path)}) && cat ${q(path)} | pm install-write -S \"\$sz\" $session ${q(sanitizeSplitName(path, i))} -", LONG_TIMEOUT_MS)
            if (w == null || w.exitCode != 0 || w.truncated) {
                sh("pm install-abandon $session")
                return false to "failed writing ${path.substringAfterLast('/')}: " + (w?.let { (it.stderr + it.stdout).trim() } ?: "not run")
            }
        }
        val commit = sh("pm install-commit $session 2>&1", LONG_TIMEOUT_MS)
        val out = commit?.stdout?.trim().orEmpty()
        return (commit != null && isCommitSuccess(out)) to out
    }

    private fun verdictFor(pkg: String?, before: InstalledVersion?, after: InstalledVersion?): Pair<Verdict, String> = when {
        pkg == null -> Verdict.UNVERIFIABLE to "Android said Success, but the package name could not be read from the APK"
        after == null -> Verdict.NOT_APPLIED to "Android said Success, but $pkg is not installed"
        before == null -> Verdict.APPLIED to "new install"
        after.lastUpdate != before.lastUpdate || after.versionCode != before.versionCode -> Verdict.APPLIED to "updated"
        else -> Verdict.NOT_APPLIED to "Android said Success, but the installed version did not change"
    }

    private fun copyObbs(workDir: String, pkg: String?): Pair<Int, String> {
        val obbs = sh("find ${q(workDir)} -iname '*.obb'")?.stdout.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (obbs.isEmpty()) return 0 to ""
        if (pkg == null || !isValidPackageName(pkg)) return 0 to "${obbs.size} OBB file(s) found but the package is unknown, not copied"
        val target = "/sdcard/Android/obb/$pkg"
        sh("mkdir -p ${q(target)}")
        var copied = 0
        for (obb in obbs) {
            val dest = target + "/" + obb.substringAfterLast('/')
            sh("cp ${q(obb)} ${q(dest)}", LONG_TIMEOUT_MS)
            val check = sh("[ \"$(stat -c%s ${q(obb)})\" = \"$(stat -c%s ${q(dest)} 2>/dev/null)\" ] && echo same")
            if (check?.stdout?.trim() == "same") copied++
        }
        return copied to "OBB copied to $target: $copied of ${obbs.size}"
    }

    private fun deleteSource(path: String): String {
        sh("rm -f ${q(path)}")
        val gone = sh("[ -e ${q(path)} ] || echo gone")?.stdout?.trim() == "gone"
        return if (gone) "source file deleted" else "source file could NOT be deleted"
    }

    private fun refusedFor(pkg: String?): String? = pkg?.let { protectedReason(it) }?.let { "protected package: $it" }

    /** Installs one file (APK alone, or a container as one session). */
    private fun installOne(entry: FileEntry, opts: InstallOptions): InstallResult {
        log.setLength(0)
        val format = entry.format ?: return InstallResult(entry.name, null, Verdict.REFUSED, null, null, 0, "unsupported file type", "")
        if (format == PackageFormat.APK) {
            val info = readManifest(entry.path)
            val pkg = info?.packageName
            refusedFor(pkg)?.let { return InstallResult(entry.name, pkg, Verdict.REFUSED, null, null, 0, it, takeLog()) }
            val before = readVersion(pkg)
            val (ok, out) = streamingInstall(listOf(entry.path), opts)
            if (!ok) return InstallResult(entry.name, pkg, Verdict.FAILED, before, null, 0, out.take(300), takeLog())
            val after = readVersion(pkg)
            val (v, note) = verdictFor(pkg, before, after)
            val extra = if (v == Verdict.APPLIED && opts.deleteAfter) "; " + deleteSource(entry.path) else ""
            return InstallResult(entry.name, pkg, v, before, after, 0, note + extra, takeLog())
        }
        val u = unpack(entry).getOrElse { return InstallResult(entry.name, null, Verdict.FAILED, null, null, 0, it.message ?: "extraction failed", takeLog()) }
        val set = resolveInstallSet(u.apks, u.manifest)
        val info = readManifest(pickBaseApk(set.paths) ?: set.paths.first())
        val pkg = u.manifest?.packageName ?: info?.packageName
        refusedFor(pkg)?.let { sh("rm -rf ${q(u.workDir)}"); return InstallResult(entry.name, pkg, Verdict.REFUSED, null, null, 0, it, takeLog()) }
        val before = readVersion(pkg)
        val (ok, out) = streamingInstall(set.paths, opts)
        if (!ok) {
            sh("rm -rf ${q(u.workDir)}")
            return InstallResult(entry.name, pkg, Verdict.FAILED, before, null, 0, out.take(300), takeLog())
        }
        val after = readVersion(pkg)
        val (v, note) = verdictFor(pkg, before, after)
        val (obb, obbNote) = if (v == Verdict.APPLIED) copyObbs(u.workDir, u.manifest?.packageName ?: pkg) else 0 to ""
        sh("rm -rf ${q(u.workDir)}")
        val extra = if (v == Verdict.APPLIED && opts.deleteAfter) "; " + deleteSource(entry.path) else ""
        val parts = listOf("${set.paths.size} APK(s), ${set.reason}", note, obbNote).filter { it.isNotEmpty() }
        return InstallResult(entry.name, pkg, v, before, after, obb, parts.joinToString("; ") + extra, takeLog())
    }

    /** Several loose APKs of one app (base + splits) in one session. */
    private fun installSplitSet(entries: List<FileEntry>, opts: InstallOptions): InstallResult {
        log.setLength(0)
        val paths = entries.map { it.path }
        val info = readManifest(pickBaseApk(paths) ?: paths.first())
        val pkg = info?.packageName
        val label = entries.joinToString(" + ") { it.name }
        refusedFor(pkg)?.let { return InstallResult(label, pkg, Verdict.REFUSED, null, null, 0, it, takeLog()) }
        val before = readVersion(pkg)
        val (ok, out) = streamingInstall(paths, opts)
        if (!ok) return InstallResult(label, pkg, Verdict.FAILED, before, null, 0, out.take(300), takeLog())
        val after = readVersion(pkg)
        val (v, note) = verdictFor(pkg, before, after)
        val extra = if (v == Verdict.APPLIED && opts.deleteAfter) "; " + entries.joinToString("; ") { deleteSource(it.path) } else ""
        return InstallResult(label, pkg, v, before, after, 0, "${paths.size} APKs in one session; $note$extra", takeLog())
    }

    suspend fun install(entries: List<FileEntry>, opts: InstallOptions, splitBundle: Boolean, onProgress: (String) -> Unit): List<InstallResult> =
        withContext(Dispatchers.IO) {
            val plain = entries.filter { it.format == PackageFormat.APK }
            val containers = entries.filter { it.format == PackageFormat.APKS || it.format == PackageFormat.XAPK }
            val out = mutableListOf<InstallResult>()
            if (splitBundle && plain.size >= 2) {
                onProgress(plain.joinToString(" + ") { it.name })
                out += installSplitSet(plain, opts)
            } else {
                plain.forEach { onProgress(it.name); out += installOne(it, opts) }
            }
            containers.forEach { onProgress(it.name); out += installOne(it, opts) }
            out
        }

    /** pulse-install "Scan auto-install folder now": installs every supported file, moves successes to installed/. */
    suspend fun scanAutoFolder(opts: InstallOptions, onProgress: (String) -> Unit): Result<List<InstallResult>> = withContext(Dispatchers.IO) {
        log.setLength(0)
        sh("mkdir -p ${q(AUTO_DIR)} ${q(AUTO_DONE_DIR)}")
        val list = sh("find ${q(AUTO_DIR)} -maxdepth 1 -type f \\( -iname '*.apk' -o -iname '*.apks' -o -iname '*.xapk' \\)")
            ?: return@withContext Result.failure(IllegalStateException("not connected to the Shizuku user service"))
        val files = list.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }.sorted()
        val out = files.map { path ->
            val name = path.substringAfterLast('/')
            onProgress(name)
            val r = installOne(FileEntry(name, path, false, 0), opts.copy(deleteAfter = false))
            if (r.verdict == Verdict.APPLIED) {
                val dest = "$AUTO_DONE_DIR/$name"
                sh("mv ${q(path)} ${q(dest)}")
                val moved = sh("[ -e ${q(dest)} ] && [ ! -e ${q(path)} ] && echo moved")?.stdout?.trim() == "moved"
                r.copy(note = r.note + if (moved) "; moved to installed/" else "; could NOT move to installed/", log = r.log + "\n\n" + takeLog())
            } else r
        }
        Result.success(out)
    }

    /** pulse-install Extract: copies base and split APKs to /sdcard/Download/pulse-extracted/<pkg>_<version>/, optional .xapk. */
    suspend fun extract(pkg: String, asXapk: Boolean): ExtractResult = withContext(Dispatchers.IO) {
        log.setLength(0)
        if (!isValidPackageName(pkg)) return@withContext ExtractResult(pkg, Verdict.REFUSED, "", emptyList(), "invalid package name", "")
        val paths = parsePmPath(sh("pm path ${q(pkg)}")?.stdout)
        if (paths.isEmpty()) return@withContext ExtractResult(pkg, Verdict.FAILED, "", emptyList(), "pm path returned no APK", takeLog())
        val v = readVersion(pkg)
        val label = extractLabel(pkg, v?.versionName, v?.versionCode)
        val dest = "$EXTRACT_ROOT/$label"
        sh("mkdir -p ${q(dest)}")
        val names = mutableListOf<String>()
        var verified = 0
        paths.forEach { src ->
            val name = src.substringAfterLast('/')
            val target = "$dest/$name"
            sh("cp ${q(src)} ${q(target)}", LONG_TIMEOUT_MS)
            val same = sh("[ \"$(stat -c%s ${q(src)})\" = \"$(stat -c%s ${q(target)} 2>/dev/null)\" ] && echo same")?.stdout?.trim() == "same"
            names += name
            if (same) verified++
        }
        if (verified != paths.size) {
            return@withContext ExtractResult(pkg, Verdict.NOT_APPLIED, dest, names, "copied and size-checked $verified of ${paths.size} file(s)", takeLog())
        }
        if (!asXapk) return@withContext ExtractResult(pkg, Verdict.APPLIED, dest, names, "${names.size} APK file(s), sizes match", takeLog())

        val manifest = buildXapkManifest(pkg, v?.versionCode, v?.versionName, names)
        sh("printf '%s' ${q(manifest)} > ${q("$dest/manifest.json")}")
        val zip = sh("command -v zip")
        if (!zip.ok() || zip!!.stdout.isBlank()) {
            return@withContext ExtractResult(pkg, Verdict.APPLIED, dest, names + "manifest.json", "zip is not available on this phone: APKs and manifest.json left loose", takeLog())
        }
        val xapkPath = "$EXTRACT_ROOT/$label.xapk"
        sh("rm -f ${q(xapkPath)}; cd ${q(dest)} && zip -q -j ${q(xapkPath)} *", LONG_TIMEOUT_MS)
        val listed = sh("unzip -l ${q(xapkPath)} 2>/dev/null | grep -c -E '\\.apk$|manifest\\.json$'")?.stdout?.trim()?.toIntOrNull() ?: 0
        val ok = listed == names.size + 1
        ExtractResult(pkg, if (ok) Verdict.APPLIED else Verdict.NOT_APPLIED, xapkPath, names + "manifest.json",
            if (ok) "XAPK created with $listed entries" else "XAPK check found $listed of ${names.size + 1} entries", takeLog())
    }

    companion object {
        const val TIMEOUT_MS = 30_000
        const val LONG_TIMEOUT_MS = 120_000
        const val LOG_CHARS = 1500
        const val WORK_ROOT = "/data/local/tmp/.void-install-work"
        const val AUTO_DIR = "/sdcard/pulse-install/auto"
        const val AUTO_DONE_DIR = "$AUTO_DIR/installed"
        const val EXTRACT_ROOT = "/sdcard/Download/pulse-extracted"
        const val DEFAULT_BROWSE = "/sdcard/Download"
    }
}

/** Bridge accessor, same pattern as the A5 to A7 feature repositories. */
internal fun InventoryRepository.toolsBridge(): ExecBridge {
    val field = InventoryRepository::class.java.getDeclaredField("bridge")
    field.isAccessible = true
    return field.get(this) as ExecBridge
}

/**
 * Installs are refused only for the static core list (framework, System UI, Shizuku, installer...):
 * updating Shizuku through Shizuku would cut the session mid-install. Launcher, keyboard and other
 * role holders can be updated normally.
 */
internal fun InventoryRepository.installerRepo(): InstallerRepository =
    InstallerRepository(toolsBridge()) { ProtectedPackages(emptyMap()).reasonFor(it) }
