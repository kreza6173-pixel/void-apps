package io.github.kreza6173pixel.cyberappmanager.inventory

/** Guide level only. The protected guard is separate and always wins. */
enum class Risk { SAFE, CAUTION, CORE, USER }

data class KbInfo(val name: String, val category: String, val risk: Risk, val note: String, val known: Boolean)
data class DebloatPreset(val id: String, val name: String, val description: String, val pkgs: List<String>)

/**
 * Package knowledge base ported from the legacy WebUI module and extended with packages found on the
 * reference Xiaomi Redmi Note 14 (Global ROM). Guidance, not a guarantee: ROMs and versions differ.
 */
object KnowledgeBase {
    private fun e(pkg: String, name: String, cat: String, risk: Risk, note: String) = pkg to KbInfo(name, cat, risk, note, true)

    private val ENTRIES: Map<String, KbInfo> = listOf(
        // Android core
        e("android", "Android System", "core", Risk.CORE, "The Android framework itself."),
        e("com.android.systemui", "System UI", "core", Risk.CORE, "Status bar, notifications and navigation."),
        e("com.android.settings", "Settings", "core", Risk.CORE, "System settings app."),
        e("com.android.phone", "Phone Services", "core", Risk.CORE, "Telephony stack and SIM handling."),
        e("com.android.server.telecom", "Telecom", "core", Risk.CORE, "Call routing service."),
        e("com.android.shell", "Shell", "core", Risk.CORE, "The shell identity Shizuku runs as."),
        e("com.android.providers.settings", "Settings Storage", "core", Risk.CORE, "Stores system settings."),
        e("com.android.providers.contacts", "Contacts Storage", "core", Risk.CORE, "Contacts database."),
        e("com.android.providers.telephony", "Telephony Storage", "core", Risk.CORE, "SMS and MMS database."),
        e("com.android.providers.media", "Media Storage", "core", Risk.CORE, "Media index used by every gallery and player."),
        e("com.google.android.providers.media.module", "Media Storage", "core", Risk.CORE, "Media index used by every gallery and player."),
        e("com.android.providers.downloads", "Download Manager", "core", Risk.CORE, "Handles downloads system-wide."),
        e("com.android.providers.calendar", "Calendar Storage", "core", Risk.CORE, "Calendar database."),
        e("com.google.android.packageinstaller", "Package Installer", "core", Risk.CORE, "Installs and uninstalls apps."),
        e("com.google.android.permissioncontroller", "Permission Controller", "core", Risk.CORE, "Runtime permission dialogs."),
        e("com.android.bluetooth", "Bluetooth", "core", Risk.CORE, "Bluetooth stack."),
        e("com.android.nfc", "NFC Service", "core", Risk.CORE, "NFC stack."),
        e("com.android.location.fused", "Fused Location", "core", Risk.CORE, "Location provider."),
        e("com.android.keychain", "Key Chain", "core", Risk.CORE, "Credential storage."),
        e("com.android.vpndialogs", "VPN Dialogs", "core", Risk.CORE, "Consent dialog every VPN app depends on."),
        e("com.android.carrierconfig", "Carrier Config", "core", Risk.CORE, "Carrier settings."),
        e("com.google.android.networkstack", "Network Stack", "core", Risk.CORE, "Connectivity checks and DHCP."),
        e("com.google.android.networkstack.tethering", "Tethering", "core", Risk.CORE, "Hotspot and tethering."),
        e("com.google.android.gms", "Google Play services", "google", Risk.CORE, "Push, location, sign-in and most Google-dependent apps."),
        e("com.google.android.gsf", "Google Services Framework", "google", Risk.CORE, "Required by Google Play services."),
        e("com.google.android.webview", "Android System WebView", "core", Risk.CORE, "Renders web content inside apps."),
        e("moe.shizuku.privileged.api", "Shizuku", "core", Risk.CORE, "Provides the shell access this app uses."),
        e("com.miui.system", "MIUI System", "oem", Risk.CORE, "HyperOS system resources."),
        e("com.miui.rom", "MIUI ROM resources", "oem", Risk.CORE, "HyperOS framework resources."),
        e("com.miui.core", "MIUI Core", "oem", Risk.CORE, "HyperOS core library."),
        e("com.lbe.security.miui", "Permission manager", "oem", Risk.CORE, "HyperOS permission enforcement."),

        // AOSP components
        e("com.android.vending", "Google Play Store", "google", Risk.CAUTION, "App store and update service."),
        e("com.android.certinstaller", "Certificate Installer", "aosp", Risk.CAUTION, "Installs certificates and VPN profiles."),
        e("com.google.android.captiveportallogin", "Captive Portal Login", "aosp", Risk.CAUTION, "Sign-in page for public Wi-Fi."),
        e("com.android.emergency", "Emergency Info", "aosp", Risk.CAUTION, "Emergency contacts on the lock screen."),
        e("com.android.cellbroadcastreceiver", "Emergency Alerts", "aosp", Risk.CAUTION, "Government and carrier alerts."),
        e("com.android.stk", "SIM Toolkit", "aosp", Risk.CAUTION, "Carrier SIM menus."),
        e("com.android.mms.service", "MMS Service", "aosp", Risk.CAUTION, "Multimedia messaging."),
        e("com.android.musicfx", "MusicFX", "aosp", Risk.CAUTION, "Equalizer used by music apps."),
        e("com.android.soundpicker", "Sound Picker", "aosp", Risk.CAUTION, "Ringtone and notification picker."),
        e("com.android.contacts", "Contacts", "oem", Risk.CAUTION, "Contacts and dialer on HyperOS."),
        e("com.android.mms", "Messaging", "oem", Risk.CAUTION, "Xiaomi SMS app."),
        e("com.android.camera", "Camera", "oem", Risk.CAUTION, "Xiaomi camera."),
        e("com.android.chrome", "Chrome", "google", Risk.CAUTION, "Web browser."),
        e("com.android.printspooler", "Print Spooler", "aosp", Risk.CAUTION, "Printing framework, also used for Save as PDF."),
        e("com.android.bips", "Default Print Service", "aosp", Risk.SAFE, "Printing. Safe if you never print."),
        e("com.android.egg", "Android Easter Egg", "aosp", Risk.SAFE, "Hidden mini-game."),
        e("com.android.wallpaper.livepicker", "Live Wallpaper Picker", "aosp", Risk.SAFE, "Only needed to pick live wallpapers."),
        e("com.android.dreams.basic", "Basic Screensaver", "aosp", Risk.SAFE, "Screensaver."),
        e("com.android.dreams.phototable", "Photo Screensaver", "aosp", Risk.SAFE, "Screensaver."),
        e("com.android.bookmarkprovider", "Bookmark Provider", "aosp", Risk.SAFE, "Legacy browser bookmarks."),
        e("com.android.providers.partnerbookmarks", "Partner Bookmarks", "aosp", Risk.SAFE, "Preloaded carrier bookmarks."),
        e("com.android.htmlviewer", "HTML Viewer", "aosp", Risk.SAFE, "Opens local .html files."),
        e("com.android.traceur", "System Tracing", "aosp", Risk.SAFE, "Developer tracing tool."),

        // Google apps
        e("com.google.android.apps.tachyon", "Google Meet", "google", Risk.SAFE, "Video calling."),
        e("com.google.android.youtube", "YouTube", "google", Risk.SAFE, "Video app."),
        e("com.google.android.apps.youtube.music", "YouTube Music", "google", Risk.SAFE, "Music streaming."),
        e("com.google.android.videos", "Google TV", "google", Risk.SAFE, "Movies and TV store."),
        e("com.google.android.apps.subscriptions.red", "Google One", "google", Risk.SAFE, "Storage subscription app."),
        e("com.google.android.apps.bard", "Gemini", "google", Risk.SAFE, "AI assistant app."),
        e("com.google.ambient.streaming", "Cross-device services", "google", Risk.SAFE, "Streams apps to other devices."),
        e("com.google.android.apps.restore", "Data Restore Tool", "google", Risk.SAFE, "Only used when copying data from an old phone."),
        e("com.google.android.apps.docs", "Google Drive", "google", Risk.CAUTION, "Cloud files."),
        e("com.google.android.apps.photos", "Google Photos", "google", Risk.CAUTION, "Photo backup and gallery."),
        e("com.google.android.gm", "Gmail", "google", Risk.CAUTION, "Email client."),
        e("com.google.android.calendar", "Google Calendar", "google", Risk.CAUTION, "Calendar app."),
        e("com.google.android.apps.maps", "Google Maps", "google", Risk.CAUTION, "Maps and navigation."),
        e("com.google.android.apps.messaging", "Google Messages", "google", Risk.CAUTION, "SMS and RCS app."),
        e("com.google.android.dialer", "Google Phone", "google", Risk.CAUTION, "Dialer."),
        e("com.google.android.contacts", "Google Contacts", "google", Risk.CAUTION, "Contacts app."),
        e("com.google.android.googlequicksearchbox", "Google app", "google", Risk.CAUTION, "Search, Assistant and the default feed."),
        e("com.google.android.apps.wellbeing", "Digital Wellbeing", "google", Risk.SAFE, "Screen-time tracking and focus modes."),
        e("com.google.android.feedback", "Market Feedback Agent", "google", Risk.SAFE, "Sends install feedback to Google."),
        e("com.google.android.partnersetup", "Google Partner Setup", "google", Risk.SAFE, "First-run partner configuration."),
        e("com.google.android.onetimeinitializer", "Google One Time Init", "google", Risk.SAFE, "First-boot helper."),
        e("com.google.android.printservice.recommendation", "Print Service Recommendation", "google", Risk.SAFE, "Suggests print plugins."),
        e("com.google.android.syncadapters.calendar", "Google Calendar Sync", "google", Risk.CAUTION, "Syncs calendar with your account."),
        e("com.google.android.projection.gearhead", "Android Auto", "google", Risk.SAFE, "Car integration."),
        e("com.google.android.marvin.talkback", "TalkBack", "google", Risk.CAUTION, "Screen reader (accessibility)."),
        e("com.google.android.tts", "Speech Services by Google", "google", Risk.CAUTION, "Text-to-speech engine."),
        e("com.google.android.inputmethod.latin", "Gboard", "google", Risk.CAUTION, "Google keyboard."),
        e("com.google.android.ext.services", "Android Services Library", "google", Risk.CAUTION, "Notification ranking and other system helpers."),
        e("com.google.android.ext.shared", "Android Shared Library", "google", Risk.CAUTION, "Shared library for system helpers."),
        e("com.google.android.as", "Android System Intelligence", "google", Risk.CAUTION, "On-device suggestions and smart features."),
        e("com.google.android.setupwizard", "Setup Wizard", "google", Risk.CAUTION, "Used during first setup and after a reset."),
        e("com.google.mainline.telemetry", "Mainline Telemetry", "google", Risk.CAUTION, "Mainline module metadata."),

        // Facebook stubs
        e("com.facebook.appmanager", "Facebook App Manager", "social", Risk.SAFE, "Preinstalled helper that keeps the Facebook app updated."),
        e("com.facebook.services", "Facebook Services", "social", Risk.SAFE, "Preinstalled background service."),
        e("com.facebook.system", "Facebook App Installer", "social", Risk.SAFE, "Preinstalled installer stub."),

        // Xiaomi / HyperOS
        e("com.miui.analytics", "Xiaomi Analytics", "oem", Risk.SAFE, "Usage telemetry."),
        e("com.miui.msa.global", "MSA (system ads)", "oem", Risk.SAFE, "Ad-delivery service."),
        e("com.miui.systemAdSolution", "Ad Solution", "oem", Risk.SAFE, "Ad-delivery service."),
        e("com.miui.bugreport", "Bug Report", "oem", Risk.SAFE, "Feedback and bug-report tool."),
        e("com.xiaomi.mipicks", "GetApps", "oem", Risk.SAFE, "Xiaomi app store with promotions."),
        e("com.miui.player", "Mi Music", "oem", Risk.SAFE, "Xiaomi music player."),
        e("com.miui.videoplayer", "Mi Video", "oem", Risk.SAFE, "Xiaomi video player."),
        e("com.miui.yellowpage", "Yellow Pages", "oem", Risk.SAFE, "Caller ID and business directory."),
        e("com.mi.globalbrowser", "Mi Browser", "oem", Risk.SAFE, "Xiaomi web browser."),
        e("com.mi.globalminusscreen", "App Vault", "oem", Risk.SAFE, "Left-of-home feed."),
        e("com.mi.appfinder", "App Finder", "oem", Risk.SAFE, "Launcher app search."),
        e("com.xiaomi.glgm", "Games", "oem", Risk.SAFE, "Game hub with promotions."),
        e("com.miui.miservice", "Services and feedback", "oem", Risk.SAFE, "Support and feedback app."),
        e("com.xiaomi.barrage", "Floating notifications", "oem", Risk.SAFE, "On-screen message overlay."),
        e("com.miui.fm", "FM Radio", "oem", Risk.SAFE, "Radio app."),
        e("com.miui.qr", "Device info QR", "oem", Risk.SAFE, "Shows device info as a QR code."),
        e("com.huaqin.factory", "Factory test (CIT)", "oem", Risk.SAFE, "Hardware test menu used in the factory."),
        e("com.mi.AutoTest", "Auto test", "oem", Risk.SAFE, "Factory automation test."),
        e("com.debug.loggerui", "Debug logger", "vendor", Risk.SAFE, "MediaTek debug logging tool."),
        e("com.xiaomi.finddevice", "Find Device", "oem", Risk.CAUTION, "Locate, lock or wipe the phone remotely."),
        e("com.miui.securitycenter", "Security", "oem", Risk.CAUTION, "Core HyperOS security and optimisation hub."),
        e("com.miui.powerkeeper", "Battery and Performance", "oem", Risk.CAUTION, "Battery management daemon."),
        e("com.xiaomi.xmsf", "Xiaomi Service Framework", "oem", Risk.CAUTION, "Push services for Xiaomi apps."),
        e("com.xiaomi.account", "Xiaomi Account", "oem", Risk.CAUTION, "Mi account sign-in."),
        e("com.miui.cloudservice", "Mi Cloud", "oem", Risk.CAUTION, "Cloud sync and backup."),
        e("com.miui.home", "System Launcher", "oem", Risk.CAUTION, "Default Xiaomi home screen."),
        e("com.miui.gallery", "Gallery", "oem", Risk.CAUTION, "Xiaomi gallery."),
        e("com.miui.cleaner", "Cleaner", "oem", Risk.CAUTION, "Storage cleaner used by Security."),
        e("com.xiaomi.joyose", "Joyose", "oem", Risk.CAUTION, "Performance profiles; may affect gaming performance."),
        e("com.xiaomi.discover", "System apps updater", "oem", Risk.CAUTION, "Delivers updates for Xiaomi apps."),
        e("com.miui.misound", "Mi Sound", "oem", Risk.CAUTION, "Audio effects and headphone tuning."),
        e("com.miui.fmservice", "FM service", "oem", Risk.CAUTION, "Radio hardware service."),
        e("com.xiaomi.scanner", "Scanner", "oem", Risk.CAUTION, "QR and document scanner."),
        e("com.xiaomi.cameratools", "Camera calibration", "oem", Risk.CAUTION, "Factory camera calibration tool."),
        e("com.jiiov.fingerprint_factorytest", "Fingerprint factory test", "vendor", Risk.CAUTION, "Fingerprint sensor test tool."),
        e("com.mi.healthglobal", "Mi Health", "oem", Risk.CAUTION, "Health and fitness tracking."),
        e("com.xiaomi.aiasst.vision", "AI vision", "oem", Risk.CAUTION, "AI assistant features."),
        e("com.microsoft.appmanager", "Link to Windows", "oem", Risk.CAUTION, "Phone link with a Windows PC."),
        e("com.microsoft.deviceintegrationservice", "Device integration service", "oem", Risk.CAUTION, "Used by Link to Windows."),
        e("com.microsoftsdk.crossdeviceservicebroker", "Cross-device service broker", "oem", Risk.CAUTION, "Used by Link to Windows."),

        // Samsung
        e("com.samsung.android.bixby.agent", "Bixby", "oem", Risk.SAFE, "Voice assistant."),
        e("com.samsung.android.bixby.service", "Bixby Service", "oem", Risk.SAFE, "Bixby background service."),
        e("com.samsung.android.app.spage", "Samsung Free", "oem", Risk.SAFE, "Left-of-home content feed."),
        e("com.samsung.android.game.gamehome", "Game Launcher", "oem", Risk.SAFE, "Game hub with recommendations."),
        e("com.samsung.android.arzone", "AR Zone", "oem", Risk.SAFE, "AR features hub."),
        e("com.samsung.android.app.tips", "Tips", "oem", Risk.SAFE, "Onboarding tips."),
    ).toMap()

