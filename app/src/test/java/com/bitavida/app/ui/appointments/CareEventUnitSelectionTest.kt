package com.bitavida.app.ui.appointments

import com.bitavida.app.data.appointments.toDomain
import com.bitavida.app.data.appointments.toEntity
import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventDraft
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventStatus
import com.bitavida.app.domain.repository.CareEventActor
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Test data is fictitious; no production clinic or address is fabricated here. */
class CareEventUnitSelectionTest {
    private val unit = ImssUnit(
        id = "unit-example", name = "Unidad ficticia", type = "Unidad médica",
        state = "Estado de ejemplo", municipality = "Municipio de ejemplo",
        address = "Calle de ejemplo 123", alternate = "",
    )
    private val draft = CareEventDraft(
        kind = CareEventKind.APPOINTMENT, title = "Consulta ficticia",
        status = CareEventStatus.CONFIRMED, reference = "Dirección anterior",
    )

    @Test fun selectingOfficialAddressSurvivesPersistenceMapping() {
        val selected = draftWithImssUnit(draft, unit, keepAddress = false)
        assertEquals("IMSS", selected.institution)
        assertEquals(unit.name, selected.location)
        assertEquals(unit.id, selected.verifiedUnitId)
        assertEquals(unit.address, selected.reference)
        val now = LocalDateTime.of(2026, 11, 21, 10, 0)
        val event = CareEvent("event", "person", selected, now, now, 1)
        val actor = CareEventActor("profile", "caregiver", "person", true)
        assertEquals(selected, event.toEntity(actor).toDomain().draft)
    }

    @Test fun addressChoiceProtectsManualChangesAndUpdatesUnitOwnedData() {
        assertEquals(ImssAddressChoice.KEEP_WRITTEN,
            resolveImssAddressChoice("Dirección propia", unit.id, unit.address, unit))
        assertEquals(ImssAddressChoice.ASK,
            resolveImssAddressChoice("Dirección propia", "otra-unidad", "Dirección anterior oficial", unit))
        assertEquals(ImssAddressChoice.USE_UNIT,
            resolveImssAddressChoice("Dirección anterior oficial", "otra-unidad", "Dirección anterior oficial", unit))
        assertEquals(ImssAddressChoice.USE_UNIT,
            resolveImssAddressChoice("", "", null, unit))
    }

    @Test fun sameUnitNumberInDifferentLocalitiesKeepsItsOwnAddress() {
        val catalog = parseImssCatalog(
            ("north|UMF 20|Unidad médica|Estado Norte|Municipio Norte|Calle Norte 10|Norte\n" +
                "south|UMF 20|Unidad médica|Estado Sur|Municipio Sur|Calle Sur 20|Sur\n").reader()
        )
        val north = searchImssUnits(catalog, "UMF 20 Municipio Norte").single()
        val south = searchImssUnits(catalog, "UMF 20 Municipio Sur").single()
        assertEquals("Calle Norte 10", draftWithImssUnit(draft, north, keepAddress = false).reference)
        assertEquals("Calle Sur 20", draftWithImssUnit(draft, south, keepAddress = false).reference)
        assertEquals("north", north.id)
        assertEquals("south", south.id)
    }

    @Test fun manualChoiceAndMissingOfficialAddressNeverInventInformation() {
        assertEquals(draft.reference, draftWithImssUnit(draft, unit, keepAddress = true).reference)
        val noAddress = unit.copy(id = "other-unit", address = null)
        assertEquals("", draftWithImssUnit(draft, noAddress, keepAddress = false).reference)
    }
}
