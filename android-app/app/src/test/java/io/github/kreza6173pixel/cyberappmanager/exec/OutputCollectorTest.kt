package io.github.kreza6173pixel.cyberappmanager.exec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputCollectorTest {

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive limit is rejected`() {
        OutputCollector(0)
    }

    @Test
    fun `nothing appended leaves truncated false`() {
        val out = OutputCollector(16)
        assertEquals("", out.text)
        assertEquals(0, out.size)
        assertFalse(out.truncated)
    }

    @Test
    fun `output below the cap is kept whole and not truncated`() {
        val out = OutputCollector(16)
        assertTrue(out.appendAll("hello"))
        assertEquals("hello", out.text)
        assertEquals(5, out.size)
        assertFalse(out.truncated)
    }

    @Test
    fun `output exactly at the cap is not truncated`() {
        val out = OutputCollector(5)
        assertTrue(out.appendAll("12345"))
        assertEquals("12345", out.text)
        assertFalse(out.truncated)
    }

    @Test
    fun `output one char over the cap is truncated`() {
        val out = OutputCollector(5)
        assertFalse(out.appendAll("123456"))
        assertEquals("12345", out.text)
        assertEquals(5, out.size)
        assertTrue(out.truncated)
    }

    @Test
    fun `the cap is never exceeded`() {
        val out = OutputCollector(10)
        out.appendAll("x".repeat(10_000))
        assertEquals(10, out.size)
        assertEquals(10, out.text.length)
        assertTrue(out.truncated)
    }

    @Test
    fun `truncated latches and keeps dropping`() {
        val out = OutputCollector(2)
        out.appendAll("abcdef")
        assertTrue(out.truncated)
        out.appendAll("more")
        assertEquals("ab", out.text)
        assertTrue(out.truncated)
    }

    @Test
    fun `appendLine adds a newline and respects the cap`() {
        val out = OutputCollector(6)
        out.appendLine("abc")
        assertEquals("abc\n", out.text)
        assertFalse(out.truncated)
    }

    @Test
    fun `appendLine stops at the cap`() {
        val out = OutputCollector(3)
        out.appendLine("abc") // the trailing newline does not fit
        assertEquals("abc", out.text)
        assertTrue(out.truncated)
    }

    @Test
    fun `markTruncated forces the flag for a timeout kill`() {
        val out = OutputCollector(64)
        out.appendAll("partial")
        out.markTruncated()
        assertTrue(out.truncated)
        assertEquals("partial", out.text)
    }

    @Test
    fun `multi-byte text is appended one char at a time`() {
        val out = OutputCollector(3)
        out.appendAll("سلام")
        assertEquals(3, out.size)
        assertEquals("سلا", out.text)
        assertTrue(out.truncated)
    }
}
