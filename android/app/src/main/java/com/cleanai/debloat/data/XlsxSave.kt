package com.cleanai.debloat.data

/**
 * Saves an opened Excel file without losing what the editor doesn't show: styles, number formats, merged cells,
 * column widths, charts, pictures, chart sheets and everything else stay exactly as they were. Only the cells that
 * were edited are rewritten (keeping each cell's style), and new sheets are added alongside the old ones.
 *
 * When a change can't be made safely (for example, editing part of an array formula, or a sheet that was
 * renamed or removed), [patched] throws [CantKeep] and the app saves a separate copy instead of touching the original.
 */
internal object XlsxSave {
    class CantKeep(message: String) : Exception(message)

    private const val WORKBOOK = "xl/workbook.xml"
    private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"
    private const val CALC_CHAIN = "xl/calcChain.xml"
    private const val TYPES = "[Content_Types].xml"
    private const val HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val WORKSHEET_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
    private const val WORKSHEET_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
    private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    /** Elements that may follow calcPr in workbook.xml; a new calcPr goes before the first of them. */
    private val AFTER_CALC = listOf(
        "oleSize", "customWorkbookViews", "pivotCaches", "smartTagPr", "smartTagTypes", "webPublishing",
        "fileRecoveryPr", "webPublishObjects", "extLst",
    )

    fun patched(sheets: List<SheetData>, src: Sheets.Source): Ooxml.Package {
        val kept = sheets.mapNotNull { it.part }
        if (kept.toSet() != src.original.keys || kept != src.original.keys.toList()) throw CantKeep("Sheets were removed or moved")
        val changes = mutableMapOf<String, String?>()
        var formulas = false
        for (sheet in sheets) {
            val part = sheet.part ?: continue
            val before = src.original.getValue(part)
            if (sheet.name != before.name) throw CantKeep("A sheet was renamed")
            val edited = (before.cells.keys + sheet.cells.keys).filter { before.cells[it].orEmpty() != sheet.cells[it].orEmpty() }
            if (edited.isEmpty()) continue
            // Any edit can change what formulas show (and shared formulas are read as plain values), so recalculate.
            formulas = true
            val xml = src.pack.text(part) ?: throw CantKeep("Missing sheet $part")
            changes[part] = patchSheet(xml, sheet, edited.toSet())
        }
        sheets.filter { it.part == null }.forEach { addSheet(src.pack, changes, it, sheets) }
        if (formulas) recalculateOnOpen(src.pack, changes)
        return src.pack.withText(changes)
    }

    // ---------------------------------------------------------------- cells

    private class Row(val open: String, val cells: MutableList<Ooxml.Element>, val rest: List<Ooxml.Element>, val created: Boolean = false) {
        var touched = false
    }

    private fun patchSheet(xml: String, sheet: SheetData, edited: Set<CellRef>): String {
        val tag = Regex("""<((?:\w+:)?)sheetData(\s[^>]*?)?(/?)>""").find(xml) ?: throw CantKeep("No cells in sheet")
        val px = tag.groupValues[1]
        val empty = tag.groupValues[3] == "/"
        val bodyStart = tag.range.last + 1
        val bodyEnd = if (empty) bodyStart else xml.indexOf("</${px}sheetData>", bodyStart).takeIf { it >= 0 } ?: throw CantKeep("Bad sheet")
        val rows = sortedMapOf<Int, Row>()
        for (e in Ooxml.elements(xml.substring(bodyStart, bodyEnd))) {
            if (e.local != "row") throw CantKeep("Unexpected ${e.name}")
            val r = Ooxml.attr(e.xml, "r")?.toIntOrNull() ?: throw CantKeep("Row without a number")
            val kids = Ooxml.elements(Ooxml.inner(e.xml))
            val cells = kids.filter { it.local == "c" }
            if (cells.any { Ooxml.attr(it.xml, "r") == null }) throw CantKeep("Cell without a reference")
            rows[r] = Row(Ooxml.open(e.xml).let { if (it.endsWith("/>")) it.dropLast(2).trimEnd() + ">" else it }, cells.toMutableList(), kids.filter { it.local != "c" })
        }
        val eval = Sheets.Evaluator { sheet.cells[it] }
        for (ref in withSharedGroups(rows.values.flatMap { it.cells }, sheet, edited)) {
            val row = rows.getOrPut(ref.row) { Row("""<${px}row r="${ref.row}">""", mutableListOf(), emptyList(), created = true) }
            row.touched = true
            val at = row.cells.indexOfFirst { Ooxml.attr(it.xml, "r") == ref.name }
            val old = row.cells.getOrNull(at)
            val style = old?.let { Ooxml.attr(it.xml, "s") }
            val fresh = cellXml(px, ref, style, sheet.cells[ref].orEmpty(), eval)
            when {
                at >= 0 && fresh != null -> row.cells[at] = Ooxml.Element("${px}c", fresh)
                at >= 0 -> row.cells.removeAt(at)
                fresh != null -> {
                    val after = row.cells.indexOfFirst { cellCol(it) > ref.col }
                    row.cells.add(if (after < 0) row.cells.size else after, Ooxml.Element("${px}c", fresh))
                }
            }
        }
        val body = rows.values.filter { it.cells.isNotEmpty() || !it.created }
            .joinToString("") { row ->
                // "spans" is only a hint about which columns are used; drop it where cells changed.
                val open = if (row.touched) Ooxml.setAttr(row.open, "spans", null) else row.open
                open + row.cells.joinToString("") { it.xml } + row.rest.joinToString("") { it.xml } + "</${px}row>"
            }
        val openTag = if (empty) "<${px}sheetData>" else xml.substring(tag.range.first, bodyStart)
        val after = if (empty) xml.substring(bodyStart) else xml.substring(bodyEnd)
        return xml.substring(0, tag.range.first) + openTag + body + (if (empty) "</${px}sheetData>" else "") + after
    }

