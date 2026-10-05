package io.github.kreza6173pixel.cyberappmanager.inventory

/**
 * Shell command for the permission audit. The full `dumpsys package` of large apps exceeds the
 * 64 KiB ExecBridge cap (android: 108563 bytes; Google Play services: 83925 bytes on the
 * reference Redmi Note 14).
 *
 * The parser needs only requested permissions, install permissions, the User 0 runtime
 * permissions section, and the sharedUser marker. Large sections such as declared permissions,
 * libraries, overlay paths and component lists are irrelevant to the audit. Extracting only those
 * sections before ExecBridge receives the output keeps the command ROM-tolerant without guessing
 * a byte cutoff. The awk state machine stops a section at the next indented lowercase header.
 * The `Packages:` and `User 0:` markers are retained because the parser uses the latter to scope
 * runtime permissions and the former makes diagnostics readable.
 */
fun permissionDumpCommand(quotedPkg: String): String = "dumpsys package $quotedPkg | awk '/^Packages:/ {inside=1; print; next} inside && /^[A-Z]/ {exit} inside && /^User 0:/ {print; next} inside && /sharedUser=SharedUserSetting\\{/ {print; next} inside && /^[[:space:]]*(requested permissions|install permissions|runtime permissions):$/ {keep=1; print; next} inside && keep && /^[[:space:]]*[a-z][a-z ]*:[[:space:]]*$/ {keep=0; next} inside && keep {print}'"

/**
 * Packages in a shared uid keep their runtime permissions in the `Shared users:` block, not in
 * `Packages:` (com.miui.securitycenter, android.uid.system/1000: `runtime permissions:` at line 4021,
 * inside Shared users: 3501..4049). Read only when [sharedUserOf] finds a shared user.
 */
fun sharedUsersDumpCommand(quotedPkg: String): String = "dumpsys package $quotedPkg | sed -n '/^Shared users:/,/^[A-Z]/p'"

/** A shared uid. Runtime permissions belong to the whole uid, so a change applies to every package in it. */
data class SharedUserInfo(val name: String, val uid: Int) {
    /** System range (for example android.uid.system/1000). Permission writes are refused for these. */
    val systemUid: Boolean get() = uid < 10000
}

private val SHARED_USER = Regex("sharedUser=SharedUserSetting\\{\\S+ ([^/\\s]+)/(\\d+)\\}")

/** First shared user in the extracted permission output, for example `sharedUser=SharedUserSetting{cfee48 android.uid.system/1000}`. */
fun sharedUserOf(packagesBlock: String): SharedUserInfo? =
    SHARED_USER.find(packagesBlock)?.let { m -> m.groupValues[2].toIntOrNull()?.let { SharedUserInfo(m.groupValues[1], it) } }
