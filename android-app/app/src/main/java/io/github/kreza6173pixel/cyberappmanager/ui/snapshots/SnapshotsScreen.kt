package io.github.kreza6173pixel.cyberappmanager.ui.snapshots

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.inventory.RestoreReport
import io.github.kreza6173pixel.cyberappmanager.inventory.Snapshot
import io.github.kreza6173pixel.cyberappmanager.inventory.SnapshotStep
import io.github.kreza6173pixel.cyberappmanager.inventory.Verdict
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SnapshotsScreen(repository: InventoryRepository, connected: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var snapshots by remember { mutableStateOf(emptyList<Snapshot>()) }
    var selected by remember { mutableStateOf<Snapshot?>(null) }
    var deleteTarget by remember { mutableStateOf<Snapshot?>(null) }
    var restoreTarget by remember { mutableStateOf<Snapshot?>(null) }
    var restorePlan by remember { mutableStateOf(emptyList<SnapshotStep>()) }
    var report by remember { mutableStateOf<RestoreReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) { snapshots = repository.snapshots() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val count = if (text == null) -1 else repository.importSnapshots(text)
            message = if (count < 0) context.getString(R.string.snapshot_import_failed) else context.getString(R.string.snapshot_import_done, count)
            refresh++
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(repository.exportSnapshots()) } != null }.getOrDefault(false)
            message = context.getString(if (ok) R.string.snapshot_export_done else R.string.snapshot_export_failed)
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text(stringResource(R.string.snapshot_delete_title)) }, text = { Text(stringResource(R.string.snapshot_delete_warning, target.name)) }, confirmButton = { TextButton(onClick = { repository.deleteSnapshot(target.id); snapshots = repository.snapshots(); if (selected?.id == target.id) selected = null; deleteTarget = null }) { Text(stringResource(R.string.action_delete)) } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } })
    }

    restoreTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!busy) restoreTarget = null },
            title = { Text(stringResource(R.string.snapshot_restore_title)) },
            text = {
                Text(
                    when {
                        busy -> stringResource(R.string.snapshot_restore_working)
                        restorePlan.isEmpty() -> stringResource(R.string.snapshot_restore_nothing)
                        else -> stringResource(R.string.snapshot_restore_warning, restorePlan.size)
                    }
                )
            },
            confirmButton = {
                if (restorePlan.isNotEmpty()) TextButton(enabled = connected && !busy, onClick = {
                    busy = true
                    scope.launch {
                        report = repository.restoreSnapshot(target.id)
                        busy = false
                        restoreTarget = null
                        refresh++
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { restoreTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    report?.let { r ->
        val applied = r.results.count { it.verdict == Verdict.APPLIED }
        val lines = r.results.joinToString("\n") { "${it.pkg} · ${it.operation.name.lowercase()} · ${it.verdict.name.lowercase()}" }
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text(stringResource(R.string.snapshot_restore_result_title)) },
            text = { SelectionContainer { Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.snapshot_restore_result, applied, r.results.size - applied)); Text(lines) } } },
            confirmButton = { TextButton(onClick = { report = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.snapshots_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_refresh)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.snapshot_import)) }
            OutlinedButton(onClick = { exportLauncher.launch("void-apps-snapshots.json") }, enabled = snapshots.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.snapshot_export)) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        if (snapshots.isEmpty()) Text(stringResource(R.string.snapshots_empty)) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(snapshots, key = { it.id }) { snapshot -> Card(onClick = { selected = snapshot }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(snapshot.name, style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.snapshot_meta, snapshot.entries.size, formatDate(snapshot.createdAtMs))); OutlinedButton(onClick = { deleteTarget = snapshot }) { Text(stringResource(R.string.action_delete)) } } } } }
    }

    selected?.let { snapshot ->
        val detailText = snapshot.entries.joinToString("\n") { "${it.pkg} · ${it.state.name.lowercase()}" }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(snapshot.name) },
            text = { SelectionContainer { Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { Text(detailText) } } },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.action_close)) } },
            dismissButton = {
                TextButton(enabled = connected && !busy, onClick = {
                    val target = snapshot
                    selected = null
                    busy = true
                    scope.launch {
                        restorePlan = repository.planRestore(target.id) ?: emptyList()
                        busy = false
                        restoreTarget = target
                    }
                }) { Text(stringResource(R.string.action_restore)) }
            },
        )
    }
}

private fun formatDate(timeMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timeMs))
