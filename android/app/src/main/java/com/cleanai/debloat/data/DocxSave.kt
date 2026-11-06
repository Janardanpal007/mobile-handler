package com.cleanai.debloat.data

import com.cleanai.debloat.data.Docx.Para
import com.cleanai.debloat.data.Docx.Style

/**
 * Saves an opened Word file without losing anything the editor doesn't understand.
 * Every part of the original package is kept. In the body, blocks that weren't edited (and all tables, pictures,
 * links and fields) are written back byte for byte; an edited paragraph keeps its original paragraph settings,
 * bookmarks and font, and only its text, bold / italic / underline, style or alignment change.
 */
internal object DocxSave {
    private const val DOCUMENT_PART = "word/document.xml"
    private const val DOCUMENT_RELS = "word/_rels/document.xml.rels"
    private const val TYPES = "[Content_Types].xml"
    private const val REL_BASE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
    private const val TYPE_BASE = "application/vnd.openxmlformats-officedocument.wordprocessingml."
    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    /** The order Word requires inside w:pPr. */
    private val PARA_ORDER = listOf(
        "pStyle", "keepNext", "keepLines", "pageBreakBefore", "framePr", "widowControl", "numPr", "suppressLineNumbers",
        "pBdr", "shd", "tabs", "suppressAutoHyphens", "kinsoku", "wordWrap", "overflowPunct", "topLinePunct", "autoSpaceDE",
        "autoSpaceDN", "bidi", "adjustRightInd", "snapToGrid", "spacing", "ind", "contextualSpacing", "mirrorIndents",
        "suppressOverlap", "jc", "textDirection", "textAlignment", "textboxTightWrap", "outlineLvl", "divId", "cnfStyle",
        "rPr", "sectPr", "pPrChange",
    )

    /** Run formatting the editor controls itself; everything else in a run's formatting is kept. */
    private val EDITOR_RUN_PROPS = setOf("b", "bCs", "i", "iCs", "u", "rPrChange")

    fun patched(doc: Docx.Document, src: Docx.Source): Ooxml.Package {
        val changes = mutableMapOf<String, String?>()
        val edited = doc.paras.filter { it.locked == null && !it.unchanged }
        // Lists that are new to a paragraph need list definitions this file is sure to have.
        val newLists = edited.any { (it.style == Style.BULLET || it.style == Style.NUMBERED) && it.style != it.origin?.parsed?.style }
        val (bulletNum, numberedNum) = if (newLists) addLists(src.pack, changes) else "1" to "2"
        val newStyles = edited.filter { it.style != it.origin?.parsed?.style }.mapNotNull { it.style.wordId }.toSet()
        addStyles(src.pack, newStyles, changes)

        val body = doc.paras.joinToString("") { p ->
            val origin = p.origin
            when {
                origin != null && (p.locked != null || p.unchanged) -> origin.xml
                origin == null -> Docx.paraXml(p, bulletNum, numberedNum)
                else -> editedPara(p, origin, bulletNum, numberedNum)
            }
        }
        changes[DOCUMENT_PART] = src.head + body + src.tail
        return src.pack.withText(changes)
    }

    /** An edited paragraph: the original's settings, bookmarks and font, with the new text and formatting. */
    private fun editedPara(p: Para, origin: Docx.Origin, bulletNum: String, numberedNum: String): String {
        val open = Ooxml.open(origin.xml).let { if (it.endsWith("/>")) it.dropLast(2).trimEnd() + ">" else it }
        val kids = Ooxml.elements(Ooxml.inner(origin.xml))
        val pPr = kids.firstOrNull { it.name == "w:pPr" }
        val before = pPr?.let { Ooxml.elements(Ooxml.inner(it.xml)) }.orEmpty()
        var props = before
        if (p.style != origin.parsed.style) {
            props = props.filter { it.local != "pStyle" && it.local != "numPr" } +
                listOfNotNull(
                    p.style.wordId?.let { Ooxml.Element("w:pStyle", """<w:pStyle w:val="$it"/>""") },
                    Docx.listProp(p.style, bulletNum, numberedNum),
                )
        }
        if (p.align != origin.parsed.align) props = props.filter { it.local != "jc" } + listOfNotNull(Docx.alignProp(p.align))
        val pPrXml = when {
            props === before -> pPr?.xml.orEmpty()
            props.isEmpty() -> ""
            else -> "<w:pPr>" + Ooxml.ordered(props, PARA_ORDER) + "</w:pPr>"
        }
        // Bookmarks are what cross-references and a table of contents point at; keep them.
        val marks = kids.filter { it.local == "bookmarkStart" || it.local == "bookmarkEnd" }.joinToString("") { it.xml }
        return open + pPrXml + marks + Docx.runsXml(p.runs, baseRunProps(kids)) + "</w:p>"
    }

