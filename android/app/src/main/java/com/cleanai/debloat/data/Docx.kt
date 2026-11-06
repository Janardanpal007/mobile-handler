package com.cleanai.debloat.data

import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A small, self-contained Word (.docx) reader and writer.
 * Paragraphs, headings, bullet / numbered lists, alignment, and bold / italic / underline can be edited.
 * Everything else in an opened file (tables, pictures, links, fields, headers, footers, styles) is kept exactly
 * as it was: such blocks are shown read-only, and saving changes only the paragraphs that were edited (see [DocxSave]).
 */
object Docx {
    enum class Style(val label: String, val wordId: String?) {
        TITLE("Title", "Title"),
        H1("Heading 1", "Heading1"),
        H2("Heading 2", "Heading2"),
        BODY("Text", null),
        BULLET("• List", "ListParagraph"),
        NUMBERED("1. List", "ListParagraph"),
    }

    enum class Align { LEFT, CENTER, RIGHT, JUSTIFY }

    data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

    /** Where a paragraph came from in an opened file: its exact XML, and how it read before any edits. */
    data class Origin(val xml: String, val parsed: Para)

    /**
     * One block of the document. A [locked] block (a table, a picture, a paragraph with a link or field) can't be
     * edited here and is saved exactly as it was; its [locked] text says what it is ("" for invisible markers).
     */
    data class Para(
        val runs: List<Run>,
        val style: Style = Style.BODY,
        val align: Align = Align.LEFT,
        val origin: Origin? = null,
        val locked: String? = null,
    ) {
        /** True when this came from the opened file and hasn't been changed. */
        val unchanged get() = origin != null && copy(origin = null) == origin.parsed

        val text get() = runs.joinToString("") { it.text }
        val bold get() = runs.isNotEmpty() && runs.filter { it.text.isNotBlank() }.ifEmpty { runs }.all { it.bold }
        val italic get() = runs.isNotEmpty() && runs.filter { it.text.isNotBlank() }.ifEmpty { runs }.all { it.italic }
        val underline get() = runs.isNotEmpty() && runs.filter { it.text.isNotBlank() }.ifEmpty { runs }.all { it.underline }

        /** New text replaces the runs with one run, keeping the paragraph's overall formatting. */
        fun withText(t: String) = if (t == text) this else copy(runs = listOf(Run(t, bold, italic, underline)))
        fun withBold(on: Boolean) = copy(runs = runs.ifEmpty { listOf(Run("")) }.map { it.copy(bold = on) })
        fun withItalic(on: Boolean) = copy(runs = runs.ifEmpty { listOf(Run("")) }.map { it.copy(italic = on) })
        fun withUnderline(on: Boolean) = copy(runs = runs.ifEmpty { listOf(Run("")) }.map { it.copy(underline = on) })
    }

    /** The opened file, kept so saving can change only what was edited. [head] / [tail] surround the body's blocks. */
    class Source(val pack: Ooxml.Package, val head: String, val tail: String)

    data class Document(val paras: List<Para>, val source: Source? = null) {
        /** Blocks shown read-only because the editor can't change them without losing something. */
        val hasLocked get() = paras.any { !it.locked.isNullOrEmpty() }
    }

    // ---------------------------------------------------------------- reading

    fun read(input: InputStream): Document {
        val pack = Ooxml.readPackage(input)
        val xml = pack.text("word/document.xml") ?: throw IllegalArgumentException("This isn't a Word .docx file.")
        val bulletIds = pack["word/numbering.xml"]?.let { bulletNumIds(it) } ?: emptySet()
        val open = xml.indexOf("<w:body")
        val bodyStart = if (open >= 0) xml.indexOf('>', open) + 1 else -1
        val bodyEnd = xml.lastIndexOf("</w:body>")
        if (bodyStart <= 0 || bodyEnd < bodyStart) {
            // Not laid out the usual way: it can be read, but saving writes a new file.
            return parseBody(xml.toByteArray(Charsets.UTF_8), bulletIds)
        }
        val blocks = Ooxml.elements(xml.substring(bodyStart, bodyEnd))
        // The last section's page settings live at the end of the body and stay there.
        val sect = blocks.lastOrNull()?.takeIf { it.name == "w:sectPr" }
        val paras = (if (sect != null) blocks.dropLast(1) else blocks).map { block(it, bulletIds) }
        return Document(
            paras.ifEmpty { listOf(Para(listOf(Run("")))) },
            Source(pack, xml.substring(0, bodyStart), (sect?.xml ?: "") + xml.substring(bodyEnd)),
        )
    }

