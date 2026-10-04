package io.github.kreza6173pixel.cyberappmanager.ui.apps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.*
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText
import kotlinx.coroutines.launch

private data class PermissionChange(val record: PermissionRecord, val grant: Boolean)
private data class ComponentToggle(val receiver: BootReceiver, val enable: Boolean)

@Composable
fun AppDetailScreen(pkg: String, repository: InventoryRepository, connected: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var entry by remember(pkg) { mutableStateOf(repository.entryFor(pkg)) }
    var details by remember(pkg) { mutableStateOf<DetailsResult?>(null) }
    var audit by remember(pkg) { mutableStateOf<PermissionAuditResult?>(null) }
    var appOps by remember(pkg) { mutableStateOf<AppOpsAuditResult?>(null) }
    var autostart by remember(pkg) { mutableStateOf<Result<AutostartAudit>?>(null) }
    var componentResult by remember(pkg) { mutableStateOf<ComponentChangeResult?>(null) }
    var busy by remember(pkg) { mutableStateOf(false) }
    var last by remember(pkg) { mutableStateOf<ActionResult?>(null) }
    var permResult by remember(pkg) { mutableStateOf<PermissionChangeResult?>(null) }
    var appOpResult by remember(pkg) { mutableStateOf<AppOpChangeResult?>(null) }
    var confirm by remember(pkg) { mutableStateOf<AppAction?>(null) }
    var permConfirm by remember(pkg) { mutableStateOf<PermissionChange?>(null) }
    var appOpChange by remember(pkg) { mutableStateOf<AppOpRecord?>(null) }
    var componentConfirm by remember(pkg) { mutableStateOf<ComponentToggle?>(null) }
    var notif by remember(pkg) { mutableStateOf<Result<NotificationAudit>?>(null) }
    var notifResult by remember(pkg) { mutableStateOf<NotificationChangeResult?>(null) }
    var listenerConfirm by remember(pkg) { mutableStateOf<ListenerService?>(null) }
    var dndConfirm by remember(pkg) { mutableStateOf<Boolean?>(null) }
    var reload by remember(pkg) { mutableStateOf(0) }
    var pinned by remember(pkg) { mutableStateOf(pkg in repository.pins()) }
    LaunchedEffect(pkg, connected, reload) { if (connected) { details = repository.details(pkg); audit = repository.permissionAudit(pkg); appOps = repository.appOpsAudit(pkg); autostart = repository.autostartRepo().audit(pkg); notif = repository.notificationRepo().audit(pkg); entry = repository.entryFor(pkg); pinned = pkg in repository.pins() } }

    val pending = confirm
    if (pending != null) AlertDialog(onDismissRequest = { confirm = null }, title = { Text(stringResource(actionLabel(pending))) }, text = { Text(stringResource(actionWarning(pending), entry?.label ?: pkg)) }, confirmButton = { TextButton({ confirm = null; busy = true; scope.launch { last = repository.perform(pkg, pending); entry = repository.entryFor(pkg); busy = false; reload++ } }) { Text(stringResource(R.string.action_confirm)) } }, dismissButton = { TextButton({ confirm = null }) { Text(stringResource(R.string.action_cancel)) } })

    val pendingPerm = permConfirm
    if (pendingPerm != null) AlertDialog(
        onDismissRequest = { permConfirm = null },
        title = { Text(stringResource(if (pendingPerm.grant) R.string.perm_grant_title else R.string.perm_revoke_title)) },
        text = { Text(stringResource(if (pendingPerm.grant) R.string.perm_grant_body else R.string.perm_revoke_body, pendingPerm.record.name, entry?.label ?: pkg)) },
        confirmButton = { TextButton({ val change = pendingPerm; permConfirm = null; busy = true; scope.launch { permResult = repository.setPermission(pkg, change.record.name, change.grant); busy = false; reload++ } }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton({ permConfirm = null }) { Text(stringResource(R.string.action_cancel)) } },
    )

    val pendingOp = appOpChange
    if (pendingOp != null) AppOpChangeDialog(pendingOp, onDismiss = { appOpChange = null }) { opMode ->
        appOpChange = null; busy = true
        scope.launch { appOpResult = repository.setAppOp(pkg, pendingOp.op, AppOpScope.PACKAGE, opMode); busy = false; reload++ }
    }

    val pendingComp = componentConfirm
    if (pendingComp != null) AlertDialog(
        onDismissRequest = { componentConfirm = null },
        title = { Text(stringResource(if (pendingComp.enable) R.string.autostart_enable_title else R.string.autostart_disable_title)) },
        text = { Text(stringResource(if (pendingComp.enable) R.string.autostart_enable_body else R.string.autostart_disable_body, pendingComp.receiver.component.substringAfter('/'))) },
        confirmButton = { TextButton({ val toggle = pendingComp; componentConfirm = null; busy = true; scope.launch { componentResult = repository.autostartRepo().setComponent(pkg, toggle.receiver.component, toggle.enable); busy = false; reload++ } }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton({ componentConfirm = null }) { Text(stringResource(R.string.action_cancel)) } },
    )

    val pendingListener = listenerConfirm
    if (pendingListener != null) AlertDialog(
        onDismissRequest = { listenerConfirm = null },
        title = { Text(stringResource(if (pendingListener.granted) R.string.notif_listener_revoke_title else R.string.notif_listener_allow_title)) },
        text = { Text(stringResource(if (pendingListener.granted) R.string.notif_listener_revoke_body else R.string.notif_listener_allow_body, pendingListener.component)) },
        confirmButton = { TextButton({ val l = pendingListener; listenerConfirm = null; busy = true; scope.launch { notifResult = repository.notificationRepo().setListener(pkg, l.component, !l.granted); busy = false; reload++ } }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton({ listenerConfirm = null }) { Text(stringResource(R.string.action_cancel)) } },
    )

    val pendingDnd = dndConfirm
    if (pendingDnd != null) AlertDialog(
        onDismissRequest = { dndConfirm = null },
        title = { Text(stringResource(if (pendingDnd) R.string.notif_dnd_allow_title else R.string.notif_dnd_revoke_title)) },
        text = { Text(stringResource(if (pendingDnd) R.string.notif_dnd_allow_body else R.string.notif_dnd_revoke_body, entry?.label ?: pkg)) },
        confirmButton = { TextButton({ val allow = pendingDnd; dndConfirm = null; busy = true; scope.launch { notifResult = repository.notificationRepo().setDndAccess(pkg, allow); busy = false; reload++ } }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton({ dndConfirm = null }) { Text(stringResource(R.string.action_cancel)) } },
    )

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val e = entry
        Text(e?.label ?: pkg, style = MaterialTheme.typography.headlineSmall)
        LtrMonoText(pkg)
        if (e != null) {
            Text(stringResource(stateLabel(e.state)) + " \u00b7 " + stringResource(if (e.isSystem) R.string.tag_system else R.string.tag_user))
            val kb = KnowledgeBase.classify(pkg, e.isSystem)
            Text(stringResource(R.string.kb_guide_format, stringResource(riskLabel(kb.risk)), kb.note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            e.protectedReason?.let { Text(stringResource(R.string.detail_protected_format, it), color = MaterialTheme.colorScheme.error) }
            if (e.protectedReason == null) OutlinedButton(onClick = { pinned = repository.setPinned(pkg, !pinned).contains(pkg) }, enabled = connected && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (pinned) R.string.action_unpin else R.string.action_pin)) }
            val actions = AppActions.availableFor(e)
            if (actions.isNotEmpty()) {
                Text(stringResource(R.string.actions_title), style = MaterialTheme.typography.titleSmall)
                actions.forEach { a -> OutlinedButton({ if (AppActions.needsConfirmation(a)) confirm = a else { busy = true; scope.launch { last = repository.perform(pkg, a); entry = repository.entryFor(pkg); busy = false; reload++ } } }, enabled = connected && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(actionLabel(a))) } }
            }
        }
        last?.let { ResultCard(it) }
        permResult?.let { PermissionResultCard(it) }
        appOpResult?.let { AppOpResultCard(it) }
        val canChange = connected && !busy && e != null && e.protectedReason == null
        PermissionAuditCard(audit, canChange = canChange) { rec -> permConfirm = PermissionChange(rec, rec.granted != true) }
        AppOpsAuditCard(appOps, canChange = canChange) { op -> appOpChange = op }
        AutostartAuditCard(
            result = autostart,
            componentResult = componentResult,
            canChange = canChange,
            onToggleComponent = { receiver, enable -> componentConfirm = ComponentToggle(receiver, enable) },
            onRetry = { reload++ },
        )
        NotificationAccessCard(
            permission = audit,
            result = notif,
            change = notifResult,
            canChange = canChange,
            onMute = { rec -> permConfirm = PermissionChange(rec, rec.granted != true) },
            onListener = { listener -> listenerConfirm = listener },
            onDnd = { allow -> dndConfirm = allow },
            onRetry = { reload++ },
        )
        when (val d = details) {
            null -> Text(stringResource(if (connected) R.string.apps_loading else R.string.apps_waiting))
            is DetailsResult.Error -> { Text(stringResource(R.string.detail_error), color = MaterialTheme.colorScheme.error); LtrMonoText(d.message); CopyShareButtons(d.message) }
            is DetailsResult.Ok -> {
                val p = d.details; val none = stringResource(R.string.value_none)
                Field(stringResource(R.string.detail_version), when { p.versionName != null && p.versionCode != null -> "${p.versionName} (${p.versionCode})"; p.versionName != null -> p.versionName; p.versionCode != null -> p.versionCode.toString(); else -> none })
                Field(stringResource(R.string.detail_sdk), "${p.minSdk ?: none} / ${p.targetSdk ?: none}")
                Field(stringResource(R.string.detail_installer), p.installer ?: none)
                Field(stringResource(R.string.detail_first), p.firstInstall ?: none)
                Field(stringResource(R.string.detail_last), p.lastUpdate ?: none)
                Field(stringResource(R.string.detail_flags), if (p.userFlags.isEmpty()) none else p.userFlags.entries.joinToString(" ") { "${it.key}=${it.value}" })
                Text(stringResource(R.string.detail_raw), style = MaterialTheme.typography.titleSmall); LtrMonoText(d.raw); CopyShareButtons(d.raw)
            }
        }
    }
}

@Composable private fun PermissionAuditCard(result: PermissionAuditResult?, canChange: Boolean, onChange: (PermissionRecord) -> Unit) {
    when (result) {
        null -> Text(stringResource(R.string.permission_audit_loading), style = MaterialTheme.typography.titleSmall)
        is PermissionAuditResult.Error -> { Text(stringResource(R.string.permission_audit_error), color = MaterialTheme.colorScheme.error); LtrMonoText(result.message); CopyShareButtons(result.message) }
        is PermissionAuditResult.Ok -> {
            val audit = result.audit; val shared = result.sharedUser; val writable = canChange && shared?.systemUid != true
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.permission_audit_title), style = MaterialTheme.typography.titleSmall)
                if (shared != null) Text(stringResource(if (shared.systemUid) R.string.permission_shared_system else R.string.permission_shared_app, shared.name, shared.uid), style = MaterialTheme.typography.labelSmall, color = if (shared.systemUid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (audit.permissions.isEmpty()) Text(stringResource(R.string.permission_audit_empty), style = MaterialTheme.typography.bodySmall)
                audit.permissions.forEach { p -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { SelectionContainer(Modifier.weight(1f)) { Text("${p.name} \u00b7 ${permissionState(p.granted)}${kindSuffix(p)}", style = MaterialTheme.typography.bodySmall, color = permissionColor(p.granted)) }; if (writable && p.changeable) TextButton({ onChange(p) }) { Text(stringResource(if (p.granted == true) R.string.perm_revoke else R.string.perm_grant)) } } }
                Text(stringResource(R.string.permission_audit_read_only), style = MaterialTheme.typography.labelSmall)
            } }
        }
    }
}