    /** The formatting of the paragraph's first text run (font, size, colour…), without what the editor sets itself. */
    private fun baseRunProps(kids: List<Ooxml.Element>): List<Ooxml.Element> {
        val run = kids.firstOrNull { it.local == "r" && it.xml.contains("<w:t") } ?: return emptyList()
        val rPr = Ooxml.elements(Ooxml.inner(run.xml)).firstOrNull { it.local == "rPr" } ?: return emptyList()
        return Ooxml.elements(Ooxml.inner(rPr.xml)).filter { it.local !in EDITOR_RUN_PROPS }
    }

    /** Adds a bullet list and a numbered list definition with ids the file doesn't use yet; returns their ids. */
    private fun addLists(pack: Ooxml.Package, changes: MutableMap<String, String?>): Pair<String, String> {
        val existing = pack.text(Docx.NUMBERING_PART)
        val usedAbstract = ids(existing, "w:abstractNumId")
        val usedNum = ids(existing, "w:numId")
        val a1 = free(usedAbstract, 0); val a2 = free(usedAbstract + a1, 0)
        val n1 = free(usedNum, 1); val n2 = free(usedNum + n1, 1)
        val abstracts = abstractNum(a1, "bullet", "•") + abstractNum(a2, "decimal", "%1.")
        val nums = """<w:num w:numId="$n1"><w:abstractNumId w:val="$a1"/></w:num><w:num w:numId="$n2"><w:abstractNumId w:val="$a2"/></w:num>"""
        changes[Docx.NUMBERING_PART] = if (existing != null && existing.contains("</w:numbering>")) {
            // abstractNum elements must come before every num element.
            val firstNum = Regex("<w:num[\\s>]").find(existing)?.range?.first
            val end = Regex("<w:numIdMacAtCleanup|</w:numbering>").find(existing)!!.range.first
            val withNums = existing.substring(0, end) + nums + existing.substring(end)
            val at = firstNum ?: end
            withNums.substring(0, at) + abstracts + withNums.substring(at)
        } else {
            addPart(pack, changes, "numbering.xml", "numbering", "numbering.main+xml")
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering xmlns:w="$W_NS">$abstracts$nums</w:numbering>"""
        }
        return n1.toString() to n2.toString()
    }

    private fun abstractNum(id: Int, format: String, text: String) =
        """<w:abstractNum w:abstractNumId="$id"><w:multiLevelType w:val="hybridMultilevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/>""" +
            """<w:numFmt w:val="$format"/><w:lvlText w:val="$text"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum>"""

    private fun ids(xml: String?, attr: String): Set<Int> =
        Regex("""$attr="(\d+)"""").findAll(xml.orEmpty()).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()

    private fun free(used: Set<Int>, from: Int): Int = generateSequence(maxOf(from, (used.maxOrNull() ?: 0) + 1)) { it + 1 }.first { it !in used }

    /** Makes sure the styles the editor uses (Title, headings, list) are defined in the file. */
    private fun addStyles(pack: Ooxml.Package, needed: Set<String>, changes: MutableMap<String, String?>) {
        if (needed.isEmpty()) return
        val existing = pack.text(Docx.STYLES_PART)
        if (existing == null || !existing.contains("</w:styles>")) {
            addPart(pack, changes, "styles.xml", "styles", "styles+xml")
            changes[Docx.STYLES_PART] = Docx.STYLES
            return
        }
        val ours = Ooxml.elements(Ooxml.inner(Docx.STYLES.substring(Docx.STYLES.indexOf("<w:styles"))))
        val missing = needed.filter { id -> !existing.contains("""w:styleId="$id"""") }
            .mapNotNull { id -> ours.firstOrNull { Ooxml.attr(it.xml, "styleId") == id }?.xml }
        if (missing.isNotEmpty()) changes[Docx.STYLES_PART] = existing.replace("</w:styles>", missing.joinToString("") + "</w:styles>")
    }

    /** Registers a new part of the document: its relationship and its content type. */
    private fun addPart(pack: Ooxml.Package, changes: MutableMap<String, String?>, target: String, relType: String, contentType: String) {
        val rels = changes[DOCUMENT_RELS] ?: pack.text(DOCUMENT_RELS) ?: Ooxml.EMPTY_RELS
        changes[DOCUMENT_RELS] = Ooxml.withRelationship(rels, REL_BASE + relType, target).first
        val types = changes[TYPES] ?: pack.text(TYPES).orEmpty()
        changes[TYPES] = Ooxml.withOverride(types, "word/$target", TYPE_BASE + contentType)
    }
}
