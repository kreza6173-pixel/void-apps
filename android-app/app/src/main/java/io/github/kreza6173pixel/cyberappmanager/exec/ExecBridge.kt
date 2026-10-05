package io.github.kreza6173pixel.cyberappmanager.exec

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/** Client-side view of the UserService connection. Pure enum, unit-testable. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

sealed interface ExecOutcome {
    data class Completed(val result: ExecResult, val durationMs: Long) : ExecOutcome
    data class Failed(val message: String) : ExecOutcome
}

/**
 * Binds the Shizuku UserService and runs commands through it.
 *
 * Recovery: the bridge listens for Shizuku's binder-dead event and unbinds itself, so the
 * next [connect] after a Shizuku restart starts from a clean state instead of holding a dead
 * binder. Callers should call [connect] whenever the home screen reaches `READY`.
 */
class ExecBridge(private val context: Context) {

    var connectionState: ConnectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set

    private var service: IUserService? = null
    private val connecting = AtomicBoolean(false)
    private var started = false

    /**
     * Only a carrier for package + class name. ShizukuExecService is NOT an Android Service and
     * is not in the manifest: the Shizuku server instantiates it by reflection as an IBinder.
     */
    private val component = ComponentName(context, ShizukuExecService::class.java)

    /** Debug builds allow a debugger on the user-service process; release builds do not. */
    private val debuggableBuild: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * One instance for bind, peek AND unbind. Building fresh args for unbind dropped the
     * mandatory processNameSuffix: UserServiceArgs.forAdd() in 13.1.5 calls
     * Objects.requireNonNull on it.
     */
    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(component)
            .daemon(false)
            .debuggable(debuggableBuild)
            .processNameSuffix(USER_SERVICE_PROCESS_SUFFIX)
            .version(USER_SERVICE_VERSION)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener { handleShizukuDied() }

    /** Newest-last, capped, shown on the console screen. The user has no logcat. */
    var bindLog: List<String> by mutableStateOf(emptyList())
        private set

    private fun note(line: String) {
        val stamp = LocalTime.now().format(TIME_FORMAT)
        bindLog = (bindLog + "$stamp  $line").takeLast(MAX_LOG_LINES)
    }

    /** Watchdog: Shizuku gives no callback and no error if it fails to start the service. */
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun noteShizukuFacts(where: String) {
        note("$where pingBinder=${value("") { Shizuku.pingBinder() }}")
        note(
            "$where serverApiVersion=${value("?") { Shizuku.getVersion() }}" +
                " uid=${value("?") { Shizuku.getUid() }}",
        )
        val perm = value("?") { Shizuku.checkSelfPermission() }
        note("$where checkSelfPermission=$perm (PERMISSION_GRANTED=${PackageManager.PERMISSION_GRANTED})")
    }

    /** Renders a call's result, or why it could not be made, as one log-safe string. */
    private inline fun value(fallback: String, call: () -> Any?): String =
        runCatching { call() }.fold(
            onSuccess = { "$it" },
            onFailure = { "$fallback(THREW ${it.javaClass.simpleName})" },
        )

    private fun scheduleWatchdog() {
        cancelWatchdog()
        mainHandler.postDelayed({
            if (connectionState == ConnectionState.CONNECTING) {
                note("  t+3s still CONNECTING, peekUserService=${value("?") { peek() }}")
            }
        }, WATCHDOG_EARLY_MS)
        mainHandler.postDelayed({
            if (connectionState == ConnectionState.CONNECTING) {
                note("  t+10s NO onServiceConnected after 10s, still CONNECTING")
                noteShizukuFacts("  t+10s")
                note("  t+10s peekUserService=${value("?") { peek() }}")
            }
        }, WATCHDOG_MS)
    }

    private fun cancelWatchdog() = mainHandler.removeCallbacksAndMessages(null)

