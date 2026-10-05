package io.github.kreza6173pixel.cyberappmanager.inventory

private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")
fun isValidPackageName(name: String): Boolean = name.length <= 255 && PACKAGE_NAME.matches(name)

fun parsePackageList(stdout: String): Set<String> {
    val out = LinkedHashSet<String>()
    for (raw in stdout.lineSequence()) {
        var line = raw.trim()
        if (line.startsWith("package:")) line = line.removePrefix("package:")
        val name = line.substringBefore(' ').trim()
        if (name.isNotEmpty() && isValidPackageName(name)) out.add(name)
    }
    return out
}

fun normalizeDigits(s: String): String {
    if (s.none { it in '\u06F0'..'\u06F9' || it in '\u0660'..'\u0669' }) return s
    return buildString(s.length) {
        for (c in s) append(when (c) {
            in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
            in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
            else -> c
        })
    }
}

data class PackageDetails(
    val versionName: String?, val versionCode: Long?, val minSdk: Int?, val targetSdk: Int?,
    val installer: String?, val firstInstall: String?, val lastUpdate: String?,
    val userFlags: Map<String, String>,
)

private val VERSION_CODE = Regex("\\bversionCode=(\\d+)")
private val MIN_SDK = Regex("\\bminSdk=(\\d+)")
private val TARGET_SDK = Regex("\\btargetSdk=(\\d+)")
private val INSTALLER = Regex("\\binstallerPackageName=(\\S+)")
private val KEY_VALUE = Regex("(\\w+)=(\\S+)")

fun parsePackageDetails(text: String): PackageDetails {
    var versionName: String? = null; var versionCode: Long? = null; var minSdk: Int? = null
    var targetSdk: Int? = null; var installer: String? = null; var firstInstall: String? = null
    var lastUpdate: String? = null; val userFlags = LinkedHashMap<String, String>()
    for (raw in text.lineSequence()) {
        val line = normalizeDigits(raw).trim()
        if (line.isEmpty()) continue
        if (versionName == null && line.startsWith("versionName=")) versionName = line.removePrefix("versionName=").trim()
        if (versionCode == null) VERSION_CODE.find(line)?.let { versionCode = it.groupValues[1].toLongOrNull(); minSdk = MIN_SDK.find(line)?.groupValues?.get(1)?.toIntOrNull(); targetSdk = TARGET_SDK.find(line)?.groupValues?.get(1)?.toIntOrNull() }
        if (installer == null) INSTALLER.find(line)?.let { installer = it.groupValues[1].takeIf { v -> v != "null" } }
        if (firstInstall == null && line.startsWith("firstInstallTime=")) firstInstall = line.removePrefix("firstInstallTime=").trim()
        if (lastUpdate == null && line.startsWith("lastUpdateTime=")) lastUpdate = line.removePrefix("lastUpdateTime=").trim()
        if (userFlags.isEmpty() && line.startsWith("User 0:") && line.contains("installed=")) KEY_VALUE.findAll(line.removePrefix("User 0:")).forEach { userFlags[it.groupValues[1]] = it.groupValues[2] }
    }
    return PackageDetails(versionName, versionCode, minSdk, targetSdk, installer, firstInstall, lastUpdate, userFlags)
}