    private fun formulaTag(cell: String): String? = Regex("""<(?:\w+:)?f\b[^>]*>""").find(cell)?.value

    /**
     * Excel stores a formula filled across cells once, in its first cell, and the other cells point back to it.
     * When that first cell is edited, every cell of the group is written out with its own full formula instead.
     * Array formulas can't be split up like that, so editing one stops the save (the app then saves a copy).
     */
    private fun withSharedGroups(cells: List<Ooxml.Element>, sheet: SheetData, edited: Set<CellRef>): Set<CellRef> {
        val names = edited.associateBy { it.name }
        val out = edited.toMutableSet()
        for (cell in cells) {
            val ref = names[Ooxml.attr(cell.xml, "r")] ?: continue
            val f = formulaTag(cell.xml) ?: continue
            if (Ooxml.attr(f, "ref") == null) continue
            if (Ooxml.attr(f, "t") != "shared") throw CantKeep("${ref.name} is part of an array formula")
            val group = Ooxml.attr(f, "si")
            cells.filter { c -> formulaTag(c.xml)?.let { Ooxml.attr(it, "t") == "shared" && Ooxml.attr(it, "si") == group } == true }
                .forEach { c ->
                    val r = Ooxml.attr(c.xml, "r").orEmpty()
                    val at = CellRef(Formula.colIndex(r.takeWhile { it.isLetter() }), r.dropWhile { it.isLetter() }.toIntOrNull() ?: 0)
                    if (at !in edited && sheet.cells[at]?.startsWith("=") != true) throw CantKeep("Couldn't read the formula in $r")
                    out += at
                }
        }
        return out
    }

    private fun cellCol(cell: Ooxml.Element): Int =
        Formula.colIndex(Ooxml.attr(cell.xml, "r").orEmpty().takeWhile { it.isLetter() })

