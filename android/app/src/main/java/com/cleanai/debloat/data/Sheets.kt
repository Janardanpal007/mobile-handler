package com.cleanai.debloat.data

import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.MathContext
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/** A cell position, 1-based: column 1 = A, row 1 = first row. */
data class CellRef(val col: Int, val row: Int) {
    val name get() = Formula.colName(col) + row
}

/** One sheet: what was typed in each cell (numbers, text, or formulas starting with "="). */
data class SheetData(val name: String, val cells: Map<CellRef, String>)

/**
 * Spreadsheets: reads and writes Excel .xlsx (several sheets) and .csv, and works out formulas.
 * Cell styles, charts and merged cells in an opened file are not kept.
 */
object Sheets {

    // ---------------------------------------------------------------- formulas

    /** Works out every cell of one sheet on demand, remembering results and catching loops (A1 = B1, B1 = A1). */
    class Evaluator(private val raw: (CellRef) -> String?) {
        private val done = mutableMapOf<CellRef, Result<Any?>>()
        private val busy = mutableSetOf<CellRef>()

        /** Double, String, or null (empty). Throws [Formula.Error] for a cell showing an error. */
        fun value(ref: CellRef): Any? {
            done[ref]?.let { return it.getOrThrow() }
            val text = raw(ref)?.takeIf { it.isNotEmpty() } ?: return null
            if (!text.startsWith("=")) return literal(text)
            if (!busy.add(ref)) throw Formula.Error("#CYCLE!")
            val r = runCatching<Any?> {
                Formula.eval(text.drop(1), cells = { c, row -> value(CellRef(c, row)) })
            }.recoverCatching { e -> throw (e as? Formula.Error ?: Formula.Error("#ERROR!")) }
            busy.remove(ref)
            done[ref] = r
            return r.getOrThrow()
        }

        /** What the grid shows for a cell. */
        fun display(ref: CellRef): String = try {
            when (val v = value(ref)) {
                null -> ""
                is Double -> format(v)
                else -> v.toString()
            }
        } catch (e: Formula.Error) {
            e.code
        }
    }

    /** "12" and "3.5" are numbers, "45%" is 0.45, anything else is text. */
    fun literal(text: String): Any {
        val t = text.trim()
        t.toDoubleOrNull()?.let { return it }
        if (t.endsWith("%")) t.dropLast(1).trim().toDoubleOrNull()?.let { return it / 100 }
        return text
    }

    fun format(v: Double): String {
        if (v == Math.rint(v) && abs(v) < 1e15) return v.toLong().toString()
        if (abs(v) >= 1e15 || abs(v) < 1e-9) return String.format(java.util.Locale.ROOT, "%.6E", v)
        return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }

    // ---------------------------------------------------------------- CSV

    fun readCsv(input: InputStream): SheetData {
        val text = input.bufferedReader(Charsets.UTF_8).readText().removePrefix("﻿")
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        // Pick the separator the file actually uses (Excel in many countries saves with ";").
        val sep = listOf(',', ';', '\t').maxByOrNull { c -> firstLine.count { it == c } } ?: ','
        val cells = mutableMapOf<CellRef, String>()
        var row = 1; var col = 1
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endField() {
            if (field.isNotEmpty()) cells[CellRef(col, row)] = field.toString()
            field.clear()
            col++
        }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                !quoted && c == sep -> endField()
                !quoted && (c == '\n' || c == '\r') -> {
                    endField()
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row++; col = 1
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty()) endField()
        return SheetData("Sheet1", cells)
    }

    /** CSV holds values only, so formulas are saved as their results (like Excel does). */
    fun writeCsv(sheet: SheetData, out: OutputStream) {
        val eval = Evaluator { sheet.cells[it] }
        val maxRow = sheet.cells.keys.maxOfOrNull { it.row } ?: 0
        val maxCol = sheet.cells.keys.maxOfOrNull { it.col } ?: 0
        val w = out.bufferedWriter(Charsets.UTF_8)
        for (r in 1..maxRow) {
            w.write((1..maxCol).joinToString(",") { c ->
                val v = eval.display(CellRef(c, r))
                if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
            })
            w.write("\r\n")
        }
        w.flush()
    }

    // ---------------------------------------------------------------- XLSX