    private fun peek(): Int = Shizuku.peekUserService(userServiceArgs, serviceConnection)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            connecting.set(false)
            cancelWatchdog()
            if (binder == null || !binder.pingBinder()) {
                note("onServiceConnected name=$name but binder was NULL or dead")
                service = null
                connectionState = ConnectionState.DISCONNECTED
                return
            }
            service = IUserService.Stub.asInterface(binder)
            connectionState = ConnectionState.CONNECTED
            note("onServiceConnected name=$name -> CONNECTED")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connecting.set(false)
            cancelWatchdog()
            service = null
            connectionState = ConnectionState.DISCONNECTED
            note("onServiceDisconnected name=$name -> DISCONNECTED")
        }
    }

    /** Idempotent. Registers the Shizuku binder-dead listener once. */
    fun start() {
        if (started) return
        started = true
        note("bridge start()")
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
            .onFailure { note("addBinderDeadListener THREW ${it.javaClass.simpleName}: ${it.message}") }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
        disconnect()
    }

    /** Requests a bind. No-op when already connected or a bind is already in flight. */
    fun connect() {
        if (service != null) {
            note("connect() ignored: already have a service")
            return
        }
        if (!connecting.compareAndSet(false, true)) {
            note("connect() ignored: a bind is already in flight")
            return
        }
        connectionState = ConnectionState.CONNECTING
        note("connect() class=${component.className}")
        note("  args daemon=false debuggable=$debuggableBuild version=$USER_SERVICE_VERSION")
        note("  processNameSuffix=$USER_SERVICE_PROCESS_SUFFIX")
        // bindUserService returns a package-private type; discard it, never let inference name it.
        val bound = try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            true
        } catch (e: Exception) {
            note("  bindUserService THREW ${e.javaClass.name}")
            note("  message: ${e.message}")
            note("  cause: ${e.cause?.javaClass?.name}: ${e.cause?.message}")
            false
        }
        if (!bound) {
            connecting.set(false)
            service = null
            connectionState = ConnectionState.DISCONNECTED
            note("  -> DISCONNECTED (bind failed, see above)")
            return
        }
        // A normal return only means the transaction was sent; the callback is what matters.
        note("  bindUserService returned normally, peekUserService=${value("?") { peek() }} (bind is async)")
        noteShizukuFacts("  bind ")
        scheduleWatchdog()
    }

    fun disconnect() {
        cancelWatchdog()
        // remove = true makes the server send transaction 16777114, i.e. our destroy(),
        // which ends the user-service process.
        runCatching {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        }
        service = null
        connecting.set(false)
        connectionState = ConnectionState.DISCONNECTED
    }

    private fun handleShizukuDied() {
        service = null
        connecting.set(false)
        connectionState = ConnectionState.DISCONNECTED
        note("Shizuku binder died -> DISCONNECTED")
    }

    /**
     * Runs [command] on the service. Must not be called from the main thread: the AIDL call
     * blocks the calling thread until the command finishes.
     */
    fun execBlocking(command: String, timeoutMs: Int): ExecOutcome {
        val binder = service
            ?: return ExecOutcome.Failed(FAILURE_NOT_CONNECTED)
        val startedAt = System.currentTimeMillis()
        return try {
            val bundle = binder.exec(command, timeoutMs)
                ?: return ExecOutcome.Failed(FAILURE_NULL_RESULT)
            ExecOutcome.Completed(
                ExecResult.fromBundle(bundle),
                System.currentTimeMillis() - startedAt,
            )
        } catch (e: Exception) {
            ExecOutcome.Failed("binder error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Asks the service to destroy the running child. Fire-and-forget. */
    fun cancel() {
        val binder = service ?: return
        runCatching { binder.cancel() }
            .onFailure { connectionState = ConnectionState.DISCONNECTED }
    }

    private companion object {
        const val FAILURE_NOT_CONNECTED = "not connected to the Shizuku user service"
        const val FAILURE_NULL_RESULT = "the user service returned no result"
        const val MAX_LOG_LINES = 40

        /** 13.1.5 requires a non-null suffix; the client library supplies no default. */
        const val USER_SERVICE_PROCESS_SUFFIX = "user_service"

        /** Bump whenever ShizukuExecService changes, so Shizuku never reuses a stale record. */
        const val USER_SERVICE_VERSION = 2

        const val WATCHDOG_MS = 10_000L
        const val WATCHDOG_EARLY_MS = 3_000L

        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