@Composable private fun AppOpsAuditCard(result: AppOpsAuditResult?, canChange: Boolean, onChange: (AppOpRecord) -> Unit) {
    var showRaw by remember(result) { mutableStateOf(false) }
    when (result) {
        null -> Text(stringResource(R.string.appops_loading), style = MaterialTheme.typography.titleSmall)
        is AppOpsAuditResult.Error -> { Text(stringResource(R.string.appops_error), color = MaterialTheme.colorScheme.error); LtrMonoText(result.message); CopyShareButtons(result.message) }
        is AppOpsAuditResult.Ok -> { val ops = result.audit.operations; val editable = canChange && result.audit.scoped && result.uid != null; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(stringResource(R.string.appops_title), style = MaterialTheme.typography.titleSmall); if (ops.isEmpty()) Text(stringResource(R.string.appops_empty), style = MaterialTheme.typography.bodySmall); ops.forEach { op -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { SelectionContainer(Modifier.weight(1f)) { Text(appOpLabel(op), style = MaterialTheme.typography.bodySmall, color = appOpColor(op.mode)) }; if (editable && op.changeable && packageScopeBlockedBy(op) == null) TextButton({ onChange(op) }) { Text(stringResource(R.string.appop_change)) } } }; Text(stringResource(R.string.appops_read_only), style = MaterialTheme.typography.labelSmall); TextButton({ showRaw = !showRaw }) { Text(stringResource(if (showRaw) R.string.appops_raw_hide else R.string.appops_raw_show)) }; if (showRaw) { LtrMonoText(result.raw); CopyShareButtons(result.raw) } } } }
    }
}

