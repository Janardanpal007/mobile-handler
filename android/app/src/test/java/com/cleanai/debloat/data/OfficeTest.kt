package com.cleanai.debloat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class FormulaTest {
    private fun eval(s: String, deg: Boolean = false) = Formula.eval(s, deg)

    @Test fun precedenceAndBrackets() {
        assertEquals(14.0, eval("2+3*4"), 1e-12)
        assertEquals(20.0, eval("(2+3)*4"), 1e-12)
        assertEquals(-4.0, eval("-2^2"), 1e-12)
        assertEquals(512.0, eval("2^3^2"), 1e-12)
    }

    @Test fun calculatorSymbolsAndImplicitMultiplication() {
        assertEquals(5.0, eval("2×sin(30)+√(16)", deg = true), 1e-12)
        assertEquals(2 * Math.PI, eval("2π"), 1e-12)
        assertEquals(15.0, eval("3(4+1)"), 1e-12)
        assertEquals(120.0, eval("5!"), 1e-12)
        assertEquals(0.5, eval("50%"), 1e-12)
        assertEquals(0.0, eval("sin(180)", deg = true), 1e-12)
        assertEquals(2000.0, eval("2e3"), 1e-12)
        assertEquals(2 * Math.E, eval("2e"), 1e-12)
    }

    @Test fun errors() {
        assertEquals("#DIV/0!", runCatching { eval("1÷0") }.exceptionOrNull()?.message)
        assertEquals("#NAME?", runCatching { eval("foo(2)") }.exceptionOrNull()?.message)
        assertEquals("#NUM!", runCatching { eval("(-1)!") }.exceptionOrNull()?.message)
    }

    @Test fun columnNames() {
        assertEquals("A", Formula.colName(1))
        assertEquals("Z", Formula.colName(26))
        assertEquals("AA", Formula.colName(27))
        assertEquals(28, Formula.colIndex("AB"))
    }
}

class SheetsTest {
    private fun sheet(vararg cells: Pair<String, String>) = SheetData(
        "S",
        cells.associate { (ref, v) ->
            val m = Regex("([A-Z]+)(\\d+)").matchEntire(ref)!!
            CellRef(Formula.colIndex(m.groupValues[1]), m.groupValues[2].toInt()) to v
        },
    )

    @Test fun formulasRangesAndErrors() {
        val s = sheet("A1" to "1200", "A2" to "350.5", "A3" to "=SUM(A1:A2)", "B1" to "=A3*18%", "B2" to "=AVERAGE(A1:A2)",
            "B3" to "=A1/0", "B4" to "=IF(A3>1000,1,0)+ROUND(PI(),2)", "C1" to "=C2", "C2" to "=C1", "D1" to "text", "D2" to "=D1+1")
        val e = Sheets.Evaluator { s.cells[it] }
        fun show(c: Int, r: Int) = e.display(CellRef(c, r))
        assertEquals("1550.5", show(1, 3))
        assertEquals("279.09", show(2, 1))
        assertEquals("775.25", show(2, 2))
        assertEquals("#DIV/0!", show(2, 3))
        assertEquals("4.14", show(2, 4))
        assertEquals("#CYCLE!", show(3, 1))
        assertEquals("#VALUE!", show(4, 2))
    }

    @Test fun xlsxRoundTrip() {
        val s = sheet("A1" to "Item", "B1" to "Cost", "A2" to "Rent & <tax>", "B2" to "1200", "B3" to "=SUM(B1:B2)")
        val out = ByteArrayOutputStream()
        Sheets.writeXlsx(listOf(s, SheetData("Second", mapOf(CellRef(3, 5) to "x"))), out)
        val back = Sheets.readXlsx(out.toByteArray().inputStream())
        assertEquals(listOf("S", "Second"), back.map { it.name })
        assertEquals(s.cells, back[0].cells)
        assertEquals("x", back[1].cells[CellRef(3, 5)])
    }

    @Test fun readsExcelFileFromAnotherApp() {
        val sheets = javaClass.classLoader!!.getResourceAsStream("sample.xlsx")!!.use { Sheets.readXlsx(it) }
        assertEquals(listOf("Budget", "Notes"), sheets.map { it.name })
        val c = sheets[0].cells
        assertEquals("Rent", c[CellRef(1, 2)])
        assertEquals("350.5", c[CellRef(2, 3)])
        assertEquals("=SUM(B2:B3)", c[CellRef(2, 4)])
        assertEquals("1550.5", Sheets.Evaluator { c[it] }.display(CellRef(2, 4)))
        assertEquals("hello", sheets[1].cells[CellRef(3, 5)])
    }

    @Test fun csvQuotesAndSeparators() {
        val csv = "name,note\r\n\"Smith, J\",\"said \"\"hi\"\"\"\r\nA,1\r\n"
        val s = Sheets.readCsv(csv.byteInputStream())
        assertEquals("Smith, J", s.cells[CellRef(1, 2)])
        assertEquals("said \"hi\"", s.cells[CellRef(2, 2)])
        val out = ByteArrayOutputStream()
        Sheets.writeCsv(s, out)
        assertEquals(csv, out.toString(Charsets.UTF_8.name()))
        val semi = Sheets.readCsv("a;b\n1;2\n".byteInputStream())
        assertEquals("2", semi.cells[CellRef(2, 2)])
    }
}

class DocxTest {
    @Test fun roundTripKeepsStylesAndFormatting() {
        val doc = Docx.Document(listOf(
            Docx.Para(listOf(Docx.Run("My title")), Docx.Style.TITLE),
            Docx.Para(listOf(Docx.Run("Plain "), Docx.Run("bold", bold = true), Docx.Run(" & <odd> \"chars\"\tafter tab", italic = true)), align = Docx.Align.CENTER),
            Docx.Para(listOf(Docx.Run("one")), Docx.Style.BULLET),
            Docx.Para(listOf(Docx.Run("two", underline = true)), Docx.Style.NUMBERED),
            Docx.Para(listOf(Docx.Run("Heading")), Docx.Style.H2, Docx.Align.RIGHT),
        ))
        val out = ByteArrayOutputStream()
        Docx.write(doc, out)
        val back = Docx.read(out.toByteArray().inputStream())
        assertEquals(doc.paras, back.paras.map { it.copy(origin = null) })
        assertTrue(back.paras.all { it.unchanged })
        assertEquals(false, back.hasLocked)
    }

    @Test fun readsWordFileFromAnotherApp() {
        val doc = javaClass.classLoader!!.getResourceAsStream("sample.docx")!!.use { Docx.read(it) }
        val p = doc.paras
        assertEquals(Docx.Style.TITLE, p[0].style)
        assertEquals("Quarterly report", p[0].text)
        assertEquals(Docx.Style.H1, p[1].style)
        assertEquals("Sales were strong this quarter.", p[2].text)
        assertTrue(p[2].runs.any { it.bold && it.text == "strong" })
        assertEquals(Docx.Style.BULLET, p[3].style)
        assertEquals(Docx.Style.NUMBERED, p[4].style)
        assertEquals(Docx.Align.CENTER, p[5].align)
        assertEquals("Centred & <special> text", p[5].text)
        // The table is kept as it is and shown locked.
        assertTrue(doc.hasLocked)
    }
}
