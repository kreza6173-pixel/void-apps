package io.github.kreza6173pixel.cyberappmanager.exec

/**
 * Masks things that look like secrets before anything is shown on the console screen.
 * Pure Kotlin, no Android, unit-tested.
 *
 * This is a display filter only, not a security boundary. It is deliberately blunt: when a
 * line contains a sensitive assignment, everything from that value to the end of the line is
 * hidden. Under-masking a secret is unrecoverable; hiding one extra token is not.
 */
object Redaction {

    const val MASK = "***REDACTED***"

    /** Key names whose assigned value is always masked, matched case-insensitively. */
    private val SENSITIVE_KEYS = listOf(
        "password", "passwd", "pwd", "secret", "token", "apikey", "api_key", "api-key",
        "accesskey", "access_key", "access-key", "privatekey", "private_key", "private-key",
        "auth", "authorization", "bearer", "session", "cookie", "credential", "credentials",
        "passphrase", "otp", "pin",
    )

    /**
     * Keys shorter than this only ever match exactly. Without it, `spin=1` would be masked
     * because it ends with `pin`.
     */
    private const val SUFFIX_MIN_LEN = 4

    /** `KEY=`, `KEY: `, `KEY =`: the key and the separator; the value runs to end of line. */
    private val KEY_ASSIGN =
        Regex("""(?<key>[A-Za-z_][A-Za-z0-9_.\-]*)(?<sep>\s*[:=]\s*)""")

    private fun isSensitiveKey(key: String): Boolean {
        val k = key.lowercase()
        return SENSITIVE_KEYS.any { candidate ->
            k == candidate ||
                (candidate.length >= SUFFIX_MIN_LEN &&
                    (k.endsWith("_$candidate") || k.endsWith("-$candidate") || k.endsWith(candidate)))
        }
    }

    /**
     * Returns [text] with the value of every sensitive assignment replaced by [MASK].
     * Lines are handled one at a time and the key is located with `findAll` rather than a
     * single greedy match, so a harmless `error:` before `password=` cannot hide the password.
     */
    fun redact(text: String): String {
        if (text.isEmpty()) return text
        return text.split('\n').joinToString("\n") { line ->
            val hit = KEY_ASSIGN.findAll(line).firstOrNull { isSensitiveKey(it.groups["key"]!!.value) }
            if (hit == null) line else line.substring(0, hit.range.last + 1) + MASK
        }
    }

    /** Convenience for a whole command line shown in the history list. */
    fun redactCommand(command: String): String = redact(command)
}
