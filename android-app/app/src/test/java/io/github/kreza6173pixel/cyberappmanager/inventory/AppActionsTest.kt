package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppActionsTest {
    private fun entry(state: AppState, system: Boolean, reason: String? = null) = AppEntry("com.example.app", "Example", system, state, reason)
    private val realEnabled = parsePackageDetails(" User 0: ceDataInode=1 deDataInode=2 installed=true suspended=false stopped=false enabled=0").userFlags

    @Test fun protectedAppsGetNoActions() { assertTrue(AppActions.availableFor(entry(AppState.ENABLED, true, "current launcher")).isEmpty()) }
    @Test fun actionsDependOnStateAndKind() { assertEquals(listOf(AppAction.SUSPEND, AppAction.FORCE_STOP, AppAction.CLEAR_DATA, AppAction.REMOVE), AppActions.availableFor(entry(AppState.ENABLED, true))); assertEquals(listOf(AppAction.SUSPEND, AppAction.FORCE_STOP, AppAction.CLEAR_DATA), AppActions.availableFor(entry(AppState.ENABLED, false))); assertEquals(listOf(AppAction.UNSUSPEND), AppActions.availableFor(entry(AppState.SUSPENDED, false))); assertEquals(listOf(AppAction.UNSUSPEND), AppActions.availableFor(entry(AppState.SUSPENDED, true))); assertEquals(listOf(AppAction.UNFREEZE), AppActions.availableFor(entry(AppState.FROZEN, false))); assertEquals(listOf(AppAction.RESTORE), AppActions.availableFor(entry(AppState.REMOVED, true))) }
    @Test fun commandsAreQuotedAndUserScoped() { assertEquals("pm disable-user --user 0 'com.example.app'", AppActions.command(AppAction.FREEZE, "com.example.app")); assertEquals("pm suspend 'com.example.app'", AppActions.command(AppAction.SUSPEND, "com.example.app")); assertEquals("pm unsuspend 'com.example.app'", AppActions.command(AppAction.UNSUSPEND, "com.example.app")); assertEquals("am force-stop --user 0 'com.example.app'", AppActions.command(AppAction.FORCE_STOP, "com.example.app")) }
    @Test(expected = IllegalArgumentException::class) fun commandRejectsInjection() { AppActions.command(AppAction.SUSPEND, "com.x; reboot") }
    @Test fun verifyUsesReadBack() { assertEquals(Verdict.APPLIED, AppActions.verify(AppAction.UNSUSPEND, realEnabled + ("suspended" to "false"), "")); assertEquals(Verdict.APPLIED, AppActions.verify(AppAction.SUSPEND, realEnabled + ("suspended" to "true"), "")); assertEquals(Verdict.NOT_APPLIED, AppActions.verify(AppAction.FREEZE, realEnabled, "")); assertEquals(Verdict.APPLIED, AppActions.verify(AppAction.FREEZE, realEnabled + ("enabled" to "3"), "")); assertEquals(Verdict.UNVERIFIABLE, AppActions.verify(AppAction.CLEAR_DATA, realEnabled, "Success")) }
    @Test fun stateFromFlags() { assertEquals(AppState.ENABLED, AppActions.stateFrom(realEnabled)); assertEquals(AppState.SUSPENDED, AppActions.stateFrom(realEnabled + ("suspended" to "true"))); assertEquals(AppState.FROZEN, AppActions.stateFrom(realEnabled + ("enabled" to "3"))); assertEquals(AppState.REMOVED, AppActions.stateFrom(realEnabled + ("installed" to "false"))); assertNull(AppActions.stateFrom(emptyMap())) }
}
