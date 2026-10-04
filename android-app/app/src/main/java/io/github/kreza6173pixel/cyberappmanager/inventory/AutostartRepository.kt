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
     * Runs `pm disable <component>` or `pm enable <component>`, then re-reads the
     * package dump to verify the component state changed. APPLIED only when the
     * read-back confirms the new state matches.
     *
     * Warning: disabling a boot receiver prevents the app from starting at boot.
     * Some apps may stop working correctly if their receiver is disabled.
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

        val beforeAudit = audit(pkg).getOrNull()
            ?: return@withContext refused("autostart state unavailable")
        val wasBefore = isComponentDisabled(component, beforeAudit.disabledComponents)

        if (enable && !wasBefore) return@withContext refused("component is already enabled", false)
        if (!enable && wasBefore) return@withContext refused("component is already disabled", true)

        val qc = ShellQuoting.quote(component)
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

        val afterAudit = audit(pkg).getOrNull()
        val isAfter = afterAudit?.let { isComponentDisabled(component, it.disabledComponents) }
        val expectedDisabled = !enable
        val verdict = when {
            isAfter == null -> Verdict.UNVERIFIABLE
            isAfter == expectedDisabled -> Verdict.APPLIED
            else -> Verdict.NOT_APPLIED
        }

        ComponentChangeResult(pkg, component, enable, verdict, cmd, output, wasBefore, isAfter)
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}

internal fun InventoryRepository.autostartRepo(): AutostartRepository {
    val field = InventoryRepository::class.java.getDeclaredField("bridge")
    field.isAccessible = true
    return AutostartRepository(field.get(this) as ExecBridge)
}
