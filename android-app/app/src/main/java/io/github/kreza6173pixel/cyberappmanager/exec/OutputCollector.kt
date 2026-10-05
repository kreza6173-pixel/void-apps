package io.github.kreza6173pixel.cyberappmanager.exec

/**
 * Accumulates process output up to a character cap. Pure Kotlin, no Android, unit-tested.
 *
 * Two rules:
 *  1. Never grow past [limitChars] characters, so a runaway `logcat` cannot exhaust memory.
 *  2. Once the cap is hit, [truncated] latches true and further input is dropped.
 */
class OutputCollector(val limitChars: Int) {

    init {
        require(limitChars > 0) { "limitChars must be > 0" }
    }

    private val builder = StringBuilder()

    var size: Int = 0
        private set

    /** Latches true as soon as the cap is exceeded; never returns to false. */
    var truncated: Boolean = false
        private set

    val text: String get() = builder.toString()

    /** Appends one character. Ignored once the cap is reached. */
    fun append(c: Char) {
        if (truncated) return
        if (size >= limitChars) {
            truncated = true
            return
        }
        builder.append(c)
        size++
    }

    /** Appends as many characters as still fit. Returns false if anything was dropped. */
    fun appendAll(text: CharSequence): Boolean {
        for (c in text) append(c)
        return !truncated
    }

    /** Appends [chunk] plus a newline, respecting the cap. Used for one output line. */
    fun appendLine(chunk: String) {
        appendAll(chunk)
        append('\n')
    }

    /** Marks output as truncated even if the cap was never reached (e.g. killed by timeout). */
    fun markTruncated() {
        truncated = true
    }
}
