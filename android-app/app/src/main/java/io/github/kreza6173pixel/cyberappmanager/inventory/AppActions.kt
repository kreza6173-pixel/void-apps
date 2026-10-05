package io.github.kreza6173pixel.cyberappmanager.inventory

import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting

enum class AppAction { SUSPEND, UNSUSPEND, FREEZE, UNFREEZE, FORCE_STOP, REMOVE, RESTORE, CLEAR_DATA }
enum class Verdict { APPLIED, NOT_APPLIED, UNVERIFIABLE, REFUSED, FAILED }

object AppActions {
    fun availableFor(e: AppEntry): List<AppAction> {
        if (e.protectedReason != null) return emptyList()
        return when (e.state) {
            AppState.ENABLED -> buildList {
                add(AppAction.SUSPEND)
                add(AppAction.FORCE_STOP)
                add(AppAction.CLEAR_DATA)
                if (e.isSystem) add(AppAction.REMOVE)
            }
            AppState.SUSPENDED -> listOf(AppAction.UNSUSPEND)
            AppState.FROZEN -> buildList {
                add(AppAction.UNFREEZE)
                if (e.isSystem) add(AppAction.REMOVE)
            }
            AppState.REMOVED -> listOf(AppAction.RESTORE)
        }
    }

    fun needsConfirmation(a: AppAction): Boolean =
        a == AppAction.SUSPEND || a == AppAction.FREEZE || a == AppAction.REMOVE || a == AppAction.CLEAR_DATA

    fun command(a: AppAction, pkg: String): String {
        require(isValidPackageName(pkg)) { "invalid package name" }
        val q = ShellQuoting.quote(pkg)
        return when (a) {
            AppAction.SUSPEND -> "pm suspend $q"
            AppAction.UNSUSPEND -> "pm unsuspend $q"
            AppAction.FREEZE -> "pm disable-user --user 0 $q"
            AppAction.UNFREEZE -> "pm enable --user 0 $q"
            AppAction.FORCE_STOP -> "am force-stop --user 0 $q"
            AppAction.REMOVE -> "pm uninstall -k --user 0 $q"
            AppAction.RESTORE -> "pm install-existing --user 0 $q"
            AppAction.CLEAR_DATA -> "pm clear --user 0 $q"
        }
    }

    fun verify(a: AppAction, after: Map<String, String>, output: String): Verdict {
        if (a == AppAction.CLEAR_DATA) return if (output.contains("Success")) Verdict.UNVERIFIABLE else Verdict.NOT_APPLIED
        if (after.isEmpty()) return Verdict.UNVERIFIABLE
        val enabled = after["enabled"]
        val ok = when (a) {
            AppAction.SUSPEND -> after["suspended"] == "true"
            AppAction.UNSUSPEND -> after["suspended"] == "false"
            AppAction.FREEZE -> enabled == "2" || enabled == "3"
            AppAction.UNFREEZE -> enabled == "0" || enabled == "1"
            AppAction.FORCE_STOP -> after["stopped"] == "true"
            AppAction.REMOVE -> after["installed"] == "false"
            AppAction.RESTORE -> after["installed"] == "true"
            AppAction.CLEAR_DATA -> false
        }
        return if (ok) Verdict.APPLIED else Verdict.NOT_APPLIED
    }

    fun stateFrom(flags: Map<String, String>): AppState? = when {
        flags.isEmpty() -> null
        flags["installed"] == "false" -> AppState.REMOVED
        flags["suspended"] == "true" -> AppState.SUSPENDED
        flags["enabled"] == "2" || flags["enabled"] == "3" -> AppState.FROZEN
        else -> AppState.ENABLED
    }
}
