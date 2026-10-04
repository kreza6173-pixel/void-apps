package io.github.kreza6173pixel.cyberappmanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.shizuku.ShizukuRuntime
import io.github.kreza6173pixel.cyberappmanager.shizuku.ShizukuState
import io.github.kreza6173pixel.cyberappmanager.shizuku.ShizukuUidKind

@Composable
fun HomeScreen(runtime: ShizukuRuntime, modifier: Modifier = Modifier, onOpenApps: () -> Unit = {}, onOpenConsole: () -> Unit = {}, onOpenSnapshots: () -> Unit = {}, onOpenPins: () -> Unit = {}, onOpenDebloat: () -> Unit = {}, onOpenSelfCheck: () -> Unit = {}, onOpenTools: () -> Unit = {}) {
    val state = runtime.state
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StateCard(runtime); StateAction(runtime, state); UidRow(runtime)
        if (state == ShizukuState.READY) { FeatureButton(R.string.home_open_apps, onOpenApps); FeatureButton(R.string.home_open_tools, onOpenTools); FeatureButton(R.string.home_open_debloat, onOpenDebloat); FeatureButton(R.string.home_open_snapshots, onOpenSnapshots); FeatureButton(R.string.home_open_pins, onOpenPins); FeatureButton(R.string.home_open_selfcheck, onOpenSelfCheck); FeatureButton(R.string.home_open_console, onOpenConsole) }
        RefreshButton(runtime)
    }
}
@Composable private fun FeatureButton(labelRes: Int, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(labelRes), modifier = Modifier.padding(start = 8.dp, end = 8.dp)) } }
@Composable private fun StateCard(runtime: ShizukuRuntime) { val state = runtime.state; Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) { Text(stringResource(state.titleRes()), style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text(stringResource(state.bodyRes()), style = MaterialTheme.typography.bodyMedium); if (state == ShizukuState.PERMISSION_NEEDED && runtime.shouldShowRationale) { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.state_rationale_hint), style = MaterialTheme.typography.bodySmall) } } } }
@Composable private fun StateAction(runtime: ShizukuRuntime, state: ShizukuState) { when (state) { ShizukuState.NOT_INSTALLED -> Button(onClick = { runtime.launchManager() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_install_shizuku), modifier = Modifier.padding(start = 8.dp, end = 8.dp)) }; ShizukuState.NOT_RUNNING -> Button(onClick = { runtime.launchManager() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_open_manager), modifier = Modifier.padding(start = 8.dp, end = 8.dp)) }; ShizukuState.PERMISSION_NEEDED -> PermissionActions(runtime); ShizukuState.READY -> Unit } }
@Composable private fun PermissionActions(runtime: ShizukuRuntime) { val stale = runtime.grantedButNotApplied; Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { if (stale) { Text(stringResource(R.string.state_granted_not_applied), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary); Button(onClick = { runtime.restartApp() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_restart_app)) } } else { Button(onClick = { runtime.requestPermission() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_request_permission), modifier = Modifier.padding(start = 8.dp, end = 8.dp)) }; Text(stringResource(R.string.state_restart_hint), style = MaterialTheme.typography.bodySmall); OutlinedButton(onClick = { runtime.restartApp() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_restart_app)) } } } }
@Composable private fun UidRow(runtime: ShizukuRuntime) { Text(stringResource(R.string.state_uid_value, runtime.uid, stringResource(runtime.uidKind.labelRes())), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) }
@Composable private fun RefreshButton(runtime: ShizukuRuntime) { OutlinedButton(onClick = { runtime.refresh() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_refresh), modifier = Modifier.padding(start = 8.dp, end = 8.dp)) } }
private fun ShizukuState.titleRes(): Int = when (this) { ShizukuState.NOT_INSTALLED -> R.string.state_not_installed_title; ShizukuState.NOT_RUNNING -> R.string.state_not_running_title; ShizukuState.PERMISSION_NEEDED -> R.string.state_permission_needed_title; ShizukuState.READY -> R.string.state_ready_title }
private fun ShizukuState.bodyRes(): Int = when (this) { ShizukuState.NOT_INSTALLED -> R.string.state_not_installed_body; ShizukuState.NOT_RUNNING -> R.string.state_not_running_body; ShizukuState.PERMISSION_NEEDED -> R.string.state_permission_needed_body; ShizukuState.READY -> R.string.state_ready_body }
private fun ShizukuUidKind.labelRes(): Int = when (this) { ShizukuUidKind.ROOT -> R.string.uid_root; ShizukuUidKind.SHELL -> R.string.uid_shell; ShizukuUidKind.OTHER -> R.string.uid_other; ShizukuUidKind.UNKNOWN -> R.string.uid_unknown }
