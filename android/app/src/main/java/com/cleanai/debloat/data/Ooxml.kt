package com.cleanai.debloat.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Helpers for saving Office files (.docx / .xlsx) without losing what the editors don't understand.
 * The idea: keep every part of the original file as it was and change only the XML the user edited.
 */
object Ooxml {

    /** Every file inside an Office package, in the original order. */
    class Package(val parts: List<Pair<String, ByteArray>>) {
        operator fun get(name: String): ByteArray? = parts.firstOrNull { it.first == name }?.second
        fun text(name: String): String? = get(name)?.toString(Charsets.UTF_8)

        /** A copy with some parts replaced, added (at the end) or removed (null). */
        fun with(changes: Map<String, ByteArray?>): Package {
            val kept = parts.mapNotNull { (n, b) -> if (n in changes) changes[n]?.let { n to it } else n to b }
            val added = changes.filterKeys { k -> parts.none { it.first == k } }.mapNotNull { (k, v) -> v?.let { k to it } }
            return Package(kept + added)
        }

        fun withText(changes: Map<String, String?>) = with(changes.mapValues { it.value?.toByteArray(Charsets.UTF_8) })

        /** Builds the whole file in memory first, so a failure never leaves a half-written file behind. */
        fun toBytes(): ByteArray {
            val buf = ByteArrayOutputStream()
            ZipOutputStream(buf).use { zip ->
                parts.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            return buf.toByteArray()
        }

        fun writeTo(out: OutputStream) = out.write(toBytes())
    }

    fun readPackage(input: InputStream): Package {
        val parts = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) parts += e.name.removePrefix("/") to zip.readBytes()
            }
        }
        return Package(parts)
    }

    // ---------------------------------------------------------------- XML pieces

    /** One element at the top level of a piece of XML: its tag name (with prefix) and its exact text. */
    class Element(val name: String, val xml: String) {
        val local get() = name.substringAfter(':')
    }

    private val TAG = Regex("<[^>]*>")

    /**
     * Splits XML content into its top-level elements, keeping each one's exact text.
     * Text between elements (normally just whitespace) is dropped.
     */
    fun elements(xml: String): List<Element> {
        val out = mutableListOf<Element>()
        var depth = 0
        var start = -1
        var name = ""
        for (m in TAG.findAll(xml)) {
            val tag = m.value
            if (tag.startsWith("<?") || tag.startsWith("<!")) continue
            when {
                tag.startsWith("</") -> {
                    depth--
                    if (depth == 0 && start >= 0) { out += Element(name, xml.substring(start, m.range.last + 1)); start = -1 }
                }
                tag.endsWith("/>") -> if (depth == 0) out += Element(tagName(tag), tag)
                else -> {
                    if (depth == 0) { start = m.range.first; name = tagName(tag) }
                    depth++
                }
            }
        }
        return out
    }

    fun tagName(tag: String): String = tag.removePrefix("<").removePrefix("/").takeWhile { !it.isWhitespace() && it != '/' && it != '>' }

    /** The opening tag of [element], its inner XML, and the closing tag ("" for a self-closing element). */
    fun open(element: String): String = TAG.find(element)?.value.orEmpty()

    fun inner(element: String): String {
        val first = open(element)
        if (first.endsWith("/>")) return ""
        return element.substring(first.length, element.lastIndexOf("</").coerceAtLeast(first.length))
    }

    /** The value of an attribute in an opening tag, ignoring any namespace prefix on the attribute name. */
    fun attr(tag: String, name: String): String? =
        Regex("""\s(?:\w+:)?$name="([^"]*)"""").find(open(tag))?.groupValues?.get(1)?.let(::unescape)

    /** Sets (or with null, removes) an attribute on an opening tag. */
    fun setAttr(tag: String, name: String, value: String?): String {
        val removed = tag.replace(Regex("""\s$name="[^"]*""""), "")
        if (value == null) return removed
        val end = if (removed.endsWith("/>")) removed.length - 2 else removed.length - 1
        return removed.substring(0, end) + """ $name="${escape(value)}"""" + removed.substring(end)
    }

    /** Puts child elements back into a schema's required order; unknown ones keep their place at the end. */
    fun ordered(children: List<Element>, order: List<String>): String =
        children.sortedBy { e -> order.indexOf(e.local).let { if (it < 0) order.size else it } }.joinToString("") { it.xml }

    /** All text in w:t (Word) or t (Excel) elements. */
    fun text(xml: String, tag: String = "w:t"): String =
        Regex("""<$tag(?:\s[^>]*)?>([^<]*)</$tag>""").findAll(xml).joinToString("") { unescape(it.groupValues[1]) }

    fun escape(s: String) = buildString {
        for (c in s) when {
            c == '&' -> append("&amp;")
            c == '<' -> append("&lt;")
            c == '>' -> append("&gt;")
            c == '"' -> append("&quot;")
            // Control characters aren't allowed in XML.
            c < ' ' && c != '\t' && c != '\n' && c != '\r' -> {}
            else -> append(c)
        }
    }

    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos);")

    fun unescape(s: String): String = ENTITY.replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
            else -> runCatching {
                val code = if (e.startsWith("#x")) e.drop(2).toInt(16) else e.drop(1).toInt()
                String(Character.toChars(code))
            }.getOrDefault(m.value)
        }
    }

    // ---------------------------------------------------------------- package bookkeeping

    /** Adds a content-type override for a new part, unless it's already there. */
    fun withOverride(types: String, part: String, contentType: String): String =
        if (types.contains("PartName=\"/$part\"")) types
        else types.replace("</Types>", """<Override PartName="/$part" ContentType="$contentType"/></Types>""")

    fun withoutOverride(types: String, part: String): String =
        types.replace(Regex("""<Override\s[^>]*PartName="/${Regex.escape(part)}"[^>]*/>"""), "")

    /** Adds a relationship and returns the new XML plus the id it was given (or the existing one). */
    fun withRelationship(rels: String, type: String, target: String): Pair<String, String> {
        val existing = Regex("""<Relationship\s[^>]*>""").findAll(rels).map { it.value }
            .firstOrNull { attr(it, "Target") == target && attr(it, "Type") == type }
        if (existing != null) return rels to attr(existing, "Id").orEmpty()
        val used = Regex("""Id="([^"]*)"""").findAll(rels).map { it.groupValues[1] }.toSet()
        val id = generateSequence(1) { it + 1 }.map { "rIdMh$it" }.first { it !in used }
        return rels.replace("</Relationships>", """<Relationship Id="$id" Type="$type" Target="$target"/></Relationships>""") to id
    }

    fun withoutRelationship(rels: String, target: String): String =
        rels.replace(Regex("""<Relationship\s[^>]*Target="${Regex.escape(target)}"[^>]*/>"""), "")

    const val EMPTY_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""
}
