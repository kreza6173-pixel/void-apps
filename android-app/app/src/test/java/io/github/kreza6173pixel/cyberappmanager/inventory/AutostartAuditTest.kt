package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AutostartAuditTest {

    @Test fun findsBootReceiverAndBackgroundOps() {
        val text = """
            Receiver Resolver Table:
              com.example.app/.BootReceiver:
                android.intent.action.BOOT_COMPLETED
            RUN_IN_BACKGROUND: ignore
            RUN_ANY_IN_BACKGROUND: allow
        """.trimIndent()
        val audit = parseAutostartAudit("com.example.app", text)
        assertEquals(1, audit.receivers.size)
        assertEquals("BOOT_COMPLETED", audit.receivers.single().action)
        assertEquals(
            listOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND"),
            audit.backgroundOps.map { it.op },
        )
        assertTrue(audit.raw.isNotEmpty())
    }

    @Test fun acceptsFullyQualifiedReceiverClass() {
        val audit = parseAutostartAudit(
            "com.example.app",
            "com.example.app/com.example.app.BootReceiver: LOCKED_BOOT_COMPLETED",
        )
        assertEquals(1, audit.receivers.size)
        assertEquals("LOCKED_BOOT_COMPLETED", audit.receivers.single().action)
    }

    @Test fun ignoresReceiverFromAnotherPackage() {
        val audit = parseAutostartAudit("com.example.app", "com.other/.Receiver: BOOT_COMPLETED")
        assertTrue(audit.receivers.isEmpty())
    }

    @Test fun ignoresBootTextFromActivityAndProviderSections() {
        val text = """
            Activity Resolver Table:
              com.example.app/.MainActivity filter 1
            Receiver Resolver Table:
              com.example.app/.BootReceiver filter 2
              Action: \"android.intent.action.BOOT_COMPLETED\"
            Service Resolver Table:
              com.example.app/androidx.startup.InitializationProvider filter 3
              Action: \"android.intent.action.BOOT_COMPLETED\"
            Registered ContentProviders:
              com.example.app/androidx.startup.InitializationProvider
        """.trimIndent()
        val audit = parseAutostartAudit("com.example.app", text)
        assertEquals(
            listOf("com.example.app/.BootReceiver"),
            audit.receivers.map { it.component },
        )
    }

    @Test fun handlesEmptyInput() {
        val audit = parseAutostartAudit("com.example.app", "")
        assertTrue(audit.receivers.isEmpty())
        assertTrue(audit.backgroundOps.isEmpty())
    }

    @Test fun findsMultipleReceiversAndActions() {
        val text = """
            Receiver Resolver Table:
              com.example.app/.BootReceiver:
                android.intent.action.BOOT_COMPLETED
              com.example.app/.UpdateReceiver:
                android.intent.action.MY_PACKAGE_REPLACED
        """.trimIndent()
        val audit = parseAutostartAudit("com.example.app", text)
        assertEquals(2, audit.receivers.size)
        assertEquals("BOOT_COMPLETED", audit.receivers[0].action)
        assertEquals("MY_PACKAGE_REPLACED", audit.receivers[1].action)
    }

    @Test fun parsesLockedBootAndQuickboot() {
        val text = """
            Receiver Resolver Table:
              com.example.app/.LockBoot:
                android.intent.action.LOCKED_BOOT_COMPLETED
              com.example.app/.QuickBoot:
                android.intent.action.QUICKBOOT_POWERON
        """.trimIndent()
        val audit = parseAutostartAudit("com.example.app", text)
        assertEquals(2, audit.receivers.size)
        assertEquals("LOCKED_BOOT_COMPLETED", audit.receivers[0].action)
        assertEquals("QUICKBOOT_POWERON", audit.receivers[1].action)
    }

    @Test fun acceptsHexPrefixedComponent() {
        val text = """
            Receiver Resolver Table:
              a1b2c3 com.example.app/.BootReceiver:
                android.intent.action.BOOT_COMPLETED
        """.trimIndent()
        val audit = parseAutostartAudit("com.example.app", text)
        assertEquals(1, audit.receivers.size)
    }

    @Test fun validatesComponentNames() {
        assertTrue(isValidComponentName("com.example.app/.BootReceiver"))
        assertTrue(isValidComponentName("com.example.app/com.example.app.BootReceiver"))
        assertFalse(isValidComponentName("noSlash"))
        assertFalse(isValidComponentName("/noPackage"))
        assertFalse(isValidComponentName("pkg/"))
    }
}
