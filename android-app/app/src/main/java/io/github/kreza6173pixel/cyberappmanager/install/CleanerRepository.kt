package io.github.kreza6173pixel.cyberappmanager.install

import android.content.Context
import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ExecResult
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import io.github.kreza6173pixel.cyberappmanager.inventory.DeviceRoles
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.inventory.ProtectedPackages
import io.github.kreza6173pixel.cyberappmanager.inventory.Verdict
import io.github.kreza6173pixel.cyberappmanager.inventory.isValidPackageName
import io.github.kreza6173pixel.cyberappmanager.inventory.parsePackageList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class CacheResult(val verdict: Verdict, val freedKb: Long?, val log: String)
data class EmptyScan(val root: String, val dirs: List<String>, val capped: Boolean, val log: String)
data class EmptyDeleteResult(val removed: List<String>, val kept: List<String>, val log: String)
data class RunningScan(val apps: List<RunningApp>, val source: String, val log: String)
data class StopResult(val pkg: String, val verdict: Verdict, val before: List<Int>, val after: List<Int>, val note: String)

/**
 * A8 cleaner, the three parts of the owner's `void-purge` module that were kept: cache trim, empty folders,
 * running apps. Everything else in void-purge (orphans, logs, duplicates, root tools) was dropped on purpose.
 *
 * Running apps fix: void-purge force-stopped EVERY third-party package in a backgrounded `( ... ) &` subshell
 * with output discarded, so nothing was listed, failures were invisible and the bridge could end the children.
 * Here the live processes come from `ps`, each stop runs in the foreground, and `ps` is read again afterwards.
 */
