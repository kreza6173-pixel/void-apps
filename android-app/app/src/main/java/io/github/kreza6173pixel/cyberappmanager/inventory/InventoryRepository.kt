package io.github.kreza6173pixel.cyberappmanager.inventory

import android.content.Context
import android.content.pm.PackageManager
import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface InventoryResult { data class Ok(val entries: List<AppEntry>, val counts: InventoryCounts) : InventoryResult; data class Error(val message: String) : InventoryResult }
sealed interface DetailsResult { data class Ok(val details: PackageDetails, val raw: String) : DetailsResult; data class Error(val message: String) : DetailsResult }
sealed interface PermissionAuditResult { data class Ok(val audit: PermissionAudit, val sharedUser: SharedUserInfo? = null) : PermissionAuditResult; data class Error(val message: String) : PermissionAuditResult }
sealed interface AppOpsAuditResult { data class Ok(val audit: AppOpsAudit, val raw: String, val uid: Int? = null, val packageText: String = "") : AppOpsAuditResult; data class Error(val message: String) : AppOpsAuditResult }
data class ActionResult(val action: AppAction, val verdict: Verdict, val command: String, val output: String, val readBack: String, val snapshotId: String? = null)
data class PermissionChangeResult(val pkg: String, val permission: String, val grant: Boolean, val verdict: Verdict, val command: String, val output: String, val before: Boolean?, val after: Boolean?)
data class AppOpChangeResult(val pkg: String, val op: String, val scope: AppOpScope, val mode: String, val verdict: Verdict, val command: String, val output: String, val before: String?, val after: String?)
data class RestoreStepResult(val pkg: String, val operation: SnapshotOperation, val verdict: Verdict, val detail: String)
data class RestoreReport(val planned: Int, val results: List<RestoreStepResult>, val safetySnapshotId: String?)
data class BatchItemResult(val pkg: String, val verdict: Verdict, val detail: String)
data class BatchReport(val action: AppAction, val results: List<BatchItemResult>, val snapshotId: String?)

fun actionFor(op: SnapshotOperation): AppAction = when (op) { SnapshotOperation.RESTORE_PACKAGE -> AppAction.RESTORE; SnapshotOperation.ENABLE -> AppAction.UNFREEZE; SnapshotOperation.DISABLE -> AppAction.FREEZE; SnapshotOperation.SUSPEND -> AppAction.SUSPEND; SnapshotOperation.UNSUSPEND -> AppAction.UNSUSPEND }
fun eligibleForBatch(e: AppEntry, action: AppAction): Boolean = e.protectedReason == null && action in AppActions.availableFor(e)

class InventoryRepository(private val context: Context, private val bridge: ExecBridge, private val snapshotStore: SnapshotStore = SnapshotStore(context), private val pinStore: PinStore = PinStore(context)) {
    @Volatile var cached: InventoryResult? = null; private set
    fun entryFor(pkg: String): AppEntry? = (cached as? InventoryResult.Ok)?.entries?.firstOrNull { it.pkg == pkg }
    fun snapshots(): List<Snapshot> = snapshotStore.list()
    fun deleteSnapshot(id: String): Boolean = snapshotStore.delete(id)
    fun exportSnapshots(): String = snapshotStore.exportJson()
    fun importSnapshots(text: String): Int = snapshotStore.importJson(text)
    fun pins(): Set<String> = pinStore.list()
    fun setPinned(pkg: String, pinned: Boolean): Set<String> = pinStore.set(pkg, pinned)

    suspend fun load(): InventoryResult = withContext(Dispatchers.IO) {
        val outputs = ArrayList<String>(LIST_COMMANDS.size)
        for (cmd in LIST_COMMANDS) when (val r = shell(cmd, false)) { is ShellResult.Ok -> outputs.add(r.stdout); is ShellResult.Bad -> return@withContext InventoryResult.Error(r.message).also { cached = it } }
        val all = parsePackageList(outputs[0]); val installed = parsePackageList(outputs[1]); val disabled = parsePackageList(outputs[2]); val suspended = parsePackageList(outputs[3]); val system = parsePackageList(outputs[4])
        if (installed.isEmpty()) return@withContext InventoryResult.Error("pm list packages returned no packages").also { cached = it }
        val pm = context.packageManager; val labels = HashMap<String, String>(); for (pkg in all + installed) labelOf(pm, pkg)?.let { labels[pkg] = it }
        val entries = mergeInventory(all, installed, disabled, suspended, system, labels, ProtectedPackages(DeviceRoles.read(context))::reasonFor); InventoryResult.Ok(entries, countsOf(entries)).also { cached = it }
    }

