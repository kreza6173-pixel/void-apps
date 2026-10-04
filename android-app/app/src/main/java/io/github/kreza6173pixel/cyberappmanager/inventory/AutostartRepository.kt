package io.github.kreza6173pixel.cyberappmanager.inventory

import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only A5 autostart probe. Runs `dumpsys package` for boot receiver discovery
 * and `appops get` for background execution state. Component writes (pm disable/enable)
 * will only be added after a phone probe proves the read-back behaviour.
 */
class AutostartRepository(private val bridge: ExecBridge) {

    suspend fun audit(pkg: String): Result<AutostartAudit> = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) {
            return@withContext Result.failure(IllegalArgumentException("invalid package name"))
        }
        val q = ShellQuoting.quote(pkg)

        /* Boot receiver discovery from the package dump. */
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

        /* Background execution state from AppOps. Non-fatal if it fails. */
        val opsText = when (val out = bridge.execBlocking("appops get $q", TIMEOUT_MS)) {
            is ExecOutcome.Failed -> ""
            is ExecOutcome.Completed -> out.result.stdout
        }

        Result.success(parseAutostartAudit(pkg, dump + "\n" + opsText))
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}

/**
 * Bridge accessor for feature repositories that need shell access but cannot change
 * the InventoryRepository constructor yet. Will be replaced when the constructor
 * exposes its bridge directly.
 */
internal fun InventoryRepository.autostartRepo(): AutostartRepository {
    val field = InventoryRepository::class.java.getDeclaredField("bridge")
    field.isAccessible = true
    return AutostartRepository(field.get(this) as ExecBridge)
}
