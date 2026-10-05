package io.github.kreza6173pixel.cyberappmanager.inventory

enum class AppState { ENABLED, FROZEN, SUSPENDED, REMOVED }
data class AppEntry(val pkg: String, val label: String, val isSystem: Boolean, val state: AppState, val protectedReason: String?)
data class InventoryCounts(val total: Int, val user: Int, val system: Int, val frozen: Int, val suspended: Int, val removed: Int, val protectedCount: Int)
enum class AppFilter { ALL, USER, SYSTEM, FROZEN, SUSPENDED, REMOVED, PROTECTED; fun matches(e: AppEntry) = when (this) { ALL -> true; USER -> !e.isSystem; SYSTEM -> e.isSystem; FROZEN -> e.state == AppState.FROZEN; SUSPENDED -> e.state == AppState.SUSPENDED; REMOVED -> e.state == AppState.REMOVED; PROTECTED -> e.protectedReason != null }; fun count(c: InventoryCounts) = when (this) { ALL -> c.total; USER -> c.user; SYSTEM -> c.system; FROZEN -> c.frozen; SUSPENDED -> c.suspended; REMOVED -> c.removed; PROTECTED -> c.protectedCount } }

fun mergeInventory(all: Set<String>, installed: Set<String>, disabled: Set<String>, suspended: Set<String>, system: Set<String>, labels: Map<String, String>, guard: (String) -> String?): List<AppEntry> {
    val universe = LinkedHashSet<String>(all); universe.addAll(installed)
    return universe.map { pkg -> val state = when { pkg !in installed -> AppState.REMOVED; pkg in disabled -> AppState.FROZEN; pkg in suspended -> AppState.SUSPENDED; else -> AppState.ENABLED }; AppEntry(pkg, labels[pkg]?.takeIf { it.isNotBlank() } ?: pkg, pkg in system, state, guard(pkg)) }.sortedWith(compareBy<AppEntry> { it.label.lowercase() }.thenBy { it.pkg })
}

/** Compatibility overload for the M0/A1 fixtures. */
fun mergeInventory(all: Set<String>, installed: Set<String>, disabled: Set<String>, system: Set<String>, labels: Map<String, String>, guard: (String) -> String?): List<AppEntry> = mergeInventory(all, installed, disabled, emptySet(), system, labels, guard)

fun countsOf(entries: List<AppEntry>) = InventoryCounts(entries.size, entries.count { !it.isSystem }, entries.count { it.isSystem }, entries.count { it.state == AppState.FROZEN }, entries.count { it.state == AppState.SUSPENDED }, entries.count { it.state == AppState.REMOVED }, entries.count { it.protectedReason != null })
fun filterApps(entries: List<AppEntry>, filter: AppFilter, query: String): List<AppEntry> { val q = query.trim().lowercase(); return entries.filter { filter.matches(it) && (q.isEmpty() || it.pkg.lowercase().contains(q) || it.label.lowercase().contains(q)) } }
