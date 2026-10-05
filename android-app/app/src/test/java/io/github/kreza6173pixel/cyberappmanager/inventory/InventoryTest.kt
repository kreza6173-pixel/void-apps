package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are real output from the reference phone (Xiaomi, Android 16, uid 2000). */
class InventoryTest {

    @Test
    fun parsesRawPmLinesWithUid() {
        val out = "package:com.miui.powerkeeper uid:1000\n" +
            "package:com.ashkanrafiee.balance uid:14726\n" +
            "package:com.anysoftkeyboard.languagepack.persian uid:10996\n" +
            "package:app.lawnchair.lawnicons uid:10835\n" +
            "package:android.miui.overlay uid:10040\n"
        assertEquals(
            listOf(
                "com.miui.powerkeeper",
                "com.ashkanrafiee.balance",
                "com.anysoftkeyboard.languagepack.persian",
                "app.lawnchair.lawnicons",
                "android.miui.overlay",
            ),
            parsePackageList(out).toList(),
        )
    }

    @Test
    fun parsesSedStrippedLinesAndIgnoresJunk() {
        val out = "android\ncom.android.settings\n\nWARNING: something\n  com.example.app  \n"
        assertEquals(listOf("android", "com.android.settings", "com.example.app"), parsePackageList(out).toList())
    }

    @Test
    fun packageNameValidation() {
        assertTrue(isValidPackageName("android"))
        assertTrue(isValidPackageName("io.github.kreza6173pixel.pulsebattery"))
        assertFalse(isValidPackageName("com.x; rm -rf /"))
        assertFalse(isValidPackageName("1com.x"))
        assertFalse(isValidPackageName("com..x"))
        assertFalse(isValidPackageName(""))
    }

    @Test
    fun normalizesPersianDigits() {
        assertEquals("2026-10-03 03:38:33", normalizeDigits("\u06F2\u06F0\u06F2\u06F6-\u06F1\u06F0-\u06F0\u06F3 \u06F0\u06F3:\u06F3\u06F8:\u06F3\u06F3"))
        assertEquals("123", normalizeDigits("\u0661\u0662\u0663"))
        assertEquals("plain 42", normalizeDigits("plain 42"))
    }

    @Test
    fun parsesRealDumpsysDetails() {
        val out = " versionCode=1 minSdk=26 targetSdk=36\n" +
            " versionName=1.0.0\n" +
            " lastUpdateTime=\u06F2\u06F0\u06F2\u06F6-\u06F1\u06F0-\u06F0\u06F3 \u06F0\u06F3:\u06F3\u06F8:\u06F3\u06F3\n" +
            " installerPackageName=com.rosan.dhizuku\n" +
            " User 0: ceDataInode=913457 deDataInode=913262 installed=true hidden=false suspended=false " +
            "distractionFlags=0 stopped=false notLaunched=false enabled=0 instant=false virtual=false quarantined=false\n" +
            " firstInstallTime=\u06F2\u06F0\u06F2\u06F6-\u06F1\u06F0-\u06F0\u06F2 \u06F0\u06F9:\u06F5\u06F8:\u06F1\u06F5\n" +
            " User 0:\n"
        val d = parsePackageDetails(out)
        assertEquals("1.0.0", d.versionName)
        assertEquals(1L, d.versionCode)
        assertEquals(26, d.minSdk)
        assertEquals(36, d.targetSdk)
        assertEquals("com.rosan.dhizuku", d.installer)
        assertEquals("2026-10-02 09:58:15", d.firstInstall)
        assertEquals("2026-10-03 03:38:33", d.lastUpdate)
        assertEquals("true", d.userFlags["installed"])
        assertEquals("0", d.userFlags["enabled"])
        assertEquals("false", d.userFlags["stopped"])
    }

    @Test
    fun emptyDetailsParseToNulls() {
        val d = parsePackageDetails("")
        assertNull(d.versionName)
        assertNull(d.versionCode)
        assertTrue(d.userFlags.isEmpty())
    }

    @Test
    fun mergeAssignsStatesAndSorts() {
        val all = setOf("com.b.removed", "com.a.frozen", "com.c.user", "android")
        val installed = setOf("com.a.frozen", "com.c.user", "android")
        val disabled = setOf("com.a.frozen")
        val system = setOf("android", "com.b.removed")
        val labels = mapOf("com.c.user" to "Calc", "android" to "Android System")
        val guard = ProtectedPackages(emptyMap())
        val entries = mergeInventory(all, installed, disabled, system, labels, guard::reasonFor)

        assertEquals(listOf("Android System", "Calc", "com.a.frozen", "com.b.removed"), entries.map { it.label })
        val byPkg = entries.associateBy { it.pkg }
        assertEquals(AppState.FROZEN, byPkg.getValue("com.a.frozen").state)
        assertEquals(AppState.REMOVED, byPkg.getValue("com.b.removed").state)
        assertEquals(AppState.ENABLED, byPkg.getValue("com.c.user").state)
        assertTrue(byPkg.getValue("android").isSystem)
        assertEquals("Android framework", byPkg.getValue("android").protectedReason)

        val c = countsOf(entries)
        assertEquals(4, c.total)
        assertEquals(2, c.user)
        assertEquals(2, c.system)
        assertEquals(1, c.frozen)
        assertEquals(1, c.removed)
        assertEquals(1, c.protectedCount)

        assertEquals(listOf("com.a.frozen"), filterApps(entries, AppFilter.FROZEN, "").map { it.pkg })
        assertEquals(listOf("com.c.user"), filterApps(entries, AppFilter.ALL, "calc").map { it.pkg })
        assertEquals(listOf("com.c.user"), filterApps(entries, AppFilter.USER, "C.USER").map { it.pkg })
    }

    @Test
    fun guardCombinesDynamicStaticAndPrefix() {
        val guard = ProtectedPackages(mapOf("app.lawnchair" to "current launcher"))
        assertEquals("current launcher", guard.reasonFor("app.lawnchair"))
        assertEquals("Shizuku", guard.reasonFor("moe.shizuku.privileged.api"))
        assertEquals("system data provider", guard.reasonFor("com.android.providers.media"))
        assertNull(guard.reasonFor("com.miui.powerkeeper"))
    }

    @Test
    fun imePackageFromRealSetting() {
        assertEquals(
            "com.google.android.inputmethod.latin",
            ProtectedPackages.imePackage("com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"),
        )
        assertNull(ProtectedPackages.imePackage("null"))
        assertNull(ProtectedPackages.imePackage(null))
    }
}
