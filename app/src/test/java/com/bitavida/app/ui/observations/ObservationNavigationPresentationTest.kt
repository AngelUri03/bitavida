package com.bitavida.app.ui.observations

import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationResult
import com.bitavida.app.domain.model.ObservationTimePrecision
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ObservationNavigationPresentationTest {
    private fun draft(id: String = "fictional-draft") = ObservationRecord(
        id = id,
        observationId = "OBS-004",
        personUnderCareId = "fictional-person",
        occurredOn = LocalDate.of(2026, 11, 18),
        occurredAt = LocalTime.of(8, 0),
        timePrecision = ObservationTimePrecision.EXACT,
        result = ObservationResult.CHANGES,
        answers = emptyMap(),
        description = "",
        createdAt = LocalDateTime.of(2026, 11, 18, 8, 0),
        actorId = "fictional-caregiver",
    )

    @Test fun registerDestinationIsStableWhileTheCaregiverEditsTheSameDraft() {
        val initial = ObservationsUiState(page = ObservationsPage.REGISTER, draft = draft())
        val edited = initial.copy(draft = initial.draft!!.copy(description = "Descripción ficticia"))
        assertEquals(initial.observationDestination(), edited.observationDestination())
        assertEquals("fictional-draft", initial.observationDestination().identity)
    }

    @Test fun navigationDirectionIsReversibleAndDoesNotAnimateApprovedFollowUp() {
        assertEquals(1, observationNavigationDirection(ObservationsPage.DETAIL, ObservationsPage.REGISTER))
        assertEquals(-1, observationNavigationDirection(ObservationsPage.REGISTER, ObservationsPage.DETAIL))
        assertFalse(observationDestinationAnimates(ObservationsPage.DETAIL, ObservationsPage.FOLLOW_UP))
        assertFalse(observationDestinationAnimates(ObservationsPage.FOLLOW_UP, ObservationsPage.DETAIL))
        assertTrue(observationDestinationAnimates(ObservationsPage.DETAIL, ObservationsPage.HISTORY))
    }

    @Test fun compactChoicesUseBalancedColumnsAndLongAnswersUseHorizontalRail() {
        assertEquals(2, observationChoiceColumns(listOf("Sí", "No"), 320f, 1f))
        assertEquals(3, observationChoiceColumns(
            listOf("Uno", "Dos", "Tres", "Cuatro", "Cinco", "Seis"),
            390f,
            1f,
        ))
        assertEquals(0, observationChoiceColumns(listOf("Uno", "Dos", "Tres"), 300f, 1f))
        assertEquals(3, observationChoiceColumns(listOf("Uno", "Dos", "Tres"), 360f, 1f))
        assertEquals(2, observationChoiceColumns(listOf("Uno", "Dos", "Tres", "Cuatro"), 320f, 1f))
        assertEquals(0, observationChoiceColumns(
            listOf("Registrar lo ocurrido", "No se realizó la revisión"),
            320f,
            1f,
        ))
        assertEquals(0, observationChoiceColumns(
            listOf("Izquierdo", "Derecho", "Ambos", "No aplica"),
            320f,
            2f,
        ))
    }

    @Test fun detailAndHistoryUseTheApprovedPresentationContracts() {
        val detail = projectFile(
            "app/src/main/java/com/bitavida/app/ui/observations/ObservationDetailContent.kt",
        ).readText()
        val history = projectFile(
            "app/src/main/java/com/bitavida/app/ui/observations/ObservationHistoryContent.kt",
        ).readText()
        assertTrue(detail.contains("CurrentIndicationCardFrame("))
        assertFalse(detail.contains("Consultar observación"))
        assertTrue(detail.contains("Fotografía adjunta"))
        assertFalse(history.contains("photoContent"))
        assertTrue(history.contains("Fotografía adjunta"))
        assertTrue(history.contains("ObservationsAction.OpenOccurrence"))
        assertTrue(history.contains("ObservationWeekCalendar("))
        assertTrue(history.contains("observation_choose_date"))
        assertTrue(history.contains("Modifier.fillMaxWidth().testTag(\"observation_choose_date\")"))
    }

    @Test fun productionRegistrationIsAFullScreenDestinationNotAModalSheet() {
        val route = projectFile(
            "app/src/main/java/com/bitavida/app/ui/observations/ObservationsScreen.kt",
        ).readText()
        val capture = projectFile(
            "app/src/main/java/com/bitavida/app/ui/observations/ObservationRegistrationSheet.kt",
        ).readText()
        assertTrue(route.contains("ObservationsPage.REGISTER"))
        assertTrue(route.contains("ObservationRegistrationContent("))
        assertFalse(route.contains("ObservationRegistrationSheet("))
        assertFalse(capture.contains("ModalBottomSheet"))
        assertTrue(capture.contains("Modifier.fillMaxSize().imePadding()"))
        assertFalse(capture.contains("Modifier.fillMaxSize().imePadding().navigationBarsPadding()"))
    }

    private fun projectFile(path: String): File =
        File(path).takeIf { it.isFile } ?: File("..", path)
}
