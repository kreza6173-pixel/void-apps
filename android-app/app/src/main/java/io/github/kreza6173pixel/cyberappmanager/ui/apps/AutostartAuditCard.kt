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
import io.github.kreza6173pixel.cyberappmanager.inventory.AutostartAudit
import io.github.kreza6173pixel.cyberappmanager.inventory.BootReceiver
import io.github.kreza6173pixel.cyberappmanager.inventory.ComponentChangeResult
import io.github.kreza6173pixel.cyberappmanager.inventory.Verdict
import io.github.kreza6173pixel.cyberappmanager.inventory.isComponentDisabled
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText

@Composable
fun AutostartAuditCard(
    result: Result<AutostartAudit>?,
    componentResult: ComponentChangeResult? = null,
    canChange: Boolean = false,
    onToggleComponent: ((BootReceiver, Boolean) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    var showRaw by remember(result) { mutableStateOf(false) }

    when (result) {
        null -> Text(
            stringResource(R.string.autostart_loading),
            style = MaterialTheme.typography.titleSmall,
        )
        else -> result.fold(
            onSuccess = { audit ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.autostart_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.autostart_note),
                            style = MaterialTheme.typography.labelSmall,
                        )

                        componentResult?.let { ComponentResultCard(it) }

                        if (audit.receivers.isEmpty()) {
                            Text(
                                stringResource(R.string.autostart_no_receivers),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        audit.receivers.forEach { receiver ->
                            val disabled = isComponentDisabled(
                                receiver.component, audit.disabledComponents,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        receiver.component,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (disabled) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        receiver.action + if (disabled) " (disabled)" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (canChange && onToggleComponent != null) {
                                    TextButton(
                                        onClick = { onToggleComponent(receiver, disabled) },
                                    ) {
                                        Text(
                                            stringResource(
                                                if (disabled) R.string.autostart_enable
                                                else R.string.autostart_disable,
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        if (audit.backgroundOps.isNotEmpty()) {
                            Text(
                                stringResource(R.string.autostart_background_title),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            audit.backgroundOps.forEach { op ->
                                Text(
                                    "${op.op}: ${op.mode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (op.mode == "allow") {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                        }

                        TextButton(onClick = { showRaw = !showRaw }) {
                            Text(
                                stringResource(
                                    if (showRaw) R.string.autostart_raw_hide
                                    else R.string.autostart_raw_show,
                                ),
                            )
                        }
                        if (showRaw) {
                            LtrMonoText(audit.raw)
                            CopyShareButtons(audit.raw)
                        }
                    }
                }
            },
            onFailure = { error ->
                Text(
                    stringResource(R.string.autostart_error, error.message ?: "unknown"),
                    color = MaterialTheme.colorScheme.error,
                )
                onRetry?.let {
                    TextButton(onClick = it) { Text(stringResource(R.string.action_refresh)) }
                }
            },
        )
    }
}

@Composable
private fun ComponentResultCard(r: ComponentChangeResult) {
    val action = if (r.enable) "Enable" else "Disable"
    val beforeState = if (r.wasBefore) "disabled" else "enabled"
    val afterState = when (r.isAfter) { true -> "disabled"; false -> "enabled"; null -> "unknown" }
    val report = "$ ${r.command}\n${r.output}\n\nbefore: $beforeState\nafter: $afterState"
    val applied = r.verdict == Verdict.APPLIED
    Text(
        "$action ${r.component.substringAfter('/')}: ${verdictText(r.verdict)}",
        style = MaterialTheme.typography.bodySmall,
        color = if (applied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    if (r.verdict == Verdict.NOT_APPLIED) {
        Text(
            stringResource(R.string.autostart_silently_kept),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    LtrMonoText(report)
    CopyShareButtons(report)
}

private fun verdictText(v: Verdict) = when (v) {
    Verdict.APPLIED -> "applied, confirmed by read-back."
    Verdict.NOT_APPLIED -> "NOT applied. Read-back does not show the change."
    Verdict.UNVERIFIABLE -> "reported success. Could not verify."
    Verdict.REFUSED -> "refused, nothing was run."
    Verdict.FAILED -> "could not run the command."
}
