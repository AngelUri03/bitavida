package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import java.text.Normalizer
import java.time.LocalDateTime
import java.util.Locale

/** Temporal presentation only: none of these groups is a persisted reminder status. */
enum class CareReminderTemporalGroup { TODAY, UPCOMING, PAST }
data class CareReminderGroup(val kind: CareReminderTemporalGroup, val records: List<CareReminder>)

enum class ReminderPostponePreset { FIFTEEN_MINUTES, ONE_HOUR, TOMORROW }
enum class ReminderDatePreset { TODAY, TOMORROW }

fun reminderTemporalGroup(record: CareReminder, now: LocalDateTime): CareReminderTemporalGroup = when {
    record.scheduledAt.isBefore(now) -> CareReminderTemporalGroup.PAST
    record.scheduledAt.toLocalDate() == now.toLocalDate() -> CareReminderTemporalGroup.TODAY
    else -> CareReminderTemporalGroup.UPCOMING
}

fun groupedCareReminders(
    records: List<CareReminder>,
    personUnderCareId: String,
    now: LocalDateTime,
): List<CareReminderGroup> {
    val active = activeCareReminders(records, personUnderCareId)
    return CareReminderTemporalGroup.entries.mapNotNull { kind ->
        active.filter { reminderTemporalGroup(it, now) == kind }
            .takeIf { it.isNotEmpty() }?.let { CareReminderGroup(kind, it) }
    }
}

/** Inputs come from the current patient's projection; today keeps priority over future and earlier errands. */
fun careRemindersForTodayCarousel(records: List<CareReminder>, now: LocalDateTime): List<CareReminder> =
    records.filter { it.status == CareReminderStatus.PENDING }.distinctBy { it.id }
        .sortedWith(compareBy<CareReminder> { reminderTemporalGroup(it, now).ordinal }
            .thenBy { it.scheduledAt }.thenBy { it.id })

/** Quick postponement is always relative to the actual tap, never the former schedule or a cached clock. */
fun reminderPostponementTime(preset: ReminderPostponePreset, requestedAt: LocalDateTime): LocalDateTime =
    when (preset) {
        ReminderPostponePreset.FIFTEEN_MINUTES -> requestedAt.plusMinutes(15)
        ReminderPostponePreset.ONE_HOUR -> requestedAt.plusHours(1)
        ReminderPostponePreset.TOMORROW -> requestedAt.plusDays(1)
    }

/** Only titles explicitly supplied by the person are compared, never historical or clinical records. */
fun similarActiveCareReminders(
    title: String,
    records: List<CareReminder>,
    personUnderCareId: String,
    excludingId: String? = null,
): List<CareReminder> {
    val sought = reminderSimilarityTokens(title)
    if (sought.isEmpty()) return emptyList()
    return activeCareReminders(records, personUnderCareId).filter { record ->
        record.id != excludingId && similarReminderTokens(sought, reminderSimilarityTokens(record.title))
    }
}

private val similarityStopWords = setOf(
    "de", "del", "la", "el", "las", "los", "un", "una", "unos", "unas", "para", "por",
    "a", "al", "y", "que", "con", "mi", "mis", "su", "sus", "en",
)
private val similaritySynonyms = mapOf(
    "bateria" to "pila", "baterias" to "pila", "pilas" to "pila",
    "papeles" to "documento", "documentos" to "documento", "documentacion" to "documento",
    "articulos" to "articulo", "insumos" to "insumo", "suministros" to "insumo",
    "dispositivos" to "dispositivo", "aparato" to "dispositivo", "aparatos" to "dispositivo",
    "familiares" to "familiar", "compras" to "compra",
)
private val organizationVerbs = setOf(
    "comprar", "preparar", "recoger", "llamar", "coordinar", "revisar", "confirmar", "organizar",
    "reponer", "llevar", "hacer", "buscar", "recordar",
)

private fun reminderSimilarityTokens(title: String): List<String> =
    Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length > 1 && it !in similarityStopWords }
        .map { similaritySynonyms[it] ?: it }.distinct()

private val genericReminderWords = setOf("cosa", "cosas", "algo", "pendiente", "pendientes", "recordatorio", "recordatorios", "articulo", "insumo")

private fun similarReminderTokens(first: List<String>, second: List<String>): Boolean {
    if (second.isEmpty() || first.none { it !in organizationVerbs && it !in genericReminderWords } ||
        second.none { it !in organizationVerbs && it !in genericReminderWords }) return false
    if (first == second || first.toSet() == second.toSet()) return true
    // Match each token at most once; generic verbs alone never justify an approximate warning.
    val available = second.toMutableList()
    var matches = 0
    var concreteMatch = false
    for (token in first) {
        val index = available.indexOfFirst { it == token }.takeIf { it >= 0 }
            ?: available.indexOfFirst { closeReminderWord(token, it) }
        if (index >= 0) {
            val matched = available.removeAt(index)
            matches++
            if (token !in organizationVerbs && matched !in organizationVerbs &&
                token !in genericReminderWords && matched !in genericReminderWords) concreteMatch = true
        }
    }
    return concreteMatch && matches >= 2 && (2.0 * matches / (first.size + second.size)) >= 0.75
}

private fun closeReminderWord(first: String, second: String): Boolean {
    val maxDistance = if (minOf(first.length, second.length) >= 8) 2 else 1
    if (minOf(first.length, second.length) < 4 || kotlin.math.abs(first.length - second.length) > maxDistance) return false
    // Only a narrow edit band is relevant; long pasted titles must not create quadratic UI work.
    val unreachable = maxDistance + 1
    var previous = IntArray(second.length + 1) { if (it <= maxDistance) it else unreachable }
    var current = IntArray(second.length + 1) { unreachable }
    for (i in first.indices) {
        val from = maxOf(0, i - maxDistance)
        val through = minOf(second.lastIndex, i + maxDistance)
        for (index in from..minOf(second.length, through + 2)) current[index] = unreachable
        if (from == 0) current[0] = if (i + 1 <= maxDistance) i + 1 else unreachable
        var rowMinimum = unreachable
        for (j in from..through) {
            current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1,
                previous[j] + if (first[i] == second[j]) 0 else 1)
            rowMinimum = minOf(rowMinimum, current[j + 1])
        }
        if (rowMinimum > maxDistance) return false
        val reusable = previous
        previous = current
        current = reusable
    }
    return previous[second.length] <= maxDistance
}
