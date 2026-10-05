package io.github.kreza6173pixel.cyberappmanager.install

/**
 * A8 pure logic, ported from the owner's `pulse-install` (installer) and `void-purge` (cleaner) modules.
 * Everything here is free of Android and shell calls so it can be unit-tested.
 */

enum class PackageFormat { APK, APKS, XAPK }

/** `.apk`, `.apks`, `.xapk` only (case-insensitive). APKM is out of scope on purpose. */
fun detectFormat(name: String): PackageFormat? {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".apk") -> PackageFormat.APK
        lower.endsWith(".apks") -> PackageFormat.APKS
        lower.endsWith(".xapk") -> PackageFormat.XAPK
        else -> null
    }
}

data class FileEntry(val name: String, val path: String, val isDir: Boolean, val size: Long) {
    val format: PackageFormat? get() = if (isDir) null else detectFormat(name)
}

/**
 * `ls -la <dir>` parser, same rules as pulse-install `parseLsLa()`: skip `total`, need 8+ fields,
 * name = fields 7.., skip `.` and `..`. Symlinks (`l`) keep only the link name. Dirs first, then by name.
 */
fun parseLsLa(dir: String, text: String): List<FileEntry> {
    val base = dir.trimEnd('/').ifEmpty { "" }
    val out = mutableListOf<FileEntry>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("total")) continue
        val t = line.split(Regex("\\s+"))
        if (t.size < 8) continue
        val perms = t[0]
        if (perms.isEmpty() || perms[0] !in "-dlcbps") continue
        var name = t.subList(7, t.size).joinToString(" ")
        if (perms[0] == 'l') name = name.substringBefore(" -> ")
        if (name == "." || name == ".." || name.isEmpty()) continue
        out += FileEntry(name, "$base/$name", perms[0] == 'd', t[4].toLongOrNull() ?: 0L)
    }
    return out.sortedWith(compareBy<FileEntry>({ !it.isDir }, { it.name.lowercase() }))
}

fun parentDir(path: String): String {
    val p = path.trimEnd('/')
    val i = p.lastIndexOf('/')
    return if (i <= 0) "/" else p.substring(0, i)
}

/** `pm install-create` prints `Success: created install session [1234]`. */
fun extractSessionId(createStdout: String?): Int? =
    Regex("\\[(\\d+)]").find(createStdout.orEmpty())?.groupValues?.get(1)?.toIntOrNull()

fun sanitizeSplitName(path: String, index: Int): String =
    "split" + index + "_" + path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9_]"), "_")

data class InstallOptions(val replace: Boolean = true, val grant: Boolean = false, val deleteAfter: Boolean = false)

fun buildInstallCreateCmd(o: InstallOptions): String {
    val flags = buildList { if (o.replace) add("-r"); if (o.grant) add("-g") }
    return ("pm install-create " + flags.joinToString(" ")).trim()
}

/** pulse-install service.sh rule: the commit worked only when its output starts with `Success`. */
fun isCommitSuccess(output: String?): Boolean = output.orEmpty().trim().startsWith("Success")

data class XapkManifest(val packageName: String?, val splitApks: List<String>)

/**
 * Minimal reader for XAPK `manifest.json`: `package_name` and `split_apks` (strings or objects with `file`).
 * Hand-written on purpose: org.json is a stub in local unit tests.
 */
fun parseXapkManifest(json: String?): XapkManifest? {
    val t = json?.trim().orEmpty()
    if (!t.startsWith("{")) return null
    val pkg = Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"").find(t)?.groupValues?.get(1)
    val splits = mutableListOf<String>()
    val start = Regex("\"split_apks\"\\s*:\\s*\\[").find(t)
    if (start != null) {
        var depth = 1
        var i = start.range.last + 1
        val sb = StringBuilder()
        while (i < t.length && depth > 0) {
            val c = t[i]
            if (c == '[') depth++ else if (c == ']') depth--
            if (depth > 0) sb.append(c)
            i++
        }
        val body = sb.toString()
        val files = Regex("\"file\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        if (files.isNotEmpty()) splits += files
        else splits += Regex("\"([^\"]+\\.apk)\"").findAll(body).map { it.groupValues[1] }
    }
    return XapkManifest(pkg, splits)
}

data class InstallSet(val paths: List<String>, val reason: String)

/** pulse-install `resolveInstallSet()`: universal.apk alone, else manifest split_apks, else every APK. */
fun resolveInstallSet(allApkPaths: List<String>, manifest: XapkManifest?): InstallSet {
    allApkPaths.firstOrNull { it.lowercase().endsWith("/universal.apk") }?.let {
        return InstallSet(listOf(it), "universal.apk found")
    }
    if (manifest != null && manifest.splitApks.isNotEmpty()) {
        val byName = allApkPaths.associateBy { it.substringAfterLast('/') }
        val resolved = manifest.splitApks.mapNotNull { byName[it.substringAfterLast('/')] }
        if (resolved.isNotEmpty()) return InstallSet(resolved, "manifest split_apks list")
    }
    return InstallSet(allApkPaths, "every .apk found")
}

/** The APK whose manifest names the package: base.apk, then a non-split, then the first. */
fun pickBaseApk(paths: List<String>): String? =
    paths.firstOrNull { it.substringAfterLast('/').equals("base.apk", true) }
        ?: paths.firstOrNull { !it.substringAfterLast('/').lowercase().let { n -> n.startsWith("split") || n.startsWith("config.") } }
        ?: paths.firstOrNull()

fun workDirName(fileName: String, stamp: Long): String =
    fileName.replace(Regex("[^A-Za-z0-9_.-]"), "_") + "-" + stamp

/** `dumpsys package <pkg>` fields used for install and extract read-back. */
data class InstalledVersion(val versionCode: Long?, val versionName: String?, val lastUpdate: String?)