    /** One cell's XML, keeping its style; null for an empty cell without a style. */
    private fun cellXml(px: String, ref: CellRef, style: String?, raw: String, eval: Sheets.Evaluator): String? {
        val s = style?.let { """ s="$it"""" }.orEmpty()
        val r = """r="${ref.name}"$s"""
        fun esc(t: String) = Ooxml.escape(t)
        return when {
            raw.isEmpty() -> if (style != null) "<${px}c $r/>" else null
            raw.startsWith("=") -> {
                val f = "<${px}f>${esc(raw.drop(1))}</${px}f>"
                val result = runCatching { eval.value(ref) }
                when (val v = result.getOrNull()) {
                    null -> if (result.isFailure) {
                        val code = (result.exceptionOrNull() as? Formula.Error)?.code ?: "#VALUE!"
                        """<${px}c $r t="e">$f<${px}v>${esc(code)}</${px}v></${px}c>"""
                    } else "<${px}c $r>$f</${px}c>"
                    is Double -> "<${px}c $r>$f<${px}v>${Sheets.format(v)}</${px}v></${px}c>"
                    else -> """<${px}c $r t="str">$f<${px}v>${esc(v.toString())}</${px}v></${px}c>"""
                }
            }
            raw.trim().toDoubleOrNull() != null -> "<${px}c $r><${px}v>${raw.trim()}</${px}v></${px}c>"
            else -> """<${px}c $r t="inlineStr"><${px}is><${px}t xml:space="preserve">${esc(raw)}</${px}t></${px}is></${px}c>"""
        }
    }

    // ---------------------------------------------------------------- workbook

    /** Adds a sheet made in the editor as a new part, next to the file's own sheets. */
    private fun addSheet(pack: Ooxml.Package, changes: MutableMap<String, String?>, sheet: SheetData, all: List<SheetData>) {
        val n = generateSequence(1) { it + 1 }.first { pack["xl/worksheets/sheet$it.xml"] == null && "xl/worksheets/sheet$it.xml" !in changes }
        val part = "xl/worksheets/sheet$n.xml"
        changes[part] = HEAD + Sheets.sheetXml(sheet)
        val (rels, id) = Ooxml.withRelationship(changes[WORKBOOK_RELS] ?: pack.text(WORKBOOK_RELS) ?: Ooxml.EMPTY_RELS, WORKSHEET_REL, "worksheets/sheet$n.xml")
        changes[WORKBOOK_RELS] = rels
        changes[TYPES] = Ooxml.withOverride(changes[TYPES] ?: pack.text(TYPES).orEmpty(), part, WORKSHEET_TYPE)

        val book = changes[WORKBOOK] ?: pack.text(WORKBOOK) ?: throw CantKeep("No workbook")
        val close = Regex("""</((?:\w+:)?)sheets>""").find(book) ?: throw CantKeep("No sheet list")
        val px = close.groupValues[1]
        val sheetId = (Regex("""sheetId="(\d+)"""").findAll(book).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0) + 1
        val taken = Regex("""<(?:\w+:)?sheet\s[^>]*>""").findAll(book).mapNotNull { Ooxml.attr(it.value, "name")?.lowercase() }.toSet() +
            all.filter { it !== sheet }.map { it.name.lowercase() }
        val base = Sheets.safeSheetName(sheet.name, all.indexOf(sheet))
        val name = generateSequence(0) { it + 1 }.map { if (it == 0) base else "${base.take(26)} ($it)" }.first { it.lowercase() !in taken }
        val ns = if (book.contains("xmlns:r=\"$R_NS\"")) "" else """ xmlns:r="$R_NS""""
        val entry = """<${px}sheet name="${Ooxml.escape(name)}" sheetId="$sheetId" r:id="$id"$ns/>"""
        changes[WORKBOOK] = book.substring(0, close.range.first) + entry + book.substring(close.range.first)
    }

    /** After formulas change, drop Excel's list of formula cells and ask it to work everything out again on open. */
    private fun recalculateOnOpen(pack: Ooxml.Package, changes: MutableMap<String, String?>) {
        if (pack[CALC_CHAIN] != null) {
            changes[CALC_CHAIN] = null
            changes[TYPES] = Ooxml.withoutOverride(changes[TYPES] ?: pack.text(TYPES).orEmpty(), CALC_CHAIN)
            changes[WORKBOOK_RELS] = Ooxml.withoutRelationship(changes[WORKBOOK_RELS] ?: pack.text(WORKBOOK_RELS).orEmpty(), "calcChain.xml")
        }
        val book = changes[WORKBOOK] ?: pack.text(WORKBOOK) ?: return
        val calc = Regex("""<((?:\w+:)?)calcPr\b[^>]*>""").find(book)
        changes[WORKBOOK] = if (calc != null) {
            book.replaceRange(calc.range, Ooxml.setAttr(calc.value, "fullCalcOnLoad", "1"))
        } else {
            val px = Regex("""<((?:\w+:)?)workbook\b""").find(book)?.groupValues?.get(1).orEmpty()
            val at = (AFTER_CALC.map { "<$px$it" } + "</${px}workbook>").mapNotNull { t -> book.indexOf(t).takeIf { it >= 0 } }.minOrNull() ?: return
            book.substring(0, at) + """<${px}calcPr fullCalcOnLoad="1"/>""" + book.substring(at)
        }
    }
}
