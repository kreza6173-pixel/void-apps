package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsTest {
    @Test fun parsesCommonAppOpsOutput() {
        val audit = parseAppOps("com.example.app", """
            Uid mode: COARSE_LOCATION: foreground; time=+1m
              POST_NOTIFICATION: allow; time=+2m
              READ_CLIPBOARD: deny
              unrelated text: not-an-op
        """.trimIndent())
        assertEquals(3, audit.operations.size)
        assertEquals("allow", audit.operations.first { it.op == "post_notification" }.mode)
        assertEquals("foreground", audit.operations.first { it.op == "coarse_location" }.mode)
    }

    @Test fun ignoresUnsupportedModesAndInvalidNames() {
        val audit = parseAppOps("pkg", """
            camera: allow
            audio_record: ignored
            bad-name: deny
        """.trimIndent())
        assertEquals(1, audit.operations.size)
        assertFalse(isValidAppOp("bad-name"))
        assertTrue(isValidAppOpMode("ignore"))
    }

    @Test fun ignoresHeadersAndEmptyMarkers() {
        val audit = parseAppOps("pkg", """
            No operations.
            Package com.example.app:
              Uid mode: LEGACY_STORAGE: allow
              CAMERA: ignore; rejectTime=+3h ago
        """.trimIndent())
        assertEquals(listOf("camera", "legacy_storage"), audit.operations.map { it.op })
        assertEquals("ignore", audit.operations.first { it.op == "camera" }.mode)
    }

    /** Lines taken from Drive on the Redmi Note 14 (Android 16, HyperOS). */
    @Test fun keepsOemOpsReadOnlyAndReportsDuplicateModes() {
        val audit = parseAppOps("com.google.android.apps.docs", """
            Uid mode: ACCESS_RESTRICTED_SETTINGS: allow
            ACCESS_RESTRICTED_SETTINGS: default; time=+19h26m44s501ms ago
            MIUIOP(10008): allow; time=+4h5m22s172ms ago
            MIUIOP(10053): ignore
        """.trimIndent())
        assertEquals(3, audit.operations.size)
        val oem = audit.operations.first { it.op == "miuiop(10008)" }
        assertTrue(oem.oem)
        assertFalse(oem.changeable)
        assertEquals(2, audit.operations.count { it.oem })
        val restricted = audit.operations.first { it.op == "access_restricted_settings" }
        assertEquals("default", restricted.mode)
        assertEquals(listOf("allow"), restricted.alsoReported)
        assertFalse(isValidAppOp("miuiop(10008)"))
        assertFalse(audit.scoped)
    }

    /** Shape measured on Drive: `appops get <uid>` is exactly the first block of `appops get <package>`. */
    @Test fun splitsUidAndPackageScopes() {
        val uid = """
            Uid mode: CAMERA: foreground
            ACCESS_RESTRICTED_SETTINGS: allow
        """.trimIndent()
        val full = uid + "\n" + """
            WAKE_LOCK: allow; time=+4h4m40s197ms ago; duration=+51ms
            ACCESS_RESTRICTED_SETTINGS: default; time=+19h26m44s501ms ago
            MIUIOP(10053): ignore
        """.trimIndent()
        val audit = parseAppOpsScoped("com.google.android.apps.docs", full, uid)
        assertTrue(audit.scoped)
        assertEquals(4, audit.operations.size)
        assertTrue(audit.operations.all { it.scoped })
        val restricted = audit.operations.first { it.op == "access_restricted_settings" }
        assertEquals("allow", restricted.uidMode)
        assertEquals("default", restricted.packageMode)
        val camera = audit.operations.first { it.op == "camera" }
        assertEquals("foreground", camera.uidMode)
        assertNull(camera.packageMode)
        assertEquals("allow", audit.operations.first { it.op == "wake_lock" }.packageMode)
        assertTrue(audit.operations.first { it.op == "miuiop(10053)" }.oem)
    }

    /** Shape measured on com.miui.securitycenter (uid 1000): the same op in both scopes with different modes. */
    @Test fun splitsSystemUidScopes() {
        val uid = "Uid mode: RECEIVE_SMS: ignore\nSYSTEM_ALERT_WINDOW: ignore"
        val full = uid + "\nRECEIVE_SMS: allow; time=+6h7m26s899ms ago\nSYSTEM_ALERT_WINDOW: default; time=+2h10m36s741ms ago; duration=+640ms\nSTART_FOREGROUND: allow; time=+2d3h8m27s68ms ago (running)"
        val audit = parseAppOpsScoped("com.miui.securitycenter", full, uid)
        assertEquals("ignore", appOpModeIn(audit, "receive_sms", AppOpScope.UID))
        assertEquals("allow", appOpModeIn(audit, "receive_sms", AppOpScope.PACKAGE))
        assertEquals("default", appOpModeIn(audit, "system_alert_window", AppOpScope.PACKAGE))
        assertEquals("allow", appOpModeIn(audit, "start_foreground", AppOpScope.PACKAGE))
        assertNull(appOpModeIn(audit, "start_foreground", AppOpScope.UID))
    }

    /** Probe on Acode: package set to ignore returned exit 0 but Android kept `allow` while uid mode was `ignore`. */
    @Test fun packageScopeIsBlockedWhileUidModeIsSet() {
        val uid = "Uid mode: ACCEPT_HANDOVER: ignore"
        val full = uid + "\nACCEPT_HANDOVER: allow\nREAD_CLIPBOARD: allow"
        val audit = parseAppOpsScoped("com.foxdebug.acode", full, uid)
        assertEquals("ignore", packageScopeBlockedBy(audit.operations.first { it.op == "accept_handover" }))
        assertNull(packageScopeBlockedBy(audit.operations.first { it.op == "read_clipboard" }))
        assertNull(packageScopeBlockedBy(AppOpRecord("camera", "allow", uidMode = "default", scoped = true)))
    }

    @Test fun fallsBackToMergedWhenUidPrefixDoesNotMatch() {
        val audit = parseAppOpsScoped("pkg", "CAMERA: allow\nWAKE_LOCK: allow", "Uid mode: CAMERA: ignore")
        assertFalse(audit.scoped)
        assertFalse(audit.operations.any { it.scoped })
        assertEquals(2, audit.operations.size)
    }

    @Test fun findsExactPackageUid() {
        val out = "package:com.google.android.apps.docs.editors.docs uid:10200\npackage:com.google.android.apps.docs uid:10176"
        assertEquals(10176, uidOf("com.google.android.apps.docs", out))
        assertNull(uidOf("com.missing", out))
    }

    @Test fun commandsUseExplicitMode() {
        assertEquals("appops set pkg camera allow", appOpsSetCommand("pkg", "camera", "allow"))
        assertEquals("appops set pkg camera default", appOpsResetCommand("pkg", "camera"))
    }

    @Test fun guardedSetCommandUsesUppercaseOpAndScopeTarget() {
        assertEquals("appops set 'com.example.app' CAMERA ignore", appOpSetCommand(AppOpScope.PACKAGE, "'com.example.app'", 10176, "camera", "ignore"))
        assertEquals("appops set 10176 CAMERA default", appOpSetCommand(AppOpScope.UID, "'com.example.app'", 10176, "camera", "default"))
    }
}
