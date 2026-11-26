package com.bitavida.app.ui.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.education.ProcedureCategory
import org.junit.Assert.*
import org.junit.Test

class ProcedureLibraryFilterTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun blankSearchShowsEveryGuideOnce() {
        val results = filterProcedureLibrary(guides, emptyList(), "", null)
        assertEquals(guides.size, results.size)
        assertEquals(results.size, results.map { it.id }.distinct().size)
    }

    @Test fun categorySearchIgnoresAccentsAndIncludesCrossListedEntries() {
        val results = filterProcedureLibrary(guides, emptyList(), "rinones", null)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { ProcedureCategory.KIDNEYS in it.categories })
    }

    @Test fun categoryFilterRetainsCrossListedGuideWithoutDuplicatingIt() {
        val vitrectomy = guides.single { it.id == "vitrectomia" }
        val results = filterProcedureLibrary(guides, listOf(vitrectomy), "cirugias", ProcedureCategory.SURGERY)
        assertEquals(1, results.count { it.id == vitrectomy.id })
    }
}
