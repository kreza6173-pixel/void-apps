package io.github.kreza6173pixel.cyberappmanager.inventory

/** A boot-related receiver found in a package dump. */
data class BootReceiver(val packageName: String, val component: String, val action: String)

data class BackgroundOpState(val op: String, val mode: String)

data class AutostartAudit(
    val packageName: String,
    val receivers: List<BootReceiver>,
    val backgroundOps: List<BackgroundOpState>,
    val disabledComponents: Set<String> = emptySet(),
    val raw: String = "",
)

/** Result of a pm disable/enable component operation with read-back. */
data class ComponentChangeResult(
    val pkg: String,
    val component: String,
    val enable: Boolean,
    val verdict: Verdict,
    val command: String,
    val output: String,
    val wasBefore: Boolean,
    val isAfter: Boolean?,
)

private val COMPONENT = Regex(
    "^(?:[0-9a-f]+\\s+)?([A-Za-z][A-Za-z0-9_.]*/[A-Za-z.][A-Za-z0-9_.\$]*)(?=$|[:\\s])"
)

private val BOOT = Regex(
    "(LOCKED_BOOT_COMPLETED|BOOT_COMPLETED|QUICKBOOT_POWERON|MY_PACKAGE_REPLACED)"
)

private val OP = Regex(
    "(?:RUN_IN_BACKGROUND|RUN_ANY_IN_BACKGROUND):\\s*([a-z]+)",
    RegexOption.IGNORE_CASE,
)

fun parseAutostartAudit(packageName: String, text: String): AutostartAudit {
    val receivers = linkedSetOf<BootReceiver>()
    var inReceiverTable = true
    var component: String? = null
    var action: String? = null

    for (line in text.lineSequence()) {
        val trimmed = line.trim()

        when {
            trimmed == "Receiver Resolver Table:" -> {
                inReceiverTable = true
                component = null; action = null
            }
            trimmed.endsWith(" Resolver Table:") && trimmed != "Receiver Resolver Table:" -> {
                inReceiverTable = false
                component = null; action = null
            }
            trimmed in setOf(
                "Permissions:", "Registered ContentProviders:", "Packages:",
                "Queries:", "Dexopt state:", "Compiler stats:",
            ) -> {
                inReceiverTable = false
                component = null; action = null
            }
        }

        if (!inReceiverTable) continue

        COMPONENT.find(trimmed)?.let {
            component = it.groupValues[1]
            action = null
        }

        BOOT.find(trimmed)?.let {
            action = it.groupValues[1]
        }

        if (component != null && action != null) {
            val c = component!!
            if (c.substringBefore('/') == packageName) {
                receivers += BootReceiver(packageName, c, action!!)
            }
            component = null
            action = null
        }
    }

    val ops = OP.findAll(text)
        .map {
            BackgroundOpState(
                it.groupValues[0].substringBefore(':').uppercase(),
                it.groupValues[1].lowercase(),
            )
        }
        .distinctBy { it.op }
        .toList()

    val disabled = parseDisabledComponents(packageName, text)

    return AutostartAudit(packageName, receivers.toList(), ops, disabled, text)
}

/**
 * Extracts the set of disabled component names from `dumpsys package` output.
 * In the Packages section, disabled components are listed under `disabledComponents:`.
 */
fun parseDisabledComponents(packageName: String, text: String): Set<String> {
    val disabled = mutableSetOf<String>()
    var inSection = false
    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        when {
            trimmed == "disabledComponents:" -> inSection = true
            inSection && (trimmed == "enabledComponents:" || trimmed.isEmpty() ||
                trimmed.endsWith(":") && !trimmed.startsWith(packageName) && !trimmed.startsWith(".")) -> {
                inSection = false
            }
            inSection && trimmed.isNotEmpty() -> {
                val cls = trimmed.trim()
                val full = if (cls.startsWith(".")) "$packageName/$cls" else if ('/' in cls) cls else "$packageName/$cls"
                disabled.add(full)
            }
        }
    }
    return disabled
}

/**
 * Checks whether a component name appears in the disabled set.
 * Handles both shorthand and fully qualified forms.
 */
fun isComponentDisabled(component: String, disabledComponents: Set<String>): Boolean {
    if (component in disabledComponents) return true
    val cls = component.substringAfter('/')
    val pkg = component.substringBefore('/')
    return "$pkg/$cls" in disabledComponents || cls in disabledComponents ||
        (cls.startsWith(".") && "$pkg$cls" in disabledComponents)
}

/** Validates a `package/class` component name for use in pm disable/enable commands. */
fun isValidComponentName(name: String): Boolean {
    val slash = name.indexOf('/')
    if (slash < 1 || slash == name.lastIndex) return false
    val pkg = name.substring(0, slash)
    val cls = name.substring(slash + 1)
    return isValidPackageName(pkg) && cls.matches(Regex("^[A-Za-z.][A-Za-z0-9_.\$]*$"))
}
