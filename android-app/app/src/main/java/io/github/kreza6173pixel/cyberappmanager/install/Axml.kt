package io.github.kreza6173pixel.cyberappmanager.install

/**
 * Minimal binary AndroidManifest.xml (AXML) reader, the native twin of pulse-install's in-browser parser.
 * Input comes from `unzip -p <apk> AndroidManifest.xml | base64`. Never throws: bad input gives null.
 */
data class ApkManifestInfo(
    val packageName: String?,
    val versionCode: Long?,
    val versionName: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val label: String?,
    val permissions: List<String>,
    val launchable: Boolean,
)

object Axml {
    private const val RES_STRING_POOL = 0x0001
    private const val RES_XML = 0x0003
    private const val RES_XML_RESOURCE_MAP = 0x0180
    private const val RES_XML_START_ELEMENT = 0x0102
    private const val RES_XML_END_ELEMENT = 0x0103
    private const val UTF8_FLAG = 0x100
    private const val NO_INDEX = -1

    private const val ATTR_LABEL = 0x01010001
    private const val ATTR_NAME = 0x01010003
    private const val ATTR_MIN_SDK = 0x0101020c
    private const val ATTR_VERSION_CODE = 0x0101021b
    private const val ATTR_VERSION_NAME = 0x0101021c
    private const val ATTR_TARGET_SDK = 0x01010270

    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_BOOL = 0x12

    private class Attr(val name: String, val resId: Int, val raw: String?, val type: Int, val data: Int)

    fun parse(bytes: ByteArray): ApkManifestInfo? = runCatching { parseOrThrow(bytes) }.getOrNull()

    private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
    private fun u32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
            ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

    private fun readStringPool(b: ByteArray, start: Int): List<String> {
        val count = u32(b, start + 8)
        val flags = u32(b, start + 16)
        val stringsStart = u32(b, start + 20)
        val utf8 = (flags and UTF8_FLAG) != 0
        val out = ArrayList<String>(count)
        for (i in 0 until count) {
            val off = start + stringsStart + u32(b, start + 28 + i * 4)
            out += if (utf8) {
                var p = off
                if ((b[p].toInt() and 0x80) != 0) p += 2 else p += 1 // char length
                var len = b[p].toInt() and 0xff
                if ((len and 0x80) != 0) { len = ((len and 0x7f) shl 8) or (b[p + 1].toInt() and 0xff); p += 2 } else p += 1
                String(b, p, len, Charsets.UTF_8)
            } else {
                var p = off
                var len = u16(b, p)
                if ((len and 0x8000) != 0) { len = ((len and 0x7fff) shl 16) or u16(b, p + 2); p += 4 } else p += 2
                String(b, p, len * 2, Charsets.UTF_16LE)
            }
        }
        return out
    }

    private fun parseOrThrow(b: ByteArray): ApkManifestInfo? {
        if (b.size < 8 || u16(b, 0) != RES_XML) return null
        var strings: List<String> = emptyList()
        var resIds = IntArray(0)
        var pkg: String? = null
        var vCode: Long? = null
        var vName: String? = null
        var minSdk: Int? = null
        var targetSdk: Int? = null
        var label: String? = null
        val perms = linkedSetOf<String>()
        val stack = ArrayList<String>()
        var hasMain = false
        var hasLauncher = false
        var launchable = false

        var off = u16(b, 2)
        while (off + 8 <= b.size) {
            val type = u16(b, off)
            val headerSize = u16(b, off + 2)
            val size = u32(b, off + 4)
            if (size < 8 || off + size > b.size) break
            when (type) {
                RES_STRING_POOL -> strings = readStringPool(b, off)
                RES_XML_RESOURCE_MAP -> resIds = IntArray((size - headerSize) / 4) { u32(b, off + headerSize + it * 4) }
                RES_XML_START_ELEMENT -> {
                    val ext = off + headerSize
                    val name = strings.getOrNull(u32(b, ext + 4)) ?: ""
                    val attrStart = u16(b, ext + 8)
                    val attrSize = u16(b, ext + 10)
                    val attrCount = u16(b, ext + 12)
                    val attrs = (0 until attrCount).map { i ->
                        val a = ext + attrStart + i * attrSize
                        val nameIdx = u32(b, a + 4)
                        val rawIdx = u32(b, a + 8)
                        Attr(
                            strings.getOrNull(nameIdx) ?: "",
                            if (nameIdx in resIds.indices) resIds[nameIdx] else 0,
                            if (rawIdx == NO_INDEX) null else strings.getOrNull(rawIdx),
                            b[a + 15].toInt() and 0xff,
                            u32(b, a + 16),
                        )
                    }
                    fun find(res: Int, plain: String) = attrs.firstOrNull { it.resId == res } ?: attrs.firstOrNull { it.name == plain }
                    fun str(a: Attr?): String? = a?.raw ?: if (a?.type == TYPE_STRING) strings.getOrNull(a.data) else null
                    fun int(a: Attr?): Long? = when {
                        a == null -> null
                        a.type == TYPE_INT_DEC || a.type == TYPE_INT_HEX -> a.data.toLong() and 0xffffffffL
                        else -> a.raw?.toLongOrNull()
                    }
                    when (name) {
                        "manifest" -> {
                            pkg = str(attrs.firstOrNull { it.name == "package" })
                            vCode = int(find(ATTR_VERSION_CODE, "versionCode"))
                            vName = str(find(ATTR_VERSION_NAME, "versionName")) ?: int(find(ATTR_VERSION_NAME, "versionName"))?.toString()
                        }
                        "uses-sdk" -> {
                            minSdk = int(find(ATTR_MIN_SDK, "minSdkVersion"))?.toInt()
                            targetSdk = int(find(ATTR_TARGET_SDK, "targetSdkVersion"))?.toInt()
                        }
                        "uses-permission", "uses-permission-sdk-23" -> str(find(ATTR_NAME, "name"))?.let { perms += it }
                        "application" -> {
                            val l = find(ATTR_LABEL, "label")
                            label = if (l != null && l.type == TYPE_REFERENCE && l.raw == null) null else str(l)
                        }
                        "intent-filter" -> { hasMain = false; hasLauncher = false }
                        "action" -> if (str(find(ATTR_NAME, "name")) == "android.intent.action.MAIN") hasMain = true
                        "category" -> if (str(find(ATTR_NAME, "name")) == "android.intent.category.LAUNCHER") hasLauncher = true
                    }
                    stack += name
                }
                RES_XML_END_ELEMENT -> {
                    val ended = stack.removeLastOrNull()
                    if (ended == "intent-filter" && hasMain && hasLauncher && stack.lastOrNull() in setOf("activity", "activity-alias")) launchable = true
                }
            }
            off += size
        }
        if (pkg == null && vCode == null) return null
        return ApkManifestInfo(pkg, vCode, vName, minSdk, targetSdk, label, perms.toList(), launchable)
    }
}