    private val PICTURE = Regex("<(w:drawing|w:pict|w:object|mc:AlternateContent|v:shape)\\b")
    private val FIELD = Regex(
        "<(w:hyperlink|w:fldSimple|w:fldChar|w:instrText|w:footnoteReference|w:endnoteReference|w:commentReference|" +
            "w:ins|w:del|w:moveFrom|w:moveTo|w:sdt|m:oMath|m:oMathPara|w:sym|w:smartTag|w:customXml|w:ruby)\\b",
    )
    private val BREAK = Regex("<w:br\\b[^>]*w:type=\"(page|column)\"")

    /** One block of the body: an editable paragraph, or something kept exactly as it is. */
    private fun block(e: Ooxml.Element, bulletIds: Set<String>): Para {
        val reason = when {
            e.name == "w:tbl" -> "Table"
            e.name != "w:p" -> if (Ooxml.text(e.xml).isBlank()) "" else "Content box"
            PICTURE.containsMatchIn(e.xml) -> "Picture"
            FIELD.containsMatchIn(e.xml) -> "Link, field or note"
            BREAK.containsMatchIn(e.xml) -> "Page break"
            else -> null
        }
        if (reason != null) {
            val shown = listOf(Run(if (e.name == "w:tbl") tableText(e.xml) else Ooxml.text(e.xml)))
            return Para(shown, origin = Origin(e.xml, Para(shown, locked = reason)), locked = reason)
        }
        val p = parseBody(e.xml.toByteArray(Charsets.UTF_8), bulletIds).paras.first()
        return p.copy(origin = Origin(e.xml, p))
    }

    /** A table's text, one line per row, cells separated by " | ". */
    private fun tableText(xml: String): String =
        Regex("<w:tr\\b[\\s\\S]*?</w:tr>").findAll(xml).joinToString("\n") { row ->
            Regex("<w:tc\\b[\\s\\S]*?</w:tc>").findAll(row.value).joinToString(" | ") { Ooxml.text(it.value).trim() }
        }

