package io.github.kreza6173pixel.cyberappmanager.ui.tools

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.install.CacheResult
import io.github.kreza6173pixel.cyberappmanager.install.EmptyDeleteResult
import io.github.kreza6173pixel.cyberappmanager.install.EmptyScan
import io.github.kreza6173pixel.cyberappmanager.install.FileEntry
import io.github.kreza6173pixel.cyberappmanager.install.InspectResult
import io.github.kreza6173pixel.cyberappmanager.install.InstallOptions
import io.github.kreza6173pixel.cyberappmanager.install.InstallResult
import io.github.kreza6173pixel.cyberappmanager.install.InstallerRepository
import io.github.kreza6173pixel.cyberappmanager.install.PackageFormat
import io.github.kreza6173pixel.cyberappmanager.install.RunningApp
import io.github.kreza6173pixel.cyberappmanager.install.RunningScan
import io.github.kreza6173pixel.cyberappmanager.install.StopResult
import io.github.kreza6173pixel.cyberappmanager.install.cleanerRepo
import io.github.kreza6173pixel.cyberappmanager.install.formatBytes
import io.github.kreza6173pixel.cyberappmanager.install.installerRepo
import io.github.kreza6173pixel.cyberappmanager.install.parentDir
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.inventory.Verdict
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A8: installer (pulse-install) and the kept parts of void-purge in one screen, three tabs. */
@Composable
fun ToolsScreen(inventory: InventoryRepository, connected: Boolean, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(0) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.tools_tab_install, R.string.tools_tab_clean, R.string.tools_tab_running).forEachIndexed { i, res ->
                if (tab == i) Button({ tab = i }, Modifier.weight(1f)) { Text(stringResource(res), maxLines = 1) }
                else OutlinedButton({ tab = i }, Modifier.weight(1f)) { Text(stringResource(res), maxLines = 1) }
            }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!connected) Text(stringResource(R.string.apps_waiting))
            when (tab) {
                0 -> InstallTab(inventory, connected)
                1 -> CleanTab(inventory, connected)
                else -> RunningTab(inventory, connected)
            }
        }
    }
}

// ------------------------------------------------------------------ install

