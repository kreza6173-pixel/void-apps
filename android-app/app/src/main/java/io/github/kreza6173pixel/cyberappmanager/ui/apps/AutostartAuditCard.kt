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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.AutostartAudit
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText

/**
 * A5 read-only autostart / boot audit card.
 * Component and background writes are not exposed until a phone probe proves read-back.
 */
@Composable
fun AutostartAuditCard(result: Result<AutostartAudit>?, onRetry: (() -> Unit)? = null) {
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
                            stringResource(R.string.autostart_read_only),
                            style = MaterialTheme.typography.labelSmall,
                        )

                        /* Boot receivers */
                        if (audit.receivers.isEmpty()) {
                            Text(
                                stringResource(R.string.autostart_no_receivers),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        audit.receivers.forEach { receiver ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    receiver.component,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    receiver.action,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        /* Background execution ops */
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

                        /* Raw output toggle */
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
