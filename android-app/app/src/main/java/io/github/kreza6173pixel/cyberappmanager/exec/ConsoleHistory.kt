package io.github.kreza6173pixel.cyberappmanager.exec

/**
 * One row of the console history. Pure data, no Android, so it is unit-tested.
 *
 * [displayCommand], [displayStdout] and [displayStderr] are the *redacted* forms. The raw
 * output is never stored in the history at all: redaction happens once, at insert time.
 */
data class HistoryEntry(
    val displayCommand: String,
    val exitCode: Int,
    val durationMs: Long,
    val displayStdout: String,
    val displayStderr: String,
    val truncated: Boolean,
    val failed: Boolean = false,
)

/**
 * Fixed-capacity, in-memory console history. Oldest entries are dropped first. Pure Kotlin.
 */
class ConsoleHistory(val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    private val entries = ArrayDeque<HistoryEntry>()

    /** Newest first, so a list renders top-down without reversing. */
    val items: List<HistoryEntry> get() = entries.toList().asReversed()

    val size: Int get() = entries.size

    fun add(entry: HistoryEntry) {
        entries.addLast(entry)
        while (entries.size > capacity) entries.removeFirst()
    }

    fun clear() = entries.clear()

    /**
     * Builds and stores an entry, redacting the command and both output streams *before*
     * anything is retained.
     */
    fun record(
        rawCommand: String,
        exitCode: Int,
        durationMs: Long,
        stdout: String,
        stderr: String,
        truncated: Boolean,
    ): HistoryEntry {
        val entry = HistoryEntry(
            displayCommand = Redaction.redactCommand(rawCommand),
            exitCode = exitCode,
            durationMs = durationMs,
            displayStdout = Redaction.redact(stdout),
            displayStderr = Redaction.redact(stderr),
            truncated = truncated,
            failed = false,
        )
        add(entry)
        return entry
    }

    /** Stores an entry for a command that never reached the service. */
    fun recordFailure(rawCommand: String, message: String): HistoryEntry {
        val entry = HistoryEntry(
            displayCommand = Redaction.redactCommand(rawCommand),
            exitCode = NO_EXIT_CODE,
            durationMs = 0L,
            displayStdout = "",
            displayStderr = Redaction.redact(message),
            truncated = false,
            failed = true,
        )
        add(entry)
        return entry
    }

    companion object {
        const val DEFAULT_CAPACITY = 50

        /** Shown when the command never produced an exit code at all. */
        const val NO_EXIT_CODE = -1
    }
}
