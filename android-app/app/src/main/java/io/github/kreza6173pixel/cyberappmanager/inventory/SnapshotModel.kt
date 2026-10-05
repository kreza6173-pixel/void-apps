package io.github.kreza6173pixel.cyberappmanager.inventory

/** A reversible package-state snapshot. It contains no app data or APK bytes. */
data class Snapshot(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val entries: List<SnapshotEntry>,
)

data class SnapshotEntry(
    val pkg: String,
    val isSystem: Boolean,
    val state: AppState,
)

enum class SnapshotOperation { RESTORE_PACKAGE, ENABLE, DISABLE, SUSPEND, UNSUSPEND }

data class SnapshotStep(
    val pkg: String,
    val operation: SnapshotOperation,
    val isSystem: Boolean,
)

/** Captures only stable, reversible state from the loaded inventory. */
fun snapshotOf(id: String, name: String, createdAtMs: Long, entries: List<AppEntry>): Snapshot =
    Snapshot(
        id = id,
        name = name,
        createdAtMs = createdAtMs,
        entries = entries
            .filter { isValidPackageName(it.pkg) }
            .map { SnapshotEntry(it.pkg, it.isSystem, it.state) }
            .sortedBy { it.pkg },
    )

/**
 * Plans changes from current state to the snapshot. Protected packages are excluded before any
 * command is generated. Removed packages are restored first, then enabled/disabled/suspended.
 */
fun planSnapshotRestore(
    snapshot: Snapshot,
    current: List<AppEntry>,
    protected: (String) -> Boolean,
): List<SnapshotStep> {
    val now = current.associateBy { it.pkg }
    val steps = ArrayList<SnapshotStep>()
    for (wanted in snapshot.entries) {
        val present = now[wanted.pkg] ?: continue
        if (protected(wanted.pkg)) continue
        if (present.state == AppState.REMOVED && wanted.state != AppState.REMOVED) {
            steps += SnapshotStep(wanted.pkg, SnapshotOperation.RESTORE_PACKAGE, wanted.isSystem)
        }
    }
    for (wanted in snapshot.entries) {
        val present = now[wanted.pkg] ?: continue
        if (protected(wanted.pkg)) continue
        val currentState = if (present.state == AppState.REMOVED && wanted.state != AppState.REMOVED) {
            AppState.ENABLED
        } else present.state
        when {
            wanted.state == AppState.FROZEN && currentState != AppState.FROZEN ->
                steps += SnapshotStep(wanted.pkg, SnapshotOperation.DISABLE, wanted.isSystem)
            wanted.state == AppState.SUSPENDED && currentState != AppState.SUSPENDED ->
                steps += SnapshotStep(wanted.pkg, SnapshotOperation.SUSPEND, wanted.isSystem)
            wanted.state == AppState.ENABLED && currentState == AppState.FROZEN ->
                steps += SnapshotStep(wanted.pkg, SnapshotOperation.ENABLE, wanted.isSystem)
            wanted.state == AppState.ENABLED && currentState == AppState.SUSPENDED ->
                steps += SnapshotStep(wanted.pkg, SnapshotOperation.UNSUSPEND, wanted.isSystem)
        }
    }
    return steps
}