@Composable
private fun InstallTab(inventory: InventoryRepository, connected: Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = remember(inventory) { inventory.installerRepo() }
    var path by remember { mutableStateOf(InstallerRepository.DEFAULT_BROWSE) }
    var pathField by remember { mutableStateOf(InstallerRepository.DEFAULT_BROWSE) }
    var listing by remember { mutableStateOf<Result<List<FileEntry>>?>(null) }
    var unzip by remember { mutableStateOf<Boolean?>(null) }
    val selected = remember { mutableStateListOf<FileEntry>() }
    var replace by remember { mutableStateOf(true) }
    var grant by remember { mutableStateOf(false) }
    var deleteAfter by remember { mutableStateOf(false) }
    var splitBundle by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<InstallResult>>(emptyList()) }
    var inspect by remember { mutableStateOf<Result<InspectResult>?>(null) }
    var confirmInstall by remember { mutableStateOf(false) }
    var confirmAuto by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(InstallHistory.read(context)) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(connected, path, reload) {
        if (connected) {
            if (unzip == null) unzip = repo.unzipAvailable()
            listing = repo.browse(path)
        }
    }
    fun go(p: String) { path = p; pathField = p }
    val opts = InstallOptions(replace, grant, deleteAfter)
    fun finish(r: List<InstallResult>) {
        results = r; busy = false; progress = ""
        InstallHistory.add(context, r); history = InstallHistory.read(context)
        listing = null; reload++
    }

    if (confirmInstall) AlertDialog(
        onDismissRequest = { confirmInstall = false },
        title = { Text(stringResource(R.string.inst_confirm_title)) },
        text = { Text(stringResource(R.string.inst_confirm_body, selected.size, selected.joinToString("\n") { it.name })) },
        confirmButton = { TextButton({
            confirmInstall = false; busy = true
            val files = selected.toList()
            scope.launch { finish(repo.install(files, opts, splitBundle) { progress = it }); selected.clear() }
        }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton({ confirmInstall = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (confirmAuto) AlertDialog(
        onDismissRequest = { confirmAuto = false },
        title = { Text(stringResource(R.string.inst_auto_title)) },
        text = { Text(stringResource(R.string.inst_auto_body, InstallerRepository.AUTO_DIR)) },
        confirmButton = { TextButton({
            confirmAuto = false; busy = true
            scope.launch {
                val r = repo.scanAutoFolder(opts) { progress = it }
                finish(r.getOrElse { listOf(InstallResult(InstallerRepository.AUTO_DIR, null, Verdict.FAILED, null, null, 0, it.message ?: "", "")) })
            }
        }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton({ confirmAuto = false }) { Text(stringResource(R.string.action_cancel)) } },
    )

    Text(stringResource(R.string.inst_note), style = MaterialTheme.typography.labelSmall)
    unzip?.let { Text(stringResource(if (it) R.string.inst_unzip_ok else R.string.inst_unzip_missing), style = MaterialTheme.typography.labelSmall, color = if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(pathField, { pathField = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.inst_path)) })
        TextButton({ go(pathField.trim().ifEmpty { "/sdcard" }) }, enabled = connected) { Text(stringResource(R.string.inst_go)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ go(parentDir(path)) }, enabled = connected && path != "/") { Text(stringResource(R.string.inst_up)) }
        OutlinedButton({ go("/sdcard/Download") }, enabled = connected) { Text("Download") }
        OutlinedButton({ go(InstallerRepository.AUTO_DIR) }, enabled = connected) { Text("auto") }
    }
    when (val l = listing) {
        null -> if (connected) Text(stringResource(R.string.apps_loading))
        else -> l.fold(
            onSuccess = { entries ->
                if (entries.isEmpty()) Text(stringResource(R.string.inst_empty_dir), style = MaterialTheme.typography.bodySmall)
                entries.take(MAX_ROWS).forEach { e -> FileRow(e, e in selected, connected && !busy, onOpen = { go(e.path) }, onToggle = {
                    if (e in selected) selected.remove(e) else selected.add(e)
                }, onInspect = { busy = true; inspect = null; scope.launch { inspect = repo.inspect(e); busy = false } }) }
                if (entries.size > MAX_ROWS) Text(stringResource(R.string.inst_more_rows, entries.size - MAX_ROWS), style = MaterialTheme.typography.labelSmall)
            },
            onFailure = { Text(stringResource(R.string.inst_browse_error, it.message ?: ""), color = MaterialTheme.colorScheme.error) },
        )
    }

    inspect?.let { InspectCard(it) { inspect = null } }

    Text(stringResource(R.string.inst_options), style = MaterialTheme.typography.titleSmall)
    CheckRow(stringResource(R.string.inst_opt_replace), replace) { replace = it }
    CheckRow(stringResource(R.string.inst_opt_grant), grant) { grant = it }
    CheckRow(stringResource(R.string.inst_opt_delete), deleteAfter) { deleteAfter = it }
    val plainCount = selected.count { it.format == PackageFormat.APK }
    if (plainCount >= 2) CheckRow(stringResource(R.string.inst_opt_split, plainCount), splitBundle) { splitBundle = it }
    val needsUnzip = selected.any { it.format != PackageFormat.APK }
    if (needsUnzip && unzip == false) Text(stringResource(R.string.inst_unzip_missing), color = MaterialTheme.colorScheme.error)
    Button({ confirmInstall = true }, Modifier.fillMaxWidth(), enabled = connected && !busy && selected.isNotEmpty() && !(needsUnzip && unzip == false)) {
        Text(stringResource(R.string.inst_install_selected, selected.size))
    }
    OutlinedButton({ confirmAuto = true }, Modifier.fillMaxWidth(), enabled = connected && !busy) { Text(stringResource(R.string.inst_auto_scan)) }
    if (busy) Text(stringResource(R.string.inst_working, progress), color = MaterialTheme.colorScheme.primary)
    results.forEach { InstallResultCard(it) }

    Text(stringResource(R.string.inst_history), style = MaterialTheme.typography.titleSmall)
    if (history.isEmpty()) Text(stringResource(R.string.inst_history_empty), style = MaterialTheme.typography.bodySmall)
    history.forEach { LtrMonoText(it) }
    if (history.isNotEmpty()) TextButton({ InstallHistory.clear(context); history = emptyList() }) { Text(stringResource(R.string.inst_history_clear)) }
}

@Composable
private fun FileRow(e: FileEntry, checked: Boolean, enabled: Boolean, onOpen: () -> Unit, onToggle: () -> Unit, onInspect: () -> Unit) {
    when {
        e.isDir -> Text("\uD83D\uDCC1 " + e.name, Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onOpen).padding(vertical = 6.dp), style = MaterialTheme.typography.bodyMedium)
        e.format != null -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, { onToggle() }, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.bodySmall)
                Text(e.format!!.name + " \u00b7 " + formatBytes(e.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onInspect, enabled = enabled) { Text(stringResource(R.string.inst_inspect)) }
        }
        else -> Text(e.name, Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InspectCard(r: Result<InspectResult>, onClose: () -> Unit) {
    var showRaw by remember(r) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.inst_inspect_title), style = MaterialTheme.typography.titleSmall)
        r.fold(
            onSuccess = { x ->
                val i = x.info
                val none = stringResource(R.string.value_none)
                Field(stringResource(R.string.inst_f_file), x.entry.name + " \u00b7 " + formatBytes(x.entry.size))
                Field(stringResource(R.string.inst_f_package), i?.packageName ?: x.xapk?.packageName ?: none)
                Field(stringResource(R.string.inst_f_label), i?.label ?: none)
                Field(stringResource(R.string.inst_f_version), "${i?.versionName ?: none} (${i?.versionCode ?: none})")
                Field(stringResource(R.string.inst_f_sdk), "${i?.minSdk ?: none} / ${i?.targetSdk ?: none}")
                Field(stringResource(R.string.inst_f_installed), x.installed?.let { "${it.versionName ?: none} (${it.versionCode ?: none})" } ?: stringResource(R.string.inst_not_installed))
                val newCode = i?.versionCode; val oldCode = x.installed?.versionCode
                if (newCode != null && oldCode != null && newCode < oldCode) Text(stringResource(R.string.inst_downgrade_warn), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Field(stringResource(R.string.inst_f_abis), x.abis.ifEmpty { listOf(stringResource(R.string.inst_abi_none)) }.joinToString(", "))
                if (x.apkCount > 1) Field(stringResource(R.string.inst_f_apks), x.apkCount.toString())
                Field(stringResource(R.string.inst_f_launchable), stringResource(if (i?.launchable == true) R.string.inst_yes else R.string.inst_no))
                Field("SHA-256", x.sha256 ?: none)
                Field(stringResource(R.string.inst_f_perms, i?.permissions?.size ?: 0), i?.permissions?.joinToString("\n") { it.removePrefix("android.permission.") }?.ifEmpty { none } ?: none)
                TextButton({ showRaw = !showRaw }) { Text(stringResource(if (showRaw) R.string.net_raw_hide else R.string.net_raw_show)) }
                if (showRaw) { LtrMonoText(x.raw); CopyShareButtons(x.raw) }
            },
            onFailure = { Text(stringResource(R.string.inst_inspect_error, it.message ?: ""), color = MaterialTheme.colorScheme.error) },
        )
        TextButton(onClose) { Text(stringResource(R.string.action_close)) }
    } }
}

@Composable
private fun InstallResultCard(r: InstallResult) {
    var showLog by remember(r) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(r.fileName + ": " + stringResource(verdictText(r.verdict)), color = verdictColor(r.verdict), style = MaterialTheme.typography.bodySmall)
        r.packageName?.let { LtrMonoText(it) }
        val none = stringResource(R.string.value_none)
        Text("before: ${r.versionBefore?.let { "${it.versionName ?: none} (${it.versionCode ?: none})" } ?: stringResource(R.string.inst_not_installed)}\nafter: ${r.versionAfter?.let { "${it.versionName ?: none} (${it.versionCode ?: none})" } ?: none}", style = MaterialTheme.typography.labelSmall)
        if (r.note.isNotEmpty()) Text(r.note, style = MaterialTheme.typography.labelSmall)
        TextButton({ showLog = !showLog }) { Text(stringResource(if (showLog) R.string.net_raw_hide else R.string.net_raw_show)) }
        if (showLog) { LtrMonoText(r.log); CopyShareButtons(r.log) }
    } }
}

