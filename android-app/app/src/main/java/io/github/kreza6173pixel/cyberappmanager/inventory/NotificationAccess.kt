package io.github.kreza6173pixel.cyberappmanager.inventory

const val NOTIF_POST_PERMISSION = "android.permission.POST_NOTIFICATIONS"
const val NOTIF_POLICY_PERMISSION = "android.permission.ACCESS_NOTIFICATION_POLICY"
const val NOTIF_LISTENER_ACTION = "android.service.notification.NotificationListenerService"

/** A notification listener service declared or approved for a package. [component] is always fully qualified. */
data class ListenerService(val component: String, val granted: Boolean)

/**
 * A6 per-app notification access state.
 * [listenerStateKnown] is false when `enabled_notification_listeners` could not be read; no change is offered then.
 * [dndGranted] is null when `enabled_notification_policy_access_packages` could not be read.
 */
data class NotificationAudit(
    val packageName: String,
    val listeners: List<ListenerService>,
    val dndRequested: Boolean,
    val dndGranted: Boolean?,
    val listenerStateKnown: Boolean,
    val raw: String = "",
)

enum class NotificationAccessKind { LISTENER, DND }

data class NotificationChangeResult(
    val pkg: String,
    val target: String,
    val kind: NotificationAccessKind,
    val allow: Boolean,
    val verdict: Verdict,
    val command: String,
    val output: String,
    val before: Boolean?,
    val after: Boolean?,
)

/** Colon-separated secure setting value. `null` (unset) and blanks give an empty list. */
fun parseNotifSecureList(raw: String?): List<String> {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty() || text == "null") return emptyList()
    return text.split(':').map { it.trim() }.filter { it.isNotEmpty() && it != "null" }
}

/**
 * Normalises `pkg/.Cls` and `pkg/pkg.Cls` to one form. Every comparison goes through this:
 * A5 showed that comparing a shorthand name with a fully qualified one silently fails.
 */
fun canonicalNotifComponent(component: String): String = expandComponentName(component.trim())

fun isListenerGranted(component: String, enabled: List<String>): Boolean {
    val target = canonicalNotifComponent(component)
    return enabled.any { canonicalNotifComponent(it) == target }
}

private val ANY_COMPONENT = Regex("([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+/[A-Za-z.][A-Za-z0-9_.\$]*)")

/** Listener components of [packageName] found in `pm query-services` (or dumpsys fallback) output, normalised and sorted. */
fun parseListenerServices(packageName: String, text: String): List<String> =
    ANY_COMPONENT.findAll(text)
        .map { it.groupValues[1].trimEnd('.') }
        .filter { it.substringBefore('/') == packageName }
        .map { canonicalNotifComponent(it) }
        .distinct()
        .sorted()
        .toList()

/**
 * Merges declared listener services with the approved list, so an approved listener that the
 * service query did not return is still shown and can be revoked.
 */
fun buildNotificationAudit(
    packageName: String,
    servicesText: String,
    listenersSetting: String?,
    dndSetting: String?,
    dndRequested: Boolean,
    raw: String = "",
): NotificationAudit {
    val enabled = parseNotifSecureList(listenersSetting)
    val declared = parseListenerServices(packageName, servicesText)
    val approvedHere = enabled.map { canonicalNotifComponent(it) }.filter { it.substringBefore('/') == packageName }
    val all = (declared + approvedHere).distinct().sorted()
    val listeners = all.map { ListenerService(it, isListenerGranted(it, enabled)) }
    val dndGranted = dndSetting?.let { setting -> parseNotifSecureList(setting).any { it == packageName } }
    return NotificationAudit(packageName, listeners, dndRequested, dndGranted, listenersSetting != null, raw)
}
