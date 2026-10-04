package io.github.kreza6173pixel.cyberappmanager.inventory

import android.os.Build
import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ExecResult
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A7 per-app network control, ported from the owner's `VOID-WALL` module (`webui/wall.js`), no root.
 *
 * - Full network block: Chain 3, `cmd connectivity set-package-networking-enabled false|true <pkg>`.
 *   Chain 3 itself is switched on with `cmd connectivity set-chain3-enabled true` before the first block,
 *   exactly like VOID-WALL does at start. It is never switched off automatically.
 *   Read-back: `cmd connectivity get-package-networking-enabled <pkg>`, fallback
 *   `dumpsys connectivity trafficcontroller` (OEM_DENY_3 on the uid row).
 * - Background data: `cmd netpolicy add|remove restrict-background-blacklist <uid>`,
 *   read back from `cmd netpolicy list restrict-background-blacklist`.
 *
 * Protected packages and shared system uids (< 10000) are refused here, not only in the UI.
 */
class NetworkRepository(
    private val bridge: ExecBridge,
    private val protectedReason: (String) -> String? = { null },
    private val sdk: Int = Build.VERSION.SDK_INT,
) {

    suspend fun audit(pkg: String): Result<NetworkAudit> = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) return@withContext Result.failure(IllegalArgumentException("invalid package name"))
        val raw = StringBuilder()
        fun probe(cmd: String): ExecResult? {
            val r = sh(cmd)
            raw.append("$ ").append(cmd).append('\n')
            raw.append(r?.let { (it.stdout + it.stderr).trim() + "\n(exit ${it.exitCode})" } ?: "(not run: user service unavailable)")
            raw.append("\n\n")
            return r
        }
        val uidOut = probe(uidCmd(pkg))
        if (uidOut == null) return@withContext Result.failure(IllegalStateException("not connected to the Shizuku user service"))
        val uid = uidOut.okText()?.let { uidForPackage(pkg, it) }
        val shared = if (uid != null) probe(sharedCmd(uid)).okText()?.let { packagesSharingUid(pkg, uid, it) }.orEmpty() else emptyList()
        val supported = sdk >= CHAIN3_MIN_SDK
        val chain3 = if (supported) probe(CHAIN3_GET).okText()?.let { parseChain3Enabled(it) } else null
        var source = ""
        var blocked: Boolean? = null
        if (supported) {
            blocked = probe(blockGetCmd(pkg)).okText()?.let { parsePackageBlocked(it.replace(pkg, " ")) }
            if (blocked != null) source = "connectivity"
            if (blocked == null && uid != null) {
                blocked = parseTrafficControllerBlocked(uid, probe(TRAFFIC_CMD)?.stdout)
                if (blocked != null) source = "trafficcontroller"
            }
        }
        val bg = if (uid != null) probe(BG_LIST).okText()?.let { parseNetpolicyUidList(it) }?.contains(uid) else null
        val saver = probe(SAVER_GET).okText()?.let { parseDataSaver(it) }
        Result.success(NetworkAudit(pkg, uid, shared, supported, chain3, blocked, source, bg, saver, raw.toString().trim()))
    }

    suspend fun setNetworkBlocked(pkg: String, block: Boolean): NetworkChangeResult = withContext(Dispatchers.IO) {
        val kind = NetworkChangeKind.NETWORK_BLOCK
        fun refused(reason: String, state: Boolean? = null) = NetworkChangeResult(pkg, kind, block, Verdict.REFUSED, "", reason, state, state)
        if (!isValidPackageName(pkg)) return@withContext refused("invalid package name")
        if (sdk < CHAIN3_MIN_SDK) return@withContext refused("Chain 3 needs Android 11 or newer")
        protectedReason(pkg)?.let { return@withContext refused("protected: $it") }
        val uid = readUid(pkg) ?: return@withContext refused("uid unavailable")
        if (uid < FIRST_APPLICATION_UID) return@withContext refused("shared system uid $uid")
        val before = readBlocked(pkg, uid)
        if (before == block) return@withContext refused(if (block) "already blocked" else "already allowed", before)

        val log = StringBuilder()
        var note = ""
        val enableChain3 = block && readChain3() != true
        if (enableChain3) {
            when (val o = bridge.execBlocking(CHAIN3_ON, TIMEOUT_MS)) {
                is ExecOutcome.Failed -> return@withContext NetworkChangeResult(pkg, kind, block, Verdict.FAILED, CHAIN3_ON, o.message, before, null)
                is ExecOutcome.Completed -> log.append("$ ").append(CHAIN3_ON).append('\n').append(outputOf(o.result)).append("\n\n")
            }
        }
        val cmd = "cmd connectivity set-package-networking-enabled " + (if (block) "false " else "true ") + ShellQuoting.quote(pkg)
        val cmdLine = if (enableChain3) "$CHAIN3_ON; $cmd" else cmd
        when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return@withContext NetworkChangeResult(pkg, kind, block, Verdict.FAILED, cmdLine, log.toString() + o.message, before, null)
            is ExecOutcome.Completed -> log.append(outputOf(o.result))
        }
        val after = readBlocked(pkg, uid)
        var verdict = networkVerdict(after, block)
        if (block && verdict == Verdict.APPLIED) {
            when (readChain3()) {
                false -> { verdict = Verdict.NOT_APPLIED; note = "Chain 3 is still off, so the block has no effect" }
                null -> note = "Chain 3 state could not be read"
                true -> {}
            }
        }
        NetworkChangeResult(pkg, kind, block, verdict, cmdLine, log.toString().trim(), before, after, note)
    }

    suspend fun setBackgroundRestricted(pkg: String, restrict: Boolean): NetworkChangeResult = withContext(Dispatchers.IO) {
        val kind = NetworkChangeKind.BACKGROUND_DATA
        fun refused(reason: String, state: Boolean? = null) = NetworkChangeResult(pkg, kind, restrict, Verdict.REFUSED, "", reason, state, state)
        if (!isValidPackageName(pkg)) return@withContext refused("invalid package name")
        protectedReason(pkg)?.let { return@withContext refused("protected: $it") }
        val uid = readUid(pkg) ?: return@withContext refused("uid unavailable")
        if (uid < FIRST_APPLICATION_UID) return@withContext refused("shared system uid $uid")
        val before = readBackground(uid) ?: return@withContext refused("background data state unavailable")
        if (before == restrict) return@withContext refused(if (restrict) "already restricted" else "already allowed", before)
        val cmd = "cmd netpolicy " + (if (restrict) "add" else "remove") + " restrict-background-blacklist " + uid
        val output = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return@withContext NetworkChangeResult(pkg, kind, restrict, Verdict.FAILED, cmd, o.message, before, null)
            is ExecOutcome.Completed -> outputOf(o.result)
        }
        val after = readBackground(uid)
        NetworkChangeResult(pkg, kind, restrict, networkVerdict(after, restrict), cmd, output, before, after)
    }

    private fun readUid(pkg: String): Int? = sh(uidCmd(pkg)).okText()?.let { uidForPackage(pkg, it) }
    private fun readChain3(): Boolean? = sh(CHAIN3_GET).okText()?.let { parseChain3Enabled(it) }
    private fun readBackground(uid: Int): Boolean? = sh(BG_LIST).okText()?.let { parseNetpolicyUidList(it) }?.contains(uid)
    private fun readBlocked(pkg: String, uid: Int): Boolean? =
        sh(blockGetCmd(pkg)).okText()?.let { parsePackageBlocked(it.replace(pkg, " ")) }
            ?: parseTrafficControllerBlocked(uid, sh(TRAFFIC_CMD)?.stdout)

    private fun sh(cmd: String): ExecResult? = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) {
        is ExecOutcome.Failed -> null
        is ExecOutcome.Completed -> o.result
    }

    private fun ExecResult?.okText(): String? = this?.takeIf { it.exitCode == 0 && !it.truncated }?.stdout

    private fun outputOf(r: ExecResult): String = (r.stdout + "\n" + r.stderr).trim() + "\n(exit ${r.exitCode})"

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val CHAIN3_GET = "cmd connectivity get-chain3-enabled"
        const val CHAIN3_ON = "cmd connectivity set-chain3-enabled true"
        const val TRAFFIC_CMD = "dumpsys connectivity trafficcontroller 2>/dev/null | grep -E 'sUidOwnerMap|OEM_DENY_3'"
        const val BG_LIST = "cmd netpolicy list restrict-background-blacklist"
        const val SAVER_GET = "cmd netpolicy get restrict-background"
        fun uidCmd(pkg: String) = "pm list packages -U --user 0 " + ShellQuoting.quote(pkg)
        fun sharedCmd(uid: Int) = "pm list packages -U --user 0 | grep -F 'uid:$uid'"
        fun blockGetCmd(pkg: String) = "cmd connectivity get-package-networking-enabled " + ShellQuoting.quote(pkg)
    }
}

/** Same bridge accessor pattern as A5/A6; the protected guard comes from the loaded inventory. */
internal fun InventoryRepository.networkRepo(): NetworkRepository {
    val field = InventoryRepository::class.java.getDeclaredField("bridge")
    field.isAccessible = true
    return NetworkRepository(field.get(this) as ExecBridge, { entryFor(it)?.protectedReason })
}