// ------------------------------------------------------------------ clean

@Composable
private fun CleanTab(inventory: InventoryRepository, connected: Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = remember(inventory) { inventory.cleanerRepo(context) }
    var busy by remember { mutableStateOf(false) }
    var cache by remember { mutableStateOf<CacheResult?>(null) }
    var confirmCache by remember { mutableStateOf(false) }
    var root by remember { mutableStateOf(DEFAULT_EMPTY_ROOT) }
    var scan by remember { mutableStateOf<Result<EmptyScan>?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf<EmptyDeleteResult?>(null) }

    if (confirmCache) AlertDialog(
        onDismissRequest = { confirmCache = false },
        title = { Text(stringResource(R.string.clean_cache_title)) },
        text = { Text(stringResource(R.string.clean_cache_body)) },
        confirmButton = { TextButton({ confirmCache = false; busy = true; scope.launch { cache = repo.trimCaches(); busy = false } }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton({ confirmCache = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
    val s = scan?.getOrNull()
    if (confirmDelete && s != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.clean_empty_confirm_title)) },
        text = { Text(stringResource(R.string.clean_empty_confirm_body, picked.size, s.root)) },
        confirmButton = { TextButton({
            confirmDelete = false; busy = true
            val list = picked.toList()
            scope.launch { deleted = repo.deleteEmpty(s.root, list); scan = null; picked.clear(); busy = false }
        }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )

    Text(stringResource(R.string.clean_note), style = MaterialTheme.typography.labelSmall)
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.clean_cache_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.clean_cache_note), style = MaterialTheme.typography.labelSmall)
        Button({ confirmCache = true }, Modifier.fillMaxWidth(), enabled = connected && !busy) { Text(stringResource(R.string.clean_cache_run)) }
        cache?.let { c ->
            Text(stringResource(verdictText(c.verdict)) + (c.freedKb?.let { " \u00b7 " + stringResource(R.string.clean_freed, formatBytes(it * 1024)) } ?: ""), color = verdictColor(c.verdict), style = MaterialTheme.typography.bodySmall)
            LtrMonoText(c.log); CopyShareButtons(c.log)
        }
    } }

    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.clean_empty_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.clean_empty_note), style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(root, { root = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inst_path)) })
        Button({ busy = true; deleted = null; scope.launch { val r = repo.scanEmpty(root); scan = r; picked.clear(); r.getOrNull()?.let { picked.addAll(it.dirs) }; busy = false } }, Modifier.fillMaxWidth(), enabled = connected && !busy) { Text(stringResource(R.string.clean_empty_scan)) }
        scan?.fold(
            onSuccess = { sc ->
                Text(stringResource(R.string.clean_empty_found, sc.dirs.size) + if (sc.capped) " " + stringResource(R.string.clean_empty_capped) else "", style = MaterialTheme.typography.bodySmall)
                sc.dirs.take(MAX_ROWS).forEach { d -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(d in picked, { if (d in picked) picked.remove(d) else picked.add(d) }, enabled = !busy)
                    Text(d.removePrefix(sc.root + "/"), style = MaterialTheme.typography.labelSmall)
                } }
                if (sc.dirs.size > MAX_ROWS) Text(stringResource(R.string.inst_more_rows, sc.dirs.size - MAX_ROWS), style = MaterialTheme.typography.labelSmall)
                if (sc.dirs.isNotEmpty()) Button({ confirmDelete = true }, Modifier.fillMaxWidth(), enabled = connected && !busy && picked.isNotEmpty()) { Text(stringResource(R.string.clean_empty_delete, picked.size)) }
            },
            onFailure = { Text(it.message ?: "", color = MaterialTheme.colorScheme.error) },
        )
        deleted?.let { d ->
            Text(stringResource(R.string.clean_empty_result, d.removed.size, d.kept.size), color = if (d.kept.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.clean_empty_rescan), style = MaterialTheme.typography.labelSmall)
            if (d.kept.isNotEmpty()) LtrMonoText(d.kept.joinToString("\n"))
        }
    } }
}

