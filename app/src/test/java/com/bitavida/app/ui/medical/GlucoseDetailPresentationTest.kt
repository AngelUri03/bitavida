package com.bitavida.app.ui.medical

import com.bitavida.app.domain.model.MeasurementFollowUp
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.MeasurementSummary
import com.bitavida.app.ui.measurements.GlucoseDetailMode
import com.bitavida.app.ui.measurements.MeasurementFollowUpAction
import com.bitavida.app.ui.measurements.glucoseDetailPresentation
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseDetailPresentationTest {
    private val kind = MeasurementKind.CAPILLARY_GLUCOSE
    private val time = LocalDateTime.of(2026, 11, 18, 14, 15)
    private val record = MeasurementRecord("fictitious", MeasurementValue.Glucose("128"), time, time)
    private val plan = MeasurementFollowUp(kind, "Frecuencia ficticia")

    @Test
    fun fourCombinationsKeepCaptureAndManagementIndependent() {
        val scenarios = listOf(
            Triple(false, false, GlucoseDetailMode.EMPTY_WITHOUT_FOLLOW_UP),
            Triple(false, true, GlucoseDetailMode.EMPTY_WITH_FOLLOW_UP),
            Triple(true, false, GlucoseDetailMode.RECORDED_WITHOUT_FOLLOW_UP),
            Triple(true, true, GlucoseDetailMode.RECORDED_WITH_FOLLOW_UP),
        )
        scenarios.forEach { (hasRecords, hasPlan, mode) ->
            val (
                actualMode,
                registerLabel,
                showIndication,
                showTodayRegister,
                managementActions,
                showHelp,
                showSuspend,
            ) = glucoseDetailPresentation(
                MeasurementSummary(
                    kind,
                    if (hasRecords) record else null,
                    if (hasPlan) plan else null,
                ),
            )
            assertEquals(mode, actualMode)
            assertEquals(
                if (hasRecords) {
                    "Registrar medición"
                } else {
                    "Registrar primera medición"
                },
                registerLabel,
            )
            assertEquals(hasPlan, showIndication)
            assertEquals(hasPlan, showTodayRegister)
            assertEquals(hasPlan, showSuspend)
            assertFalse(showHelp)
            assertEquals(
                if (hasPlan) listOf(
                    MeasurementFollowUpAction.CORRECT,
                    MeasurementFollowUpAction.CHANGE_INDICATIONS,
                    MeasurementFollowUpAction.VIEW_CHANGES,
                ) else listOf(MeasurementFollowUpAction.CONFIGURE),
                managementActions,
            )
        }
    }

    @Test
    fun pressureHistoryRemainsAvailableBeforeAnyActivePlan() {
        val (
            _,
            _,
            showIndication,
            showTodayRegister,
            managementActions,
            showHelp,
            showSuspend,
        ) = glucoseDetailPresentation(
            MeasurementSummary(MeasurementKind.BLOOD_PRESSURE, null, null),
            hasFollowUpHistory = true,
        )
        assertFalse(showIndication)
        assertFalse(showSuspend)
        assertFalse(showTodayRegister)
        assertFalse(showHelp)
        assertEquals(
            listOf(
                MeasurementFollowUpAction.CONFIGURE,
                MeasurementFollowUpAction.VIEW_CHANGES,
            ),
            managementActions,
        )
    }

    @Test
    fun suspendedPlanKeepsIndicationAndHistoryWithoutOfferingAnotherSuspension() {
        val (
            _,
            registerLabel,
            showIndication,
            showTodayRegister,
            managementActions,
            showHelp,
            showSuspend,
        ) = glucoseDetailPresentation(
            MeasurementSummary(kind, record, plan.copy(suspended = true)),
        )
        assertTrue(showIndication)
        assertFalse(showSuspend)
        assertFalse(showTodayRegister)
        assertFalse(showHelp)
        assertTrue(MeasurementFollowUpAction.VIEW_CHANGES in managementActions)
        assertEquals("Registrar medición", registerLabel)
    }
}
