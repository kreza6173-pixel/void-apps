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
import io.github.kreza6173pixel.cyberappmanager.inventory.FIRST_APPLICATION_UID
import io.github.kreza6173pixel.cyberappmanager.inventory.NetworkAudit
import io.github.kreza6173pixel.cyberappmanager.inventory.NetworkChangeKind
import io.github.kreza6173pixel.cyberappmanager.inventory.NetworkChangeResult
import io.github.kreza6173pixel.cyberappmanager.inventory.Verdict
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText

/**
 * A7 per-app network card: full network block (Chain 3) and background data (netpolicy).
 * A button is offered only when the current state was read from Android.
 */
@Composable
fun NetworkAccessCard(
    result: Result<NetworkAudit>?,
    change: NetworkChangeResult?,
    canChange: Boolean,
    onBlock: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    var showRaw by remember(result) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.net_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.net_note), style = MaterialTheme.typography.labelSmall)
            if (change != null) NetworkChangeReport(change)
            val audit = result?.getOrNull()
            if (result == null) {
                Text(stringResource(R.string.net_loading), style = MaterialTheme.typography.bodySmall)
            } else if (audit == null) {
                Text(
                    stringResource(R.string.net_error, result.exceptionOrNull()?.message ?: "unknown"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_refresh)) }
            } else {
                NetworkAuditBody(audit, canChange, onBlock, onBackground)
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(stringResource(if (showRaw) R.string.net_raw_hide else R.string.net_raw_show))
                }
                if (showRaw) {
                    LtrMonoText(audit.raw)
                    CopyShareButtons(audit.raw)
                }
            }
        }
    }
}

@Composable
private fun NetworkAuditBody(audit: NetworkAudit, canChange: Boolean, onBlock: (Boolean) -> Unit, onBackground: (Boolean) -> Unit) {
    val uid = audit.uid
    val appUid = uid != null && uid >= FIRST_APPLICATION_UID
    when {
        uid == null -> Text(stringResource(R.string.net_uid_unknown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        !appUid -> Text(stringResource(R.string.net_uid, uid.toString()) + " · " + stringResource(R.string.net_uid_system), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        else -> Text(stringResource(R.string.net_uid, uid.toString()), style = MaterialTheme.typography.labelSmall)
    }
    if (audit.sharedWith.isNotEmpty()) {
        Text(
            stringResource(R.string.net_shared_warning, audit.sharedWith.joinToString(", ")),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    val writable = canChange && appUid

    Text(stringResource(R.string.net_block_title), style = MaterialTheme.typography.labelMedium)
    if (!audit.chain3Supported) {
        Text(stringResource(R.string.net_chain3_unsupported), style = MaterialTheme.typography.bodySmall)
    } else {
        val blocked = audit.networkBlocked
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when (blocked) {
                            true -> R.string.net_blocked
                            false -> R.string.net_allowed
                            null -> R.string.net_block_unknown
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (blocked == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                if (audit.blockSource.isNotEmpty()) {
                    Text(stringResource(R.string.net_source, audit.blockSource), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (writable && blocked != null) {
                TextButton(onClick = { onBlock(!blocked) }) {
                    Text(stringResource(if (blocked) R.string.net_unblock else R.string.net_block))
                }
            }
        }
        Text(
            stringResource(
                R.string.net_chain3_state,
                stringResource(
                    when (audit.chain3Enabled) {
                        true -> R.string.net_chain3_on
                        false -> R.string.net_chain3_off
                        null -> R.string.net_chain3_unknown
                    },
                ),
            ),
            style = MaterialTheme.typography.labelSmall,
        )
        if (blocked == true) Text(stringResource(R.string.net_reboot_note), style = MaterialTheme.typography.labelSmall)
    }

    Text(stringResource(R.string.net_bg_title), style = MaterialTheme.typography.labelMedium)
    val bg = audit.backgroundRestricted
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(
                when (bg) {
                    true -> R.string.net_bg_restricted
                    false -> R.string.net_bg_allowed
                    null -> R.string.net_bg_unknown
                },
            ),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (bg == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        if (writable && bg != null) {
            TextButton(onClick = { onBackground(!bg) }) {
                Text(stringResource(if (bg) R.string.net_allow else R.string.net_restrict))
            }
        }
    }
    audit.dataSaver?.let {
        Text(
            stringResource(R.string.net_saver, stringResource(if (it) R.string.net_on else R.string.net_off)),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun NetworkChangeReport(r: NetworkChangeResult) {
    val what = stringResource(if (r.kind == NetworkChangeKind.NETWORK_BLOCK) R.string.net_block_title else R.string.net_bg_title)
    val action = stringResource(
        when (r.kind) {
            NetworkChangeKind.NETWORK_BLOCK -> if (r.restrict) R.string.net_block else R.string.net_unblock
            NetworkChangeKind.BACKGROUND_DATA -> if (r.restrict) R.string.net_restrict else R.string.net_allow
        },
    )
    Text(
        "$what · $action: " + stringResource(netVerdictRes(r.verdict)),
        style = MaterialTheme.typography.bodySmall,
        color = if (r.verdict == Verdict.APPLIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    if (r.verdict == Verdict.NOT_APPLIED && r.output.endsWith("(exit 0)")) {
        Text(stringResource(R.string.net_silently_kept), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    val isBlock = r.kind == NetworkChangeKind.NETWORK_BLOCK
    val report = buildString {
        append("$ ").append(r.command).append('\n').append(r.output)
        append("\n\nbefore: ").append(netState(isBlock, r.before)).append("\nafter: ").append(netState(isBlock, r.after))
        if (r.note.isNotEmpty()) append("\nnote: ").append(r.note)
    }
    LtrMonoText(report)
    CopyShareButtons(report)
}

private fun netState(isBlock: Boolean, v: Boolean?) = when (v) {
    true -> if (isBlock) "blocked" else "restricted"
    false -> "allowed"
    null -> "unknown"
}

private fun netVerdictRes(v: Verdict) = when (v) {
    Verdict.APPLIED -> R.string.verdict_applied
    Verdict.NOT_APPLIED -> R.string.verdict_not_applied
    Verdict.UNVERIFIABLE -> R.string.verdict_unverifiable
    Verdict.REFUSED -> R.string.verdict_refused
    Verdict.FAILED -> R.string.verdict_failed
}