// ------------------------------------------------------------------ running

@Composable
private fun RunningTab(inventory: InventoryRepository, connected: Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = remember(inventory) { inventory.cleanerRepo(context) }
    var busy by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf<Result<RunningScan>?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var confirm by remember { mutableStateOf(false) }
    var stops by remember { mutableStateOf<Pair<List<StopResult>, String>?>(null) }
    var showLog by remember { mutableStateOf(false) }
    fun refresh() { busy = true; scope.launch { scan = repo.runningApps(); picked.clear(); busy = false } }
    LaunchedEffect(connected) { if (connected && scan == null) refresh() }

    val apps: List<RunningApp> = scan?.getOrNull()?.apps.orEmpty()
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.run_confirm_title)) },
        text = { Text(stringResource(R.string.run_confirm_body, picked.size, picked.joinToString("\n"))) },
        confirmButton = { TextButton({
            confirm = false; busy = true
            val chosen = apps.filter { it.packageName in picked }
            scope.launch { stops = repo.stopApps(chosen); scan = repo.runningApps(); picked.clear(); busy = false }
        }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
    )

    Text(stringResource(R.string.run_note), style = MaterialTheme.typography.labelSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ refresh() }, enabled = connected && !busy) { Text(stringResource(R.string.run_refresh)) }
        OutlinedButton({ picked.clear(); picked.addAll(apps.filter { it.protectedReason == null }.map { it.packageName }) }, enabled = connected && !busy && apps.isNotEmpty()) { Text(stringResource(R.string.run_select_all)) }
    }
    stops?.let { (list, log) ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.run_result_title), style = MaterialTheme.typography.titleSmall)
            list.forEach { r -> Text("${r.pkg}: " + stringResource(verdictText(r.verdict)) + " \u00b7 " + r.note + "\npid before ${r.before.joinToString(",").ifEmpty { "-" }} \u00b7 after ${r.after.joinToString(",").ifEmpty { "-" }}", color = verdictColor(r.verdict), style = MaterialTheme.typography.labelSmall) }
            TextButton({ showLog = !showLog }) { Text(stringResource(if (showLog) R.string.net_raw_hide else R.string.net_raw_show)) }
            if (showLog) { LtrMonoText(log); CopyShareButtons(log) }
        } }
    }
    when (val sc = scan) {
        null -> if (connected) Text(stringResource(R.string.apps_loading))
        else -> sc.fold(
            onSuccess = { s ->
                Text(stringResource(R.string.run_found, s.apps.size, s.source), style = MaterialTheme.typography.bodySmall)
                s.apps.forEach { a -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(a.packageName in picked, { if (a.packageName in picked) picked.remove(a.packageName) else picked.add(a.packageName) }, enabled = !busy && a.protectedReason == null)
                    Column(Modifier.weight(1f)) {
                        val label = inventory.entryFor(a.packageName)?.label
                        Text(label ?: a.packageName, style = MaterialTheme.typography.bodySmall)
                        if (label != null) Text(a.packageName, style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.run_pids, a.pids.joinToString(","), a.processes.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        a.protectedReason?.let { Text(stringResource(R.string.run_protected, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    }
                } }
                Button({ confirm = true }, Modifier.fillMaxWidth(), enabled = connected && !busy && picked.isNotEmpty()) { Text(stringResource(R.string.run_stop_selected, picked.size)) }
            },
            onFailure = { Text(it.message ?: "", color = MaterialTheme.colorScheme.error) },
        )
    }
}

// ------------------------------------------------------------------ shared

@Composable private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label, style = MaterialTheme.typography.bodySmall) }
}