    /** numIds whose first level is a bullet (the rest are numbered lists). */
    private fun bulletNumIds(xml: ByteArray): Set<String> {
        val p = parser(xml)
        val abstractIsBullet = mutableMapOf<String, Boolean>()
        val numToAbstract = mutableMapOf<String, String>()
        var abstractId: String? = null
        var numId: String? = null
        var level: String? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "w:abstractNum" -> abstractId = p.attr("w:abstractNumId")
                "w:lvl" -> level = p.attr("w:ilvl")
                "w:numFmt" -> if (level == "0" && abstractId != null) abstractIsBullet[abstractId!!] = p.attr("w:val") == "bullet"
                "w:num" -> numId = p.attr("w:numId")
                "w:abstractNumId" -> numId?.let { n -> p.attr("w:val")?.let { numToAbstract[n] = it } }
            }
        }
        return numToAbstract.filterValues { abstractIsBullet[it] == true }.keys
    }

    private fun parseBody(xml: ByteArray, bulletIds: Set<String>): Document {
        val p = parser(xml)
        val paras = mutableListOf<Para>()
        var runs = mutableListOf<Run>()
        var style = Style.BODY
        var align = Align.LEFT
        var inPara = false
        var inRun = false
        var inRunProps = false
        var bold = false; var italic = false; var underline = false
        var listNumId: String? = null
        var inText = false
        val text = StringBuilder()

        fun flushRunText() {
            if (text.isNotEmpty()) {
                runs += Run(text.toString(), bold, italic, underline)
                text.clear()
            }
        }

        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "w:p" -> { inPara = true; runs = mutableListOf(); style = Style.BODY; align = Align.LEFT; listNumId = null }
                    "w:pStyle" -> style = styleFor(p.attr("w:val"))
                    "w:numId" -> listNumId = p.attr("w:val")
                    "w:jc" -> align = when (p.attr("w:val")) {
                        "center" -> Align.CENTER
                        "right", "end" -> Align.RIGHT
                        "both", "distribute" -> Align.JUSTIFY
                        else -> Align.LEFT
                    }
                    "w:r" -> { inRun = true; bold = false; italic = false; underline = false }
                    "w:rPr" -> if (inRun) inRunProps = true
                    "w:b" -> if (inRunProps) bold = p.attr("w:val").let { it == null || it == "1" || it == "true" }
                    "w:i" -> if (inRunProps) italic = p.attr("w:val").let { it == null || it == "1" || it == "true" }
                    "w:u" -> if (inRunProps) underline = p.attr("w:val").let { it != null && it != "none" }
                    "w:t" -> inText = true
                    "w:tab" -> if (inRun) text.append('\t')
                    "w:br", "w:cr" -> if (inRun) text.append(' ')
                }
                XmlPullParser.TEXT -> if (inText) text.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "w:t" -> inText = false
                    "w:rPr" -> inRunProps = false
                    "w:r" -> { flushRunText(); inRun = false }
                    "w:p" -> {
                        if (inPara) {
                            val s = if (listNumId != null && listNumId != "0") (if (listNumId in bulletIds) Style.BULLET else Style.NUMBERED) else style
                            paras += Para(merge(runs), s, align)
                        }
                        inPara = false
                    }
                }
            }
        }
        return Document(paras.ifEmpty { listOf(Para(listOf(Run("")))) })
    }

    /** Joins neighbouring runs with the same formatting. */
    private fun merge(runs: List<Run>): List<Run> {
        val out = mutableListOf<Run>()
        for (r in runs) {
            val last = out.lastOrNull()
            if (last != null && last.bold == r.bold && last.italic == r.italic && last.underline == r.underline) out[out.lastIndex] = last.copy(text = last.text + r.text)
            else out += r
        }
        return out.ifEmpty { listOf(Run("")) }
    }

    private fun styleFor(id: String?): Style {
        val s = id.orEmpty().lowercase().replace(" ", "")
        return when {
            s == "title" -> Style.TITLE
            s == "heading1" -> Style.H1
            s.startsWith("heading") -> Style.H2
            // Word's built-in "List Bullet" / "List Number" styles carry their numbering in the style itself.
            s.startsWith("listbullet") -> Style.BULLET
            s.startsWith("listnumber") -> Style.NUMBERED
            else -> Style.BODY
        }
    }

    private fun parser(xml: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(xml.inputStream(), "UTF-8")
    }

    private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

    // ---------------------------------------------------------------- writing

    /** Saves [doc]. An opened file keeps everything that wasn't edited; a new one is written from scratch. */
    fun write(doc: Document, out: OutputStream) {
        val source = doc.source
        out.write(if (source != null) DocxSave.patched(doc, source).toBytes() else fresh(doc))
    }

    private fun fresh(doc: Document): ByteArray {
        val buf = ByteArrayOutputStream()
        ZipOutputStream(buf).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("word/_rels/document.xml.rels", DOC_RELS)
            put("word/styles.xml", STYLES)
            put("word/numbering.xml", NUMBERING)
            put("word/document.xml", documentXml(doc))
        }
        return buf.toByteArray()
    }

    private fun documentXml(doc: Document) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
        for (p in doc.paras) append(paraXml(p, BULLET_NUM, NUMBERED_NUM))
        append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>""")
        append("</w:body></w:document>")
    }

    /** The list numbering ids new lists use: 1 and 2 in a new file; [DocxSave] adds free ones to an opened file. */
    private const val BULLET_NUM = "1"
    private const val NUMBERED_NUM = "2"

    /** pPr children for a paragraph's style, list and alignment. */
    internal fun styleProps(p: Para, bulletNum: String, numberedNum: String): List<Ooxml.Element> = buildList {
        p.style.wordId?.let { add(Ooxml.Element("w:pStyle", """<w:pStyle w:val="$it"/>""")) }
        listProp(p.style, bulletNum, numberedNum)?.let { add(it) }
        alignProp(p.align)?.let { add(it) }
    }

    internal fun listProp(style: Style, bulletNum: String, numberedNum: String): Ooxml.Element? = when (style) {
        Style.BULLET -> bulletNum
        Style.NUMBERED -> numberedNum
        else -> null
    }?.let { Ooxml.Element("w:numPr", """<w:numPr><w:ilvl w:val="0"/><w:numId w:val="$it"/></w:numPr>""") }

    internal fun alignProp(a: Align): Ooxml.Element? = when (a) {
        Align.CENTER -> "center"
        Align.RIGHT -> "right"
        Align.JUSTIFY -> "both"
        Align.LEFT -> null
    }?.let { Ooxml.Element("w:jc", """<w:jc w:val="$it"/>""") }

    /** A whole new paragraph. */
    internal fun paraXml(p: Para, bulletNum: String, numberedNum: String) =
        "<w:p><w:pPr>" + styleProps(p, bulletNum, numberedNum).joinToString("") { it.xml } + "</w:pPr>" + runsXml(p.runs, emptyList()) + "</w:p>"

    /** Runs as Word XML. [base] is run formatting to keep (font, size, colour…); bold / italic / underline come from each run. */
    internal fun runsXml(runs: List<Run>, base: List<Ooxml.Element>) = buildString {
        for (r in runs) {
            if (r.text.isEmpty()) continue
            append("<w:r>")
            val props = base + listOfNotNull(
                if (r.bold) Ooxml.Element("w:b", "<w:b/>") else null,
                if (r.italic) Ooxml.Element("w:i", "<w:i/>") else null,
                if (r.underline) Ooxml.Element("w:u", """<w:u w:val="single"/>""") else null,
            )
            if (props.isNotEmpty()) append("<w:rPr>").append(Ooxml.ordered(props, RUN_ORDER)).append("</w:rPr>")
            // Tabs become real Word tabs; everything else is escaped text.
            r.text.split('\t').forEachIndexed { i, part ->
                if (i > 0) append("<w:tab/>")
                if (part.isNotEmpty()) append("""<w:t xml:space="preserve">""").append(escape(part)).append("</w:t>")
            }
            append("</w:r>")
        }
    }

    /** The order Word requires inside w:rPr. */
    internal val RUN_ORDER = listOf(
        "rStyle", "rFonts", "b", "bCs", "i", "iCs", "caps", "smallCaps", "strike", "dstrike", "outline", "shadow", "emboss",
        "imprint", "noProof", "snapToGrid", "vanish", "webHidden", "color", "spacing", "w", "kern", "position", "sz", "szCs",
        "highlight", "u", "effect", "bdr", "shd", "fitText", "vertAlign", "rtl", "cs", "em", "lang", "eastAsianLayout",
        "specVanish", "oMath", "rPrChange",
    )

    fun escape(s: String) = Ooxml.escape(s)

    internal const val STYLES_PART = "word/styles.xml"
    internal const val NUMBERING_PART = "word/numbering.xml"

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

    private const val DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/></Relationships>"""

    internal const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="80"/></w:pPr><w:rPr><w:sz w:val="56"/><w:szCs w:val="56"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="360" w:after="80"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:color w:val="2F5496"/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="160" w:after="80"/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:color w:val="2F5496"/><w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:ind w:left="720"/><w:contextualSpacing/></w:pPr></w:style></w:styles>"""

    internal const val NUMBERING = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="hybridMultilevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum><w:abstractNum w:abstractNumId="1"><w:multiLevelType w:val="hybridMultilevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num><w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num></w:numbering>"""
}
