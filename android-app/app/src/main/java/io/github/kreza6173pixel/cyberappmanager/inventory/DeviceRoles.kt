package io.github.kreza6173pixel.cyberappmanager.inventory

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.webkit.WebView

/**
 * Reads which packages currently hold a role the phone cannot live without.
 *
 * Public APIs from the app process, NOT the shell: on the reference ROM
 * `cmd role holders` and `cmd webviewupdate get-webview-provider` are unknown commands and
 * the shell HOME resolve prints "No activity found". Each lookup is isolated, so one failing
 * API never hides the others.
 */
object DeviceRoles {

    fun read(context: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        fun put(pkg: String?, reason: String) {
            if (pkg.isNullOrBlank() || pkg == "android" || out.containsKey(pkg)) return
            if (isValidPackageName(pkg)) out[pkg] = reason
        }

        put(context.packageName, "this app")
        put(ProtectedPackages.SHIZUKU_MANAGER, "Shizuku")

        put(
            runCatching {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                @Suppress("DEPRECATION")
                val info = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                info?.activityInfo?.packageName
            }.getOrNull(),
            "current launcher",
        )
        put(
            ProtectedPackages.imePackage(
                runCatching {
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                }.getOrNull(),
            ),
            "current keyboard",
        )
        put(
            runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull(),
            "default phone app",
        )
        put(runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(), "default SMS app")
        put(runCatching { WebView.getCurrentWebViewPackage()?.packageName }.getOrNull(), "WebView provider")
        return out
    }
}
