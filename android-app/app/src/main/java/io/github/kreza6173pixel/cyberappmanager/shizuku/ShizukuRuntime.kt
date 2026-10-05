package io.github.kreza6173pixel.cyberappmanager.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku

/** Package id of the Shizuku manager app. */
const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"

/** F-Droid page for the manager app; used when no store app handles `market://`. */
const val MANAGER_FDROID_URL = "https://f-droid.org/packages/moe.shizuku.privileged.api/"

/** Result of a manager-app launch attempt. */
enum class ManagerLaunchResult { STARTED, NO_APP }

/**
 * Thin Android wrapper around the Shizuku client library. Everything here is a side
 * effect; all decision-making lives in [resolveShizukuState] so it stays testable.
 */
class ShizukuRuntime(private val context: Context) {

    /** Latest resolved state, readable from Compose. */
    var state: ShizukuState by mutableStateOf(ShizukuState.NOT_INSTALLED)
        private set

    /** Raw facts behind [state], exposed for the UI (rationale copy, uid). */
    var signals: ShizukuSignals by mutableStateOf(
        ShizukuSignals(managerInstalled = false, binderAlive = false, permissionGranted = false)
    )
        private set

    /** `Shizuku.getUid()`, or -1 when not READY. */
    var uid: Int by mutableStateOf(-1)
        private set

    /**
     * Shizuku reported GRANTED, but asking the server still says denied. Seen on the user's
     * Shizuku build: the server applies a new grant only to newly attached processes, so the
     * app has to restart.
     */
    var grantedButNotApplied: Boolean by mutableStateOf(false)
        private set

    /**
     * The permission dialog is shown automatically at most once per process, so a user who
     * dismisses it is never nagged in a loop. The Grant button always works.
     */
    private var autoAsked = false

    val uidKind: ShizukuUidKind get() = classifyUid(uid)

    /** Set when `Shizuku.shouldShowRequestPermissionRationale()` is true. */
    val shouldShowRationale: Boolean get() = signals.rationaleShouldShow

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            refresh()
            grantedButNotApplied =
                grantResult == PERMISSION_GRANTED && state == ShizukuState.PERMISSION_NEEDED
        }

    /** Registers the three listeners and performs an immediate refresh. */
    fun start() {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refresh()
    }

    /** Unregisters the listeners. Safe to call more than once. */
    fun stop() {
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
    }

    /** Re-reads every signal. Safe to call from any binder/permission callback. */
    fun refresh() {
        val installed = isManagerInstalled()
        val alive = installed && Shizuku.pingBinder()
        val granted = alive && checkPermissionGranted()
        val rationale = alive && runCatching { Shizuku.shouldShowRequestPermissionRationale() }
            .getOrDefault(false)

        signals = ShizukuSignals(installed, alive, granted, rationale)
        state = resolveShizukuState(signals)
        uid = if (state == ShizukuState.READY) readUid() else -1
        if (state != ShizukuState.PERMISSION_NEEDED) grantedButNotApplied = false

        // Fresh install: open the Shizuku dialog right away instead of waiting for a tap.
        // Skipped when the user chose "deny" before (rationale), so the hint is shown instead.
        if (state == ShizukuState.PERMISSION_NEEDED && !autoAsked && !rationale) {
            autoAsked = true
            requestPermission()
        }
    }

    /** True when the Shizuku manager package resolves. Needs the `<queries>` entry (API 30+). */
    fun isManagerInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Asks the Shizuku service for permission. The answer arrives via the result listener. */
    fun requestPermission() {
        if (state != ShizukuState.PERMISSION_NEEDED) return
        runCatching { Shizuku.requestPermission(REQUEST_CODE_PERMISSION) }
    }

    /**
     * Relaunches the app in a fresh process, which attaches to Shizuku again and so picks up a
     * permission granted after the previous attach. The non-daemon user service dies with us.
     */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) {
            Runtime.getRuntime().exit(0)
        }
    }

    /** Launches the manager app, else the store page, else the F-Droid page. */
    fun launchManager(): ManagerLaunchResult {
        getLaunchIntentForInstalledManager()?.let { return startIntent(it) }
        // `setPackage` returns void, so it cannot be chained onto the constructor.
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$MANAGER_PACKAGE"))
        store.setPackage("com.android.vending")
        if (startIntent(store) == ManagerLaunchResult.STARTED) return ManagerLaunchResult.STARTED
        return startIntent(Intent(Intent.ACTION_VIEW, Uri.parse(MANAGER_FDROID_URL)))
    }

    private fun getLaunchIntentForInstalledManager(): Intent? =
        context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)

    /**
     * [context] is the application context, not an Activity, so every launch needs
     * `FLAG_ACTIVITY_NEW_TASK` or the framework throws at runtime.
     */
    private fun startIntent(intent: Intent): ManagerLaunchResult =
        if (runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        ) {
            ManagerLaunchResult.STARTED
        } else {
            ManagerLaunchResult.NO_APP
        }

    private fun checkPermissionGranted(): Boolean =
        runCatching { Shizuku.checkSelfPermission() == PERMISSION_GRANTED }.getOrDefault(false)

    private fun readUid(): Int = runCatching { Shizuku.getUid() }.getOrDefault(-1)

    private companion object {
        /** Arbitrary request code echoed back to [permissionResultListener]. */
        const val REQUEST_CODE_PERMISSION = 1
    }
}