@Composable private fun AppOpChangeDialog(op: AppOpRecord, onDismiss: () -> Unit, onConfirm: (String) -> Unit) { var opMode by remember(op) { mutableStateOf(op.packageMode ?: "default") }; AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.appop_change_title, op.op)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(stringResource(R.string.appop_scope_label), style = MaterialTheme.typography.labelMedium); ChoiceRow(stringResource(R.string.appop_scope_package), true, true) {}; ChoiceRow(stringResource(R.string.appop_scope_uid_unsupported), false, false) {}; Text(stringResource(R.string.appop_mode_label), style = MaterialTheme.typography.labelMedium); AppOpRecord.CHANGEABLE_MODES.forEach { m -> ChoiceRow(m, opMode == m, true) { opMode = m } }; Text(stringResource(R.string.appop_change_note), style = MaterialTheme.typography.labelSmall) } }, confirmButton = { TextButton({ onConfirm(opMode) }) { Text(stringResource(R.string.action_confirm)) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } }) }
@Composable private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selected, onClick = onClick, enabled = enabled); Text(label, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun AppOpResultCard(r: AppOpChangeResult) { val report = "$ ${r.command}\n${r.output}\n\nbefore: ${r.before ?: "default (not listed)"}\nafter: ${r.after ?: "default (not listed)"}"; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(stringResource(R.string.appop_result_format, r.op, r.scope.name.lowercase(), r.mode, stringResource(verdictRes(r.verdict))), color = if (r.verdict == Verdict.APPLIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error); if (r.verdict == Verdict.NOT_APPLIED && r.before == r.after && r.output.endsWith("(exit 0)")) Text(stringResource(R.string.appop_silently_kept), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error); LtrMonoText(report); CopyShareButtons(report) } } }
@Composable private fun PermissionResultCard(r: PermissionChangeResult) { val report = "$ ${r.command}\n${r.output}\n\nbefore: ${permissionState(r.before)}\nafter: ${permissionState(r.after)}"; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(stringResource(if (r.grant) R.string.perm_grant else R.string.perm_revoke) + " " + r.permission.substringAfterLast('.') + ": " + stringResource(verdictRes(r.verdict)), color = if (r.verdict == Verdict.APPLIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error); LtrMonoText(report); CopyShareButtons(report) } } }
private fun permissionState(granted: Boolean?) = when (granted) { true -> "granted"; false -> "not granted"; null -> "unknown" }
private fun kindSuffix(p: PermissionRecord): String = when { p.runtime && p.fixed -> " \u00b7 runtime \u00b7 fixed"; p.runtime -> " \u00b7 runtime"; p.granted != null -> " \u00b7 install-time"; else -> "" }
private fun appOpLabel(op: AppOpRecord): String = buildString { append(op.op); if (op.scoped) { op.uidMode?.let { append(" \u00b7 uid: ").append(it) }; op.packageMode?.let { append(" \u00b7 package: ").append(it) } } else { append(" \u00b7 ").append(op.mode); if (op.alsoReported.isNotEmpty()) append(" (also reported: " + op.alsoReported.joinToString(", ") + ")") }; if (op.oem) append(" \u00b7 OEM \u00b7 read-only") }
@Composable private fun permissionColor(granted: Boolean?) = when (granted) { true -> MaterialTheme.colorScheme.primary; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.onSurfaceVariant }
@Composable private fun appOpColor(mode: String) = when (mode) { "allow" -> MaterialTheme.colorScheme.primary; "foreground" -> MaterialTheme.colorScheme.tertiary; "deny", "ignore" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }
private fun verdictRes(v: Verdict) = when (v) { Verdict.APPLIED -> R.string.verdict_applied; Verdict.NOT_APPLIED -> R.string.verdict_not_applied; Verdict.UNVERIFIABLE -> R.string.verdict_unverifiable; Verdict.REFUSED -> R.string.verdict_refused; Verdict.FAILED -> R.string.verdict_failed }
@Composable private fun ResultCard(r: ActionResult) { val report = "$ ${r.command}\n${r.output}\n\nread-back:\n${r.readBack}"; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(stringResource(actionLabel(r.action)) + ": " + stringResource(verdictRes(r.verdict)), color = if (r.verdict == Verdict.APPLIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error); LtrMonoText(report); CopyShareButtons(report) } } }
@Composable private fun Field(label: String, value: String) { Column { Text(label, style = MaterialTheme.typography.labelMedium); LtrMonoText(value) } }
private fun riskLabel(r: Risk) = when (r) { Risk.SAFE -> R.string.risk_safe; Risk.CAUTION -> R.string.risk_caution; Risk.CORE -> R.string.risk_core; Risk.USER -> R.string.risk_user }
private fun stateLabel(s: AppState) = when (s) { AppState.ENABLED -> R.string.detail_state_enabled; AppState.FROZEN -> R.string.detail_state_frozen; AppState.SUSPENDED -> R.string.detail_state_suspended; AppState.REMOVED -> R.string.detail_state_removed }
private fun actionLabel(a: AppAction) = when (a) { AppAction.SUSPEND -> R.string.action_suspend; AppAction.UNSUSPEND -> R.string.action_unsuspend; AppAction.FREEZE -> R.string.action_freeze; AppAction.UNFREEZE -> R.string.action_unfreeze; AppAction.FORCE_STOP -> R.string.action_force_stop; AppAction.REMOVE -> R.string.action_remove; AppAction.RESTORE -> R.string.action_restore; AppAction.CLEAR_DATA -> R.string.action_clear }
private fun actionWarning(a: AppAction) = when (a) { AppAction.SUSPEND -> R.string.warn_suspend; AppAction.FREEZE -> R.string.warn_freeze; AppAction.REMOVE -> R.string.warn_remove; AppAction.CLEAR_DATA -> R.string.warn_clear; else -> R.string.warn_generic }
