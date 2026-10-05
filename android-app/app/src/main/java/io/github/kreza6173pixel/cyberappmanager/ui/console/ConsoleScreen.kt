package io.github.kreza6173pixel.cyberappmanager.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.exec.ConnectionState
import io.github.kreza6173pixel.cyberappmanager.exec.ConsoleHistory
import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.HistoryEntry
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A built-in command. Every preset is read-only. */
private class Preset(val labelRes: Int, val command: String)

// The package-list presets are the raw inputs the M1 app registry will parse; their real
// output from the device is needed for the parser unit tests.
private val PRESETS = listOf(
    Preset(R.string.preset_id, "id"),
    Preset(R.string.preset_sdk, "getprop ro.build.version.sdk"),
    Preset(R.string.preset_model, "getprop ro.product.model"),
    Preset(R.string.preset_uptime, "uptime"),
    Preset(R.string.preset_user_apps, "pm list packages -3 -U"),
    Preset(R.string.preset_disabled_apps, "pm list packages -d"),
    Preset(R.string.preset_system_count, "pm list packages -s | wc -l"),
)

private const val TIMEOUT_MS = 15_000

private const val MAX_BIND_LOG_LINES = 12

/**
 * Console screen. Only reachable when Shizuku is READY. The whole screen is one LazyColumn so
 * nothing can be squeezed off the bottom. ConsoleHistory.items is already newest-first.
 */
@Composable
fun ConsoleScreen(bridge: ExecBridge, modifier: Modifier = Modifier) {
    val history = remember { ConsoleHistory() }
    var entries by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var command by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // Open by default; folds after a run so the result is right below the input.
    var showPresets by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // The AIDL call blocks until the command finishes, so it must never run on the main thread.
    fun run(raw: String) {
        if (busy || raw.isBlank()) return
        showPresets = false
        scope.launch {
            busy = true
            val outcome = withContext(Dispatchers.IO) { bridge.execBlocking(raw, TIMEOUT_MS) }
            when (outcome) {
                is ExecOutcome.Failed -> history.recordFailure(raw, outcome.message)
                is ExecOutcome.Completed -> history.record(
                    rawCommand = raw,
                    exitCode = outcome.result.exitCode,
                    durationMs = outcome.durationMs,
                    stdout = outcome.result.stdout,
                    stderr = outcome.result.stderr,
                    truncated = outcome.result.truncated,
                )
            }
            entries = history.items
            busy = false
        }
    }

    val state = bridge.connectionState
    val connected = state == ConnectionState.CONNECTED

    // Collapsed once connected (it has done its job), open again on any other state.
    var showLog by remember { mutableStateOf(true) }
    LaunchedEffect(state) { showLog = state != ConnectionState.CONNECTED }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionLabel(state, Modifier.weight(1f))
                TextButton(onClick = { showLog = !showLog }) {
                    Text(
                        stringResource(
                            if (showLog) R.string.console_bind_log_hide else R.string.console_bind_log_show
                        )
                    )
                }
            }
        }

        if (showLog) {
            item { BindLog(bridge.bindLog) }
        }

        item {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                enabled = !busy,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
                label = { Text(stringResource(R.string.console_command_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { run(command.trim()) },
                    enabled = !busy && connected && command.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.console_run))
                }
                OutlinedButton(
                    onClick = { bridge.cancel() },
                    enabled = busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.console_cancel))
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.console_presets_title, PRESETS.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showPresets = !showPresets }) {
                    Text(
                        stringResource(
                            if (showPresets) R.string.console_presets_hide else R.string.console_presets_show
                        )
                    )
                }
            }
        }

        if (showPresets) {
            item {
                PresetList(
                    enabled = !busy && connected,
                    onRun = { run(it) },
                    onEdit = { command = it },
                )
            }
        }

        items(entries) { entry -> HistoryCard(entry) }
    }
}

/** One aligned list: name on top, command below in mono, actions on the end edge. */
@Composable
private fun PresetList(
    enabled: Boolean,
    onRun: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
            PRESETS.forEachIndexed { index, preset ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 12.dp, end = 12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(preset.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LtrMonoText(
                            text = preset.command,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onEdit(preset.command) }) {
                        Text(stringResource(R.string.console_preset_edit))
                    }
                    TextButton(onClick = { onRun(preset.command) }, enabled = enabled) {
                        Text(stringResource(R.string.console_preset_run))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionLabel(state: ConnectionState, modifier: Modifier = Modifier) {
    val labelRes = when (state) {
        ConnectionState.DISCONNECTED -> R.string.console_disconnected
        ConnectionState.CONNECTING -> R.string.console_connecting
        ConnectionState.CONNECTED -> R.string.console_connected
    }
    Text(
        text = stringResource(R.string.console_state_format, stringResource(labelRes)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}

/** Last few bind events, newest last. */
@Composable
private fun BindLog(lines: List<String>) {
    val shown = lines.takeLast(MAX_BIND_LOG_LINES)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.console_bind_log),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (shown.isNotEmpty()) CopyShareButtons(lines.joinToString("\n"))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.console_bind_facts),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            if (shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.console_bind_log_empty),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LtrMonoText(shown.joinToString("\n"))
            }
        }
    }
}

/** Plain-text form of one result, for Copy / Share. Uses the already-redacted fields only. */
private fun HistoryEntry.asReport(): String = buildString {
    append("$ ").append(displayCommand).append('\n')
    if (failed) append("did not run\n") else append("exit $exitCode, $durationMs ms\n")
    if (truncated) append("[output truncated]\n")
    if (displayStdout.isNotEmpty()) append('\n').append(displayStdout)
    if (displayStderr.isNotEmpty()) append("\n[stderr]\n").append(displayStderr)
}

@Composable
private fun HistoryCard(entry: HistoryEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
            CopyShareButtons(entry.asReport())
            LtrMonoText("$ " + entry.displayCommand)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (entry.failed) {
                    stringResource(R.string.console_meta_no_exit)
                } else {
                    stringResource(R.string.console_meta_format, entry.exitCode, entry.durationMs)
                },
                style = MaterialTheme.typography.labelMedium,
            )
            if (entry.truncated) {
                Text(
                    text = stringResource(R.string.console_truncated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (entry.displayStdout.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                LtrMonoText(entry.displayStdout)
            }
            if (entry.displayStderr.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                LtrMonoText(entry.displayStderr, MaterialTheme.colorScheme.error)
            }
        }
    }
}
