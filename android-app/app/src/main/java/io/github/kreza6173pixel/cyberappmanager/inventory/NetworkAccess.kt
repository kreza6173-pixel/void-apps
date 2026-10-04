package io.github.kreza6173pixel.cyberappmanager.inventory

/** First uid that belongs to an app. Lower uids are shared system identities and are never touched here. */
const val FIRST_APPLICATION_UID = 10_000

/** Minimum SDK for Chain 3 (`cmd connectivity set-package-networking-enabled`), same gate as VOID-WALL. */
const val CHAIN3_MIN_SDK = 30

/**
 * A7 per-app network state, ported from the owner's `VOID-WALL` module.
 *
 * Every state is nullable: null means Android did not give a readable answer, and no success is
 * ever claimed for it.
 */
data class NetworkAudit(
    val packageName: String,
    val uid: Int?,
    /** Other packages that share [uid]. Blocking this app blocks them too. */
    val sharedWith: List<String>,
    val chain3Supported: Boolean,
    val chain3Enabled: Boolean?,
    /** true = Chain 3 denies this package (no network at all). */
    val networkBlocked: Boolean?,
    /** Where [networkBlocked] came from: "connectivity", "trafficcontroller" or "". */
    val blockSource: String,
    /** true = uid is on the netpolicy restrict-background list (no background data on metered networks). */
    val backgroundRestricted: Boolean?,
    /** Global Data Saver switch, informational only. */
    val dataSaver: Boolean?,
    val raw: String = "",
)

enum class NetworkChangeKind { NETWORK_BLOCK, BACKGROUND_DATA }

data class NetworkChangeResult(
    val pkg: String,
    val kind: NetworkChangeKind,
    /** true = restrict (block network / restrict background), false = allow. */
    val restrict: Boolean,
    val verdict: Verdict,
    val command: String,
    val output: String,
    val before: Boolean?,
    val after: Boolean?,
    val note: String = "",
)

private val UID_IN_PM = Regex("^package:(\\S+)\\s+uid:(\\d+)")

/** `pm list packages -U` lines as package to uid. Multi-user forms like `uid:10123,1010123` keep the first uid. */
fun parsePmUidList(text: String): Map<String, Int> {
    val out = linkedMapOf<String, Int>()
    for (line in text.lineSequence()) {
        val m = UID_IN_PM.find(line.trim()) ?: continue
        m.groupValues[2].toIntOrNull()?.let { out[m.groupValues[1]] = it }
    }
    return out
}

/** Exact-match uid lookup; `pm list packages -U <filter>` is a substring filter, so neighbours are ignored. */
fun uidForPackage(pkg: String, pmText: String): Int? = parsePmUidList(pmText)[pkg]

fun packagesSharingUid(pkg: String, uid: Int, pmText: String): List<String> =
    parsePmUidList(pmText).filter { it.value == uid && it.key != pkg }.keys.sorted()

private val NOISE = Regex("unknown|usage|invalid|exception|error|not found|no such", RegexOption.IGNORE_CASE)
private val NEG = Regex("\\b(false|deny|denied|disabled|blocked)\\b", RegexOption.IGNORE_CASE)
private val POS = Regex("\\b(true|allow|allowed|enabled)\\b", RegexOption.IGNORE_CASE)

/**
 * Tolerant reader for one-line boolean answers (`chain:enabled`, `true`, `pkg: deny`).
 * Help text, errors and long output give null instead of a guess.
 */
fun parseShellBoolean(text: String?): Boolean? {
    val t = text?.trim().orEmpty()
    if (t.isEmpty() || t.length > 200 || t.lines().size > 3 || NOISE.containsMatchIn(t)) return null
    val neg = NEG.containsMatchIn(t)
    val pos = POS.containsMatchIn(t.replace(NEG, " "))
    return when {
        neg && !pos -> false
        pos && !neg -> true
        else -> null
    }
}

/** `cmd connectivity get-chain3-enabled`: true when the Chain 3 firewall chain is on. */
fun parseChain3Enabled(text: String?): Boolean? = parseShellBoolean(text)

/** `cmd connectivity get-package-networking-enabled <pkg>`: returns true when the package is BLOCKED. */
fun parsePackageBlocked(text: String?): Boolean? = parseShellBoolean(text)?.not()

/**
 * Fallback read-back from `dumpsys connectivity trafficcontroller | grep -E 'sUidOwnerMap|OEM_DENY_3'`.
 * The owner map must be present, otherwise nothing is concluded. A uid row carrying OEM_DENY_3 = blocked.
 */
fun parseTrafficControllerBlocked(uid: Int, text: String?): Boolean? {
    val t = text.orEmpty()
    if (!t.contains("sUidOwnerMap")) return null
    return t.lineSequence().any { line ->
        val parts = line.trim().split(Regex("\\s+"))
        parts.firstOrNull()?.trimEnd(':') == uid.toString() && line.contains("OEM_DENY_3")
    }
}

/**
 * `cmd netpolicy list restrict-background-blacklist` prints
 * `Restrict background blacklisted UIDs: 10123 10200` or `...: none`. Every number after a colon is a uid.
 * Returns null for output that does not look like that list.
 */
fun parseNetpolicyUidList(text: String?): Set<Int>? {
    val t = text?.trim() ?: return null
    if (t.isEmpty() || NOISE.containsMatchIn(t.substringAfter(':', ""))) return null
    if (!t.contains(':')) return null
    val out = linkedSetOf<Int>()
    for (line in t.lineSequence()) {
        val tail = if (line.contains(':')) line.substringAfter(':') else line
        Regex("\\b\\d+\\b").findAll(tail).mapNotNull { it.value.toIntOrNull() }.forEach { out += it }
    }
    return out
}

/** `cmd netpolicy get restrict-background`: `Restrict background status: enabled|disabled`. */
fun parseDataSaver(text: String?): Boolean? = parseShellBoolean(text?.let { it.substringAfter(':', it) })

fun networkVerdict(after: Boolean?, expected: Boolean): Verdict = when {
    after == null -> Verdict.UNVERIFIABLE
    after == expected -> Verdict.APPLIED
    else -> Verdict.NOT_APPLIED
}
