package com.bitavida.app.data.medical

import android.content.Context
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.data.notes.toDomain
import com.bitavida.app.data.reminders.toDomain
import com.bitavida.app.domain.repository.CareReminderActor
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.usecase.ReportNoteHistory
import com.bitavida.app.domain.usecase.ReportReminderHistory
import com.bitavida.app.domain.usecase.ReportSupplementaryHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Authorized read only; never prunes image data or mutates either module. */
class ReportSupplementaryHistoryReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun load(noteActor: NoteActor, reminderActor: CareReminderActor): ReportSupplementaryHistory =
        withContext(Dispatchers.IO) {
            require(noteActor.profileId == reminderActor.profileId &&
                noteActor.caregiverId == reminderActor.caregiverId &&
                noteActor.personUnderCareId == reminderActor.personUnderCareId)
            val database = SecureDatabaseProvider.open(appContext)
            // Each DAO performs its own authorization and transaction. These two reads
            // are not an atomic snapshot with each other or the report's other sources.
            val notes = database.noteDao().loadReportHistory(noteActor)
            val reminders = database.careReminderDao().loadReportHistory(reminderActor)
            val noteVersions = notes.revisions.groupBy { it.noteId }
            val reminderVersions = reminders.revisions.groupBy { it.reminderId }
            ReportSupplementaryHistory(
                notes = notes.current.map { current ->
                    ReportNoteHistory(current.toDomain(),
                        noteVersions[current.id].orEmpty().map { it.snapshot.toDomain() })
                },
                reminders = reminders.current.map { current ->
                    ReportReminderHistory(current.toDomain(),
                        reminderVersions[current.id].orEmpty().map { it.snapshot.toDomain() })
                },
            )
        }
}
