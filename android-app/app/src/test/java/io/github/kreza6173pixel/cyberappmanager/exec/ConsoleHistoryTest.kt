package io.github.kreza6173pixel.cyberappmanager.exec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleHistoryTest {

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive capacity is rejected`() {
        ConsoleHistory(0)
    }

    @Test
    fun `a new history is empty`() {
        assertEquals(0, ConsoleHistory().size)
    }

    @Test
    fun `entries come back newest first`() {
        val h = ConsoleHistory()
        h.add(entry("one"))
        h.add(entry("two"))
        assertEquals(listOf("two", "one"), h.items.map { it.displayCommand })
    }

    @Test
    fun `capacity is enforced by dropping the oldest`() {
        val h = ConsoleHistory(capacity = 3)
        for (i in 1..5) h.add(entry("cmd$i"))
        assertEquals(3, h.size)
        assertEquals(listOf("cmd5", "cmd4", "cmd3"), h.items.map { it.displayCommand })
    }

    @Test
    fun `record stores the exit code, duration and truncated flag`() {
        val h = ConsoleHistory()
        val e = h.record("id", exitCode = 0, durationMs = 12, stdout = "uid=2000", stderr = "", truncated = true)
        assertEquals(0, e.exitCode)
        assertEquals(12L, e.durationMs)
        assertTrue(e.truncated)
        assertFalse(e.failed)
        assertEquals(1, h.size)
    }

    @Test
    fun `record redacts stdout before storing it`() {
        val h = ConsoleHistory()
        val e = h.record("cat env", 0, 5, "API_TOKEN=abc123\nHOME=/root", "", false)
        assertFalse(e.displayStdout.contains("abc123"))
        assertTrue(e.displayStdout.contains(Redaction.MASK))
        assertTrue(e.displayStdout.contains("HOME=/root"))
    }

    @Test
    fun `record redacts stderr before storing it`() {
        val h = ConsoleHistory()
        val e = h.record("cmd", 1, 5, "", "password=hunter2", false)
        assertFalse(e.displayStderr.contains("hunter2"))
    }

    @Test
    fun `record redacts the stored command text`() {
        val h = ConsoleHistory()
        val e = h.record("export password=hunter2", 0, 1, "", "", false)
        assertFalse(e.displayCommand.contains("hunter2"))
    }

    @Test
    fun `a failure is recorded with no exit code`() {
        val h = ConsoleHistory()
        val e = h.recordFailure("id", "not connected")
        assertTrue(e.failed)
        assertEquals(ConsoleHistory.NO_EXIT_CODE, e.exitCode)
        assertEquals("not connected", e.displayStderr)
    }

    @Test
    fun `recordFailure redacts the message`() {
        val h = ConsoleHistory()
        val e = h.recordFailure("id", "binder error: password=hunter2")
        assertFalse(e.displayStderr.contains("hunter2"))
    }

    @Test
    fun `clear empties the history`() {
        val h = ConsoleHistory()
        h.add(entry("x"))
        h.clear()
        assertEquals(0, h.size)
    }

    private fun entry(command: String) = HistoryEntry(
        displayCommand = command,
        exitCode = 0,
        durationMs = 0,
        displayStdout = "",
        displayStderr = "",
        truncated = false,
    )
}
