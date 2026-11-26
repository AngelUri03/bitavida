package com.bitavida.app.ui.demo

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.MedicationOccurrenceTiming
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime

internal val todayPreviewNow = LocalDateTime.of(2026, 11, 20, 10, 0)

/** Fictitious presentation only; runtime always receives the persisted modules' projections. */
internal fun todayPreviewProjection(multiple: Boolean = true): TodayCareProjection {
    val medication = TodayMedicationItem(UpcomingMedicationAdministration(
        id = "preview-dose", medicationPlanId = "preview-plan", medicationPlanRevisionId = "preview-revision",
        administrationId = "preview-administration", medicationName = "Medicamento ficticio",
        form = MedicationProductForm.TABLET, amount = MedicationAmount("1", "tableta"),
        timing = MedicationOccurrenceTiming.TIME_WINDOW,
        startsAt = todayPreviewNow.minusHours(1), endsAt = todayPreviewNow.plusHours(1),
        activityRelation = null, customActivity = null,
    ))
    val measurement = TodayMeasurementItem(MeasurementKind.CAPILLARY_GLUCOSE, GlucoseScheduledOccurrence(
        id = "preview-reading", followUpId = "preview-follow-up", followUpRevisionId = "preview-follow-revision",
        slotId = "preview-slot", timingType = GlucoseFollowUpTimingType.EXACT_TIME,
        scheduledAt = todayPreviewNow.plusHours(1), suggestedContext = GlucoseContext.BEFORE_MEAL,
        reminderAt = null,
    ))
    val observation = ObservationOccurrence(
        id = "preview-observation", observationId = "OBS-001", followUpId = "preview-observation-follow-up",
        followUpRevisionId = "preview-observation-revision", slotId = "preview-observation-slot",
        scheduledAt = todayPreviewNow.minusMinutes(15), state = ObservationOccurrenceState.UNCONFIRMED,
    )
    val reminder = CareReminder(
        id = "preview-reminder", personUnderCareId = "preview-person", title = "Preparar documentos",
        details = null, scheduledAt = todayPreviewNow.plusHours(2), notificationsEnabled = false,
        status = CareReminderStatus.PENDING, createdAt = todayPreviewNow.minusDays(1),
        modifiedAt = todayPreviewNow.minusDays(1), closedAt = null, revision = 1,
    )
    return TodayCareProjection(
        medications = if (multiple) (0..3).map { index ->
            medication.copy(occurrence = medication.occurrence.copy(
                id = medication.id + index, medicationName = "Medicamento ficticio " + (index + 1),
                startsAt = todayPreviewNow.plusHours(index.toLong()), endsAt = null,
                timing = MedicationOccurrenceTiming.EXACT_TIME,
            ))
        } else listOf(medication),
        measurements = listOf(measurement), observations = listOf(observation),
        reminders = listOf(reminder), hasScheduledActivities = true,
    )
}

internal fun todayPreviewNotes() = listOf(
    Note("preview-note-one", "preview-person", "Para la salida familiar",
        "Llevar el libro y los objetos personales que acordamos.", null,
        todayPreviewNow, todayPreviewNow, 1),
    Note("preview-note-two", "preview-person", "Contacto de apoyo",
        "La familia confirmó que podemos coordinar la visita.", null,
        todayPreviewNow, todayPreviewNow, 1),
)

@Preview(name = "Operativo · Claro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844)
@Preview(name = "Operativo · Oscuro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Operativo · Compacto", showBackground = true, locale = "es", widthDp = 320, heightDp = 720)
@Preview(name = "Operativo · Texto 200 %", showBackground = true, locale = "es", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
private fun TodayOperationalPreview() {
    BitavidaTheme {
        TodayDemoScreen(
            caregiverDisplayName = "Cuidadora ficticia", personUnderCareDisplayName = "Persona ficticia",
            currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
            projection = todayPreviewProjection(), notes = todayPreviewNotes(), motionEnabledOverride = false,
        )
    }
}

/**
 * Stable complete-screen anchors for the one-item-per-module fixture.
 * Header=0; medication heading/body=1/2; measurement=3/4; observation=5/6;
 * reminder=7/8; notes=9. Each preview keeps the real screen, thread and navigation.
 */
private enum class TodayPreviewSection(val firstVisibleItemIndex: Int) {
    MEDICATIONS(1), MEASUREMENTS(3), OBSERVATIONS(5), REMINDERS(7), NOTES(9),
}

@Composable
private fun TodayModuleContext(section: TodayPreviewSection) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = section.firstVisibleItemIndex)
    BitavidaTheme {
        TodayDemoScreen(
            caregiverDisplayName = "Cuidadora ficticia",
            personUnderCareDisplayName = "Persona ficticia",
            currentDateTime = todayPreviewNow,
            state = TodayDemoState.HasPlan,
            projection = todayPreviewProjection(multiple = false),
            notes = todayPreviewNotes(),
            listState = listState,
            motionEnabledOverride = false,
        )
    }
}