    /** Presets may only contain known SAFE entries; a unit test enforces this. */
    val presets: List<DebloatPreset> = listOf(
        DebloatPreset("telemetry", "Telemetry and ads", "Analytics and ad-delivery services.", listOf("com.miui.analytics", "com.miui.msa.global", "com.miui.systemAdSolution", "com.miui.bugreport", "com.google.android.feedback", "com.debug.loggerui")),
        DebloatPreset("facebook", "Facebook stubs", "Preinstalled Facebook helper packages.", listOf("com.facebook.appmanager", "com.facebook.services", "com.facebook.system")),
        DebloatPreset("xiaomi", "Xiaomi extras", "Promotional and duplicate Xiaomi apps.", listOf("com.xiaomi.mipicks", "com.miui.player", "com.miui.videoplayer", "com.miui.yellowpage", "com.mi.globalbrowser", "com.mi.globalminusscreen", "com.mi.appfinder", "com.xiaomi.glgm", "com.miui.miservice", "com.xiaomi.barrage", "com.miui.fm")),
        DebloatPreset("factory", "Factory and test tools", "Hardware test menus and debug tools left from the factory.", listOf("com.huaqin.factory", "com.mi.AutoTest", "com.miui.qr", "com.android.traceur")),
        DebloatPreset("google", "Google extras", "Optional Google apps and helpers.", listOf("com.google.android.apps.tachyon", "com.google.android.apps.youtube.music", "com.google.android.videos", "com.google.android.apps.subscriptions.red", "com.google.android.apps.bard", "com.google.ambient.streaming", "com.google.android.apps.restore", "com.google.android.apps.wellbeing", "com.google.android.projection.gearhead", "com.google.android.printservice.recommendation", "com.google.android.onetimeinitializer", "com.google.android.partnersetup")),
        DebloatPreset("aosp", "AOSP leftovers", "Screensavers, easter egg and other rarely used components.", listOf("com.android.bips", "com.android.egg", "com.android.wallpaper.livepicker", "com.android.dreams.basic", "com.android.dreams.phototable", "com.android.bookmarkprovider", "com.android.providers.partnerbookmarks", "com.android.htmlviewer")),
        DebloatPreset("samsung", "Samsung extras", "Bixby, Samsung Free and other optional One UI apps.", listOf("com.samsung.android.bixby.agent", "com.samsung.android.bixby.service", "com.samsung.android.app.spage", "com.samsung.android.game.gamehome", "com.samsung.android.arzone", "com.samsung.android.app.tips")),
    )

