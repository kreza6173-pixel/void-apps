package io.github.kreza6173pixel.cyberappmanager.inventory

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SnapshotStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val legacyFile = File(context.applicationContext.filesDir, "snapshots.json")
    @Synchronized fun list(): List<Snapshot> = read().sortedByDescending { it.createdAtMs }
    @Synchronized fun get(id: String): Snapshot? = read().firstOrNull { it.id == id }
    @Synchronized fun put(snapshot: Snapshot) { require(snapshot.id.isNotBlank()); write((read().filterNot { it.id == snapshot.id } + snapshot).sortedByDescending { it.createdAtMs }.take(MAX_SNAPSHOTS)) }
    @Synchronized fun delete(id: String): Boolean { val current = read(); val next = current.filterNot { it.id == id }; if (next.size == current.size) return false; write(next); return true }
    @Synchronized fun exportJson(): String = snapshotsToJson(read().sortedByDescending { it.createdAtMs })
    @Synchronized fun importJson(text: String): Int { val incoming = parseSnapshots(text) ?: return -1; if (incoming.isEmpty()) return 0; val byId = LinkedHashMap<String, Snapshot>(); read().forEach { byId[it.id] = it }; incoming.forEach { byId[it.id] = it }; write(byId.values.sortedByDescending { it.createdAtMs }.take(MAX_SNAPSHOTS)); return incoming.size }
    private fun read(): List<Snapshot> { val texts = mediaUris().mapNotNull { runCatching { resolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }.getOrNull() }; val all = if (texts.isNotEmpty()) texts else listOfNotNull(runCatching { legacyFile.takeIf { it.exists() }?.readText() }.getOrNull()); val byId = LinkedHashMap<String, Snapshot>(); all.forEach { parseSnapshots(it)?.forEach { s -> byId[s.id] = s } }; return byId.values.toList() }
    private fun write(snapshots: List<Snapshot>) { val text = snapshotsToJson(snapshots); runCatching { val files = mediaUris(); val canonical = files.firstOrNull { displayName(it) == FILE_NAME } ?: files.firstOrNull() ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME); put(MediaStore.MediaColumns.MIME_TYPE, "application/json"); put(MediaStore.MediaColumns.RELATIVE_PATH, DIRECTORY) }) ?: error("could not create persistent storage"); resolver.openOutputStream(canonical, "wt")?.bufferedWriter()?.use { it.write(text) } ?: error("could not open persistent storage"); files.filter { it != canonical }.forEach { runCatching { resolver.delete(it, null, null) } } }.onFailure { legacyFile.writeText(text) } }
    private fun mediaUris(): List<Uri> = runCatching { val base = MediaStore.Files.getContentUri("external"); resolver.query(base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME), "${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(DIRECTORY), null)?.use { c -> val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID); val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME); buildList { if (c.moveToFirst()) do { val n = c.getString(name) ?: continue; if (n.startsWith(PREFIX) && n.endsWith(".json")) add(MediaStore.Files.getContentUri("external", c.getLong(id))) } while (c.moveToNext()) } } ?: emptyList() }.getOrElse { emptyList() }
    private fun displayName(uri: Uri): String? = runCatching { resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
    private companion object { const val DIRECTORY = "Download/VOID APPS/"; const val FILE_NAME = "snapshots.json"; const val PREFIX = "snapshots"; const val MAX_SNAPSHOTS = 50 }
}

fun parseSnapshots(text: String): List<Snapshot>? = runCatching { val trimmed = text.trim(); val objects = if (trimmed.startsWith("[")) JSONArray(trimmed).let { a -> List(a.length()) { a.getJSONObject(it) } } else listOf(JSONObject(trimmed)); objects.mapNotNull { runCatching { snapshotFromJson(it) }.getOrNull() } }.getOrNull()
private fun snapshotFromJson(json: JSONObject): Snapshot { val entries = json.optJSONArray("entries") ?: JSONArray(); val id = json.getString("id"); require(id.isNotBlank()); return Snapshot(id, json.optString("name", "Snapshot"), json.optLong("createdAtMs", 0L), buildList(entries.length()) { for (i in 0 until entries.length()) { val item = entries.optJSONObject(i) ?: continue; val state = runCatching { AppState.valueOf(item.getString("state")) }.getOrNull() ?: continue; val pkg = item.optString("pkg"); if (isValidPackageName(pkg)) add(SnapshotEntry(pkg, item.optBoolean("isSystem"), state)) } }.sortedBy { it.pkg }) }
private fun snapshotsToJson(snapshots: List<Snapshot>): String { val array = JSONArray(); snapshots.forEach { s -> array.put(JSONObject().apply { put("id", s.id); put("name", s.name); put("createdAtMs", s.createdAtMs); put("entries", JSONArray().apply { s.entries.forEach { e -> put(JSONObject().apply { put("pkg", e.pkg); put("isSystem", e.isSystem); put("state", e.state.name) }) } }) }) }; return array.toString() }

class PinStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val legacyFile = File(context.applicationContext.filesDir, "pins.json")
    @Synchronized fun list(): Set<String> { val texts = mediaUris().mapNotNull { runCatching { resolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }.getOrNull() }; val all = if (texts.isNotEmpty()) texts else listOfNotNull(runCatching { legacyFile.takeIf { it.exists() }?.readText() }.getOrNull()); return all.flatMap { text -> runCatching { val a = JSONArray(text); List(a.length()) { a.optString(it) } }.getOrDefault(emptyList()) }.filter(::isValidPackageName).toSet() }
    @Synchronized fun set(pkg: String, pinned: Boolean): Set<String> { require(isValidPackageName(pkg)); val next = list().toMutableSet().apply { if (pinned) add(pkg) else remove(pkg) }; write(next); return next }
    private fun write(pins: Set<String>) { val text = JSONArray().apply { pins.sorted().forEach(::put) }.toString(); runCatching { val files = mediaUris(); val canonical = files.firstOrNull { displayName(it) == FILE_NAME } ?: files.firstOrNull() ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME); put(MediaStore.MediaColumns.MIME_TYPE, "application/json"); put(MediaStore.MediaColumns.RELATIVE_PATH, DIRECTORY) }) ?: error("could not create persistent storage"); resolver.openOutputStream(canonical, "wt")?.bufferedWriter()?.use { it.write(text) } ?: error("could not open persistent storage"); files.filter { it != canonical }.forEach { runCatching { resolver.delete(it, null, null) } } }.onFailure { legacyFile.writeText(text) } }
    private fun mediaUris(): List<Uri> = runCatching { val base = MediaStore.Files.getContentUri("external"); resolver.query(base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME), "${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(DIRECTORY), null)?.use { c -> val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID); val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME); buildList { if (c.moveToFirst()) do { val n = c.getString(name) ?: continue; if (n.startsWith(PREFIX) && n.endsWith(".json")) add(MediaStore.Files.getContentUri("external", c.getLong(id))) } while (c.moveToNext()) } } ?: emptyList() }.getOrElse { emptyList() }
    private fun displayName(uri: Uri): String? = runCatching { resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
    private companion object { const val DIRECTORY = "Download/VOID APPS/"; const val FILE_NAME = "pins.json"; const val PREFIX = "pins" }
}
