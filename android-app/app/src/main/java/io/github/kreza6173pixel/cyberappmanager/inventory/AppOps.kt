package io.github.kreza6173pixel.cyberappmanager.inventory

/**
 * Android AppOps state for one package. This is separate from manifest/runtime permissions.
 * [oem] marks vendor operations such as `MIUIOP(10008)`: shown for honesty, never changeable.
 * [alsoReported] lists other modes Android printed for the same op when scopes could not be separated.
 * [uidMode] / [packageMode] are filled when uid and package scopes were separated ([scoped] = true).
 */
data class AppOpRecord(
    val op: String,
    val mode: String,
    val detail: String = "",
    val oem: Boolean = false,
    val alsoReported: List<String> = emptyList(),
    val uidMode: String? = null,
    val packageMode: String? = null,
    val scoped: Boolean = false,
) {
    val changeable: Boolean get() = !oem && mode in CHANGEABLE_MODES
    companion object {
        val CHANGEABLE_MODES = listOf("allow", "ignore", "deny", "foreground", "default")
        /** Modes that are parsed and shown but never sent. `ask` is printed by HyperOS (seen as `MIUIOP(10017): ask`). */
        val READ_ONLY_MODES = listOf("ask")
        val KNOWN_MODES = CHANGEABLE_MODES + READ_ONLY_MODES
    }
}

/** [scoped] is true only when uid and package scopes were separated reliably; changes require it. */
data class AppOpsAudit(val packageName: String, val operations: List<AppOpRecord>, val scoped: Boolean = false)

/** Where an AppOps change is applied. UID affects every package in the uid. */
enum class AppOpScope { PACKAGE, UID }

/*
 * Android emits both `camera: allow` and prefixed lines such as `Uid mode: COARSE_LOCATION: foreground`.
 * The line start stays anchored so fragments like `name: deny` inside `bad-name: deny` are never parsed;
 * only an optional word prefix (letters and spaces, then a colon) is allowed before the operation.
 * OEM ops carry a numeric suffix in parentheses, for example `MIUIOP(10008): allow`.
 */
private val APP_OP_LINE = Regex(
    "^(?:[A-Za-z ]+:\\s*)?([a-z][a-z0-9_]*(?:\\(\\d+\\))?):\\s*([a-z]+)(?:;\\s*(.*))?$",
    RegexOption.IGNORE_CASE,
)
private val OEM_APP_OP = Regex("^[a-z][a-z0-9_]*\\(\\d+\\)$")
private val PACKAGE_UID = Regex("^package:(\\S+)\\s+uid:(\\d+)")

private data class OpLine(val op: String, val mode: String, val detail: String, val oem: Boolean)

private fun opLineOf(raw: String): OpLine? {
    val match = APP_OP_LINE.find(raw.trim()) ?: return null
    val op = match.groupValues[1].lowercase()
    val mode = match.groupValues[2].lowercase()
    val oem = OEM_APP_OP.matches(op)
    return if (mode !in AppOpRecord.KNOWN_MODES || (!oem && !isValidAppOp(op))) null
    else OpLine(op, mode, match.groupValues.getOrNull(3).orEmpty(), oem)
}

/** Every usable operation line, in the order Android printed them. */
private fun opLines(text: String): List<OpLine> = text.lineSequence().mapNotNull(::opLineOf).toList()

/** Non-empty lines of `appops get` output that VOID did not understand. Self-check reports them so new ROM formats are found. */
fun unparsedAppOpLines(text: String): List<String> =
    text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && it != "No operations." && opLineOf(it) == null }.toList()

/** Merged view of `appops get <package>`: one record per op, other reported modes kept in [AppOpRecord.alsoReported]. */
fun parseAppOps(packageName: String, text: String): AppOpsAudit {
    val records = linkedMapOf<String, AppOpRecord>()
    for (line in opLines(text)) {
        val previous = records[line.op]
        val also = if (previous == null) emptyList() else (previous.alsoReported + previous.mode).distinct().filter { it != line.mode }
        records[line.op] = AppOpRecord(line.op, line.mode, line.detail, line.oem, also)
    }
    return AppOpsAudit(packageName, records.values.sortedBy { it.op })
}

