package io.github.kreza6173pixel.cyberappmanager.inventory

/** A boot-related receiver found in a package dump. */
data class BootReceiver(val packageName: String, val component: String, val action: String)

data class BackgroundOpState(val op: String, val mode: String)

data class AutostartAudit(
    val packageName: String,
    val receivers: List<BootReceiver>,
    val backgroundOps: List<BackgroundOpState>,
    val raw: String = "",
)

/**
 * Component name at line start, with an optional dumpsys hex hash prefix.
 * Accepts both shorthand (`pkg/.Cls`) and fully qualified (`pkg/full.Cls`) forms.
 * Ends on `:`, whitespace, or end of string.
 */
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

/**
 * Best-effort parser for `dumpsys package <pkg>` output.
 *
 * Boot receivers are only matched inside the Receiver Resolver Table.
 * The parser starts permissive (`inReceiverTable = true`) so single-line
 * or partial output still works, but any non-receiver boundary marker
 * (Activity/Service/Provider Resolver Table, Permissions, Packages, etc.)
 * resets it until the next `Receiver Resolver Table:` header.
 *
 * `component` and `action` are tracked independently and paired when both
 * are found. This handles all three real-device formats:
 *   1. `pkg/.Cls: ACTION`           (component + action on one line)
 *   2. `pkg/.Cls:\n  ACTION`        (component then action on next line)
 *   3. `ACTION:\n  hex pkg/.Cls`    (action header then component below)
 */
fun parseAutostartAudit(packageName: String, text: String): AutostartAudit {
    val receivers = linkedSetOf<BootReceiver>()
    var inReceiverTable = true
    var component: String? = null
    var action: String? = null

    for (line in text.lineSequence()) {
        val trimmed = line.trim()

        /* Section boundaries. */
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

        /* Try to extract a component and/or a boot action from this line. */
        COMPONENT.find(trimmed)?.let {
            /* A new component resets both: each filter is independent. */
            component = it.groupValues[1]
            action = null
        }

        BOOT.find(trimmed)?.let {
            action = it.groupValues[1]
        }

        /* Pair them as soon as both are available. */
        if (component != null && action != null) {
            val c = component!!
            if (c.substringBefore('/') == packageName) {
                receivers += BootReceiver(packageName, c, action!!)
            }
            component = null
            action = null
        }
    }

    /* Background ops are extracted from the full text, not just the receiver table. */
    val ops = OP.findAll(text)
        .map {
            BackgroundOpState(
                it.groupValues[0].substringBefore(':').uppercase(),
                it.groupValues[1].lowercase(),
            )
        }
        .distinctBy { it.op }
        .toList()

    return AutostartAudit(packageName, receivers.toList(), ops, text)
}

/** Validates a `package/class` component name for use in pm disable/enable commands. */
fun isValidComponentName(name: String): Boolean {
    val slash = name.indexOf('/')
    if (slash < 1 || slash == name.lastIndex) return false
    val pkg = name.substring(0, slash)
    val cls = name.substring(slash + 1)
    return isValidPackageName(pkg) && cls.matches(Regex("^[A-Za-z.][A-Za-z0-9_.\$]*$"))
}