    suspend fun details(pkg: String): DetailsResult = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) return@withContext DetailsResult.Error("invalid package name: $pkg")
        when (val r = shell("dumpsys package ${ShellQuoting.quote(pkg)} | grep -E ${ShellQuoting.quote(DETAIL_PATTERN)} | head -n 20", true)) { is ShellResult.Ok -> { val p = parsePackageDetails(r.stdout); AppActions.stateFrom(p.userFlags)?.let { updateState(pkg, it) }; DetailsResult.Ok(p, r.stdout.trim()) }; is ShellResult.Bad -> DetailsResult.Error(r.message) }
    }

    /**
     * Reads the active Packages: block (fits the 64 KiB cap). For shared-uid packages the Shared users: block,
     * which holds their runtime permissions, is read in a second call; if that call fails the Packages-only
     * result is kept. When [authoritative], pm check-permission is the authority for runtime state; Self-check
     * passes false because it only checks that the output can be read and parsed.
     */
    suspend fun permissionAudit(pkg: String, authoritative: Boolean = true): PermissionAuditResult = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) return@withContext PermissionAuditResult.Error("invalid package name: $pkg")
        val q = ShellQuoting.quote(pkg)
        val raw = when (val r = shell(permissionDumpCommand(q), false)) { is ShellResult.Ok -> r.stdout; is ShellResult.Bad -> return@withContext PermissionAuditResult.Error(r.message) }
        val shared = sharedUserOf(raw)
        val sharedRaw = if (shared != null) (shell(sharedUsersDumpCommand(q), false) as? ShellResult.Ok)?.stdout else null
        val parsed = parsePermissionAudit(pkg, if (sharedRaw != null) raw + "\n\n" + sharedRaw else raw)
        val runtime = parsed.permissions.filter { it.runtime }
        if (runtime.isEmpty() || !authoritative) return@withContext PermissionAuditResult.Ok(parsed, shared)
        val checks = runtime.joinToString("; ") { p -> "printf '%s\\n' ${ShellQuoting.quote(p.name)}; pm check-permission $q ${ShellQuoting.quote(p.name)} 0" }
        val lines = when (val r = shell(checks, true)) { is ShellResult.Ok -> r.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList(); is ShellResult.Bad -> emptyList() }
        val checked = buildMap<String, Boolean?> {
            runtime.forEachIndexed { index, p ->
                val answer = lines.getOrNull(index * 2)?.let { lines.getOrNull(index * 2 + 1) }
                put(p.name, when (answer?.lowercase()) { "granted" -> true; "denied" -> false; else -> null })
            }
        }
        PermissionAuditResult.Ok(parsed.copy(permissions = parsed.permissions.map { p -> if (p.runtime && checked[p.name] != null) p.copy(granted = checked[p.name]) else p }), shared)
    }

    /**
     * Read-only AppOps audit. `appops get <package>` gives uid block + package block; `appops get <uid>` gives
     * only the uid block, which lets the scopes be separated. Without a uid answer the merged view is used.
     * Raw output of both commands is kept so ROM formats stay visible instead of being guessed.
     */
    suspend fun appOpsAudit(pkg: String): AppOpsAuditResult = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) return@withContext AppOpsAuditResult.Error("invalid package name: $pkg")
        val q = ShellQuoting.quote(pkg)
        val full = when (val r = shell("appops get $q", false)) { is ShellResult.Ok -> r.stdout; is ShellResult.Bad -> return@withContext AppOpsAuditResult.Error(r.message) }
        val uid = (shell("pm list packages -U $q", true) as? ShellResult.Ok)?.let { uidOf(pkg, it.stdout) }
        val uidText = uid?.let { (shell("appops get $it", false) as? ShellResult.Ok)?.stdout }
        val audit = if (uidText != null) parseAppOpsScoped(pkg, full, uidText) else parseAppOps(pkg, full)
        val raw = buildString {
            append("$ appops get ").append(pkg).append('\n').append(full.trim())
            if (uid != null && uidText != null) append("\n\n$ appops get ").append(uid).append(" (uid)\n").append(uidText.trim())
        }
        AppOpsAuditResult.Ok(audit, raw, uid, full)
    }

    /**
     * Guarded AppOps change for one op in package scope, then read back with a fresh scoped audit.
     * Refused: uid scope and apps on a system uid (< 10000), because the reference ROM returned success and
     * kept the mode in both cases. An op missing from a scope counts as default. APPLIED only on a matching read-back.
     */
    suspend fun setAppOp(pkg: String, op: String, scope: AppOpScope, mode: String): AppOpChangeResult = withContext(Dispatchers.IO) {
        fun refused(reason: String, before: String? = null) = AppOpChangeResult(pkg, op, scope, mode, Verdict.REFUSED, "", reason, before, before)
        if (!isValidPackageName(pkg) || !isValidAppOp(op) || !isValidAppOpMode(mode)) return@withContext refused("invalid package, operation or mode")
        if (scope == AppOpScope.UID) return@withContext refused("uid-scope changes are disabled: this ROM returned success and kept the uid mode (CAMERA, CALL_PHONE on Acode); use Grant or Revoke in Permissions")
        val e = entryFor(pkg) ?: return@withContext refused("inventory not loaded")
        e.protectedReason?.let { return@withContext refused("protected: $it") }
        val beforeResult = appOpsAudit(pkg) as? AppOpsAuditResult.Ok ?: return@withContext refused("AppOps state unavailable")
        val uid = beforeResult.uid ?: return@withContext refused("uid unavailable")
        if (uid < 10000) return@withContext refused("system uid $uid: this ROM silently kept package changes for system-uid apps (securitycenter BLUETOOTH_CONNECT)")
        if (!beforeResult.audit.scoped) return@withContext refused("uid and package scope could not be separated on this ROM")
        val before = appOpModeIn(beforeResult.audit, op, scope)
        beforeResult.audit.operations.firstOrNull { it.op == op }?.let(::packageScopeBlockedBy)?.let { uidMode ->
            return@withContext refused("uid mode $uidMode is set for this op and takes precedence; package changes have no effect and were silently kept on this ROM", before)
        }
        if ((before ?: "default") == mode) return@withContext refused("already in the requested state", before)
        val cmd = appOpSetCommand(scope, ShellQuoting.quote(pkg), uid, op, mode)
        val output = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) { is ExecOutcome.Failed -> return@withContext AppOpChangeResult(pkg, op, scope, mode, Verdict.FAILED, cmd, o.message, before, null); is ExecOutcome.Completed -> (o.result.stdout + "\n" + o.result.stderr).trim() + "\n(exit ${o.result.exitCode})" }
        val afterAudit = (appOpsAudit(pkg) as? AppOpsAuditResult.Ok)?.audit
        val after = afterAudit?.let { appOpModeIn(it, op, scope) }
        val verdict = when { afterAudit == null || !afterAudit.scoped -> Verdict.UNVERIFIABLE; (after ?: "default") == mode -> Verdict.APPLIED; else -> Verdict.NOT_APPLIED }
        AppOpChangeResult(pkg, op, scope, mode, verdict, cmd, output, before, after)
    }

    /**
     * Read-only Self-check: permission audit (parse only) and AppOps audit for every package in [scope],
     * one at a time. Nothing is changed. [shouldStop] ends the run early and keeps the partial report.
     */
    suspend fun selfCheck(scope: SelfCheckScope, shouldStop: () -> Boolean, onProgress: (Int, Int) -> Unit): SelfCheckReport = withContext(Dispatchers.IO) {
        val targets = currentEntries().orEmpty().filter { e -> e.state != AppState.REMOVED && when (scope) { SelfCheckScope.USER -> !e.isSystem; SelfCheckScope.SYSTEM -> e.isSystem; SelfCheckScope.ALL -> true } }.sortedBy { it.pkg }
        var permissionErrors = 0; var sizeCapHits = 0; var missingState = 0; var opsErrors = 0; var unsplit = 0; var unrecognised = 0; var checked = 0
        val items = ArrayList<SelfCheckItem>()
        withContext(Dispatchers.Main) { onProgress(0, targets.size) }
        for (e in targets) {
            if (shouldStop()) break
            val issues = ArrayList<String>()
            when (val p = permissionAudit(e.pkg, authoritative = false)) {
                is PermissionAuditResult.Ok -> if (p.audit.permissions.isNotEmpty() && p.audit.permissions.none { it.granted != null }) { missingState++; issues += "permissions: ${p.audit.permissions.size} listed, none with a granted state" }
                is PermissionAuditResult.Error -> { permissionErrors++; if (p.message.contains("truncated")) sizeCapHits++; issues += "permissions: " + p.message.lineSequence().first() }
            }
            when (val o = appOpsAudit(e.pkg)) {
                is AppOpsAuditResult.Ok -> {
                    if (!o.audit.scoped && o.audit.operations.isNotEmpty()) { unsplit++; issues += "appops: uid and package scope not split" }
                    val odd = unparsedAppOpLines(o.packageText)
                    if (odd.isNotEmpty()) { unrecognised++; issues += "appops: ${odd.size} unrecognised line(s): " + odd.take(3).joinToString(" | ") }
                }
                is AppOpsAuditResult.Error -> { opsErrors++; if (o.message.contains("truncated")) sizeCapHits++; issues += "appops: " + o.message.lineSequence().first() }
            }
            if (issues.isNotEmpty()) items += SelfCheckItem(e.pkg, e.isSystem, issues)
            checked++
            val done = checked
            withContext(Dispatchers.Main) { onProgress(done, targets.size) }
        }
        SelfCheckReport(scope, targets.size, checked, permissionErrors, sizeCapHits, missingState, opsErrors, unsplit, unrecognised, items)
    }

    suspend fun setPermission(pkg: String, permission: String, grant: Boolean): PermissionChangeResult = withContext(Dispatchers.IO) {
        fun refused(reason: String, before: Boolean? = null) = PermissionChangeResult(pkg, permission, grant, Verdict.REFUSED, "", reason, before, before)
        if (!isValidPackageName(pkg) || !isValidPermissionName(permission)) return@withContext refused("invalid package or permission name")
        val e = entryFor(pkg) ?: return@withContext refused("inventory not loaded"); e.protectedReason?.let { return@withContext refused("protected: $it") }
        val beforeAudit = permissionAudit(pkg) as? PermissionAuditResult.Ok ?: return@withContext refused("permission state unavailable")
        beforeAudit.sharedUser?.takeIf { it.systemUid }?.let { return@withContext refused("shared system uid ${it.name}/${it.uid}: a change would apply to every package in it") }
        val before = beforeAudit.audit.permissions.firstOrNull { it.name == permission } ?: return@withContext refused("permission state unavailable")
        if (!before.changeable) return@withContext refused(if (before.fixed) "fixed by system or policy" else "not a changeable runtime permission", before.granted)
        if (before.granted == grant) return@withContext refused("already in the requested state", before.granted)
        val cmd = (if (grant) "pm grant" else "pm revoke") + " --user 0 ${ShellQuoting.quote(pkg)} ${ShellQuoting.quote(permission)}"
        val output = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) { is ExecOutcome.Failed -> return@withContext PermissionChangeResult(pkg, permission, grant, Verdict.FAILED, cmd, o.message, before.granted, null); is ExecOutcome.Completed -> (o.result.stdout + "\n" + o.result.stderr).trim() + "\n(exit ${o.result.exitCode})" }
        val after = (permissionAudit(pkg) as? PermissionAuditResult.Ok)?.audit?.permissions?.firstOrNull { it.name == permission }?.granted
        PermissionChangeResult(pkg, permission, grant, if (after == grant) Verdict.APPLIED else if (after == null) Verdict.UNVERIFIABLE else Verdict.NOT_APPLIED, cmd, output, before.granted, after)
    }

    suspend fun perform(pkg: String, action: AppAction): ActionResult = withContext(Dispatchers.IO) { val e = entryFor(pkg); if (e == null || action !in AppActions.availableFor(e)) return@withContext ActionResult(action, Verdict.REFUSED, "", e?.protectedReason?.let { "protected: $it" } ?: "action not available for this app", ""); execute(pkg, action, if (AppActions.needsConfirmation(action)) captureSnapshot("Before ${action.name.lowercase().replace('_', ' ')}") else null) }
    suspend fun performBatch(pkgs: List<String>, action: AppAction): BatchReport = withContext(Dispatchers.IO) { val targets = pkgs.distinct(); val eligible = targets.filter { entryFor(it)?.let { e -> eligibleForBatch(e, action) } == true }.toSet(); val snap = if (eligible.isNotEmpty() && action != AppAction.FORCE_STOP) captureSnapshot("Before batch ${action.name.lowercase().replace('_', ' ')}") else null; BatchReport(action, targets.map { if (it !in eligible) BatchItemResult(it, Verdict.REFUSED, entryFor(it)?.protectedReason?.let { r -> "protected: $r" } ?: "not available for current state") else { val r = execute(it, action, snap); BatchItemResult(it, r.verdict, r.output) } }, snap) }
    private suspend fun execute(pkg: String, action: AppAction, snapshotId: String?): ActionResult { val cmd = AppActions.command(action, pkg); val output = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) { is ExecOutcome.Failed -> return ActionResult(action, Verdict.FAILED, cmd, o.message, "", snapshotId); is ExecOutcome.Completed -> (o.result.stdout + "\n" + o.result.stderr).trim() + "\n(exit ${o.result.exitCode})" }; val d = details(pkg); val flags = (d as? DetailsResult.Ok)?.details?.userFlags ?: emptyMap(); val raw = (d as? DetailsResult.Ok)?.raw ?: (d as? DetailsResult.Error)?.message.orEmpty(); val v = AppActions.verify(action, flags, output); AppActions.stateFrom(flags)?.let { updateState(pkg, it) }; return ActionResult(action, v, cmd, output, raw, snapshotId) }
    suspend fun planRestore(id: String): List<SnapshotStep>? = withContext(Dispatchers.IO) { val s = snapshotStore.get(id) ?: return@withContext null; val c = currentEntries() ?: return@withContext null; val by = c.associateBy { it.pkg }; planSnapshotRestore(s, c) { by[it]?.protectedReason != null } }
    suspend fun restoreSnapshot(id: String): RestoreReport = withContext(Dispatchers.IO) { val steps = planRestore(id) ?: return@withContext RestoreReport(0, emptyList(), null); if (steps.isEmpty()) return@withContext RestoreReport(0, emptyList(), null); RestoreReport(steps.size, steps.map { runStep(it) }, captureSnapshot("Before undo")) }
    private suspend fun runStep(s: SnapshotStep): RestoreStepResult { val a = actionFor(s.operation); if (entryFor(s.pkg)?.protectedReason != null) return RestoreStepResult(s.pkg, s.operation, Verdict.REFUSED, "protected"); val cmd = runCatching { AppActions.command(a, s.pkg) }.getOrNull() ?: return RestoreStepResult(s.pkg, s.operation, Verdict.REFUSED, "invalid package name"); val out = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) { is ExecOutcome.Failed -> return RestoreStepResult(s.pkg, s.operation, Verdict.FAILED, o.message); is ExecOutcome.Completed -> (o.result.stdout + "\n" + o.result.stderr).trim() }; val flags = (details(s.pkg) as? DetailsResult.Ok)?.details?.userFlags ?: emptyMap(); return RestoreStepResult(s.pkg, s.operation, AppActions.verify(a, flags, out), out) }
    private suspend fun currentEntries(): List<AppEntry>? = (cached as? InventoryResult.Ok)?.entries ?: (load() as? InventoryResult.Ok)?.entries
    private fun captureSnapshot(name: String): String? { val current = (cached as? InventoryResult.Ok)?.entries ?: return null; synchronized(snapshotStore) { val now = System.currentTimeMillis(); val last = snapshotStore.list().firstOrNull(); if (last != null && last.name == name && now - last.createdAtMs in 0..10_000 && last.entries == current.map { SnapshotEntry(it.pkg, it.isSystem, it.state) }.sortedBy { it.pkg }) return last.id; val id = UUID.randomUUID().toString(); snapshotStore.put(snapshotOf(id, name, now, current)); return id } }
    private fun updateState(pkg: String, state: AppState) { val ok = cached as? InventoryResult.Ok ?: return; val es = ok.entries.map { if (it.pkg == pkg) it.copy(state = state) else it }; cached = InventoryResult.Ok(es, countsOf(es)) }
    private fun labelOf(pm: PackageManager, pkg: String): String? = runCatching { @Suppress("DEPRECATION") val i = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS); pm.getApplicationLabel(i).toString() }.getOrNull()
    private sealed interface ShellResult { data class Ok(val stdout: String) : ShellResult; data class Bad(val message: String) : ShellResult }
    private fun shell(command: String, allowExitOne: Boolean): ShellResult = when (val o = bridge.execBlocking(command, TIMEOUT_MS)) { is ExecOutcome.Failed -> ShellResult.Bad(o.message); is ExecOutcome.Completed -> { val r = o.result; when { r.truncated -> ShellResult.Bad("output truncated (64 KiB cap): $command"); r.exitCode == 0 || (allowExitOne && r.exitCode == 1) -> ShellResult.Ok(r.stdout); else -> ShellResult.Bad("exit ${r.exitCode}: $command\n${r.stderr.trim()}") } } }
    private companion object { const val TIMEOUT_MS = 20_000; val LIST_COMMANDS = listOf("pm list packages -u | sed 's/^package://'", "pm list packages | sed 's/^package://'", "pm list packages -d | sed 's/^package://'", "pm list packages --suspended | sed 's/^package://'", "pm list packages -s -u | sed 's/^package://'"); const val DETAIL_PATTERN = "versionName=|versionCode=|firstInstallTime=|lastUpdateTime=|installerPackageName=|User 0:" }
}
