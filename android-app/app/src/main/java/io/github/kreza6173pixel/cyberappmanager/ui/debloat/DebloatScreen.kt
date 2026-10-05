package io.github.kreza6173pixel.cyberappmanager.ui.debloat

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
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.*
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText
import kotlinx.coroutines.launch

private val DEBLOAT_ACTIONS = listOf(AppAction.SUSPEND, AppAction.REMOVE, AppAction.RESTORE)

@Composable
fun DebloatScreen(repository: InventoryRepository, connected: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var inventory by remember { mutableStateOf(repository.cached) }
    LaunchedEffect(connected) { if (connected && inventory !is InventoryResult.Ok) inventory = repository.load() }
    var preset by remember { mutableStateOf<DebloatPreset?>(null) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var action by remember { mutableStateOf(AppAction.SUSPEND) }
    var search by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<BatchReport?>(null) }
    val entries = (inventory as? InventoryResult.Ok)?.entries?.associateBy { it.pkg } ?: emptyMap()

    fun eligibleNow(pkg: String, a: AppAction): Boolean {
        val entry = entries[pkg] ?: return false
        return eligibleForBatch(entry, a) && KnowledgeBase.classify(pkg, entry.isSystem).risk == Risk.SAFE
    }

    if (showDisclaimer) AlertDialog(
        onDismissRequest = { showDisclaimer = false },
        title = { Text(stringResource(R.string.debloat_disclaimer_title)) },
        text = { Text(stringResource(R.string.debloat_disclaimer)) },
        confirmButton = { TextButton(onClick = { showDisclaimer = false }) { Text(stringResource(R.string.action_close)) } },
    )

    val current = preset
    if (confirm && current != null) AlertDialog(
        onDismissRequest = { if (!busy) confirm = false },
        title = { Text(stringResource(R.string.batch_confirm_title)) },
        text = { Text(if (busy) stringResource(R.string.snapshot_restore_working) else stringResource(R.string.debloat_confirm_body, stringResource(actionLabel(action)), picked.size, current.name)) },
        confirmButton = { TextButton(enabled = connected && !busy, onClick = {
            busy = true
            val targets = picked.toList()
            val chosen = action
            scope.launch {
                report = repository.performBatch(targets, chosen)
                inventory = repository.cached
                busy = false
                confirm = false
                picked = emptySet()
            }
        }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
    )

    report?.let { r ->
        val applied = r.results.count { it.verdict == Verdict.APPLIED }
        val lines = r.results.joinToString("\n") { "${it.pkg} · ${it.verdict.name.lowercase()}" }
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text(stringResource(R.string.batch_result_title)) },
            text = { SelectionContainer { Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.snapshot_restore_result, applied, r.results.size - applied)); Text(lines) } } },
            confirmButton = { TextButton(onClick = { report = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    Column(modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactDisclaimer(onClick = { showDisclaimer = true })
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(stringResource(R.string.debloat_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        )
        when {
            inventory !is InventoryResult.Ok -> Text(stringResource(if (connected) R.string.apps_loading else R.string.apps_waiting))
            current == null -> {
                val q = search.trim().lowercase()
                val shownPresets = KnowledgeBase.presets.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.description.lowercase().contains(q) || it.pkgs.any { pkg -> pkg.lowercase().contains(q) || KnowledgeBase.classify(pkg, true).name.lowercase().contains(q) } }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shownPresets, key = { it.id }) { p ->
                        val present = p.pkgs.count { it in entries }
                        Card(onClick = { preset = p; picked = p.pkgs.filter { eligibleNow(it, action) }.toSet() }, enabled = present > 0, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text(p.description, style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.debloat_present, present, p.pkgs.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (shownPresets.isEmpty()) item { Text(stringResource(R.string.debloat_no_matches)) }
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ preset = null; picked = emptySet(); search = "" }, enabled = !busy) { Text(stringResource(R.string.action_back)) }
                    Text(current.name, style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (a in DEBLOAT_ACTIONS) {
                        val label = stringResource(actionLabel(a))
                        if (a == action) Button({}) { Text(label) } else OutlinedButton({ action = a; picked = picked.filter { eligibleNow(it, a) }.toSet() }, enabled = !busy) { Text(label) }
                    }
                }
                Text(stringResource(actionHint(action)), style = MaterialTheme.typography.bodySmall)
                Button({ confirm = true }, enabled = connected && picked.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.debloat_apply, picked.size)) }
                val q = search.trim().lowercase()
                val shownPkgs = current.pkgs.filter { pkg -> q.isEmpty() || pkg.lowercase().contains(q) || (entries[pkg]?.label ?: KnowledgeBase.classify(pkg, true).name).lowercase().contains(q) }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shownPkgs, key = { it }) { pkg ->
                        val entry = entries[pkg]
                        val info = KnowledgeBase.classify(pkg, entry?.isSystem ?: true)
                        val eligible = eligibleNow(pkg, action)
                        val status = when {
                            entry == null -> stringResource(R.string.debloat_not_present)
                            entry.protectedReason != null -> stringResource(R.string.tag_protected_format, entry.protectedReason)
                            !eligible -> entry.state.name.lowercase() + " · " + stringResource(R.string.debloat_not_applicable)
                            else -> entry.state.name.lowercase() + " · " + stringResource(if (entry.isSystem) R.string.tag_system else R.string.tag_user)
                        }
                        Row(Modifier.fillMaxWidth().clickable(enabled = eligible && !busy) { picked = if (pkg in picked) picked - pkg else picked + pkg }.padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { if (eligible) Checkbox(checked = pkg in picked, onCheckedChange = null) }
                            Column(Modifier.weight(1f)) {
                                Text(entry?.label ?: info.name, style = MaterialTheme.typography.bodyLarge)
                                LtrMonoText(pkg)
                                Text(status, style = MaterialTheme.typography.labelSmall, color = if (eligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                Text(info.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider()
                    }
                    if (shownPkgs.isEmpty()) item { Text(stringResource(R.string.debloat_no_matches)) }
                }
            }
        }
    }
}

@Composable
private fun CompactDisclaimer(onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.debloat_disclaimer_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.debloat_disclaimer_short), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Text(stringResource(R.string.debloat_disclaimer_read_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun actionLabel(a: AppAction): Int = when (a) {
    AppAction.REMOVE -> R.string.action_remove
    AppAction.RESTORE -> R.string.action_restore
    else -> R.string.action_suspend
}

private fun actionHint(a: AppAction): Int = when (a) {
    AppAction.RESTORE -> R.string.debloat_hint_restore
    AppAction.REMOVE -> R.string.debloat_hint_remove
    else -> R.string.debloat_hint_suspend
}
