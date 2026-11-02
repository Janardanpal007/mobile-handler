package com.cleanai.debloat.data

import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A small, self-contained Word (.docx) reader and writer.
 * It keeps paragraphs, headings, bullet / numbered lists, alignment, and bold / italic / underline text.
 * Tables are read as plain paragraphs; pictures, headers and footers are not kept (the editor warns about this).
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

    data class Para(val runs: List<Run>, val style: Style = Style.BODY, val align: Align = Align.LEFT) {
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

    data class Document(val paras: List<Para>, val lostContent: Boolean = false)

    // ---------------------------------------------------------------- reading

    fun read(input: InputStream): Document {
        var body: ByteArray? = null
        var numbering: ByteArray? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when (e.name) {
                    "word/document.xml" -> body = zip.readBytes()
                    "word/numbering.xml" -> numbering = zip.readBytes()
                }
            }
        }
        val xml = body ?: throw IllegalArgumentException("This isn't a Word .docx file.")
        val bulletIds = numbering?.let { bulletNumIds(it) } ?: emptySet()
        return parseBody(xml, bulletIds)
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
        var lost = false
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
                    "w:drawing", "w:pict", "w:object", "w:tbl", "w:headerReference", "w:footerReference", "w:footnoteReference" -> lost = true
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
        return Document(paras.ifEmpty { listOf(Para(listOf(Run("")))) }, lost)
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

    fun write(doc: Document, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
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
    }

    private fun documentXml(doc: Document) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
        for (p in doc.paras) {
            append("<w:p><w:pPr>")
            p.style.wordId?.let { append("""<w:pStyle w:val="$it"/>""") }
            when (p.style) {
                Style.BULLET -> append("""<w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr>""")
                Style.NUMBERED -> append("""<w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr>""")
                else -> {}
            }
            when (p.align) {
                Align.CENTER -> append("""<w:jc w:val="center"/>""")
                Align.RIGHT -> append("""<w:jc w:val="right"/>""")
                Align.JUSTIFY -> append("""<w:jc w:val="both"/>""")
                Align.LEFT -> {}
            }
            append("</w:pPr>")
            for (r in p.runs) {
                if (r.text.isEmpty()) continue
                append("<w:r>")
                if (r.bold || r.italic || r.underline) {
                    append("<w:rPr>")
                    if (r.bold) append("<w:b/>")
                    if (r.italic) append("<w:i/>")
                    if (r.underline) append("""<w:u w:val="single"/>""")
                    append("</w:rPr>")
                }
                // Tabs become real Word tabs; everything else is escaped text.
                r.text.split('\t').forEachIndexed { i, part ->
                    if (i > 0) append("<w:tab/>")
                    if (part.isNotEmpty()) append("""<w:t xml:space="preserve">""").append(escape(part)).append("</w:t>")
                }
                append("</w:r>")
            }
            append("</w:p>")
        }
        append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>""")
        append("</w:body></w:document>")
    }

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

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

    private const val DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="80"/></w:pPr><w:rPr><w:sz w:val="56"/><w:szCs w:val="56"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="360" w:after="80"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:color w:val="2F5496"/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="160" w:after="80"/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:color w:val="2F5496"/><w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:ind w:left="720"/><w:contextualSpacing/></w:pPr></w:style></w:styles>"""

    private const val NUMBERING = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="hybridMultilevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum><w:abstractNum w:abstractNumId="1"><w:multiLevelType w:val="hybridMultilevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num><w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num></w:numbering>"""
}
