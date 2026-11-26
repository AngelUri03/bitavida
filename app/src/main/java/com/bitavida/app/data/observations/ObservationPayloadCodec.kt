package com.bitavida.app.data.observations

import com.bitavida.app.domain.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Base64

/** Explicit versioned format; neither Java serialization nor Android runtime is required. */
internal object ObservationPayloadCodec {
    fun encode(record: ObservationRecord): String = encodePayload {
        writeInt(1)
        writeUTF(record.id); writeUTF(record.observationId); writeUTF(record.personUnderCareId)
        writeUTF(record.occurredOn.toString()); nullable(record.occurredAt?.toString())
        writeUTF(record.timePrecision.name); writeUTF(record.result.name)
        writeInt(record.answers.size)
        record.answers.toSortedMap().forEach { (key, values) -> writeUTF(key); strings(values) }
        writeUTF(record.description); strings(record.localPhotoIds)
        nullable(record.followUpId); nullable(record.occurrenceId); nullable(record.scheduledAt?.toString())
        writeUTF(record.createdAt.toString()); writeUTF(record.actorId); writeInt(record.schemaVersion)
    }

    fun decodeRecord(payload: String): ObservationRecord = decodePayload(payload) {
        require(readInt() == 1)
        val id = readUTF(); val type = readUTF(); val person = readUTF()
        val date = LocalDate.parse(readUTF()); val time = nullable()?.let(LocalTime::parse)
        val precision = ObservationTimePrecision.valueOf(readUTF())
        val result = ObservationResult.valueOf(readUTF())
        val answers = linkedMapOf<String, List<String>>()
        repeat(count()) { val key = readUTF(); require(key !in answers); answers[key] = strings() }
        ObservationRecord(
            id, type, person, date, time, precision, result, answers, readUTF(), strings(),
            nullable(), nullable(), nullable()?.let(LocalDateTime::parse),
            LocalDateTime.parse(readUTF()), readUTF(), readInt(),
        )
    }

    fun encode(revision: GlucoseFollowUpRevision): String = encodePayload {
        writeInt(1)
        writeUTF(revision.id); writeUTF(revision.followUpId); writeInt(revision.revisionNumber)
        writeUTF(revision.indication); writeUTF(revision.frequency.name)
        strings(revision.weekdays.sortedBy(DayOfWeek::getValue).map { it.name })
        writeInt(revision.everyNDaysInterval ?: 0); nullable(revision.everyNDaysReferenceDate?.toString())
        writeUTF(revision.startsOn.toString()); nullable(revision.endsOn?.toString())
        writeUTF(revision.effectiveFrom.toString()); writeInt(revision.slots.size)
        revision.slots.forEach { slot ->
            writeUTF(slot.id); writeUTF(slot.timingType.name); writeUTF(slot.time.toString())
            nullable(slot.windowEnd?.toString()); writeBoolean(slot.endsNextDay)
            writeUTF(slot.suggestedContext.name); writeBoolean(slot.reminderEnabled)
            nullable(slot.reminderTime?.toString()); writeBoolean(slot.reminderNextDay)
        }
        writeBoolean(revision.remindersEnabled); writeUTF(revision.changeKind.name)
        nullable(revision.changeReason); writeUTF(revision.capturedAt.toString()); writeUTF(revision.authorActorId)
    }

    fun decodeRevision(payload: String): GlucoseFollowUpRevision = decodePayload(payload) {
        require(readInt() == 1)
        val id = readUTF(); val followUp = readUTF(); val number = readInt()
        val indication = readUTF(); val frequency = GlucoseFollowUpFrequency.valueOf(readUTF())
        val days = strings().mapTo(linkedSetOf(), DayOfWeek::valueOf)
        val interval = readInt().takeIf { it != 0 }; val reference = nullable()?.let(LocalDate::parse)
        val starts = LocalDate.parse(readUTF()); val ends = nullable()?.let(LocalDate::parse)
        val effective = LocalDate.parse(readUTF())
        val slots = List(count()) {
            GlucoseFollowUpSlot(
                readUTF(), GlucoseFollowUpTimingType.valueOf(readUTF()), LocalTime.parse(readUTF()),
                nullable()?.let(LocalTime::parse), readBoolean(), GlucoseContext.valueOf(readUTF()),
                readBoolean(), nullable()?.let(LocalTime::parse), readBoolean(),
            )
        }
        GlucoseFollowUpRevision(id, followUp, number, indication, frequency, days, interval, reference,
            starts, ends, effective, slots, readBoolean(), GlucoseFollowUpChangeKind.valueOf(readUTF()),
            nullable(), LocalDateTime.parse(readUTF()), readUTF())
    }

    private fun encodePayload(write: DataOutputStream.() -> Unit): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use(write)
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun <T> decodePayload(payload: String, read: DataInputStream.() -> T): T {
        require(payload.length <= 1_000_000)
        return DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(payload))).use {
            val result = read.invoke(it)
            require(it.available() == 0)
            result
        }
    }

    private fun DataOutputStream.nullable(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.nullable(): String? = if (readBoolean()) readUTF() else null
    private fun DataOutputStream.strings(values: List<String>) {
        writeInt(values.size)
        values.forEach(::writeUTF)
    }
    private fun DataInputStream.count(): Int = readInt().also { require(it in 0..100) }
    private fun DataInputStream.strings(): List<String> = List(count()) { readUTF() }
}
