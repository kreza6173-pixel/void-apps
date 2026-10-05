package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAccessTest {

    @Test fun uidLookupIsExactMatch() {
        val text = """
            package:com.example.app.helper uid:10201
            package:com.example.app uid:10200
        """.trimIndent()
        assertEquals(10200, uidForPackage("com.example.app", text))
        assertNull(uidForPackage("com.example", text))
    }

    @Test fun uidLookupKeepsFirstUidOfMultiUserForm() {
        assertEquals(10123, uidForPackage("com.a", "package:com.a uid:10123,1010123"))
    }

    @Test fun sharedUidPackagesExcludeSelf() {
        val text = """
            package:com.a uid:10300
            package:com.b uid:10300
            package:com.c uid:103001
        """.trimIndent()
        assertEquals(listOf("com.b"), packagesSharingUid("com.a", 10300, text))
    }

    @Test fun chain3StateFormats() {
        assertEquals(true, parseChain3Enabled("chain:enabled\n"))
        assertEquals(false, parseChain3Enabled("chain:disabled"))
        assertEquals(true, parseChain3Enabled("true"))
        assertNull(parseChain3Enabled("Unknown command: get-chain3-enabled"))
        assertNull(parseChain3Enabled(""))
    }

    @Test fun packageBlockedIsInverseOfNetworkingEnabled() {
        assertEquals(true, parsePackageBlocked("false"))
        assertEquals(false, parsePackageBlocked("true"))
        assertEquals(true, parsePackageBlocked(" : deny"))
        assertEquals(false, parsePackageBlocked(" : allow"))
        assertNull(parsePackageBlocked("Usage: cmd connectivity ..."))
    }

    @Test fun helpTextIsNeverReadAsState() {
        val help = (1..10).joinToString("\n") { "  set-package-networking-enabled [true|false] [package]" }
        assertNull(parseShellBoolean(help))
    }

    @Test fun trafficControllerFallback() {
        val text = """
            sUidOwnerMap:
                10200 OEM_DENY_3_MATCH
                10201 STANDBY_MATCH OEM_DENY_3_MATCH
        """.trimIndent()
        assertEquals(true, parseTrafficControllerBlocked(10200, text))
        assertEquals(false, parseTrafficControllerBlocked(10300, text))
        assertNull(parseTrafficControllerBlocked(10200, "10200 OEM_DENY_3_MATCH"))
    }

    @Test fun netpolicyListFormats() {
        assertEquals(setOf(10123, 10200), parseNetpolicyUidList("Restrict background blacklisted UIDs: 10123 10200 \n"))
        assertEquals(emptySet<Int>(), parseNetpolicyUidList("Restrict background blacklisted UIDs: none"))
        assertNull(parseNetpolicyUidList(""))
        assertNull(parseNetpolicyUidList("Error: unknown command"))
    }

    @Test fun dataSaverFormat() {
        assertEquals(true, parseDataSaver("Restrict background status: enabled"))
        assertEquals(false, parseDataSaver("Restrict background status: disabled"))
    }

    @Test fun verdictNeedsReadBack() {
        assertEquals(Verdict.APPLIED, networkVerdict(true, true))
        assertEquals(Verdict.NOT_APPLIED, networkVerdict(false, true))
        assertEquals(Verdict.UNVERIFIABLE, networkVerdict(null, true))
        assertTrue(FIRST_APPLICATION_UID == 10_000)
        assertFalse(CHAIN3_MIN_SDK > 30)
    }
}
