package io.github.kreza6173pixel.cyberappmanager

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.exec.ConnectionState
import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.shizuku.ShizukuRuntime
import io.github.kreza6173pixel.cyberappmanager.shizuku.ShizukuState
import io.github.kreza6173pixel.cyberappmanager.ui.apps.AppDetailScreen
import io.github.kreza6173pixel.cyberappmanager.ui.apps.AppsScreen
import io.github.kreza6173pixel.cyberappmanager.ui.console.ConsoleScreen
import io.github.kreza6173pixel.cyberappmanager.ui.debloat.DebloatScreen
import io.github.kreza6173pixel.cyberappmanager.ui.home.HomeScreen
import io.github.kreza6173pixel.cyberappmanager.ui.pins.PinsScreen
import io.github.kreza6173pixel.cyberappmanager.ui.selfcheck.SelfCheckScreen
import io.github.kreza6173pixel.cyberappmanager.ui.snapshots.SnapshotsScreen
import io.github.kreza6173pixel.cyberappmanager.ui.theme.CyberAppManagerTheme
import io.github.kreza6173pixel.cyberappmanager.ui.tools.ToolsScreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val runtime by lazy { ShizukuRuntime(applicationContext) }
    private val bridge by lazy { ExecBridge(applicationContext) }
    private val inventory by lazy { InventoryRepository(applicationContext, bridge) }
    override fun attachBaseContext(newBase: Context) { Locale.setDefault(Locale.US); val config = Configuration(newBase.resources.configuration); config.setLocale(Locale.US); config.setLayoutDirection(Locale.US); super.attachBaseContext(newBase.createConfigurationContext(config)) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { CyberAppManagerTheme { DisposableEffect(Unit) { runtime.start(); bridge.start(); onDispose { bridge.stop(); runtime.stop() } }; AppRoot(runtime, bridge, inventory) } } }
    override fun onResume() { super.onResume(); runtime.refresh() }
}

private enum class Screen { HOME, CONSOLE, APPS, APP_DETAIL, SNAPSHOTS, PINS, DEBLOAT, SELF_CHECK, TOOLS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(runtime: ShizukuRuntime, bridge: ExecBridge, inventory: InventoryRepository) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedPkg by remember { mutableStateOf("") }
    val ready = runtime.state == ShizukuState.READY
    DisposableEffect(ready) { if (ready) bridge.connect() else bridge.disconnect(); onDispose { bridge.disconnect() } }
    val connected = bridge.connectionState == ConnectionState.CONNECTED
    val shown = if (ready) screen else Screen.HOME
    BackHandler(enabled = shown != Screen.HOME) { screen = if (shown == Screen.APP_DETAIL) Screen.APPS else Screen.HOME }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(when (shown) { Screen.HOME -> R.string.app_name; Screen.CONSOLE -> R.string.console_title; Screen.APPS -> R.string.apps_title; Screen.APP_DETAIL -> R.string.detail_title; Screen.SNAPSHOTS -> R.string.snapshots_title; Screen.PINS -> R.string.pins_title; Screen.DEBLOAT -> R.string.debloat_title; Screen.SELF_CHECK -> R.string.selfcheck_title; Screen.TOOLS -> R.string.tools_title }), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp)) }) }) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (shown) {
            Screen.HOME -> HomeScreen(runtime, contentModifier, { screen = Screen.APPS }, { screen = Screen.CONSOLE }, { screen = Screen.SNAPSHOTS }, { screen = Screen.PINS }, { screen = Screen.DEBLOAT }, { screen = Screen.SELF_CHECK }, { screen = Screen.TOOLS })
            Screen.CONSOLE -> ConsoleScreen(bridge, contentModifier)
            Screen.APPS -> AppsScreen(inventory, connected, contentModifier) { pkg -> selectedPkg = pkg; screen = Screen.APP_DETAIL }
            Screen.APP_DETAIL -> AppDetailScreen(selectedPkg, inventory, connected, contentModifier)
            Screen.SNAPSHOTS -> SnapshotsScreen(inventory, connected, contentModifier)
            Screen.PINS -> PinsScreen(inventory, contentModifier)
            Screen.DEBLOAT -> DebloatScreen(inventory, connected, contentModifier)
            Screen.SELF_CHECK -> SelfCheckScreen(inventory, connected, contentModifier)
            Screen.TOOLS -> ToolsScreen(inventory, connected, contentModifier)
        }
    }
}
