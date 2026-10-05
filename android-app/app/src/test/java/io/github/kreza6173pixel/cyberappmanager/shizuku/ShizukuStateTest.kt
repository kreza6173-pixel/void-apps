package io.github.kreza6173pixel.cyberappmanager.shizuku

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hermetic JVM tests for the pure state-resolution logic. No Android and no Shizuku
 * classes are touched, so these run under `testDebugUnitTest` with no mocks.
 */
class ShizukuStateTest {

    private fun signals(
        installed: Boolean,
        binder: Boolean,
        granted: Boolean,
        rationale: Boolean = false,
    ) = ShizukuSignals(
        managerInstalled = installed,
        binderAlive = binder,
        permissionGranted = granted,
        rationaleShouldShow = rationale,
    )

    @Test
    fun `manager missing outranks everything`() {
        assertEquals(
            ShizukuState.NOT_INSTALLED,
            resolveShizukuState(signals(installed = false, binder = true, granted = true)),
        )
    }

    @Test
    fun `dead binder outranks a granted permission flag`() {
        assertEquals(
            ShizukuState.NOT_RUNNING,
            resolveShizukuState(signals(installed = true, binder = false, granted = true)),
        )
    }

    @Test
    fun `installed and stopped is NOT_RUNNING`() {
        assertEquals(
            ShizukuState.NOT_RUNNING,
            resolveShizukuState(signals(installed = true, binder = false, granted = false)),
        )
    }

    @Test
    fun `live binder without permission is PERMISSION_NEEDED`() {
        assertEquals(
            ShizukuState.PERMISSION_NEEDED,
            resolveShizukuState(signals(installed = true, binder = true, granted = false)),
        )
    }

    @Test
    fun `live binder with permission is READY`() {
        assertEquals(
            ShizukuState.READY,
            resolveShizukuState(signals(installed = true, binder = true, granted = true)),
        )
    }

    @Test
    fun `rationale flag never changes the resolved state`() {
        assertEquals(
            resolveShizukuState(signals(installed = true, binder = true, granted = false)),
            resolveShizukuState(
                signals(installed = true, binder = true, granted = false, rationale = true)
            ),
        )
    }

    @Test
    fun `all eight signal combinations are covered`() {
        val seen = mutableSetOf<ShizukuState>()
        for (installed in listOf(false, true)) {
            for (binder in listOf(false, true)) {
                for (granted in listOf(false, true)) {
                    seen += resolveShizukuState(signals(installed, binder, granted))
                }
            }
        }
        assertEquals(ShizukuState.entries.toSet(), seen)
    }

    @Test
    fun `uid 0 is root and uid 2000 is shell`() {
        assertEquals(ShizukuUidKind.ROOT, classifyUid(UID_ROOT))
        assertEquals(ShizukuUidKind.SHELL, classifyUid(UID_SHELL))
    }

    @Test
    fun `unknown uid is reported when shizuku is not ready`() {
        assertEquals(ShizukuUidKind.UNKNOWN, classifyUid(-1))
    }

    @Test
    fun `an unexpected but valid uid is OTHER`() {
        assertEquals(ShizukuUidKind.OTHER, classifyUid(10123))
    }
}
