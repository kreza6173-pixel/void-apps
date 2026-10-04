package io.github.kreza6173pixel.cyberappmanager.inventory

import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A5 autostart probe and guarded component control.
 *
 * Read-only: `dumpsys package` for boot receivers, `appops get` for background state.
 * Write: `pm disable/enable` for individual receiver components, with read-back.
 *
 * Background execution control (RUN_IN_BACKGROUND, RUN_ANY_IN_BACKGROUND) is already
 * handled by A4's AppOps infrastructure in InventoryRepository.setAppOp.
 */
class AutostartRepository(private val bridge: ExecBridge) {

    suspend fun audit(pkg: String): Result<AutostartAudit> = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) {
            return@withContext Result.failure(IllegalArgumentException("invalid package name"))
        }
        val q = ShellQuoting.quote(pkg)

        val dump = when (val out = bridge.execBlocking("dumpsys package $q", TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return@withContext Result.failure(IllegalStateException(out.message))
            is ExecOutcome.Completed -> {
                if (out.result.truncated) {
                    return@withContext Result.failure(
                        IllegalStateException("output truncated (64 KiB cap)"),
                    )
                }
                out.result.stdout
            }
        }

        val opsText = when (val out = bridge.execBlocking("appops get $q", TIMEOUT_MS)) {
            is ExecOutcome.Failed -> ""
            is ExecOutcome.Completed -> out.result.stdout
        }

        Result.success(parseAutostartAudit(pkg, dump + "\n" + opsText))
    }

    /**
     * Guarded component disable/enable with read-back.
     *
     * HyperOS (and possibly other ROMs) silently ignores shorthand component names
     * like `pkg/.Cls` in pm disable/enable, so the command always uses the fully
     * qualified form `pkg/full.class.Name`.
     *
     * Read-back uses a targeted grep on the `disabledComponents:` section of
     * `dumpsys package` instead of parsing the full output, because the section
     * structure varies across ROMs.
     */
    suspend fun setComponent(
        pkg: String,
        component: String,
        enable: Boolean,
    ): ComponentChangeResult = withContext(Dispatchers.IO) {
        fun refused(reason: String, was: Boolean = false) =
            ComponentChangeResult(pkg, component, enable, Verdict.REFUSED, "", reason, was, was)

        if (!isValidPackageName(pkg)) return@withContext refused("invalid package name")
        if (!isValidComponentName(component)) return@withContext refused("invalid component name")
        if (component.substringBefore('/') != pkg) return@withContext refused("component does not belong to this package")

        /* Read current state with a targeted grep. */
        val className = resolveClassName(component)
        val wasBefore = isComponentCurrentlyDisabled(pkg, className)
            ?: return@withContext refused("component state unavailable")

        if (enable && !wasBefore) return@withContext refused("component is already enabled", false)
        if (!enable && wasBefore) return@withContext refused("component is already disabled", true)

        /* Execute the change with the fully qualified name. */
        val fullComponent = expandComponentName(component)
        val qc = ShellQuoting.quote(fullComponent)
        val cmd = if (enable) "pm enable $qc" else "pm disable $qc"
        val output = when (val out = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return@withContext ComponentChangeResult(
                pkg, component, enable, Verdict.FAILED, cmd, out.message, wasBefore, null,
            )
            is ExecOutcome.Completed -> {
                (out.result.stdout + "\n" + out.result.stderr).trim() +
                    "\n(exit ${out.result.exitCode})"
            }
        }

        /* Read back with the same targeted grep. */
        val isAfter = isComponentCurrentlyDisabled(pkg, className)
        val expectedDisabled = !enable
        val verdict = when {
            isAfter == null -> Verdict.UNVERIFIABLE
            isAfter == expectedDisabled -> Verdict.APPLIED
            else -> Verdict.NOT_APPLIED
        }

        ComponentChangeResult(pkg, component, enable, verdict, cmd, output, wasBefore, isAfter)
    }

    /**
     * Targeted read-back: checks whether the class name appears inside the
     * `disabledComponents:` section of `dumpsys package`.
     *
     * Uses `sed` to extract the `disabledComponents:` block and grep for the class,
     * avoiding full-output parsing that can break across ROM formats.
     */
    private fun isComponentCurrentlyDisabled(pkg: String, className: String): Boolean? {
        val q = ShellQuoting.quote(pkg)
        val qClass = ShellQuoting.quote(className)
        val cmd = "dumpsys package $q | sed -n '/disabledComponents:/,/enabledComponents:\\|^[^ ]/p' | grep -q $qClass"
        return when (val out = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> null
            is ExecOutcome.Completed -> out.result.exitCode == 0
        }
    }

    /**
     * Extracts the bare class name from a component reference.
     * `com.example.app/.BootReceiver` -> `com.example.app.BootReceiver`
     * `com.example.app/com.example.app.BootReceiver` -> `com.example.app.BootReceiver`
     */
    private fun resolveClassName(component: String): String {
        val pkg = component.substringBefore('/')
        val cls = component.substringAfter('/')
        return if (cls.startsWith(".")) "$pkg$cls" else cls
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}

/**
 * Expands a shorthand component name to its fully qualified form.
 * `com.example.app/.BootReceiver` becomes `com.example.app/com.example.app.BootReceiver`.
 * Already fully qualified names are returned unchanged.
 */
fun expandComponentName(component: String): String {
    val slash = component.indexOf('/')
    if (slash < 0) return component
    val pkg = component.substring(0, slash)
    val cls = component.substring(slash + 1)
    return if (cls.startsWith(".")) "$pkg/$pkg$cls" else component
}

internal fun InventoryRepository.autostartRepo(): AutostartRepository {
    val field = InventoryRepository::class.java.getDeclaredField("bridge")
    field.isAccessible = true
    return AutostartRepository(field.get(this) as ExecBridge)
}
