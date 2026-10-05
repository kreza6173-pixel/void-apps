package io.github.kreza6173pixel.cyberappmanager.exec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionTest {

    @Test
    fun `empty input is returned unchanged`() {
        assertEquals("", Redaction.redact(""))
    }

    @Test
    fun `ordinary output is untouched`() {
        val text = "uid=2000(u0_a0) gid=2000(u0_a0) groups=2000(u0_a0)"
        assertEquals(text, Redaction.redact(text))
    }

    @Test
    fun `a plain assignment is untouched`() {
        val text = "MODEL=Pixel_7"
        assertEquals(text, Redaction.redact(text))
    }

    @Test
    fun `a password assignment is masked`() {
        assertEquals("password=${Redaction.MASK}", Redaction.redact("password=hunter2"))
    }

    @Test
    fun `an api token assignment is masked`() {
        assertEquals("API_TOKEN=${Redaction.MASK}", Redaction.redact("API_TOKEN=abc123"))
    }

    @Test
    fun `a colon separated secret is masked`() {
        assertEquals("secret: ${Redaction.MASK}", Redaction.redact("secret: abc123"))
    }

    @Test
    fun `a suffixed key is masked`() {
        assertEquals("my_password=${Redaction.MASK}", Redaction.redact("my_password=p"))
    }

    @Test
    fun `a quoted secret value is masked`() {
        assertEquals("token=${Redaction.MASK}", Redaction.redact("token=\"s3cr3t\""))
    }

    @Test
    fun `a sensitive key is found even after an unrelated assignment`() {
        assertEquals(
            "binder error: password=${Redaction.MASK}",
            Redaction.redact("binder error: password=hunter2"),
        )
    }

    @Test
    fun `only the sensitive part of a line is masked`() {
        val out = Redaction.redact("before password=hunter2 after")
        assertTrue(out.startsWith("before password="))
        assertFalse(out.contains("hunter2"))
        assertTrue(out.endsWith(Redaction.MASK))
    }

    @Test
    fun `a short key does not match as a suffix`() {
        assertEquals("spin=1", Redaction.redact("spin=1"))
        assertEquals("id=2000", Redaction.redact("id=2000"))
    }

    @Test
    fun `an exact short sensitive key still matches`() {
        assertEquals("pin=${Redaction.MASK}", Redaction.redact("pin=1234"))
        assertEquals("otp=${Redaction.MASK}", Redaction.redact("otp=1234"))
    }

    @Test
    fun `a proxy authorization header is masked`() {
        assertEquals(
            "Proxy-Authorization: ${Redaction.MASK}",
            Redaction.redact("Proxy-Authorization: Basic Zm9vYmFy"),
        )
    }

    @Test
    fun `an authorization header is masked`() {
        val out = Redaction.redact("Authorization: Bearer abc.def.ghi")
        assertFalse(out.contains("abc.def.ghi"))
        assertTrue(out.contains(Redaction.MASK))
    }

    @Test
    fun `a private key assignment is masked`() {
        assertFalse(Redaction.redact("PRIVATE_KEY=-----BEGIN-----").contains("BEGIN"))
    }

    @Test
    fun `several secrets in one line are all masked`() {
        val out = Redaction.redact("user=me token=aaa password=bbb")
        assertFalse(out.contains("aaa"))
        assertFalse(out.contains("bbb"))
        assertTrue(out.contains("user=me"))
    }

    @Test
    fun `a key that merely contains a word is not masked`() {
        val text = "tokenizer=bpe"
        assertEquals(text, Redaction.redact(text))
    }

    @Test
    fun `redactCommand behaves the same as redact`() {
        val cmd = "curl -H 'password: hunter2'"
        assertEquals(Redaction.redact(cmd), Redaction.redactCommand(cmd))
    }
}
