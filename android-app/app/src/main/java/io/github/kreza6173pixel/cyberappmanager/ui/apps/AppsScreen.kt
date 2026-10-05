package io.github.kreza6173pixel.cyberappmanager.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.*
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText
import kotlinx.coroutines.launch

private val BATCH_ACTIONS = listOf(AppAction.SUSPEND, AppAction.UNSUSPEND, AppAction.FORCE_STOP)

@Composable
fun AppsScreen(repository: InventoryRepository, connected: Boolean, modifier: Modifier = Modifier, onOpenApp: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf(repository.cached) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var pendingBatch by remember { mutableStateOf<AppAction?>(null) }
    var batchReport by remember { mutableStateOf<BatchReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(reload, connected) { if (connected && (reload > 0 || result == null)) { loading = true; result = repository.load(); loading = false } }

    pendingBatch?.let { action ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingBatch = null },
            title = { Text(stringResource(R.string.batch_confirm_title)) },
            text = { Text(if (busy) stringResource(R.string.snapshot_restore_working) else stringResource(R.string.batch_confirm_body, stringResource(batchLabel(action)), selected.size)) },
            confirmButton = {
                TextButton(enabled = connected && !busy, onClick = {
                    busy = true
                    val targets = selected.toList()
                    scope.launch {
                        batchReport = repository.performBatch(targets, action)
                        result = repository.cached
                        busy = false
                        pendingBatch = null
                        selected = emptySet()
                        selecting = false
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingBatch = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    batchReport?.let { r ->
        val applied = r.results.count { it.verdict == Verdict.APPLIED }
        val lines = r.results.joinToString("\n") { "${it.pkg} · ${it.verdict.name.lowercase()}" }
        AlertDialog(
            onDismissRequest = { batchReport = null },
            title = { Text(stringResource(R.string.batch_result_title)) },
            text = { SelectionContainer { Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.snapshot_restore_result, applied, r.results.size - applied)); Text(lines) } } },
            confirmButton = { TextButton(onClick = { batchReport = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    Column(modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.apps_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val current = result
        val counts = (current as? InventoryResult.Ok)?.counts
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in AppFilter.entries) { val label = stringResource(filterLabel(f)) + (counts?.let { " ${f.count(it)}" } ?: ""); if (f == filter) Button({ filter = f }) { Text(label) } else OutlinedButton({ filter = f }) { Text(label) } }
        }
        when {
            !connected -> Text(stringResource(R.string.apps_waiting))
            current == null -> Text(stringResource(R.string.apps_loading))
            current is InventoryResult.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.apps_error), color = MaterialTheme.colorScheme.error); LtrMonoText(current.message); CopyShareButtons(current.message); OutlinedButton({ reload++ }, enabled = !loading) { Text(stringResource(R.string.action_refresh)) } }
            current is InventoryResult.Ok -> {
                val shown = remember(current, filter, query) { filterApps(current.entries, filter, query) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.apps_shown_format, shown.size, current.entries.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton({ selecting = !selecting; if (!selecting) selected = emptySet() }, enabled = !busy) { Text(stringResource(if (selecting) R.string.action_done else R.string.action_select)) }
                    TextButton({ reload++ }, enabled = !loading && !busy) { Text(stringResource(if (loading) R.string.apps_loading else R.string.action_refresh)) }
                }
                if (selecting) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.batch_selected, selected.size), style = MaterialTheme.typography.bodySmall)
                        for (a in BATCH_ACTIONS) OutlinedButton({ pendingBatch = a }, enabled = connected && selected.isNotEmpty() && !busy) { Text(stringResource(batchLabel(a))) }
                        TextButton({ selected = emptySet() }, enabled = selected.isNotEmpty() && !busy) { Text(stringResource(R.string.action_clear_selection)) }
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.pkg }) { e ->
                        AppRow(e, selecting, e.pkg in selected) {
                            if (selecting) {
                                if (e.protectedReason == null) selected = if (e.pkg in selected) selected - e.pkg else selected + e.pkg
                            } else onOpenApp(e.pkg)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable private fun AppRow(e: AppEntry, selecting: Boolean, checked: Boolean, onClick: () -> Unit) {
    val tags = mutableListOf(stringResource(if (e.isSystem) R.string.tag_system else R.string.tag_user))
    when (e.state) { AppState.FROZEN -> tags += stringResource(R.string.tag_frozen); AppState.SUSPENDED -> tags += stringResource(R.string.tag_suspended); AppState.REMOVED -> tags += stringResource(R.string.tag_removed); AppState.ENABLED -> Unit }
    e.protectedReason?.let { tags += stringResource(R.string.tag_protected_format, it) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Protected packages can never join a batch, so they get an empty slot instead of a checkbox.
        if (selecting) Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { if (e.protectedReason == null) Checkbox(checked = checked, onCheckedChange = null) }
        Column(Modifier.weight(1f)) {
            if (e.label != e.pkg) Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LtrMonoText(e.pkg)
            Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = if (e.protectedReason != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
}

private fun batchLabel(a: AppAction): Int = when (a) { AppAction.SUSPEND -> R.string.action_suspend; AppAction.UNSUSPEND -> R.string.action_unsuspend; AppAction.FORCE_STOP -> R.string.action_force_stop; else -> R.string.action_confirm }
private fun filterLabel(f: AppFilter): Int = when (f) { AppFilter.ALL -> R.string.filter_all; AppFilter.USER -> R.string.filter_user; AppFilter.SYSTEM -> R.string.filter_system; AppFilter.FROZEN -> R.string.filter_frozen; AppFilter.SUSPENDED -> R.string.filter_suspended; AppFilter.REMOVED -> R.string.filter_removed; AppFilter.PROTECTED -> R.string.filter_protected }
