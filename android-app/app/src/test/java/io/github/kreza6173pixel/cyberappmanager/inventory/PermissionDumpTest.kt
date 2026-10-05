package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionDumpTest {
    @Test fun commandExtractsOnlyPermissionSectionsBeforeTheCap() {
        val cmd = permissionDumpCommand("'com.example.app'")
        assertTrue(cmd.contains("dumpsys package 'com.example.app' | awk"))
        assertTrue(cmd.contains("requested permissions"))
        assertTrue(cmd.contains("install permissions"))
        assertTrue(cmd.contains("runtime permissions"))
        assertFalse(cmd.contains("grep"))
        assertEquals("dumpsys package 'com.example.app' | sed -n '/^Shared users:/,/^[A-Z]/p'", sharedUsersDumpCommand("'com.example.app'"))
    }

    /** Shape of the sections retained by the awk extractor. */
    @Test fun parsesThePermissionSectionsAsReturnedByExtractor() {
        val block = """
            Packages:
              Package [com.example.app] (abc123):
                requested permissions:
                  android.permission.CAMERA
                  android.permission.INTERNET
                install permissions:
                  android.permission.INTERNET: granted=true
                User 0: ceDataInode=1 installed=true hidden=false
                  gids=[3003]
                  runtime permissions:
                    android.permission.CAMERA: granted=false, flags=[ USER_SET ]
        """.trimIndent()
        val audit = parsePermissionAudit("com.example.app", block)
        assertEquals(listOf("android.permission.CAMERA", "android.permission.INTERNET"), audit.permissions.map { it.name })
        val camera = audit.permissions.first { it.name == "android.permission.CAMERA" }
        assertTrue(camera.runtime)
        assertEquals(false, camera.granted)
        assertEquals(true, audit.permissions.first { it.name == "android.permission.INTERNET" }.granted)
        assertNull(sharedUserOf(block))
    }

    /** Shape measured on com.miui.securitycenter: runtime permissions live only in Shared users:. */
    @Test fun readsSharedUserRuntimePermissionsAsFixed() {
        val packages = """
            Packages:
              Package [com.miui.securitycenter] (1a2b3c):
                sharedUser=SharedUserSetting{cfee48 android.uid.system/1000}
                requested permissions:
                  android.permission.ACCESS_COARSE_LOCATION
                User 0: ceDataInode=1 installed=true hidden=false
            Hidden system packages:
        """.trimIndent()
        val shared = """
            Shared users:
              SharedUser [android.uid.system] (cfee48):
                userId=1000
                User 0:
                  gids=[1000]
                  runtime permissions:
                    android.permission.ACCESS_COARSE_LOCATION: granted=true, flags=[ SYSTEM_FIXED|GRANTED_BY_DEFAULT|RESTRICTION_SYSTEM_EXEMPT|RESTRICTION_UPGRADE_EXEMPT]
            Dexopt state:
        """.trimIndent()
        val info = sharedUserOf(packages)
        assertEquals(SharedUserInfo("android.uid.system", 1000), info)
        assertTrue(info!!.systemUid)
        val coarse = parsePermissionAudit("com.miui.securitycenter", packages + "\n\n" + shared).permissions.single()
        assertTrue(coarse.runtime)
        assertTrue(coarse.fixed)
        assertEquals(true, coarse.granted)
        assertFalse(coarse.changeable)
    }

    @Test fun appRangeSharedUidIsNotSystem() {
        assertFalse(SharedUserInfo("com.google.uid.shared", 10140).systemUid)
    }
}