@Preview(name = "Medicamentos en contexto · Claro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844)
@Preview(name = "Medicamentos en contexto · Oscuro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Medicamentos en contexto · Compacto", showBackground = true, locale = "es", widthDp = 320, heightDp = 720)
@Preview(name = "Medicamentos en contexto · Texto 200 %", showBackground = true, locale = "es", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
internal fun TodayMedicationsContextPreview() = TodayModuleContext(TodayPreviewSection.MEDICATIONS)

@Preview(name = "Mediciones en contexto · Claro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844)
@Preview(name = "Mediciones en contexto · Oscuro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Mediciones en contexto · Compacto", showBackground = true, locale = "es", widthDp = 320, heightDp = 720)
@Preview(name = "Mediciones en contexto · Texto 200 %", showBackground = true, locale = "es", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
internal fun TodayMeasurementsContextPreview() = TodayModuleContext(TodayPreviewSection.MEASUREMENTS)

@Preview(name = "Observaciones en contexto · Claro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844)
@Preview(name = "Observaciones en contexto · Oscuro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Observaciones en contexto · Compacto", showBackground = true, locale = "es", widthDp = 320, heightDp = 720)
@Preview(name = "Observaciones en contexto · Texto 200 %", showBackground = true, locale = "es", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
internal fun TodayObservationsContextPreview() = TodayModuleContext(TodayPreviewSection.OBSERVATIONS)

@Preview(name = "Recordatorios en contexto · Claro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844)
@Preview(name = "Recordatorios en contexto · Oscuro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Recordatorios en contexto · Compacto", showBackground = true, locale = "es", widthDp = 320, heightDp = 720)
@Preview(name = "Recordatorios en contexto · Texto 200 %", showBackground = true, locale = "es", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
internal fun TodayRemindersContextPreview() = TodayModuleContext(TodayPreviewSection.REMINDERS)

@Preview(name = "Notas en contexto · Claro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844)
@Preview(name = "Notas en contexto · Oscuro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Notas en contexto · Compacto", showBackground = true, locale = "es", widthDp = 320, heightDp = 720)
@Preview(name = "Notas en contexto · Texto 200 %", showBackground = true, locale = "es", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
internal fun TodayNotesContextPreview() = TodayModuleContext(TodayPreviewSection.NOTES)

/** Bounded fictitious scenarios for the complete continuous-thread surface, never runtime data. */
internal enum class TodayThreadPreviewCase {
    ALL_CATEGORIES, MEDICATIONS_ONLY, WITHOUT_MEDICATIONS, MANY_OBSERVATIONS,
    ONE_EXPANDED, MULTIPLE_EXPANDED, RESOLVED_MEDICATION_MODULE, EMPTY_WITH_NOTES,
}

internal data class TodayThreadPreviewFixture(
    val projection: TodayCareProjection,
    val notes: List<Note> = todayPreviewNotes(),
    val expandedSections: Set<String> = emptySet(),
    val initialKey: String = "header",
) {
    /** Mirrors stable presentation keys, rather than assuming a fixed number of preceding rows. */
    fun initialItemIndex(): Int {
        val keys = buildList {
            add("header")
            if (projection.pendingCount == 0) add("clear_day")
            fun section(module: String, heading: String, rowKeys: List<String>) {
                if (rowKeys.isEmpty()) return
                add(heading)
                addAll(if (module in expandedSections) rowKeys else rowKeys.take(3))
                if (rowKeys.size > 3) add("more_" + module)
            }
            section("medications", "medication_heading", projection.medications.map { "medication:" + it.id })
            section("measurements", "measurement_heading", projection.measurements.map { "measurement:" + it.id })
            section("observations", "observation_heading", projection.observations.map { "observation:" + it.id })
            section("reminders", "reminder_heading", projection.reminders.map { "reminder:" + it.id })
            if (notes.isNotEmpty()) add("notes")
            add("thread_end")
        }
        return keys.indexOf(initialKey).coerceAtLeast(0)
    }
}

internal fun todayThreadPreviewFixture(case: TodayThreadPreviewCase): TodayThreadPreviewFixture {
    val base = todayPreviewProjection(multiple = false)
    fun medications(count: Int) = List(count) { index ->
        val source = base.medications.single().occurrence
        TodayMedicationItem(source.copy(
            id = "thread-fictional-dose-$index",
            medicationName = "Medicamento ficticio " + (index + 1),
            timing = MedicationOccurrenceTiming.EXACT_TIME,
            startsAt = todayPreviewNow.plusMinutes(index * 40L),
            endsAt = null,
        ))
    }
    fun measurements(count: Int) = List(count) { index ->
        base.measurements.single().copy(occurrence = base.measurements.single().occurrence.copy(
            id = "thread-fictional-measurement-$index",
            slotId = "thread-fictional-measurement-slot-$index",
            scheduledAt = todayPreviewNow.plusMinutes(index * 35L),
        ))
    }
    fun observations(count: Int) = List(count) { index ->
        base.observations.single().copy(
            id = "thread-fictional-observation-$index",
            slotId = "thread-fictional-observation-slot-$index",
            scheduledAt = todayPreviewNow.plusMinutes(index * 30L),
        )
    }
    fun reminders(count: Int) = List(count) { index ->
        base.reminders.single().copy(
            id = "thread-fictional-reminder-$index",
            title = "Preparar documentos ficticios " + (index + 1),
            scheduledAt = todayPreviewNow.plusMinutes(index * 25L),
        )
    }
    return when (case) {
        TodayThreadPreviewCase.ALL_CATEGORIES -> TodayThreadPreviewFixture(base)
        TodayThreadPreviewCase.MEDICATIONS_ONLY -> TodayThreadPreviewFixture(
            base.copy(medications = medications(4), measurements = emptyList(),
                observations = emptyList(), reminders = emptyList()),
            notes = emptyList(),
        )
        TodayThreadPreviewCase.WITHOUT_MEDICATIONS -> TodayThreadPreviewFixture(
            base.copy(medications = emptyList(), measurements = measurements(3), observations = observations(2)),
        )
        TodayThreadPreviewCase.MANY_OBSERVATIONS -> TodayThreadPreviewFixture(
            base.copy(medications = emptyList(), measurements = emptyList(), observations = observations(8)),
            initialKey = "observation_heading",
        )
        TodayThreadPreviewCase.ONE_EXPANDED -> {
            val medicationRows = medications(8)
            TodayThreadPreviewFixture(
                base.copy(medications = medicationRows),
                expandedSections = setOf("medications"),
                initialKey = "medication:" + medicationRows.last().id,
            )
        }
        TodayThreadPreviewCase.MULTIPLE_EXPANDED -> TodayThreadPreviewFixture(
            base.copy(medications = medications(5), measurements = measurements(4),
                observations = observations(8), reminders = reminders(4)),
            expandedSections = setOf("medications", "measurements", "observations", "reminders"),
            initialKey = "more_measurements",
        )
        TodayThreadPreviewCase.RESOLVED_MEDICATION_MODULE -> TodayThreadPreviewFixture(
            base.copy(medications = emptyList()),
        )
        TodayThreadPreviewCase.EMPTY_WITH_NOTES -> TodayThreadPreviewFixture(TodayCareProjection())
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "320 · Claro", showBackground = true, locale = "es", widthDp = 320, heightDp = 780)
@Preview(name = "320 · Oscuro", showBackground = true, locale = "es", widthDp = 320, heightDp = 780,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "200 % · Claro", showBackground = true, locale = "es", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Preview(name = "200 % · Oscuro", showBackground = true, locale = "es", widthDp = 320, heightDp = 1100, fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class TodayThreadScenarioPreviews

@Composable
private fun TodayThreadScenario(case: TodayThreadPreviewCase) {
    val fixture = todayThreadPreviewFixture(case)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = fixture.initialItemIndex())
    BitavidaTheme {
        TodayDemoScreen(
            caregiverDisplayName = "Cuidadora ficticia", personUnderCareDisplayName = "Persona ficticia",
            currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
            projection = fixture.projection, notes = fixture.notes,
            initialExpandedSections = fixture.expandedSections,
            listState = listState, motionEnabledOverride = false,
        )
    }
}

@TodayThreadScenarioPreviews
@Composable
internal fun TodayThreadAllCategoriesPreview() = TodayThreadScenario(TodayThreadPreviewCase.ALL_CATEGORIES)

@TodayThreadScenarioPreviews
@Composable
internal fun TodayThreadMedicationsOnlyPreview() = TodayThreadScenario(TodayThreadPreviewCase.MEDICATIONS_ONLY)

@TodayThreadScenarioPreviews
@Composable
internal fun TodayThreadWithoutMedicationsPreview() = TodayThreadScenario(TodayThreadPreviewCase.WITHOUT_MEDICATIONS)

@TodayThreadScenarioPreviews
@Composable
internal fun TodayThreadManyObservationsPreview() = TodayThreadScenario(TodayThreadPreviewCase.MANY_OBSERVATIONS)

@TodayThreadScenarioPreviews
@Composable
internal fun TodayThreadOneExpandedPreview() = TodayThreadScenario(TodayThreadPreviewCase.ONE_EXPANDED)

@TodayThreadScenarioPreviews
@Composable
internal fun TodayThreadMultipleExpandedPreview() = TodayThreadScenario(TodayThreadPreviewCase.MULTIPLE_EXPANDED)

@TodayThreadScenarioPreviews
@Composable
internal fun TodayThreadResolvedCategoryPreview() = TodayThreadScenario(TodayThreadPreviewCase.RESOLVED_MEDICATION_MODULE)

@TodayThreadScenarioPreviews
@Composable
internal fun TodayThreadEmptyWithNotesPreview() = TodayThreadScenario(TodayThreadPreviewCase.EMPTY_WITH_NOTES)

@Preview(name = "Una expandida · Horizontal claro", showBackground = true, locale = "es", widthDp = 844, heightDp = 390)
@Preview(name = "Una expandida · Horizontal oscuro", showBackground = true, locale = "es", widthDp = 844, heightDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun TodayThreadOneExpandedLandscapePreview() = TodayThreadScenario(TodayThreadPreviewCase.ONE_EXPANDED)

@Preview(name = "Varias expandidas · Horizontal claro", showBackground = true, locale = "es", widthDp = 844, heightDp = 390)
@Preview(name = "Varias expandidas · Horizontal oscuro", showBackground = true, locale = "es", widthDp = 844, heightDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun TodayThreadMultipleExpandedLandscapePreview() = TodayThreadScenario(TodayThreadPreviewCase.MULTIPLE_EXPANDED)

@Preview(name = "Día al corriente · Claro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844)
@Preview(name = "Día al corriente · Oscuro", showBackground = true, locale = "es", widthDp = 390, heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayCaughtUpPreview() {
    BitavidaTheme {
        TodayDemoScreen(
            currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
            projection = TodayCareProjection(hasScheduledActivities = true), notes = todayPreviewNotes(),
            motionEnabledOverride = false,
        )
    }
}

@Preview(name = "Toma confirmada · Deshacer", showBackground = true, locale = "es", widthDp = 390, heightDp = 844)
@Preview(name = "Toma confirmada · Texto 200 %", showBackground = true, locale = "es", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
private fun TodayConfirmedMedicationPreview() {
    BitavidaTheme {
        TodayDemoScreen(
            currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
            projection = todayPreviewProjection(multiple = false), notes = todayPreviewNotes(),
            medicationFeedback = "Toma registrada", onUndoMedication = {},
            motionEnabledOverride = false,
        )
    }
}

@Preview(name = "Sin programación · Notas", showBackground = true, locale = "es", widthDp = 390, heightDp = 844)
@Composable
private fun TodayUnscheduledWithNotesPreview() {
    BitavidaTheme {
        TodayDemoScreen(currentDateTime = todayPreviewNow, notes = todayPreviewNotes(), motionEnabledOverride = false)
    }
}
