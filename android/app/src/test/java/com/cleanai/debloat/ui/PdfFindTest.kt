package com.cleanai.debloat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfFindTest {

    private val pages = listOf(
        listOf("Invoice 2026-11", "Total due:  ₹4,500"),
        emptyList(), // a scanned page has no text
        listOf("Thank you for your business", "total paid"),
    )

    @Test
    fun `finds every line on every page ignoring case`() {
        val hits = findInPages(pages, "total")
        assertEquals(listOf(FindHit(0, "Total due:  ₹4,500"), FindHit(2, "total paid")), hits)
    }

    @Test
    fun `extra spaces in the text or the query still match`() {
        assertEquals(listOf(0), findInPages(pages, "due:   ₹4,500").map { it.page })
        assertEquals(listOf(2), findInPages(pages, "  thank you ").map { it.page })
    }

    @Test
    fun `blank query or no match gives nothing`() {
        assertTrue(findInPages(pages, "   ").isEmpty())
        assertTrue(findInPages(pages, "refund").isEmpty())
    }

    @Test
    fun `stops at the limit`() {
        val many = List(10) { listOf("same line", "same line") }
        assertEquals(5, findInPages(many, "same", limit = 5).size)
    }
}
