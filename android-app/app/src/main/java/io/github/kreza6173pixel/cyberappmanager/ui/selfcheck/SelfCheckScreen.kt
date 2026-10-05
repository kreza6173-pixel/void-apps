package io.github.kreza6173pixel.cyberappmanager.ui.selfcheck

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.inventory.SelfCheckReport
import io.github.kreza6173pixel.cyberappmanager.inventory.SelfCheckScope
import io.github.kreza6173pixel.cyberappmanager.inventory.selfCheckText
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Read-only Self-check over a group of packages. Leaving the screen stops the run. */
@Composable
fun SelfCheckScreen(repository: InventoryRepository, connected: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val stop = remember { AtomicBoolean(false) }
    var target by remember { mutableStateOf(SelfCheckScope.USER) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var report by remember { mutableStateOf<SelfCheckReport?>(null) }
    val device = remember { deviceLine() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.selfcheck_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.selfcheck_intro), style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) { Text(stringResource(R.string.selfcheck_rom_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp)) }
        LtrMonoText(device)
        GroupRow(R.string.selfcheck_scope_user, target == SelfCheckScope.USER, !running) { target = SelfCheckScope.USER }
        GroupRow(R.string.selfcheck_scope_system, target == SelfCheckScope.SYSTEM, !running) { target = SelfCheckScope.SYSTEM }
        GroupRow(R.string.selfcheck_scope_all, target == SelfCheckScope.ALL, !running) { target = SelfCheckScope.ALL }
        if (!connected) Text(stringResource(R.string.apps_waiting), style = MaterialTheme.typography.bodySmall)
        if (running) {
            Text(stringResource(R.string.selfcheck_progress, progress.first, progress.second), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { stop.set(true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.selfcheck_stop)) }
        } else {
            Button(
                onClick = {
                    stop.set(false); report = null; progress = 0 to 0; running = true
                    val group = target
                    scope.launch {
                        report = repository.selfCheck(group, { stop.get() }) { done, total -> progress = done to total }
                        running = false
                    }
                },
                enabled = connected,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.selfcheck_start)) }
        }
        report?.let { r ->
            val text = selfCheckText(r, device)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!r.complete) Text(stringResource(R.string.selfcheck_partial), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.selfcheck_summary, r.checked, r.permissionErrors, r.sizeCapHits, r.missingPermissionState, r.appOpsErrors, r.appOpsUnsplit, r.unrecognisedAppOps), style = MaterialTheme.typography.bodySmall)
                    if (r.items.isEmpty()) Text(stringResource(R.string.selfcheck_no_issues), color = MaterialTheme.colorScheme.primary)
                }
            }
            LtrMonoText(text)
            CopyShareButtons(text)
            Text(stringResource(R.string.selfcheck_issue_hint), style = MaterialTheme.typography.bodySmall)
            LtrMonoText(stringResource(R.string.selfcheck_issue_url))
        }
    }
}

@Composable private fun GroupRow(labelRes: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun deviceLine(): String = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}"