    private val OVERLAY_RE = Regex("""(\.auto_generated_rro_|_rro$|\.overlay(\.|$)|_overlay$|\.rro$|^com\.android\.theme\.|^com\.android\.internal\.|\.resources$)""", RegexOption.IGNORE_CASE)
    private val VENDOR_RE = Regex("""^(com\.qualcomm\.|com\.qti\.|vendor\.qti\.|org\.codeaurora\.|com\.mediatek\.|com\.mtk\.|com\.unisoc\.|com\.sprd\.|com\.nxp\.|com\.goodix\.|com\.novatek\.)""")
    private val CORE_PREFIX = Regex("""^(com\.android\.providers\.|com\.android\.server\.|com\.android\.wifi|com\.google\.android\.overlay\.|com\.android\.cts\.)""")
    private val TLD = Regex("^(com|org|net|io|app|android|me|dev|co|ir|de|fr|ru|cn|jp)$")
    private val GENERIC_LAST = setOf("app", "android", "mobile", "client", "main", "free", "pro", "lite", "release", "phone", "prod", "full")

    val size: Int get() = ENTRIES.size

    fun classify(pkg: String, isSystem: Boolean): KbInfo {
        ENTRIES[pkg]?.let { return it }
        val name = prettify(pkg)
        return when {
            CORE_PREFIX.containsMatchIn(pkg) -> KbInfo(name, "core", Risk.CORE, "System component.", false)
            OVERLAY_RE.containsMatchIn(pkg) -> KbInfo(name, "overlay", Risk.CORE, "Resource overlay (theme or config). Not an app.", false)
            !isSystem -> KbInfo(name, "user", Risk.USER, "Installed by you.", false)
            VENDOR_RE.containsMatchIn(pkg) -> KbInfo(name, "vendor", Risk.CAUTION, "Chipset or vendor component. Research before touching.", false)
            pkg.startsWith("com.android.") -> KbInfo(name, "aosp", Risk.CAUTION, "AOSP component.", false)
            pkg.startsWith("com.google.") -> KbInfo(name, "google", Risk.CAUTION, "Google component.", false)
            pkg.startsWith("com.miui.") || pkg.startsWith("com.xiaomi.") || pkg.startsWith("com.mi.") -> KbInfo(name, "oem", Risk.CAUTION, "Xiaomi or HyperOS component.", false)
            else -> KbInfo(name, "oem", Risk.CAUTION, "Unknown system package. Research before changing it.", false)
        }
    }

    fun prettify(pkg: String): String {
        val parts = pkg.split('.').filter { it.isNotEmpty() }.toMutableList()
        while (parts.size > 1 && TLD.matches(parts[0])) parts.removeAt(0)
        val seg = if (parts.size > 1 && parts.last() in GENERIC_LAST) parts.takeLast(2) else parts.takeLast(1)
        val words = seg.joinToString(" ").replace(Regex("[_-]+"), " ").replace(Regex("([a-z0-9])([A-Z])"), "\$1 \$2").split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }.ifEmpty { pkg }
    }
}
