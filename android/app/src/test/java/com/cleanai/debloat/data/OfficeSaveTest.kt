package com.cleanai.debloat.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Round trips through real Office files (made with Microsoft Word / Excel): saving must keep everything the
 * editors don't touch. Each test also leaves its output in build/office-check/ so it can be opened in Office.
 */
private fun resource(name: String) = OfficeSaveTest::class.java.classLoader!!.getResourceAsStream(name)!!.readBytes()

private fun parts(bytes: ByteArray) = Ooxml.readPackage(bytes.inputStream()).parts.toMap()

private fun keep(name: String, bytes: ByteArray) {
    File("build/office-check").apply { mkdirs() }.resolve(name).writeBytes(bytes)
}

/** Every XML part must still be well-formed XML. */
private fun assertWellFormed(bytes: ByteArray) {
    parts(bytes).filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (name, xml) ->
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(xml.inputStream(), "UTF-8")
        runCatching { while (p.next() != XmlPullParser.END_DOCUMENT) Unit }.onFailure { throw AssertionError("$name is broken: ${it.message}") }
    }
}

class OfficeSaveTest {

    // ---------------------------------------------------------------- Word

    private fun saveDocx(doc: Docx.Document): ByteArray = ByteArrayOutputStream().also { Docx.write(doc, it) }.toByteArray()

    @Test fun `saving an unedited Word file keeps every part exactly`() {
        val original = resource("rich.docx")
        val saved = saveDocx(Docx.read(original.inputStream()))
        val before = parts(original)
        val after = parts(saved)
        assertEquals(before.keys, after.keys)
        before.forEach { (name, bytes) -> assertArrayEquals(name, bytes, after[name]) }
    }

    @Test fun `tables pictures links and headers are shown locked and kept`() {
        val doc = Docx.read(resource("rich.docx").inputStream())
        val locked = doc.paras.mapNotNull { it.locked }.filter { it.isNotEmpty() }
        assertTrue(locked.toString(), "Table" in locked)
        assertTrue(locked.toString(), "Picture" in locked)
        assertTrue(locked.toString(), "Link, field or note" in locked)
        assertTrue(doc.paras.first { it.locked == "Table" }.text.contains("Paper | 42"))
        assertTrue(doc.hasLocked)
    }

    @Test fun `editing paragraphs keeps the picture table link header and the paragraph's font`() {
        val original = resource("rich.docx")
        val doc = Docx.read(original.inputStream())
        val edited = doc.copy(paras = doc.paras.map { p ->
            when (p.text) {
                "Plain paragraph to edit." -> p.withText("Edited on the phone").withBold(true)
                "Red Arial intro paragraph." -> p.withText("Still red, still Arial")
                else -> p
            }
        })
        val saved = saveDocx(edited)
        keep("edited.docx", saved)
        assertWellFormed(saved)

        val before = parts(original)
        val after = parts(saved)
        for (name in before.keys - "word/document.xml") assertArrayEquals(name, before[name], after[name])
        val xml = after.getValue("word/document.xml").toString(Charsets.UTF_8)
        val oldXml = before.getValue("word/document.xml").toString(Charsets.UTF_8)
        // Tables, pictures and links are written back exactly as they were.
        Ooxml.elements(oldXml.substring(oldXml.indexOf("<w:body>") + 8, oldXml.lastIndexOf("</w:body>")))
            .filter { it.name == "w:tbl" || it.xml.contains("<w:drawing") || it.xml.contains("<w:hyperlink") }
            .forEach { assertTrue("lost ${it.name}", xml.contains(it.xml)) }
        // The red Arial paragraph keeps its font and colour with the new text.
        val red = Ooxml.elements(xml.substring(xml.indexOf("<w:body>") + 8)).first { it.xml.contains("Still red, still Arial") }.xml
        assertTrue(red, red.contains("Arial") && red.contains("FF0000"))

        val back = Docx.read(saved.inputStream())
        val texts = back.paras.map { it.text }
        assertTrue(texts.contains("Edited on the phone"))
        assertTrue(back.paras.first { it.text == "Edited on the phone" }.bold)
        assertEquals(doc.paras.size, back.paras.size)
    }

    @Test fun `a new list in an opened file gets list numbers the file doesn't already use`() {
        val doc = Docx.read(resource("rich.docx").inputStream())
        val withList = doc.copy(paras = doc.paras + Docx.Para(listOf(Docx.Run("New bullet")), Docx.Style.BULLET) +
            Docx.Para(listOf(Docx.Run("New number")), Docx.Style.NUMBERED))
        val saved = saveDocx(withList)
        keep("new-lists.docx", saved)
        assertWellFormed(saved)
        val numbering = parts(saved).getValue("word/numbering.xml").toString(Charsets.UTF_8)
        val oldNumbering = parts(resource("rich.docx")).getValue("word/numbering.xml").toString(Charsets.UTF_8)
        // Every list the file already had is still there, unchanged.
        val inner = { x: String -> Ooxml.elements(x.substring(x.indexOf("<w:numbering"))).first().let { Ooxml.inner(it.xml) } }
        Ooxml.elements(inner(oldNumbering)).forEach { assertTrue(it.name, numbering.contains(it.xml)) }
        val back = Docx.read(saved.inputStream()).paras
        assertEquals(Docx.Style.BULLET, back.first { it.text == "New bullet" }.style)
        assertEquals(Docx.Style.NUMBERED, back.first { it.text == "New number" }.style)
        // The original bullets still read as bullets.
        assertEquals(Docx.Style.BULLET, back.first { it.text == "First bullet" }.style)
    }

    // ---------------------------------------------------------------- Excel

