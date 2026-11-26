package com.bitavida.app.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalAccessibilityManager
import com.bitavida.app.ui.medical.TodayMedicationFeedback
import com.bitavida.app.ui.medical.TodayMedicationFeedbackStatus
import kotlinx.coroutines.delay

/** One receipt timeout outside navigation; reopening or rotating never renews an old action. */
@Composable
internal fun rememberTodayMedicationFeedback(
    feedback: TodayMedicationFeedback?,
    onExpire: (String) -> Unit,
): TodayMedicationFeedback? {
    val accessibility = LocalAccessibilityManager.current
    val expire by rememberUpdatedState(onExpire)
    val terminal = feedback?.status in setOf(
        TodayMedicationFeedbackStatus.STORED,
        TodayMedicationFeedbackStatus.UNDONE,
        TodayMedicationFeedbackStatus.FAILED,
    )
    val duration = accessibility?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = 8_000L,
        containsIcons = true,
        containsText = true,
        containsControls = feedback?.status == TodayMedicationFeedbackStatus.STORED,
    ) ?: 8_000L
    val remaining = feedback?.let {
        remainingTodayMedicationFeedbackMillis(it.createdAtEpochMillis, System.currentTimeMillis(), duration)
    } ?: 0L
    LaunchedEffect(feedback?.requestId, feedback?.status, accessibility) {
        val receipt = feedback ?: return@LaunchedEffect
        if (!terminal) return@LaunchedEffect
        delay(remaining)
        expire(receipt.requestId)
    }
    return feedback.takeUnless { terminal && remaining == 0L }
}

internal fun remainingTodayMedicationFeedbackMillis(
    createdAtEpochMillis: Long,
    nowEpochMillis: Long,
    accessibleDurationMillis: Long,
): Long = (accessibleDurationMillis - (nowEpochMillis - createdAtEpochMillis).coerceAtLeast(0L))
    .coerceAtLeast(0L)

internal fun TodayMedicationFeedback.presentationMessage(): String? = when (status) {
    TodayMedicationFeedbackStatus.STORED -> if (outcome ==
        com.bitavida.app.domain.model.MedicationIntakeOutcome.COMPLETED) {
        "Toma registrada: " + medicationName
    } else {
        "Declaración registrada: " + medicationName
    }
    TodayMedicationFeedbackStatus.UNDONE -> "Corrección guardada. La toma vuelve a pendientes."
    TodayMedicationFeedbackStatus.FAILED -> message ?: "No se pudo registrar la toma. Inténtalo otra vez."
    TodayMedicationFeedbackStatus.SAVING, TodayMedicationFeedbackStatus.AWAITING_CONSENT -> null
}
