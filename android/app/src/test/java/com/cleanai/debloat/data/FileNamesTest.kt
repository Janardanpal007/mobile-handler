package com.cleanai.debloat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNamesTest {
    @Test
    fun `keeps a normal name and drops a typed extension`() {
        assertEquals("Rent agreement", FileNames.clean("  Rent agreement  ", ".pdf"))
        assertEquals("Budget 2026", FileNames.clean("Budget 2026.XLSX", ".xlsx"))
    }

    @Test
    fun `replaces characters files cannot hold`() {
        assertEquals("Q1 Q2 report", FileNames.clean("Q1/Q2:report", ".docx"))
        assertEquals("a b", FileNames.clean("a*?\"<>|b", ".pdf"))
    }

    @Test
    fun `returns null when nothing usable is left`() {
        assertNull(FileNames.clean("   ", ".pdf"))
        assertNull(FileNames.clean("///", ".pdf"))
        assertNull(FileNames.clean(".pdf", ".pdf"))
    }

    @Test
    fun `limits very long names`() {
        assertEquals(80, FileNames.clean("x".repeat(200), ".pdf")!!.length)
    }
}
