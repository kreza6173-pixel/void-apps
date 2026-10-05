package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotModelTest {
    private fun entry(pkg: String, state: AppState, system: Boolean = false) =
        AppEntry(pkg, pkg, system, state, null)

    @Test
    fun snapshotSortsAndKeepsOnlyValidPackages() {
        val snapshot = snapshotOf(
            "s1", "before", 1L,
            listOf(entry("com.z", AppState.SUSPENDED), entry("bad;reboot", AppState.ENABLED), entry("com.a", AppState.FROZEN)),
        )
        assertEquals(listOf("com.a", "com.z"), snapshot.entries.map { it.pkg })
    }

    @Test
    fun restoreRestoresRemovedBeforeOtherState() {
        val snapshot = Snapshot(
            "s1", "before", 1L,
            listOf(
                SnapshotEntry("com.a", false, AppState.FROZEN),
                SnapshotEntry("com.b", false, AppState.SUSPENDED),
                SnapshotEntry("com.c", false, AppState.ENABLED),
            ),
        )
        val current = listOf(
            entry("com.a", AppState.ENABLED),
            entry("com.b", AppState.ENABLED),
            entry("com.c", AppState.FROZEN),
        )
        val steps = planSnapshotRestore(snapshot, current) { false }
        assertEquals(
            listOf(SnapshotOperation.DISABLE, SnapshotOperation.SUSPEND, SnapshotOperation.ENABLE),
            steps.map { it.operation },
        )
    }

    @Test
    fun protectedPackagesAreNeverPlanned() {
        val snapshot = Snapshot("s1", "before", 1L, listOf(SnapshotEntry("com.a", false, AppState.FROZEN)))
        val current = listOf(entry("com.a", AppState.ENABLED))
        val steps = planSnapshotRestore(snapshot, current) { it == "com.a" }
        assertTrue(steps.isEmpty())
    }
}
