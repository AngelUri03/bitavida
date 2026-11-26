package com.bitavida.app.ui.appointments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.io.File

class ImssCatalogTest {
    private val units = parseImssCatalog(StringReader("""
        # Datos ficticios para pruebas.
        a|UMF 25 Río Claro|Unidad de medicina familiar|México|Nezahualcóyotl||Río Claro
        b|Hospital General Regional 1|Hospital general regional|Ciudad de México|Cuauhtémoc|Calle ficticia 1|Siglo XXI
        c|UMF 25 Sol|Unidad de medicina familiar|Jalisco|Guadalajara||
        a|Duplicado|Unidad médica|México|Otra||
    """.trimIndent()))

    @Test fun parsesAndPreservesSeparateUnitsWithSameNumber() {
        assertEquals(listOf("a", "b", "c"), units.map { it.id })
        assertEquals(2, searchImssUnits(units, "25").size)
    }

    @Test fun searchesIgnoringAccentsAndRecognizesClinicAbbreviation() {
        assertEquals("a", searchImssUnits(units, "clinica 25 nezahualcoyotl").single().id)
        assertEquals("a", searchImssUnits(units, "RIO CLARO").single().id)
        assertEquals(setOf("a", "c"), searchImssUnits(units, "umf 025").map { it.id }.toSet())
    }

    @Test fun bundledCatalogHasTheOfficialAddressForTheSelectedUnit() {
        val path = "src/main/res/raw/imss_units.txt"
        val file = listOf(File(path), File("app", path)).first { it.isFile }
        val catalog = file.reader(Charsets.UTF_8).use(::parseImssCatalog)
        val found = searchImssUnits(catalog, "clinica 92 azteca").single()
        assertEquals("imss-umf-15-54-92-15-033", found.id)
        assertEquals("Ecatepec de Morelos", found.municipality)
        assertEquals("Avenida Carlos Hank González s/n, col. Laderas del Peñón, Ecatepec de Morelos, C.P. 55135, México", found.address)
        assertEquals(null, catalog.single { it.id == "imss-umf-15-54-191-15-033" }.address)
    }

    @Test fun searchesHospitalsAndLocalityWithoutInventingMissingAddress() {
        assertEquals("b", searchImssUnits(units, "hospital cuauhtemoc").single().id)
        assertEquals("b", searchImssUnits(units, "siglo xxi").single().id)
        assertEquals(null, units.first().address)
        assertTrue(searchImssUnits(units, "").isEmpty())
    }
}
