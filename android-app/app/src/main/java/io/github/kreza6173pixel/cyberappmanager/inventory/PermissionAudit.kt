package io.github.kreza6173pixel.cyberappmanager.inventory

/**
 * One permission as Android reports it for user 0.
 * [runtime] is true only when the permission was listed under the user 0 `runtime permissions:` section.
 */
data class PermissionRecord(
    val name: String,
    val requested: Boolean,
    val granted: Boolean?,
    val runtime: Boolean = false,
    val flags: Set<String> = emptySet(),
) {
    /** Locked by the system or a device policy; `pm grant/revoke` must not be attempted. */
    val fixed: Boolean get() = flags.any { it == "SYSTEM_FIXED" || it == "POLICY_FIXED" }

    /** Only runtime permissions with a known state and no fixed flag may be changed. */
    val changeable: Boolean get() = runtime && !fixed && granted != null
}

data class PermissionAudit(val packageName: String, val permissions: List<PermissionRecord>)

private enum class PermSection { NONE, REQUESTED, INSTALL, RUNTIME }

private val PERMISSION_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
private val NAME_LINE = Regex("^([A-Za-z][A-Za-z0-9_.]*)(: .*)?$")
private val GRANT_LINE = Regex("^([A-Za-z][A-Za-z0-9_.]*): granted=(true|false)(.*)$")
private val FLAGS = Regex("flags=\\[([^\\]]*)\\]")
private val USER_LINE = Regex("^User (\\d+):")

fun isValidPermissionName(name: String): Boolean = name.length <= 255 && PERMISSION_NAME.matches(name)

/**
 * Parses the permission sections of `dumpsys package <pkg>`:
 * `requested permissions:`, `install permissions:` and the `runtime permissions:` section of `User 0:` only.
 * Any line that does not fit the current section ends it, so unknown ROM output never creates state.
 */
fun parsePermissionAudit(packageName: String, text: String): PermissionAudit {
    val requested = LinkedHashSet<String>()
    val install = HashMap<String, Boolean>()
    val runtime = HashMap<String, Pair<Boolean, Set<String>>>()
    var section = PermSection.NONE
    var user: Int? = null
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) { section = PermSection.NONE; continue }
        val userMatch = USER_LINE.find(line)
        if (userMatch != null) { user = userMatch.groupValues[1].toIntOrNull(); section = PermSection.NONE; continue }
        when (line) {
            "requested permissions:" -> { section = PermSection.REQUESTED; continue }
            "install permissions:" -> { section = PermSection.INSTALL; continue }
            "runtime permissions:" -> { section = if (user == 0) PermSection.RUNTIME else PermSection.NONE; continue }
        }
        when (section) {
            PermSection.REQUESTED -> {
                val m = NAME_LINE.matchEntire(line)
                if (m != null && isValidPermissionName(m.groupValues[1])) requested += m.groupValues[1] else section = PermSection.NONE
            }
            PermSection.INSTALL -> {
                val m = GRANT_LINE.matchEntire(line)
                if (m != null && isValidPermissionName(m.groupValues[1])) install[m.groupValues[1]] = m.groupValues[2] == "true" else section = PermSection.NONE
            }
            PermSection.RUNTIME -> {
                val m = GRANT_LINE.matchEntire(line)
                if (m != null && isValidPermissionName(m.groupValues[1])) {
                    val flags = FLAGS.find(m.groupValues[3])?.groupValues?.get(1)?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
                    runtime[m.groupValues[1]] = (m.groupValues[2] == "true") to flags
                } else section = PermSection.NONE
            }
            PermSection.NONE -> Unit
        }
    }
    val names = LinkedHashSet<String>(requested)
    names += install.keys
    names += runtime.keys
    val records = names.map { n ->
        val rt = runtime[n]
        if (rt != null) PermissionRecord(n, n in requested, rt.first, true, rt.second)
        else PermissionRecord(n, n in requested, install[n])
    }.sortedBy { it.name }
    return PermissionAudit(packageName, records)
}
