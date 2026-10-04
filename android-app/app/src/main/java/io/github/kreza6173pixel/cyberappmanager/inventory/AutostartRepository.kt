package io.github.kreza6173pixel.cyberappmanager.inventory

import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A5 autostart probe and guarded component control. */
class AutostartRepository(private val bridge: ExecBridge) {
    suspend fun audit(pkg: String): Result<AutostartAudit> = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) return@withContext Result.failure(IllegalArgumentException("invalid package name"))
        val q = ShellQuoting.quote(pkg)
        val dump = when (val out = bridge.execBlocking("dumpsys package $q", TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return@withContext Result.failure(IllegalStateException(out.message))
            is ExecOutcome.Completed -> {
                if (out.result.truncated) return@withContext Result.failure(IllegalStateException("output truncated (64 KiB cap)"))
                out.result.stdout
            }
        }
        val opsText = when (val out = bridge.execBlocking("appops get $q", TIMEOUT_MS)) {
            is ExecOutcome.Failed -> ""
            is ExecOutcome.Completed -> out.result.stdout
        }
        val parsed = parseAutostartAudit(pkg, dump + "\n" + opsText)
        val disabled = queryDisabledComponents(pkg)
        Result.success(if (disabled != null) parsed.copy(disabledComponents = disabled) else parsed)
    }

    suspend fun setComponent(pkg: String, component: String, enable: Boolean): ComponentChangeResult = withContext(Dispatchers.IO) {
        fun refused(reason: String, was: Boolean = false) = ComponentChangeResult(pkg, component, enable, Verdict.REFUSED, "", reason, was, was)
        if (!isValidPackageName(pkg)) return@withContext refused("invalid package name")
        if (!isValidComponentName(component)) return@withContext refused("invalid component name")
        if (component.substringBefore('/') != pkg) return@withContext refused("component does not belong to this package")
        val wasBefore = isComponentCurrentlyDisabled(pkg, component) ?: return@withContext refused("component state unavailable")
        if (enable && !wasBefore) return@withContext refused("component is already enabled")
        if (!enable && wasBefore) return@withContext refused("component is already disabled", true)
        val fullComponent = expandComponentName(component)
        val cmd = (if (enable) "pm enable " else "pm disable ") + ShellQuoting.quote(fullComponent)
        val output = when (val out = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return@withContext ComponentChangeResult(pkg, component, enable, Verdict.FAILED, cmd, out.message, wasBefore, null)
            is ExecOutcome.Completed -> (out.result.stdout + "\n" + out.result.stderr).trim() + "\n(exit ${out.result.exitCode})"
        }
        val isAfter = isComponentCurrentlyDisabled(pkg, component)
        val verdict = when {
            isAfter == null -> Verdict.UNVERIFIABLE
            isAfter == !enable -> Verdict.APPLIED
            else -> Verdict.NOT_APPLIED
        }
        ComponentChangeResult(pkg, component, enable, verdict, cmd, output, wasBefore, isAfter)
    }

    /** Extract only the exact disabledComponents block. No sed ranges or grep false positives. */
    private fun queryDisabledComponents(pkg: String): Set<String>? {
        val q = ShellQuoting.quote(pkg)
        val cmd = "dumpsys package $q | awk '/^[[:space:]]*disabledComponents:[[:space:]]*$/ {inside=1; next} /^[[:space:]]*enabledComponents:[[:space:]]*$/ {inside=0; next} /^[^[:space:]]/ {inside=0} inside {print}'"
        val out = bridge.execBlocking(cmd, TIMEOUT_MS)
        if (out !is ExecOutcome.Completed || out.result.truncated) return null
        return out.result.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.endsWith(":") }.map { line ->
            if ('/' in line) line else "$pkg/$line"
        }.toSet()
    }

    private fun isComponentCurrentlyDisabled(pkg: String, component: String): Boolean? {
        val disabled = queryDisabledComponents(pkg) ?: return null
        val full = expandComponentName(component)
        return disabled.any { expandComponentName(it) == full }
    }

    private companion object { const val TIMEOUT_MS = 20_000 }
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