class CleanerRepository(
    private val bridge: ExecBridge,
    private val protectedReason: (String) -> String?,
) {
    private val log = StringBuilder()

    private fun sh(cmd: String, timeout: Int = TIMEOUT_MS): ExecResult? {
        val r = when (val o = bridge.execBlocking(cmd, timeout)) {
            is ExecOutcome.Failed -> null
            is ExecOutcome.Completed -> o.result
        }
        log.append("$ ").append(cmd.lineSequence().first().take(300)).append('\n')
        log.append(r?.let { (it.stdout + it.stderr).trim().take(LOG_CHARS) + "\n(exit ${it.exitCode})" } ?: "(not run: user service unavailable)")
        log.append("\n\n")
        return r
    }

    private fun q(s: String) = ShellQuoting.quote(s)
    private fun takeLog(): String = log.toString().trim().also { log.setLength(0) }

    private fun availableKb(): Long? = sh("df -k /data 2>/dev/null")?.stdout?.let { parseDfAvailableKb(it) }

    /** void-purge App Cache Cleanup: `pm trim-caches 999999999999`; free space on /data measured before and after. */
    suspend fun trimCaches(): CacheResult = withContext(Dispatchers.IO) {
        log.setLength(0)
        val before = availableKb()
        val r = sh("pm trim-caches 999999999999 2>&1", LONG_TIMEOUT_MS)
        val after = availableKb()
        val failed = r == null || r.exitCode != 0 || Regex("error|exception|denied", RegexOption.IGNORE_CASE).containsMatchIn(r.stdout)
        val freed = if (before != null && after != null) (after - before).coerceAtLeast(0) else null
        CacheResult(if (failed) Verdict.FAILED else Verdict.APPLIED, freed, takeLog())
    }

    /** void-purge Empty Folders: `find <root> -type d -empty`, deepest first, capped at [MAX_EMPTY] rows. */
    suspend fun scanEmpty(root: String): Result<EmptyScan> = withContext(Dispatchers.IO) {
        log.setLength(0)
        val r0 = normalizePath(root)
        if (!isAllowedScanRoot(r0)) return@withContext Result.failure(IllegalArgumentException("choose a folder inside /sdcard, not /sdcard itself"))
        val r = sh("find ${q(r0)} -depth -type d -empty 2>/dev/null | head -n ${MAX_EMPTY + 1}", LONG_TIMEOUT_MS)
            ?: return@withContext Result.failure(IllegalStateException("not connected to the Shizuku user service"))
        val dirs = parseEmptyDirs(r0, r.stdout)
        Result.success(EmptyScan(r0, dirs.take(MAX_EMPTY), dirs.size > MAX_EMPTY, takeLog()))
    }

    /** `rmdir` only (never `rm -rf`): a folder that got content since the scan is simply kept. Read back with `[ -e ]`. */
    suspend fun deleteEmpty(root: String, dirs: List<String>): EmptyDeleteResult = withContext(Dispatchers.IO) {
        log.setLength(0)
        val r0 = normalizePath(root)
        val safe = dirs.map { normalizePath(it) }.filter { isAllowedScanRoot(r0) && it.startsWith("$r0/") && isSafePath(it) }.distinct()
        val kept = mutableListOf<String>()
        safe.chunked(CHUNK).forEach { chunk ->
            val args = chunk.joinToString(" ") { q(it) }
            sh("for p in $args; do [ -d \"\$p\" ] && rmdir -- \"\$p\" 2>/dev/null; done; for p in $args; do [ -e \"\$p\" ] && echo \"\$p\"; done")
                ?.stdout.orEmpty().lines().map { normalizePath(it) }.filter { it.isNotEmpty() }.let { kept += it }
        }
        EmptyDeleteResult(safe.filter { it !in kept }, kept, takeLog())
    }

    private fun readProcesses(): Pair<List<ProcessRow>, String>? {
        val r = sh("ps -A -o USER,PID,NAME 2>/dev/null") ?: return null
        val rows = parsePs(r.stdout)
        if (rows.any { Regex("^u\\d+_a\\d+$").matches(it.user) }) return rows to "ps -A -o USER,PID,NAME"
        val d = sh("ps -A 2>/dev/null") ?: return null
        val fallback = d.stdout.lineSequence().mapNotNull { line ->
            val t = line.trim().split(Regex("\\s+"))
            val pid = t.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            ProcessRow(t[0], pid, t.last())
        }.toList()
        return fallback to "ps -A"
    }

    /** Running third-party apps for user 0, from live processes. */
    suspend fun runningApps(): Result<RunningScan> = withContext(Dispatchers.IO) {
        log.setLength(0)
        val installed = sh("pm list packages -3 --user 0")?.stdout?.let { parsePackageList(it) }
            ?: return@withContext Result.failure(IllegalStateException("not connected to the Shizuku user service"))
        val (rows, source) = readProcesses() ?: return@withContext Result.failure(IllegalStateException("ps is not available"))
        Result.success(RunningScan(groupRunningApps(rows, installed, protectedReason), source, takeLog()))
    }

    /** `am force-stop --user 0 <pkg>` one by one in the foreground, then a fresh `ps` per package. */
    suspend fun stopApps(apps: List<RunningApp>): Pair<List<StopResult>, String> = withContext(Dispatchers.IO) {
        log.setLength(0)
        val results = mutableListOf<StopResult>()
        val stopped = mutableListOf<RunningApp>()
        for (app in apps) {
            val reason = protectedReason(app.packageName)
            if (!isValidPackageName(app.packageName) || reason != null) {
                results += StopResult(app.packageName, Verdict.REFUSED, app.pids, app.pids, reason?.let { "protected: $it" } ?: "invalid package name")
                continue
            }
            val r = sh("am force-stop --user 0 ${q(app.packageName)} 2>&1")
            if (r == null || r.exitCode != 0) {
                results += StopResult(app.packageName, Verdict.FAILED, app.pids, app.pids, r?.stdout?.trim()?.take(200) ?: "not run")
            } else stopped += app
        }
        if (stopped.isNotEmpty()) {
            delay(SETTLE_MS)
            val rows = readProcesses()?.first
            for (app in stopped) {
                val now = rows?.let { stillRunning(app.packageName, it) }
                results += when {
                    now == null -> StopResult(app.packageName, Verdict.UNVERIFIABLE, app.pids, emptyList(), "process list could not be read again")
                    now.isEmpty() -> StopResult(app.packageName, Verdict.APPLIED, app.pids, now, "stopped")
                    now.none { it in app.pids } -> StopResult(app.packageName, Verdict.APPLIED, app.pids, now, "stopped, then Android or the app started it again")
                    else -> StopResult(app.packageName, Verdict.NOT_APPLIED, app.pids, now, "still running with the same process")
                }
            }
        }
        results.sortedBy { it.pkg } to takeLog()
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val LONG_TIMEOUT_MS = 120_000
        const val LOG_CHARS = 1500
        const val MAX_EMPTY = 2000
        const val CHUNK = 50
        const val SETTLE_MS = 1_200L
    }
}

/**
 * Stops are refused for every protected package: this app, Shizuku, launcher, keyboard, dialer, SMS,
 * WebView (DeviceRoles) and the static core list, plus whatever the loaded inventory marks protected.
 */
internal fun InventoryRepository.cleanerRepo(context: Context): CleanerRepository {
    val guard = ProtectedPackages(DeviceRoles.read(context))
    return CleanerRepository(toolsBridge()) { guard.reasonFor(it) ?: entryFor(it)?.protectedReason }
}
