package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchEligibilityTest {
    private fun e(state: AppState, system: Boolean = false, reason: String? = null) = AppEntry("com.a", "A", system, state, reason)

    @Test fun protectedPackagesNeverJoinABatch() {
        assertFalse(eligibleForBatch(e(AppState.ENABLED, reason = "current launcher"), AppAction.SUSPEND))
        assertFalse(eligibleForBatch(e(AppState.SUSPENDED, reason = "current launcher"), AppAction.UNSUSPEND))
    }

    @Test fun eligibilityFollowsCurrentState() {
        assertTrue(eligibleForBatch(e(AppState.ENABLED), AppAction.SUSPEND))
        assertTrue(eligibleForBatch(e(AppState.ENABLED, system = true), AppAction.SUSPEND))
        assertFalse(eligibleForBatch(e(AppState.SUSPENDED), AppAction.SUSPEND))
        assertTrue(eligibleForBatch(e(AppState.SUSPENDED), AppAction.UNSUSPEND))
        assertFalse(eligibleForBatch(e(AppState.ENABLED), AppAction.UNSUSPEND))
        assertTrue(eligibleForBatch(e(AppState.ENABLED), AppAction.FORCE_STOP))
    }
}
