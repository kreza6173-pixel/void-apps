package io.github.kreza6173pixel.cyberappmanager.inventory

import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A5 autostart probe and guarded component control.
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

        val audit = parseAutostartAudit(pkg, dump + "\n" + opsText)

        /*
         * The section parser for disabledComponents is fragile across ROM formats.
         * Use a targeted sed pipeline to reliably extract disabled components,
         * then merge into the audit result.
         */
        val disabled = queryDisabledComponents(pkg)
        val merged = if (disabled != null && disabled != audit.disabledComponents) {
            audit.copy(disabledComponents = disabled)
        } else {
            audit
        }

        Result.success(merged)
    }

    /**
     * Targeted query: extracts the disabledComponents section with sed and
     * returns the set of fully qualified component names found.
     * Returns null if the command fails (non-fatal).
     */
    private fun queryDisabledComponents(pkg: String): Set<String>? {
        val q = ShellQuoting.quote(pkg)
        val cmd = "dumpsys package $q | sed -n '/disabledComponents:/,/enabledComponents:\\|^[^ ]/p'"
        val out = bridge.execBlocking(cmd, TIMEOUT_MS)
        if (out is ExecOutcome.Failed) return null
        val text = (out as ExecOutcome.Completed).result.stdout
        val components = mutableSetOf<String>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed == "disabledComponents:" ||
                trimmed == "enabledComponents:" || trimmed.startsWith("---")) continue
            if (trimmed.contains('.') && !trimmed.contains(' ') && !trimmed.endsWith(":")) {
                components.add("$pkg/$trimmed")
            }
        }
        return components
    }

    /**
     * Guarded component disable/enable with read-back.
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

        val className = resolveClassName(component)
        val wasBefore = isComponentCurrentlyDisabled(pkg, className)
            ?: return@withContext refused("component state unavailable")

        if (enable && !wasBefore) return@withContext refused("component is already enabled", false)
        if (!enable && wasBefore) return@withContext refused("component is already disabled", true)

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

        val isAfter = isComponentCurrentlyDisabled(pkg, className)
        val expectedDisabled = !enable
        val verdict = when {
            isAfter == null -> Verdict.UNVERIFIABLE
            isAfter == expectedDisabled -> Verdict.APPLIED
            else -> Verdict.NOT_APPLIED
        }

        ComponentChangeResult(pkg, component, enable, verdict, cmd, output, wasBefore, isAfter)
    }

    private fun isComponentCurrentlyDisabled(pkg: String, className: String): Boolean? {
        val q = ShellQuoting.quote(pkg)
        val qClass = ShellQuoting.quote(className)
        val cmd = "dumpsys package $q | sed -n '/disabledComponents:/,/enabledComponents:\\|^[^ ]/p' | grep -q $qClass"
        return when (val out = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> null
            is ExecOutcome.Completed -> out.result.exitCode == 0
        }
    }

    private fun resolveClassName(component: String): String {
        val pkg = component.substringBefore('/')
        val cls = component.substringAfter('/')
        return if (cls.startsWith(".")) "$pkg$cls" else cls
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}

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