/**
 * Scoped view. On the reference ROM `appops get <uid>` prints only the uid block, and `appops get <package>`
 * prints that same block first, then the package block. The leading lines of [packageText] that match
 * [uidText] line by line are uid scope; the rest is package scope. An empty uid block means every line is
 * package scope, unless the package output still shows a `Uid mode:` line. If the prefix does not match
 * exactly, the merged [parseAppOps] view is returned instead of guessing.
 */
fun parseAppOpsScoped(packageName: String, packageText: String, uidText: String): AppOpsAudit {
    val all = opLines(packageText)
    val uid = opLines(uidText)
    if (uid.isEmpty() && packageText.lineSequence().any { it.trim().startsWith("Uid mode:") }) return parseAppOps(packageName, packageText)
    val prefixMatches = uid.size <= all.size && uid.indices.all { all[it].op == uid[it].op && all[it].mode == uid[it].mode }
    if (!prefixMatches) return parseAppOps(packageName, packageText)
    val uidByOp = uid.associateBy { it.op }
    val pkgByOp = all.drop(uid.size).associateBy { it.op }
    val records = (uidByOp.keys + pkgByOp.keys).mapNotNull { op ->
        val u = uidByOp[op]
        val p = pkgByOp[op]
        val main = p ?: u ?: return@mapNotNull null
        AppOpRecord(op, main.mode, main.detail, main.oem, emptyList(), u?.mode, p?.mode, true)
    }
    return AppOpsAudit(packageName, records.sortedBy { it.op }, scoped = true)
}

/** Mode of [op] in [scope], or null when Android did not list it there (which means default). */
fun appOpModeIn(audit: AppOpsAudit, op: String, scope: AppOpScope): String? {
    val record = audit.operations.firstOrNull { it.op == op } ?: return null
    return if (scope == AppOpScope.UID) record.uidMode else record.packageMode
}

/**
 * Non-null when a package-scope change must be refused: the op has a non-default uid mode.
 * AOSP evaluates the uid mode first, so a package mode has no effect while it is set, and on the
 * reference ROM such package changes were silently kept (ACCEPT_HANDOVER on Drive and Acode).
 */
fun packageScopeBlockedBy(record: AppOpRecord): String? = record.uidMode?.takeIf { it != "default" }

/** Exact uid from `pm list packages -U <filter>`; the filter is a substring match, so the name must be equal. */
fun uidOf(pkg: String, listOutput: String): Int? = listOutput.lineSequence()
    .mapNotNull { PACKAGE_UID.find(it.trim()) }
    .firstOrNull { it.groupValues[1] == pkg }
    ?.groupValues?.get(2)?.toIntOrNull()

/** Strict: OEM ops never pass, so set/reset commands can only target standard AppOps names. */
fun isValidAppOp(name: String): Boolean = name.matches(Regex("^[a-z][a-z0-9_]*$"))
fun isValidAppOpMode(mode: String): Boolean = mode in AppOpRecord.CHANGEABLE_MODES
fun appOpsSetCommand(pkg: String, op: String, mode: String): String = "appops set $pkg $op $mode"
fun appOpsResetCommand(pkg: String, op: String): String = "appops set $pkg $op default"

/**
 * Command for a guarded change. Android matches op names case-sensitively against the uppercase names
 * it prints, so the op is sent uppercase. Uid scope uses the numeric uid, the same form as `appops get <uid>`.
 */
fun appOpSetCommand(scope: AppOpScope, quotedPkg: String, uid: Int, op: String, mode: String): String {
    val target = if (scope == AppOpScope.UID) uid.toString() else quotedPkg
    return "appops set $target ${op.uppercase()} $mode"
}
