package io.github.kreza6173pixel.cyberappmanager.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.ListenerService
import io.github.kreza6173pixel.cyberappmanager.inventory.NOTIF_POST_PERMISSION
import io.github.kreza6173pixel.cyberappmanager.inventory.NotificationAccessKind
import io.github.kreza6173pixel.cyberappmanager.inventory.NotificationAudit
import io.github.kreza6173pixel.cyberappmanager.inventory.NotificationChangeResult
import io.github.kreza6173pixel.cyberappmanager.inventory.PermissionAuditResult
import io.github.kreza6173pixel.cyberappmanager.inventory.PermissionRecord
import io.github.kreza6173pixel.cyberappmanager.inventory.Verdict
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText

/**
 * A6 per-app notification card: posting (mute), listener access, Do Not Disturb access.
 * Mute uses the permission audit already loaded for the Permissions card, so both cards always agree.
 */
@Composable
fun NotificationAccessCard(
    permission: PermissionAuditResult?,
    result: Result<NotificationAudit>?,
    change: NotificationChangeResult?,
    canChange: Boolean,
    onMute: (PermissionRecord) -> Unit,
    onListener: (ListenerService) -> Unit,
    onDnd: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    var showRaw by remember(result) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.notif_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.notif_note), style = MaterialTheme.typography.labelSmall)
            if (change != null) NotificationChangeReport(change)

            Text(stringResource(R.string.notif_post_title), style = MaterialTheme.typography.labelMedium)
            PostNotificationsRow(permission, canChange, onMute)

            if (result == null) {
                Text(stringResource(R.string.notif_loading), style = MaterialTheme.typography.bodySmall)
            } else {
                val audit = result.getOrNull()
                if (audit == null) {
                    Text(
                        stringResource(R.string.notif_error, result.exceptionOrNull()?.message ?: "unknown"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_refresh)) }
                } else {
                    ListenerSection(audit, canChange, onListener)
                    DndSection(audit, canChange, onDnd)
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(stringResource(if (showRaw) R.string.notif_raw_hide else R.string.notif_raw_show))
                    }
                    if (showRaw) {
                        LtrMonoText(audit.raw)
                        CopyShareButtons(audit.raw)
                    }
                }
            }
        }
    }
}

@Composable
private fun PostNotificationsRow(permission: PermissionAuditResult?, canChange: Boolean, onMute: (PermissionRecord) -> Unit) {
    when (permission) {
        null -> Text(stringResource(R.string.notif_loading), style = MaterialTheme.typography.bodySmall)
        is PermissionAuditResult.Error -> Text(
            stringResource(R.string.notif_post_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        is PermissionAuditResult.Ok -> {
            val record = permission.audit.permissions.firstOrNull { it.name == NOTIF_POST_PERMISSION }
            if (record == null) {
                Text(stringResource(R.string.notif_post_na), style = MaterialTheme.typography.bodySmall)
            } else {
                val writable = canChange && permission.sharedUser?.systemUid != true && record.changeable
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            when (record.granted) {
                                true -> R.string.notif_post_allowed
                                false -> R.string.notif_post_muted
                                null -> R.string.notif_post_unknown
                            },
                        ),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (record.granted == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    if (writable) {
                        TextButton(onClick = { onMute(record) }) {
                            Text(stringResource(if (record.granted == true) R.string.notif_mute else R.string.notif_unmute))
                        }
                    }
                }
                if (!record.changeable) Text(stringResource(R.string.notif_post_fixed), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ListenerSection(audit: NotificationAudit, canChange: Boolean, onListener: (ListenerService) -> Unit) {
    Text(stringResource(R.string.notif_listener_title), style = MaterialTheme.typography.labelMedium)
    if (!audit.listenerStateKnown) {
        Text(stringResource(R.string.notif_listener_unknown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    } else if (audit.listeners.isEmpty()) {
        Text(stringResource(R.string.notif_listener_none), style = MaterialTheme.typography.bodySmall)
    }
    audit.listeners.forEach { listener ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(listener.component, style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(if (listener.granted) R.string.notif_granted else R.string.notif_not_granted),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (listener.granted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canChange && audit.listenerStateKnown) {
                TextButton(onClick = { onListener(listener) }) {
                    Text(stringResource(if (listener.granted) R.string.notif_revoke else R.string.notif_allow))
                }
            }
        }
    }
}

@Composable
private fun DndSection(audit: NotificationAudit, canChange: Boolean, onDnd: (Boolean) -> Unit) {
    Text(stringResource(R.string.notif_dnd_title), style = MaterialTheme.typography.labelMedium)
    val granted = audit.dndGranted
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(
                when {
                    granted == null -> R.string.notif_dnd_unknown
                    granted == true -> R.string.notif_dnd_granted
                    audit.dndRequested -> R.string.notif_dnd_not_granted
                    else -> R.string.notif_dnd_not_requested
                },
            ),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (granted == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val offer = canChange && granted != null && (granted == true || audit.dndRequested)
        if (offer) {
            TextButton(onClick = { onDnd(granted != true) }) {
                Text(stringResource(if (granted == true) R.string.notif_revoke else R.string.notif_allow))
            }
        }
    }
}

@Composable
private fun NotificationChangeReport(r: NotificationChangeResult) {
    val what = stringResource(if (r.kind == NotificationAccessKind.LISTENER) R.string.notif_listener_title else R.string.notif_dnd_title)
    val action = stringResource(if (r.allow) R.string.notif_allow else R.string.notif_revoke)
    Text(
        "$what \u00b7 $action: " + stringResource(notifVerdictRes(r.verdict)),
        style = MaterialTheme.typography.bodySmall,
        color = if (r.verdict == Verdict.APPLIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    if (r.verdict == Verdict.NOT_APPLIED && r.output.endsWith("(exit 0)")) {
        Text(stringResource(R.string.notif_silently_kept), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    val report = "$ ${r.command}\n${r.output}\n\nbefore: ${accessState(r.before)}\nafter: ${accessState(r.after)}"
    LtrMonoText(report)
    CopyShareButtons(report)
}

private fun accessState(v: Boolean?) = when (v) { true -> "granted"; false -> "not granted"; null -> "unknown" }

private fun notifVerdictRes(v: Verdict) = when (v) {
    Verdict.APPLIED -> R.string.verdict_applied
    Verdict.NOT_APPLIED -> R.string.verdict_not_applied
    Verdict.UNVERIFIABLE -> R.string.verdict_unverifiable
    Verdict.REFUSED -> R.string.verdict_refused
    Verdict.FAILED -> R.string.verdict_failed
}