@Composable private fun Field(label: String, value: String) { Column { Text(label, style = MaterialTheme.typography.labelMedium); LtrMonoText(value) } }

internal fun verdictText(v: Verdict) = when (v) {
    Verdict.APPLIED -> R.string.verdict_applied
    Verdict.NOT_APPLIED -> R.string.verdict_not_applied
    Verdict.UNVERIFIABLE -> R.string.verdict_unverifiable
    Verdict.REFUSED -> R.string.verdict_refused
    Verdict.FAILED -> R.string.verdict_failed
}

@Composable internal fun verdictColor(v: Verdict) = if (v == Verdict.APPLIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

private const val MAX_ROWS = 300
private const val DEFAULT_EMPTY_ROOT = "/sdcard/Android"

/** Last install results, kept on the phone only (SharedPreferences), newest first. */
internal object InstallHistory {
    private const val PREFS = "void_install_history"
    private const val KEY = "lines"
    private const val MAX = 30

    fun read(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }

    fun add(context: Context, results: List<InstallResult>) {
        if (results.isEmpty()) return
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val lines = results.map { "$stamp  ${it.verdict.name}  ${it.fileName}" + (it.packageName?.let { p -> "  $p" } ?: "") }
        val all = (lines + read(context)).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, all.joinToString("\n")).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
