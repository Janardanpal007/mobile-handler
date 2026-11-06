package com.cleanai.debloat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocLaunchTest {
    @Test
    fun `picks the editor from the file type`() {
        assertEquals(DocLaunch.Kind.PDF, DocLaunch.kindOf("application/pdf", null))
        assertEquals(DocLaunch.Kind.WORD, DocLaunch.kindOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", null))
        assertEquals(DocLaunch.Kind.EXCEL, DocLaunch.kindOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null))
        assertEquals(DocLaunch.Kind.EXCEL, DocLaunch.kindOf("text/csv", null))
    }

    @Test
    fun `falls back to the file name when the type is vague`() {
        assertEquals(DocLaunch.Kind.WORD, DocLaunch.kindOf("application/octet-stream", "Report.DOCX"))
        assertEquals(DocLaunch.Kind.EXCEL, DocLaunch.kindOf(null, "budget.xlsx"))
        assertEquals(DocLaunch.Kind.PDF, DocLaunch.kindOf(null, "scan.pdf"))
    }

    @Test
    fun `returns null for files no editor handles`() {
        assertNull(DocLaunch.kindOf("application/msword", "old.doc"))
        assertNull(DocLaunch.kindOf("image/png", "photo.png"))
        assertNull(DocLaunch.kindOf(null, null))
    }
}
