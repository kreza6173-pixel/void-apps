package io.github.kreza6173pixel.cyberappmanager.inventory

import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ExecResult
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A6 per-app notification access, ported from the owner's `void-pulse` module.
 *
 * - Posting notifications (mute) is not handled here: it is the POST_NOTIFICATIONS runtime permission and goes
 *   through the A4 `InventoryRepository.setPermission` path, which is already phone-verified with read-back.
 * - Listener access: `cmd notification allow_listener|disallow_listener <pkg/Cls>`, read back from
 *   `settings get secure enabled_notification_listeners`.
 * - DND access: `cmd notification allow_dnd|disallow_dnd <pkg>`, read back from
 *   `settings get secure enabled_notification_policy_access_packages`.
 *
 * Protected packages are refused here, not only in the UI.
 */
class NotificationRepository(
    private val bridge: ExecBridge,
    private val protectedReason: (String) -> String? = { null },
) {

    suspend fun audit(pkg: String): Result<NotificationAudit> = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) return@withContext Result.failure(IllegalArgumentException("invalid package name"))
        val q = ShellQuoting.quote(pkg)
        val raw = StringBuilder()
        fun probe(cmd: String): ExecResult? {
            val r = sh(cmd)
            raw.append("$ ").append(cmd).append('\n')
            raw.append(r?.let { (it.stdout + it.stderr).trim() + "\n(exit ${it.exitCode})" } ?: "(not run: user service unavailable)")
            raw.append("\n\n")
            return r
        }
        val services = probe(SERVICES_CMD)
        val servicesText = if (services != null && services.exitCode == 0 && !services.truncated) {
            services.stdout
        } else {
            probe("dumpsys package $q | grep -F -B3 -A4 ${ShellQuoting.quote(NOTIF_LISTENER_ACTION)}")?.stdout.orEmpty()
        }
        val listeners = probe(LISTENERS_CMD)
        val dnd = probe(DND_CMD)
        val requested = probe("dumpsys package $q | grep -c -F ${ShellQuoting.quote(NOTIF_POLICY_PERMISSION)}")
        if (services == null && listeners == null && dnd == null) {
            return@withContext Result.failure(IllegalStateException("not connected to the Shizuku user service"))
        }
        val dndRequested = (requested?.stdout?.trim()?.lineSequence()?.firstOrNull()?.trim()?.toIntOrNull() ?: 0) > 0
        Result.success(buildNotificationAudit(pkg, servicesText, listeners.okText(), dnd.okText(), dndRequested, raw.toString().trim()))
    }

    suspend fun setListener(pkg: String, component: String, allow: Boolean): NotificationChangeResult = withContext(Dispatchers.IO) {
        val target = canonicalNotifComponent(component)
        fun refused(reason: String, state: Boolean? = null) =
            NotificationChangeResult(pkg, target, NotificationAccessKind.LISTENER, allow, Verdict.REFUSED, "", reason, state, state)
        if (!isValidPackageName(pkg)) return@withContext refused("invalid package name")
        if (!isValidComponentName(target)) return@withContext refused("invalid component name")
        if (target.substringBefore('/') != pkg) return@withContext refused("component does not belong to this package")
        protectedReason(pkg)?.let { return@withContext refused("protected: $it") }
        val before = readListeners()?.let { isListenerGranted(target, it) }
            ?: return@withContext refused("listener state unavailable")
        if (before == allow) return@withContext refused(if (allow) "already allowed" else "already revoked", before)
        val cmd = "cmd notification " + (if (allow) "allow_listener " else "disallow_listener ") + ShellQuoting.quote(target)
        val output = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return@withContext NotificationChangeResult(pkg, target, NotificationAccessKind.LISTENER, allow, Verdict.FAILED, cmd, o.message, before, null)
            is ExecOutcome.Completed -> outputOf(o.result)
        }
        val after = readListeners()?.let { isListenerGranted(target, it) }
        NotificationChangeResult(pkg, target, NotificationAccessKind.LISTENER, allow, verdictOf(after, allow), cmd, output, before, after)
    }

    suspend fun setDndAccess(pkg: String, allow: Boolean): NotificationChangeResult = withContext(Dispatchers.IO) {
        fun refused(reason: String, state: Boolean? = null) =
            NotificationChangeResult(pkg, pkg, NotificationAccessKind.DND, allow, Verdict.REFUSED, "", reason, state, state)
        if (!isValidPackageName(pkg)) return@withContext refused("invalid package name")
        protectedReason(pkg)?.let { return@withContext refused("protected: $it") }
        val before = readDnd()?.contains(pkg) ?: return@withContext refused("DND access state unavailable")
        if (before == allow) return@withContext refused(if (allow) "already allowed" else "already revoked", before)
        val cmd = "cmd notification " + (if (allow) "allow_dnd " else "disallow_dnd ") + ShellQuoting.quote(pkg)
        val output = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return@withContext NotificationChangeResult(pkg, pkg, NotificationAccessKind.DND, allow, Verdict.FAILED, cmd, o.message, before, null)
            is ExecOutcome.Completed -> outputOf(o.result)
        }
        val after = readDnd()?.contains(pkg)
        NotificationChangeResult(pkg, pkg, NotificationAccessKind.DND, allow, verdictOf(after, allow), cmd, output, before, after)
    }

    private fun readListeners(): List<String>? = sh(LISTENERS_CMD).okText()?.let { parseNotifSecureList(it) }
    private fun readDnd(): List<String>? = sh(DND_CMD).okText()?.let { parseNotifSecureList(it) }

    private fun sh(cmd: String): ExecResult? = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) {
        is ExecOutcome.Failed -> null
        is ExecOutcome.Completed -> o.result
    }

    private fun ExecResult?.okText(): String? = this?.takeIf { it.exitCode == 0 && !it.truncated }?.stdout

    private fun outputOf(r: ExecResult): String = (r.stdout + "\n" + r.stderr).trim() + "\n(exit ${r.exitCode})"

    private fun verdictOf(after: Boolean?, expected: Boolean): Verdict = when {
        after == null -> Verdict.UNVERIFIABLE
        after == expected -> Verdict.APPLIED
        else -> Verdict.NOT_APPLIED
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val SERVICES_CMD = "pm query-services --user 0 --components -a android.service.notification.NotificationListenerService"
        const val LISTENERS_CMD = "settings get secure enabled_notification_listeners"
        const val DND_CMD = "settings get secure enabled_notification_policy_access_packages"
    }
}

/** Same bridge accessor pattern as A5; the protected guard comes from the loaded inventory. */
internal fun InventoryRepository.notificationRepo(): NotificationRepository {
    val field = InventoryRepository::class.java.getDeclaredField("bridge")
    field.isAccessible = true
    return NotificationRepository(field.get(this) as ExecBridge) { entryFor(it)?.protectedReason }
}
