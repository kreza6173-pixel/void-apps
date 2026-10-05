package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAuditTest {
    private val sample = """
Packages:
  Package [com.example.app] (abc123):
    userId=10200
    declared permissions:
      com.example.app.PRIVATE: prot=signature, INSTALLED
    requested permissions:
      android.permission.CAMERA
      android.permission.READ_SMS: restricted=true
      android.permission.INTERNET
      android.permission.POST_NOTIFICATIONS
      android.permission.WRITE_MEDIA_STORAGE
    install permissions:
      android.permission.INTERNET: granted=true
    User 0: ceDataInode=1 installed=true hidden=false suspended=false stopped=false enabled=0
      gids=[3003]
      runtime permissions:
        android.permission.POST_NOTIFICATIONS: granted=true, flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED]
        android.permission.CAMERA: granted=false, flags=[ USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
        android.permission.READ_SMS: granted=false, flags=[ SYSTEM_FIXED]
    User 10: ceDataInode=2 installed=true
      runtime permissions:
        android.permission.CAMERA: granted=true, flags=[ USER_SET]
Queries:
""".trimIndent()

    private val audit = parsePermissionAudit("com.example.app", sample)
    private fun rec(name: String) = audit.permissions.first { it.name == name }

    @Test fun runtimePermissionsComeFromUserZeroOnly() {
        val camera = rec("android.permission.CAMERA")
        assertTrue(camera.runtime)
        assertEquals(false, camera.granted)
        assertTrue(camera.changeable)
    }

    @Test fun grantedRuntimePermissionIsChangeable() {
        val notif = rec("android.permission.POST_NOTIFICATIONS")
        assertTrue(notif.runtime)
        assertEquals(true, notif.granted)
        assertTrue(notif.changeable)
        assertTrue("USER_SET" in notif.flags)
    }

    @Test fun fixedRuntimePermissionIsNotChangeable() {
        val sms = rec("android.permission.READ_SMS")
        assertTrue(sms.fixed)
        assertFalse(sms.changeable)
    }

    @Test fun installPermissionIsNeverChangeable() {
        val internet = rec("android.permission.INTERNET")
        assertFalse(internet.runtime)
        assertEquals(true, internet.granted)
        assertFalse(internet.changeable)
    }

    @Test fun requestedWithoutStateStaysUnknown() {
        val media = rec("android.permission.WRITE_MEDIA_STORAGE")
        assertNull(media.granted)
        assertFalse(media.changeable)
    }

    @Test fun declaredPermissionsAreNotTreatedAsRequested() {
        assertTrue(audit.permissions.none { it.name == "com.example.app.PRIVATE" })
    }

    @Test fun emptyOutputDoesNotInventState() {
        assertTrue(parsePermissionAudit("pkg", "permission denied").permissions.isEmpty())
    }

    @Test fun permissionNamesAreValidated() {
        assertTrue(isValidPermissionName("android.permission.CAMERA"))
        assertFalse(isValidPermissionName("CAMERA"))
        assertFalse(isValidPermissionName("android.permission.CAMERA; reboot"))
    }
}
