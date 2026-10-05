package io.github.kreza6173pixel.cyberappmanager.inventory

/**
 * The ONE guard for the whole app. Every write action (A2 onwards) must ask [reasonFor]
 * first and refuse when it returns non-null. Pure: the dynamic roles are read elsewhere
 * (DeviceRoles) and passed in.
 *
 * @param dynamic package -> reason, e.g. current launcher, keyboard, dialer, SMS, WebView,
 *   this app and the Shizuku manager.
 */
class ProtectedPackages(private val dynamic: Map<String, String>) {

    fun reasonFor(pkg: String): String? =
        dynamic[pkg]
            ?: STATIC[pkg]
            ?: PREFIXES.firstOrNull { pkg.startsWith(it.first) }?.second

    companion object {
        const val SHIZUKU_MANAGER = "moe.shizuku.privileged.api"

        /** Packages whose loss breaks the phone, the bridge, or the ability to undo. */
        val STATIC: Map<String, String> = mapOf(
            "android" to "Android framework",
            "com.android.systemui" to "System UI",
            "com.android.settings" to "Settings",
            "com.android.phone" to "phone service",
            "com.android.server.telecom" to "call service",
            "com.android.shell" to "shell, used by Shizuku",
            "com.android.packageinstaller" to "package installer",
            "com.google.android.packageinstaller" to "package installer",
            "com.android.permissioncontroller" to "permission controller",
            "com.google.android.permissioncontroller" to "permission controller",
            "com.android.networkstack" to "network stack",
            "com.google.android.networkstack" to "network stack",
            "com.android.bluetooth" to "Bluetooth",
            "com.android.nfc" to "NFC",
            "com.android.se" to "secure element",
            "com.android.externalstorage" to "storage access",
            "com.android.documentsui" to "file picker",
            "com.android.inputdevices" to "input devices",
            SHIZUKU_MANAGER to "Shizuku",
        )

        val PREFIXES: List<Pair<String, String>> = listOf(
            "com.android.providers." to "system data provider",
        )

        /**
         * `settings get secure default_input_method` / Settings.Secure value looks like
         * `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`.
         */
        fun imePackage(setting: String?): String? {
            val v = setting?.trim().orEmpty()
            if (v.isEmpty() || v == "null") return null
            return v.substringBefore('/').takeIf { it.isNotEmpty() && isValidPackageName(it) }
        }
    }
}
