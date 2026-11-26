package com.bitavida.app.data.reminders

import android.content.Context
import com.bitavida.app.data.local.secure.CareReminderEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.repository.CareReminderActor
import com.bitavida.app.domain.repository.CareReminderAuthorizationException
import com.bitavida.app.domain.repository.CareReminderRepository
import com.bitavida.app.domain.repository.CareReminderStorageException
import com.bitavida.app.domain.usecase.CareReminderCommand
import com.bitavida.app.domain.usecase.CareReminderValidationException
import java.security.MessageDigest
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reuses the single Room/SQLCipher/Keystore database; never stores errands in preferences or logs. */
class EncryptedCareReminderRepository(context: Context) : CareReminderRepository {
    private val appContext = context.applicationContext

    override suspend fun load(actor: CareReminderActor): List<CareReminder> = storage {
        SecureDatabaseProvider.open(appContext).careReminderDao().load(actor).map { it.toDomain() }
    }

    override suspend fun apply(actor: CareReminderActor, command: CareReminderCommand, now: LocalDateTime): CareReminder = storage {
        SecureDatabaseProvider.open(appContext).careReminderDao()
            .apply(actor, command, careReminderRequestFingerprint(command), now).toDomain()
    }

    private suspend fun <T> storage(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: CareReminderValidationException) { throw error }
        catch (error: CareReminderAuthorizationException) { throw error }
        catch (error: Exception) { throw CareReminderStorageException(error) }
    }
}

internal fun CareReminderEntity.toDomain() = CareReminder(id, personUnderCareId, title, details,
    LocalDateTime.parse(scheduledAt), notificationsEnabled, CareReminderStatus.valueOf(status),
    LocalDateTime.parse(createdAt), LocalDateTime.parse(modifiedAt), closedAt?.let(LocalDateTime::parse),
    revision, LocalDateTime.parse(originallyScheduledAt))

internal fun CareReminder.toEntity(actor: CareReminderActor) = CareReminderEntity(id, actor.profileId,
    actor.caregiverId, personUnderCareId, title, details, scheduledAt.toString(), notificationsEnabled,
    status.name, createdAt.toString(), modifiedAt.toString(), closedAt?.toString(), revision,
    originallyScheduledAt.toString())

/** A digest distinguishes operation retries from a reused key with a different request. */
internal fun careReminderRequestFingerprint(command: CareReminderCommand): String {
    val fields: List<String?> = listOf(command.operationId, command.id) + when (command) {
        is CareReminderCommand.Create -> listOf("CREATE", command.draft.title, command.draft.details,
            command.draft.scheduledAt.toString(), command.draft.notificationsEnabled.toString())
        is CareReminderCommand.Edit -> listOf("EDIT", command.expectedRevision.toString(),
            command.draft.title, command.draft.details, command.draft.scheduledAt.toString(),
            command.draft.notificationsEnabled.toString())
        is CareReminderCommand.Postpone -> listOf("POSTPONE", command.expectedRevision.toString(),
            command.scheduledAt.toString())
        is CareReminderCommand.Close -> listOf("CLOSE", command.expectedRevision.toString(), command.status.name)
    }
    val canonical = fields.joinToString("") { value -> if (value == null) "-1:" else "${value.length}:$value" }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
