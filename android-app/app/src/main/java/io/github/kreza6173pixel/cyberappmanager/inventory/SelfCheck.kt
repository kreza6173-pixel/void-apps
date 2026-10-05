package io.github.kreza6173pixel.cyberappmanager.inventory

/** Which installed packages a Self-check run covers. Packages removed for the user are skipped. */
enum class SelfCheckScope { USER, SYSTEM, ALL }

/** One package with at least one problem. Packages without problems are only counted. */
data class SelfCheckItem(val pkg: String, val isSystem: Boolean, val issues: List<String>)

/** Read-only Self-check result. [checked] < [planned] when the run was stopped early. */
data class SelfCheckReport(
    val scope: SelfCheckScope,
    val planned: Int,
    val checked: Int,
    val permissionErrors: Int,
    val sizeCapHits: Int,
    val missingPermissionState: Int,
    val appOpsErrors: Int,
    val appOpsUnsplit: Int,
    val unrecognisedAppOps: Int,
    val items: List<SelfCheckItem>,
) {
    val complete: Boolean get() = checked == planned
}

/**
 * Plain-text report for Copy/Share and GitHub issues. Kept in English so maintainers can read every report.
 * Packages with permission problems are listed first, so rare findings are not buried under AppOps lines.
 */
fun selfCheckText(r: SelfCheckReport, device: String): String = buildString {
    appendLine("VOID // APPS self-check")
    appendLine("device: $device")
    appendLine("group: ${r.scope.name.lowercase()}, checked ${r.checked} of ${r.planned}" + (if (r.complete) "" else " (stopped early)"))
    appendLine("permission errors: ${r.permissionErrors} (64 KiB cap hits: ${r.sizeCapHits})")
    appendLine("packages without any permission state: ${r.missingPermissionState}")
    appendLine("appops errors: ${r.appOpsErrors}")
    appendLine("appops uid/package not split: ${r.appOpsUnsplit}")
    appendLine("packages with unrecognised appops lines: ${r.unrecognisedAppOps}")
    if (r.items.isEmpty()) {
        appendLine("no problems found")
    } else {
        appendLine()
        r.items.sortedBy { item -> if (item.issues.any { it.startsWith("permissions:") }) 0 else 1 }.forEach { item ->
            appendLine("${item.pkg} (${if (item.isSystem) "system" else "user"})")
            item.issues.forEach { appendLine("  - $it") }
        }
    }
}.trimEnd()
