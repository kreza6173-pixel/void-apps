package io.github.kreza6173pixel.cyberappmanager.shizuku

/**
 * Pure Shizuku client state machine. No Android / Shizuku imports on purpose so the
 * resolution logic can be unit-tested on the JVM (`testDebugUnitTest`) with no mocks.
 *
 * State chain: NOT_INSTALLED -> NOT_RUNNING -> PERMISSION_NEEDED -> READY
 */
enum class ShizukuState {
    /** The Shizuku manager app is not present on the device. */
    NOT_INSTALLED,

    /** The manager is installed but its service is not running (no binder). */
    NOT_RUNNING,

    /** Binder is alive but this app has not been granted Shizuku permission yet. */
    PERMISSION_NEEDED,

    /** Binder is alive and permission granted: Shizuku APIs are usable. */
    READY,
}

/** Raw, directly observable facts about the Shizuku client. All are plain values. */
data class ShizukuSignals(
    /** `PackageManager` can resolve the Shizuku manager package. */
    val managerInstalled: Boolean,
    /** `Shizuku.pingBinder()`. */
    val binderAlive: Boolean,
    /** `Shizuku.checkSelfPermission() == PERMISSION_GRANTED`. */
    val permissionGranted: Boolean,
    /** `Shizuku.shouldShowRequestPermissionRationale()`. Copy hint only, never changes state. */
    val rationaleShouldShow: Boolean = false,
)

/**
 * Resolves the state from the observed signals.
 *
 * Order matters: a missing manager outranks everything, a dead binder outranks permission,
 * and permission is only meaningful once the binder is alive.
 */
fun resolveShizukuState(signals: ShizukuSignals): ShizukuState = when {
    !signals.managerInstalled -> ShizukuState.NOT_INSTALLED
    !signals.binderAlive -> ShizukuState.NOT_RUNNING
    !signals.permissionGranted -> ShizukuState.PERMISSION_NEEDED
    else -> ShizukuState.READY
}

/** UID flavour reported by `Shizuku.getUid()` once the state is READY. */
enum class ShizukuUidKind {
    /** uid 0: the Shizuku service itself is running as root. */
    ROOT,

    /** uid 2000: the Shizuku service is running as the ADB shell user. */
    SHELL,

    /** Some other, still-valid uid. */
    OTHER,

    /** Not ready, or `getUid()` threw. uid is not available. */
    UNKNOWN,
}

const val UID_ROOT = 0
const val UID_SHELL = 2000

/** Maps a raw uid to its kind. Pure, so it is covered by the unit tests too. */
fun classifyUid(uid: Int): ShizukuUidKind = when {
    uid < 0 -> ShizukuUidKind.UNKNOWN
    uid == UID_ROOT -> ShizukuUidKind.ROOT
    uid == UID_SHELL -> ShizukuUidKind.SHELL
    else -> ShizukuUidKind.OTHER
}