    private fun saveXlsx(book: Workbook): ByteArray = ByteArrayOutputStream().also { Sheets.writeWorkbook(book, it) }.toByteArray()

    @Test fun `saving an unedited Excel file keeps every part exactly`() {
        val original = resource("rich.xlsx")
        val saved = saveXlsx(Sheets.readWorkbook(original.inputStream()))
        val before = parts(original)
        val after = parts(saved)
        assertEquals(before.keys, after.keys)
        before.forEach { (name, bytes) -> assertArrayEquals(name, bytes, after[name]) }
    }

    @Test fun `formulas Excel fills down are read as real formulas`() {
        val cells = Sheets.readWorkbook(resource("rich.xlsx").inputStream()).sheets.first().cells
        assertEquals("=B3*2", cells[CellRef(3, 3)])
        assertEquals("=B4*2", cells[CellRef(3, 4)])
        assertEquals("=B6*2", cells[CellRef(3, 6)])
        assertEquals("=\$A4+D\$2", Sheets.shiftRefs("=\$A1+B\$2", 2, 3))
        assertEquals("=LOG10(B2)&\"A1\"", Sheets.shiftRefs("=LOG10(A1)&\"A1\"", 1, 1))
    }

    @Test fun `editing cells keeps styles merged cells charts and the other sheet`() {
        val original = resource("rich.xlsx")
        val book = Sheets.readWorkbook(original.inputStream())
        val budget = book.sheets[0]
        val cells = budget.cells + (CellRef(1, 1) to "Budget 2027") + (CellRef(2, 3) to "15") + (CellRef(4, 9) to "new text")
        val saved = saveXlsx(Workbook(listOf(budget.copy(cells = cells)) + book.sheets.drop(1), book.source))
        keep("edited.xlsx", saved)
        assertWellFormed(saved)

        val before = parts(original)
        val after = parts(saved)
        val untouched = before.keys - setOf("xl/worksheets/sheet1.xml", "xl/workbook.xml", "xl/calcChain.xml", "[Content_Types].xml", "xl/_rels/workbook.xml.rels")
        untouched.forEach { assertArrayEquals(it, before[it], after[it]) }
        val sheet = after.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        assertTrue(sheet.contains("""<mergeCell ref="A1:C1"/>"""))
        assertTrue(sheet, sheet.contains("""<c r="A1" s="2" t="inlineStr">"""))
        assertTrue(sheet.contains("""<drawing r:id="rId1"/>"""))
        // Excel's list of formula cells is dropped and it recalculates when the file opens.
        assertNull(after["xl/calcChain.xml"])
        assertFalse(after.getValue("[Content_Types].xml").toString(Charsets.UTF_8).contains("calcChain"))
        assertTrue(after.getValue("xl/workbook.xml").toString(Charsets.UTF_8).contains("fullCalcOnLoad=\"1\""))

        val back = Sheets.readWorkbook(saved.inputStream())
        assertEquals(listOf("Budget", "Notes"), back.sheets.map { it.name })
        assertEquals("Budget 2027", back.sheets[0].cells[CellRef(1, 1)])
        assertEquals("new text", back.sheets[0].cells[CellRef(4, 9)])
        assertEquals("30", Sheets.Evaluator { back.sheets[0].cells[it] }.display(CellRef(3, 3)))
        assertEquals("Second sheet stays", back.sheets[1].cells[CellRef(1, 1)])
    }

    @Test fun `changing the first cell of a filled-down formula writes the whole group out`() {
        val book = Sheets.readWorkbook(resource("rich.xlsx").inputStream())
        val budget = book.sheets[0]
        val changed = Workbook(listOf(budget.copy(cells = budget.cells + (CellRef(3, 3) to "=B3*3"))) + book.sheets.drop(1), book.source)
        assertTrue(Sheets.keepsEverything(changed))
        val saved = saveXlsx(changed)
        keep("shared-formula.xlsx", saved)
        assertWellFormed(saved)
        val sheet = parts(saved).getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        assertFalse(sheet.contains("t=\"shared\""))
        val back = Sheets.readWorkbook(saved.inputStream()).sheets[0].cells
        assertEquals("=B3*3", back[CellRef(3, 3)])
        assertEquals("=B5*2", back[CellRef(3, 5)])
        assertTrue(sheet, sheet.contains("""<c r="C5" s="3"><f>B5*2</f><v>60</v></c>"""))
    }

    @Test fun `a sheet added in the app is saved next to the original sheets`() {
        val original = resource("rich.xlsx")
        val book = Sheets.readWorkbook(original.inputStream())
        val added = SheetData("Notes", mapOf(CellRef(1, 1) to "added", CellRef(2, 1) to "=2*21"))
        val saved = saveXlsx(Workbook(book.sheets + added, book.source))
        keep("added-sheet.xlsx", saved)
        assertWellFormed(saved)
        val back = Sheets.readWorkbook(saved.inputStream())
        assertEquals(listOf("Budget", "Notes", "Notes (1)"), back.sheets.map { it.name })
        assertEquals("added", back.sheets[2].cells[CellRef(1, 1)])
        val before = parts(original)
        val after = parts(saved)
        listOf("xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml", "xl/charts/chart1.xml", "xl/styles.xml")
            .forEach { assertArrayEquals(it, before[it], after[it]) }
        assertNotNull(after["xl/worksheets/sheet3.xml"])
    }

    @Test fun `a renamed sheet can't be kept, so the app saves a copy`() {
        val book = Sheets.readWorkbook(resource("rich.xlsx").inputStream())
        val renamed = Workbook(listOf(book.sheets[0].copy(name = "Other")) + book.sheets.drop(1), book.source)
        assertFalse(Sheets.keepsEverything(renamed))
    }
}