fun parseInstalledVersion(text: String?): InstalledVersion? {
    val t = text.orEmpty()
    val code = Regex("versionCode=(\\d+)").find(t)?.groupValues?.get(1)?.toLongOrNull()
    val name = Regex("versionName=(\\S+)").find(t)?.groupValues?.get(1)
    val upd = Regex("lastUpdateTime=([^\\n]+)").find(t)?.groupValues?.get(1)?.trim()
    return if (code == null && name == null && upd == null) null else InstalledVersion(code, name, upd)
}

/** `pm path <pkg>` lines `package:/data/app/.../base.apk`. */
fun parsePmPath(text: String?): List<String> =
    text.orEmpty().lineSequence().map { it.trim() }.filter { it.startsWith("package:") }
        .map { it.removePrefix("package:") }.filter { it.isNotEmpty() }.toList()

fun extractLabel(pkg: String, versionName: String?, versionCode: Long?): String {
    val v = (versionName ?: versionCode?.toString() ?: "unknown").replace(Regex("[^A-Za-z0-9_.-]"), "_")
    return "${pkg}_$v"
}

/** Generated XAPK manifest, same fields as pulse-install. */
fun buildXapkManifest(pkg: String, versionCode: Long?, versionName: String?, splitNames: List<String>): String {
    fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
    return "{\"package_name\":\"${esc(pkg)}\",\"version_code\":\"${versionCode ?: ""}\",\"version_name\":\"${esc(versionName ?: "")}\"," +
        "\"split_apks\":[" + splitNames.joinToString(",") { "{\"file\":\"${esc(it)}\"}" } + "]}"
}

// ---------------------------------------------------------------- cleaner (void-purge)

/** void-purge DENY_PATHS: these exact roots are never scanned or touched. */
val DENY_PATHS = setOf("", "/", "/sdcard", "/storage/emulated/0", "/data", "/system", "/storage", "/storage/emulated")

fun normalizePath(p: String): String = p.trim().replace(Regex("/+"), "/").trimEnd('/')

/** void-purge `isSafePath`, plus: absolute, no `..`, no control characters. */
fun isSafePath(p: String): Boolean {
    val n = normalizePath(p)
    if (n in DENY_PATHS || !n.startsWith("/")) return false
    if (n.split('/').any { it == ".." || it == "." }) return false
    return n.none { it.isISOControl() }
}

/** A scan root is allowed only under shared storage. */
fun isAllowedScanRoot(p: String): Boolean {
    val n = normalizePath(p)
    return isSafePath(n) && (n.startsWith("/sdcard/") || n.startsWith("/storage/emulated/0/"))
}

/** Lines from `find <root> -depth -type d -empty`, kept only when strictly below [root] and safe. */
fun parseEmptyDirs(root: String, text: String?): List<String> {
    val r = normalizePath(root)
    return text.orEmpty().lineSequence().map { normalizePath(it) }
        .filter { it.startsWith("$r/") && isSafePath(it) }
        .distinct().toList()
}

/** `df -k <path>` second line: Filesystem 1K-blocks Used Available Use% Mounted. Returns available KiB. */
fun parseDfAvailableKb(text: String?): Long? {
    val lines = text.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size < 2) return null
    val t = lines.last().split(Regex("\\s+"))
    return if (t.size >= 4) t[3].toLongOrNull() else null
}

data class ProcessRow(val user: String, val pid: Int, val name: String)

/** `ps -A -o USER,PID,NAME`. The header and malformed rows are skipped; PID must be numeric. */
fun parsePs(text: String?): List<ProcessRow> =
    text.orEmpty().lineSequence().mapNotNull { line ->
        val t = line.trim().split(Regex("\\s+"))
        if (t.size < 3) return@mapNotNull null
        val pid = t[1].toIntOrNull() ?: return@mapNotNull null
        ProcessRow(t[0], pid, t.subList(2, t.size).joinToString(" "))
    }.toList()

/** Package that owns a process name: `pkg` or `pkg:service`. */
fun processPackage(name: String): String = name.substringBefore(':')

data class RunningApp(val packageName: String, val pids: List<Int>, val processes: List<String>, val protectedReason: String?)

/**
 * Groups app processes (users `u<N>_a<M>`) by package and keeps only installed packages from [installed].
 * [protect] returns a reason for packages that must never be stopped.
 */
fun groupRunningApps(rows: List<ProcessRow>, installed: Set<String>, protect: (String) -> String?): List<RunningApp> =
    rows.filter { Regex("^u\\d+_a\\d+$").matches(it.user) }
        .groupBy { processPackage(it.name) }
        .filterKeys { it in installed }
        .map { (pkg, list) -> RunningApp(pkg, list.map { it.pid }.sorted(), list.map { it.name }.distinct().sorted(), protect(pkg)) }
        .sortedWith(compareBy<RunningApp>({ it.protectedReason != null }, { it.packageName }))

fun stillRunning(pkg: String, rows: List<ProcessRow>): List<Int> =
    rows.filter { processPackage(it.name) == pkg }.map { it.pid }

/** `cmd package resolve-activity --brief ...` last line `pkg/.Activity`, or `settings get secure default_input_method`. */
fun packageOfComponentLine(text: String?): String? =
    text.orEmpty().lines().map { it.trim() }.lastOrNull { it.contains('/') }?.substringBefore('/')?.takeIf { it.isNotEmpty() && !it.contains(' ') }

fun formatBytes(n: Long): String {
    val u = listOf("B", "KB", "MB", "GB")
    var v = n.toDouble()
    var i = 0
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return if (i == 0) "$n B" else String.format(java.util.Locale.US, "%.1f %s", v, u[i])
}
