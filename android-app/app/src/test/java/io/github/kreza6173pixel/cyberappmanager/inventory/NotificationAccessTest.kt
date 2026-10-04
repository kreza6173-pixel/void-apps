package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessTest {

    @Test fun secureListHandlesNullAndBlank() {
        assertTrue(parseNotifSecureList(null).isEmpty())
        assertTrue(parseNotifSecureList("null").isEmpty())
        assertTrue(parseNotifSecureList("  \n").isEmpty())
    }

    @Test fun secureListSplitsOnColon() {
        assertEquals(
            listOf("com.a/com.a.Listener", "com.b/.Other"),
            parseNotifSecureList("com.a/com.a.Listener:com.b/.Other\n"),
        )
    }

    /** The A5 lesson: shorthand and fully qualified names must compare equal. */
    @Test fun listenerGrantedMatchesShorthandAgainstFullName() {
        val enabled = parseNotifSecureList("com.example.app/com.example.app.NotifListener")
        assertTrue(isListenerGranted("com.example.app/.NotifListener", enabled))
        assertTrue(isListenerGranted("com.example.app/com.example.app.NotifListener", enabled))
        assertFalse(isListenerGranted("com.example.app/.Other", enabled))
    }

    @Test fun listenerGrantedMatchesFullNameAgainstShorthand() {
        val enabled = parseNotifSecureList("com.example.app/.NotifListener")
        assertTrue(isListenerGranted("com.example.app/com.example.app.NotifListener", enabled))
    }

    @Test fun parsesOnlyThisPackagesListeners() {
        val text = """
            com.example.app/.NotifListener
            com.other.app/com.other.app.Listener
            com.example.app/com.example.app.sub.Second
        """.trimIndent()
        assertEquals(
            listOf("com.example.app/com.example.app.NotifListener", "com.example.app/com.example.app.sub.Second"),
            parseListenerServices("com.example.app", text),
        )
    }

    @Test fun parsesDumpsysFallbackShape() {
        val text = """
                android.service.notification.NotificationListenerService:
                  1a2b3c com.example.app/.NotifListener filter 4d5e6f
                    Action: "android.service.notification.NotificationListenerService"
        """.trimIndent()
        assertEquals(listOf("com.example.app/com.example.app.NotifListener"), parseListenerServices("com.example.app", text))
    }

    @Test fun auditKeepsApprovedListenerMissingFromQuery() {
        val audit = buildNotificationAudit(
            "com.example.app",
            servicesText = "",
            listenersSetting = "com.example.app/com.example.app.Hidden:com.other/.L",
            dndSetting = "com.other",
            dndRequested = false,
        )
        assertEquals(listOf(ListenerService("com.example.app/com.example.app.Hidden", true)), audit.listeners)
        assertEquals(false, audit.dndGranted)
        assertTrue(audit.listenerStateKnown)
    }

    @Test fun auditMarksDeclaredListenerNotGranted() {
        val audit = buildNotificationAudit("com.example.app", "com.example.app/.NotifListener", "null", "com.example.app:com.other", true)
        assertEquals(listOf(ListenerService("com.example.app/com.example.app.NotifListener", false)), audit.listeners)
        assertEquals(true, audit.dndGranted)
        assertTrue(audit.dndRequested)
    }

    @Test fun unreadableSettingsAreUnknownNotFalse() {
        val audit = buildNotificationAudit("com.example.app", "com.example.app/.NotifListener", null, null, false)
        assertFalse(audit.listenerStateKnown)
        assertNull(audit.dndGranted)
    }

    @Test fun dndMatchRequiresExactPackage() {
        val audit = buildNotificationAudit("com.example", "", "", "com.example.app", false)
        assertEquals(false, audit.dndGranted)
    }
}
