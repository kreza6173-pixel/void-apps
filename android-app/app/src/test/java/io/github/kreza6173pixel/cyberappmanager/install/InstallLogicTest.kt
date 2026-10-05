package io.github.kreza6173pixel.cyberappmanager.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class InstallLogicTest {

    @Test fun formatsWithoutApkm() {
        assertEquals(PackageFormat.APK, detectFormat("App.APK"))
        assertEquals(PackageFormat.APKS, detectFormat("a.apks"))
        assertEquals(PackageFormat.XAPK, detectFormat("game.xapk"))
        assertNull(detectFormat("bundle.apkm"))
        assertNull(detectFormat("notes.txt"))
    }

    @Test fun lsParserKeepsSpacesAndSortsDirsFirst() {
        val text = """
            total 24
            drwxrwx--x 2 root sdcard_rw 4096 2026-10-01 10:00 .
            drwxrwx--x 9 root sdcard_rw 4096 2026-10-01 10:00 ..
            -rw-rw---- 1 root sdcard_rw 12345 2026-10-01 10:00 My App 1.0.apk
            drwxrwx--x 2 root sdcard_rw 4096 2026-10-01 10:00 zeta
            -rw-rw---- 1 root sdcard_rw 99 2026-10-01 10:00 readme.txt
        """.trimIndent()
        val e = parseLsLa("/sdcard/Download/", text)
        assertEquals(listOf("zeta", "My App 1.0.apk", "readme.txt"), e.map { it.name })
        assertEquals("/sdcard/Download/My App 1.0.apk", e[1].path)
        assertEquals(12345L, e[1].size)
        assertTrue(e[0].isDir)
        assertEquals(PackageFormat.APK, e[1].format)
        assertNull(e[2].format)
    }

    @Test fun sessionIdAndCommit() {
        assertEquals(1234, extractSessionId("Success: created install session [1234]"))
        assertNull(extractSessionId("Error: java.lang.SecurityException"))
        assertTrue(isCommitSuccess("Success\n"))
        assertFalse(isCommitSuccess("Failure [INSTALL_FAILED_VERSION_DOWNGRADE]"))
        assertEquals("pm install-create -r -g", buildInstallCreateCmd(InstallOptions(true, true)))
        assertEquals("pm install-create", buildInstallCreateCmd(InstallOptions(false, false)))
        assertEquals("split2_my_app_v1_apk", sanitizeSplitName("/x/my app-v1.apk", 2))
    }

    @Test fun xapkManifestStringsAndObjects() {
        val a = parseXapkManifest("""{"package_name":"com.game","split_apks":[{"file":"com.game.apk","id":"base"},{"file":"config.arm64_v8a.apk"}],"expansions":[]}""")!!
        assertEquals("com.game", a.packageName)
        assertEquals(listOf("com.game.apk", "config.arm64_v8a.apk"), a.splitApks)
        val b = parseXapkManifest("""{"package_name":"com.b","split_apks":["base.apk","split_en.apk"]}""")!!
        assertEquals(listOf("base.apk", "split_en.apk"), b.splitApks)
        assertNull(parseXapkManifest("not json"))
    }

    @Test fun installSetOrder() {
        val all = listOf("/w/base.apk", "/w/split_config.en.apk", "/w/universal.apk")
        assertEquals(listOf("/w/universal.apk"), resolveInstallSet(all, null).paths)
        val noUni = listOf("/w/base.apk", "/w/config.en.apk", "/w/extra.apk")
        assertEquals(listOf("/w/base.apk", "/w/config.en.apk"), resolveInstallSet(noUni, XapkManifest("p", listOf("base.apk", "config.en.apk"))).paths)
        assertEquals(noUni, resolveInstallSet(noUni, null).paths)
        assertEquals("/w/base.apk", pickBaseApk(noUni))
    }

    @Test fun installedVersionAndPmPath() {
        val v = parseInstalledVersion("    versionCode=42 minSdk=24 targetSdk=34\n    versionName=1.2.3\n    lastUpdateTime=2026-10-04 12:00:01")!!
        assertEquals(42L, v.versionCode)
        assertEquals("1.2.3", v.versionName)
        assertEquals("2026-10-04 12:00:01", v.lastUpdate)
        assertNull(parseInstalledVersion(""))
        assertEquals(listOf("/data/app/x/base.apk", "/data/app/x/split_a.apk"), parsePmPath("package:/data/app/x/base.apk\npackage:/data/app/x/split_a.apk\n"))
        assertEquals("com.a_1.0_beta", extractLabel("com.a", "1.0 beta", null))
    }

    @Test fun pathSafety() {
        assertFalse(isSafePath("/sdcard"))
        assertFalse(isSafePath("/sdcard/"))
        assertFalse(isSafePath("/"))
        assertFalse(isSafePath("/sdcard/Android/../.."))
        assertTrue(isSafePath("/sdcard/Android"))
        assertFalse(isAllowedScanRoot("/data/local/tmp"))
        assertTrue(isAllowedScanRoot("/sdcard/Android/"))
        assertEquals(
            listOf("/sdcard/Android/data/a/cache", "/sdcard/Android/x"),
            parseEmptyDirs("/sdcard/Android", "/sdcard/Android/data/a/cache\n/sdcard/Android\n/sdcard/Other\n/sdcard/Android/x\n"),
        )
    }

    @Test fun dfParser() {
        assertEquals(1234567L, parseDfAvailableKb("Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-50 115000000 90000000 1234567 90% /data"))
        assertNull(parseDfAvailableKb(""))
    }

    @Test fun runningAppsFromPs() {
        val ps = """
            USER           PID NAME
            root             1 init
            system         612 system_server
            u0_a123      18452 com.example.reader
            u0_a123      18467 com.example.reader:sync
            u0_a305      19110 com.vendor.music
            u0_a400      20000 com.not.installed
        """.trimIndent()
        val rows = parsePs(ps)
        assertEquals(6, rows.size)
        val apps = groupRunningApps(rows, setOf("com.example.reader", "com.vendor.music")) { if (it == "com.vendor.music") "current launcher" else null }
        assertEquals(listOf("com.example.reader", "com.vendor.music"), apps.map { it.packageName })
        assertEquals(listOf(18452, 18467), apps[0].pids)
        assertEquals("current launcher", apps[1].protectedReason)
        assertEquals(listOf(18452, 18467), stillRunning("com.example.reader", rows))
        assertTrue(stillRunning("com.gone", rows).isEmpty())
    }

    @Test fun xapkManifestBuilder() {
        val m = buildXapkManifest("com.a", 7, "1.0", listOf("base.apk", "split_x.apk"))
        assertEquals(listOf("base.apk", "split_x.apk"), parseXapkManifest(m)!!.splitApks)
        assertEquals("com.a", parseXapkManifest(m)!!.packageName)
    }

    @Test fun axmlManifest() {
        val info = Axml.parse(Base64.getDecoder().decode(FIXTURE))!!
        assertEquals("com.example.app", info.packageName)
        assertEquals(42L, info.versionCode)
        assertEquals("1.2.3", info.versionName)
        assertEquals(24, info.minSdk)
        assertEquals(34, info.targetSdk)
        assertEquals("My App", info.label)
        assertEquals(listOf("android.permission.INTERNET"), info.permissions)
        assertTrue(info.launchable)
        assertNull(Axml.parse(byteArrayOf(1, 2, 3)))
    }

    private companion object {
        /** Binary AndroidManifest.xml built by a reference encoder (UTF-16 string pool, resource map). */
        const val FIXTURE = "AwAIAIAFAAABABwAxAIAABUAAAAAAAAAAAAAAHAAAAAAAAAAAAAAABoAAAA0AAAAUgAAAHYAAACCAAAAkAAAAKIAAAC2AAAAygAAAOwAAAAGAQAAGgEAADgBAABIAQAAXAEAAH4BAACMAQAAxgEAAP4BAABCAgAACwB2AGUAcgBzAGkAbwBuAEMAbwBkAGUAAAALAHYAZQByAHMAaQBvAG4ATgBhAG0AZQAAAA0AbQBpAG4AUwBkAGsAVgBlAHIAcwBpAG8AbgAAABAAdABhAHIAZwBlAHQAUwBkAGsAVgBlAHIAcwBpAG8AbgAAAAQAbgBhAG0AZQAAAAUAbABhAGIAZQBsAAAABwBwAGEAYwBrAGEAZwBlAAAACABtAGEAbgBpAGYAZQBzAHQAAAAIAHUAcwBlAHMALQBzAGQAawAAAA8AdQBzAGUAcwAtAHAAZQByAG0AaQBzAHMAaQBvAG4AAAALAGEAcABwAGwAaQBjAGEAdABpAG8AbgAAAAgAYQBjAHQAaQB2AGkAdAB5AAAADQBpAG4AdABlAG4AdAAtAGYAaQBsAHQAZQByAAAABgBhAGMAdABpAG8AbgAAAAgAYwBhAHQAZQBnAG8AcgB5AAAADwBjAG8AbQAuAGUAeABhAG0AcABsAGUALgBhAHAAcAAAAAUAMQAuADIALgAzAAAAGwBhAG4AZAByAG8AaQBkAC4AcABlAHIAbQBpAHMAcwBpAG8AbgAuAEkATgBUAEUAUgBOAEUAVAAAABoAYQBuAGQAcgBvAGkAZAAuAGkAbgB0AGUAbgB0AC4AYQBjAHQAaQBvAG4ALgBNAEEASQBOAAAAIABhAG4AZAByAG8AaQBkAC4AaQBuAHQAZQBuAHQALgBjAGEAdABlAGcAbwByAHkALgBMAEEAVQBOAEMASABFAFIAAAAGAE0AeQAgAEEAcABwAAAAAACAAQgAIAAAABsCAQEcAgEBDAIBAXACAQEDAAEBAQABAQIBEABgAAAAAAAAAP//////////BwAAABQAFAADAAAAAAAAAP////8GAAAADwAAAAgAAAMAAAAA/////wAAAAD/////CAAAECoAAAD/////AQAAABAAAAAIAAADEAAAAAIBEABMAAAAAAAAAP//////////CAAAABQAFAACAAAAAAAAAP////8CAAAA/////wgAABAYAAAA/////wMAAAD/////CAAAECIAAAADARAAGAAAAAAAAAD//////////wgAAAACARAAOAAAAAAAAAD//////////wkAAAAUABQAAQAAAAAAAAD/////BAAAABEAAAAIAAADEQAAAAMBEAAYAAAAAAAAAP//////////CQAAAAIBEAA4AAAAAAAAAP//////////CgAAABQAFAABAAAAAAAAAP////8FAAAAFAAAAAgAAAMUAAAAAgEQACQAAAAAAAAA//////////8LAAAAFAAUAAAAAAAAAAAAAgEQACQAAAAAAAAA//////////8MAAAAFAAUAAAAAAAAAAAAAgEQADgAAAAAAAAA//////////8NAAAAFAAUAAEAAAAAAAAA/////wQAAAASAAAACAAAAxIAAAADARAAGAAAAAAAAAD//////////w0AAAACARAAOAAAAAAAAAD//////////w4AAAAUABQAAQAAAAAAAAD/////BAAAABMAAAAIAAADEwAAAAMBEAAYAAAAAAAAAP//////////DgAAAAMBEAAYAAAAAAAAAP//////////DAAAAAMBEAAYAAAAAAAAAP//////////CwAAAAMBEAAYAAAAAAAAAP//////////CgAAAAMBEAAYAAAAAAAAAP//////////BwAAAA=="
    }
}
