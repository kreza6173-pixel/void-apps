package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreMappingTest {
    @Test
    fun everyOperationMapsToItsReversibleAction() {
        assertEquals(AppAction.RESTORE, actionFor(SnapshotOperation.RESTORE_PACKAGE))
        assertEquals(AppAction.UNFREEZE, actionFor(SnapshotOperation.ENABLE))
        assertEquals(AppAction.FREEZE, actionFor(SnapshotOperation.DISABLE))
        assertEquals(AppAction.SUSPEND, actionFor(SnapshotOperation.SUSPEND))
        assertEquals(AppAction.UNSUSPEND, actionFor(SnapshotOperation.UNSUSPEND))
    }

    @Test
    fun restorePlanUndoesASuspend() {
        val before = Snapshot("s", "Before suspend", 1L, listOf(SnapshotEntry("com.a", false, AppState.ENABLED)))
        val now = listOf(AppEntry("com.a", "A", false, AppState.SUSPENDED, null))
        val steps = planSnapshotRestore(before, now) { false }
        assertEquals(listOf(AppAction.UNSUSPEND), steps.map { actionFor(it.operation) })
    }
}
