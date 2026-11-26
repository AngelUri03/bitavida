package com.bitavida.app.domain.model

import java.time.LocalDateTime

const val MAX_NOTE_TITLE_LENGTH = 100
const val MAX_NOTE_CONTENT_LENGTH = 2_000

/** A caregiver's free note, never a task, indication or scheduled event. */
data class Note(
    val id: String,
    val personUnderCareId: String,
    val title: String,
    val content: String?,
    val imageId: String?,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime,
    val revision: Int,
    val deletedAt: LocalDateTime? = null,
    val legacyIndicationId: String? = null,
)

data class NoteDraft(
    val title: String,
    val content: String? = null,
    val imageId: String? = null,
)
