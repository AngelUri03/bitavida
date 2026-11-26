package com.bitavida.app.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.bitavida.app.data.appointments.EncryptedCareEventRepository
import com.bitavida.app.data.local.secure.NoteImageEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.data.notes.EncryptedNoteRepository
import com.bitavida.app.data.reminders.EncryptedCareReminderRepository
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.CareEventActor
import com.bitavida.app.domain.repository.CareEventCommand
import com.bitavida.app.domain.repository.CareReminderActor
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.usecase.*
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

/** Seed only the isolated demo database through the same encrypted repositories as the app. */
internal suspend fun seedDemoSupplementary(
    context: Context,
    profile: CareProfile,
    reference: DemoReference,
): Set<String> {
    require(context.packageName == "com.bitavida.app.demo")
    val person = profile.personUnderCare.id
    val caregiver = profile.caregiver.id
    val notes = EncryptedNoteRepository(context)
    val noteActor = NoteActor(profile.id, caregiver, person, true)
    val start = reference.firstDate.atTime(9, 0)
    val noteData = listOf(
        Triple("demo-note-supplies", "Materiales", "Revisar los materiales de cuidado antes de salir."),
        Triple("demo-note-questions", "Preguntas para la próxima consulta", "Anotar dudas y llevar los registros para conversar con el profesional de salud."),
        Triple("demo-note-routine", "Rutina cotidiana", "Preparar una bolsa con documentos y una libreta. Mantener los teléfonos de contacto a mano y revisar los pendientes juntos."),
        Triple("demo-note-walk", "Paseo", "Elegir una hora cómoda para salir."),
    )
    for ((index, item) in noteData.withIndex()) {
        val (id, title, content) = item
        if (notes.load(noteActor).none { it.id == id }) {
            val imageId = if (index == 1 || index == 2) "$id-image" else null
            if (imageId != null) {
                val dao = SecureDatabaseProvider.open(context).noteDao()
                if (dao.image(imageId, profile.id, person) == null) {
                    val bitmap = Bitmap.createBitmap(480, 300, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.rgb(235, 244, 240))
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(85, 145, 129) }
                    canvas.drawCircle(240f, 150f, 80f, paint)
                    paint.color = Color.rgb(255, 255, 255)
                    canvas.drawRoundRect(185f, 100f, 295f, 200f, 12f, 12f, paint)
                    paint.color = Color.rgb(85, 145, 129)
                    paint.strokeWidth = 7f
                    canvas.drawLine(205f, 135f, 275f, 135f, paint)
                    canvas.drawLine(205f, 160f, 260f, 160f, paint)
                    val bytes = ByteArrayOutputStream().use { out ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out))
                        out.toByteArray()
                    }
                    bitmap.recycle()
                    try {
                        dao.storeImage(noteActor, NoteImageEntity(imageId, profile.id, person, bytes, LocalDateTime.now().toString()))
                    } finally { bytes.fill(0) }
                }
            }
            val created = ManageNotes(notes)(noteActor, NoteCommand.Create("$id-create", id,
                NoteDraft(title, content, imageId)), start.plusDays(index.toLong()))
            check(created.id == id)
        }
    }
    val editedNote = notes.load(noteActor).single { it.id == "demo-note-routine" }
    if (editedNote.revision == 1) {
        ManageNotes(notes)(noteActor, NoteCommand.Edit("demo-note-routine-edit", editedNote.id, 1,
            NoteDraft(editedNote.title, editedNote.content + " Revisar la agenda al final de la semana.", editedNote.imageId)),
            start.plusDays(10))
    }

    val reminders = EncryptedCareReminderRepository(context)
    val reminderActor = CareReminderActor(profile.id, caregiver, person, true)
    val reminderTitles = listOf("Preparar documentos", "Revisar materiales", "Organizar el botiquín",
        "Confirmar traslado", "Consultar la agenda", "Preparar una lista de preguntas", "Revisar contactos")
    val reminderIds = reminderTitles.indices.map { "demo-reminder-active-${it + 1}" }
    for ((index, id) in reminderIds.withIndex()) {
        if (reminders.load(reminderActor).none { it.id == id }) {
            val scheduled = if (index == 0) reference.local.toLocalDateTime() else reference.local.toLocalDateTime().plusDays(index.toLong()).withHour(12).withMinute(0)
            val created = ManageCareReminders(reminders)(reminderActor,
                CareReminderCommand.Create("$id-create", id, CareReminderDraft(reminderTitles[index],
                    "Actividad ficticia para organizar el cuidado.", scheduled, false)), start)
            check(created.id == id)
        }
    }
    val moved = reminders.load(reminderActor).single { it.id == reminderIds[1] }
    if (moved.revision == 1) {
        ManageCareReminders(reminders)(reminderActor,
            CareReminderCommand.Postpone("demo-reminder-active-2-move", moved.id, 1, moved.scheduledAt.plusDays(1)), start.plusDays(1))
    }
    val historicalIds = listOf("demo-reminder-done-1", "demo-reminder-done-2")
    for ((index, id) in historicalIds.withIndex()) {
        val scheduled = start.plusDays(5L + index * 30)
        if (reminders.load(reminderActor).none { it.id == id }) {
            ManageCareReminders(reminders)(reminderActor,
                CareReminderCommand.Create("$id-create", id,
                    CareReminderDraft("Organizar documentos de consulta", "Ejemplo realizado.", scheduled, false)),
                scheduled.minusDays(1))
        }
        val item = reminders.load(reminderActor).single { it.id == id }
        if (item.revision == 1 && item.status == CareReminderStatus.PENDING) {
            ManageCareReminders(reminders)(reminderActor,
                CareReminderCommand.Close("$id-close", id, 1, CareReminderStatus.DONE), scheduled.plusMinutes(15))
        }
    }

    val events = EncryptedCareEventRepository(context)
    val eventActor = CareEventActor(profile.id, caregiver, person, true)
    data class EventSeed(val id: String, val draft: CareEventDraft, val createAt: LocalDateTime)
    fun consultation(id: String, time: LocalDateTime, specialty: String, type: CareConsultationType) =
        EventSeed(id, CareEventDraft(CareEventKind.APPOINTMENT, "Consulta de seguimiento",
            scheduledAt = time, location = "Unidad de atención ficticia", status = CareEventStatus.CONFIRMED,
            purpose = CareEventPurpose.MEDICAL_CONSULTATION, consultationType = type,
            isFollowUp = true, specialty = specialty), minOf(time.minusDays(4), reference.local.toLocalDateTime().minusMinutes(1)))
    val first = reference.firstDate
    val eventData = listOf(
        consultation("demo-event-family-1", first.plusDays(8).atTime(10, 0), "Medicina familiar", CareConsultationType.FAMILY_MEDICINE),
        consultation("demo-event-family-2", first.plusMonths(1).plusDays(8).atTime(10, 30), "Medicina familiar", CareConsultationType.FAMILY_MEDICINE),
        consultation("demo-event-specialty", first.plusMonths(1).plusDays(15).atTime(11, 0), "Medicina interna", CareConsultationType.SPECIALIST),
        EventSeed("demo-event-study", CareEventDraft(CareEventKind.STUDY, "Estudio de laboratorio",
            scheduledAt = first.plusDays(20).atTime(9, 0), location = "Laboratorio ficticio",
            status = CareEventStatus.SCHEDULED, purpose = CareEventPurpose.STUDY),
            first.plusDays(16).atTime(9, 0)),
        consultation("demo-event-next", reference.local.toLocalDateTime().plusDays(8), "Medicina general", CareConsultationType.GENERAL_MEDICINE),
        consultation("demo-event-rescheduled", reference.local.toLocalDateTime().plusDays(16), "Medicina familiar", CareConsultationType.FAMILY_MEDICINE),
        consultation("demo-event-cancelled", reference.local.toLocalDateTime().plusDays(20), "Medicina familiar", CareConsultationType.FAMILY_MEDICINE),
        EventSeed("demo-event-next-study", CareEventDraft(CareEventKind.STUDY, "Estudio programado",
            scheduledAt = reference.local.toLocalDateTime().plusDays(25), location = "Laboratorio ficticio",
            status = CareEventStatus.SCHEDULED, purpose = CareEventPurpose.STUDY), reference.local.toLocalDateTime().minusDays(1)),
    )
    for (event in eventData) {
        if (events.load(eventActor).none { it.id == event.id }) {
            val saved = events.save(eventActor, CareEventCommand.Create("${event.id}-create", event.id, event.draft), event.createAt)
            check(saved.id == event.id)
        }
    }
    val rescheduled = events.load(eventActor).single { it.id == "demo-event-rescheduled" }
    if (rescheduled.revision == 1) {
        val newTime = requireNotNull(rescheduled.draft.scheduledAt).plusDays(2)
        events.save(eventActor, CareEventCommand.Revise("demo-event-rescheduled-edit", rescheduled.id, 1,
            rescheduled.draft.copy(scheduledAt = newTime, scheduledDate = newTime.toLocalDate(),
                changeReason = "Cambio de fecha ficticio.")), reference.local.toLocalDateTime())
    }
    val cancelled = events.load(eventActor).single { it.id == "demo-event-cancelled" }
    if (cancelled.revision == 1) {
        events.save(eventActor, CareEventCommand.Revise("demo-event-cancelled-edit", cancelled.id, 1,
            cancelled.draft.copy(status = CareEventStatus.CANCELLED, changeReason = "Cancelación ficticia.")), reference.local.toLocalDateTime())
    }

    val expectedNotes = noteData.map { it.first }.toSet()
    val expectedReminders = (reminderIds + historicalIds).toSet()
    val expectedEvents = eventData.map { it.id }.toSet()
    check(notes.load(noteActor).map { it.id }.containsAll(expectedNotes))
    check(reminders.load(reminderActor).map { it.id }.containsAll(expectedReminders))
    check(events.load(eventActor).map { it.id }.containsAll(expectedEvents))
    return expectedNotes + expectedReminders + expectedEvents
}