    fun readXlsx(input: InputStream): List<SheetData> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name.endsWith(".xml") || e.name.endsWith(".rels")) files[e.name.removePrefix("/")] = zip.readBytes()
            }
        }
        val workbook = files["xl/workbook.xml"] ?: throw IllegalArgumentException("This isn't an Excel .xlsx file.")
        val targets = mutableMapOf<String, String>()
        files["xl/_rels/workbook.xml.rels"]?.let { rels ->
            each(rels) { p ->
                if (p.name.endsWith("Relationship")) {
                    val target = p.attr("Target").orEmpty()
                    targets[p.attr("Id").orEmpty()] = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
                }
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        val sheets = mutableListOf<SheetData>()
        each(workbook) { p ->
            if (p.name.endsWith("sheet") && !p.name.endsWith("sheets")) {
                val name = p.attr("name") ?: "Sheet${sheets.size + 1}"
                val rid = (0 until p.attributeCount).firstOrNull { p.getAttributeName(it).endsWith(":id") }?.let { p.getAttributeValue(it) }
                val path = targets[rid] ?: "xl/worksheets/sheet${sheets.size + 1}.xml"
                files[path]?.let { sheets += SheetData(name, sheetCells(it, shared)) }
            }
        }
        return sheets.ifEmpty { listOf(SheetData("Sheet1", emptyMap())) }
    }

    private fun sharedStrings(xml: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val p = parser(xml)
        val sb = StringBuilder()
        var inT = false
        var inPhonetic = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "si" -> sb.clear()
                    "t" -> inT = true
                    "rPh" -> inPhonetic = true
                }
                XmlPullParser.TEXT -> if (inT && !inPhonetic) sb.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "t" -> inT = false
                    "rPh" -> inPhonetic = false
                    "si" -> out += sb.toString()
                }
            }
        }
        return out
    }

    private val REF = Regex("^([A-Z]+)(\\d+)$")

    private fun sheetCells(xml: ByteArray, shared: List<String>): Map<CellRef, String> {
        val cells = mutableMapOf<CellRef, String>()
        val p = parser(xml)
        var row = 0; var col = 0
        var type: String? = null
        var formula = StringBuilder(); var value = StringBuilder(); var inline = StringBuilder()
        var inF = false; var inV = false; var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "row" -> { row = p.attr("r")?.toIntOrNull() ?: (row + 1); col = 0 }
                    "c" -> {
                        val m = p.attr("r")?.let { REF.matchEntire(it) }
                        if (m != null) { col = Formula.colIndex(m.groupValues[1]); row = m.groupValues[2].toInt() } else col++
                        type = p.attr("t")
                        formula = StringBuilder(); value = StringBuilder(); inline = StringBuilder()
                    }
                    "f" -> inF = true
                    "v" -> inV = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> when {
                    inF -> formula.append(p.text)
                    inV -> value.append(p.text)
                    inT -> inline.append(p.text)
                }
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "f" -> inF = false
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val v = value.toString()
                        val text = when {
                            formula.isNotBlank() -> "=" + formula.toString()
                            type == "s" -> v.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                            type == "inlineStr" -> inline.toString()
                            type == "b" -> if (v == "1") "TRUE" else "FALSE"
                            else -> v
                        }
                        if (text.isNotEmpty() && col > 0 && row > 0) cells[CellRef(col, row)] = text
                    }
                }
            }
        }
        return cells
    }

    fun writeXlsx(sheets: List<SheetData>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val head = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
            put("[Content_Types].xml", head +
                """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                sheets.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } +
                "</Types>")
            put("_rels/.rels", head + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", head +
                """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                sheets.mapIndexed { i, s -> """<sheet name="${Docx.escape(safeSheetName(s.name, i))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") +
                "</sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", head +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                sheets.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } +
                """<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", head + STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", head + sheetXml(s)) }
        }
    }

    private fun safeSheetName(name: String, i: Int) =
        name.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().take(31).ifEmpty { "Sheet${i + 1}" }

    private fun sheetXml(sheet: SheetData) = buildString {
        val eval = Evaluator { sheet.cells[it] }
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        sheet.cells.filterValues { it.isNotEmpty() }.keys.groupBy { it.row }.toSortedMap().forEach { (r, refs) ->
            append("""<row r="$r">""")
            refs.sortedBy { it.col }.forEach { ref ->
                val raw = sheet.cells[ref]!!
                val name = ref.name
                when {
                    raw.startsWith("=") -> {
                        // The formula plus its current result, so other apps show the value straight away.
                        val result = runCatching { eval.value(ref) }
                        val v = result.getOrNull()
                        when {
                            result.isFailure -> append("""<c r="$name" t="e"><f>${Docx.escape(raw.drop(1))}</f><v>${Docx.escape((result.exceptionOrNull() as? Formula.Error)?.code ?: "#VALUE!")}</v></c>""")
                            v is Double -> append("""<c r="$name"><f>${Docx.escape(raw.drop(1))}</f><v>${format(v)}</v></c>""")
                            else -> append("""<c r="$name"><f>${Docx.escape(raw.drop(1))}</f></c>""")
                        }
                    }
                    raw.trim().toDoubleOrNull() != null -> append("""<c r="$name"><v>${raw.trim()}</v></c>""")
                    else -> append("""<c r="$name" t="inlineStr"><is><t xml:space="preserve">${Docx.escape(raw)}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private const val STYLES = """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    // ---------------------------------------------------------------- XML helpers

    private fun parser(xml: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(xml.inputStream(), "UTF-8")
    }

    private fun each(xml: ByteArray, onStart: (XmlPullParser) -> Unit) {
        val p = parser(xml)
        while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG) onStart(p)
    }

    /** "x:c" -> "c": some files prefix every tag. */
    private fun local(name: String) = name.substringAfter(':')

    private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)
}
