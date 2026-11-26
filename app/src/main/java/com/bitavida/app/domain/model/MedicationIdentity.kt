package com.bitavida.app.domain.model

import java.text.Normalizer
import java.util.Locale

/**
 * Conservative medication identity used only within one person-under-care scope.
 *
 * It normalizes Unicode width/composition, case and repeated whitespace. It deliberately
 * keeps accents and punctuation so similar names are never merged by fuzzy matching.
 */
fun normalizeMedicationNameForIdentity(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

/**
 * Groups persisted treatment periods without rewriting their identifiers.
 *
 * A stable series always stays together. Separate legacy series are consolidated for reading
 * only when their current normalized names match exactly; no nominal similarity is inferred.
 */
fun List<MedicationPlan>.groupByMedicationIdentity(): List<List<MedicationPlan>> {
    val groups = mutableListOf<MutableList<MedicationPlan>>()
    sortedWith(
        compareBy<MedicationPlan>(MedicationPlan::createdAtEpochMillis)
            .thenBy(MedicationPlan::id),
    ).forEach { plan ->
        val normalizedName = normalizeMedicationNameForIdentity(
            plan.currentRevision.medicationName,
        )
        val matching = groups.withIndex().filter { (_, group) ->
            group.any { existing ->
                existing.medicationSeriesId == plan.medicationSeriesId ||
                    normalizeMedicationNameForIdentity(
                        existing.currentRevision.medicationName,
                    ) == normalizedName
            }
        }
        if (matching.isEmpty()) {
            groups += mutableListOf(plan)
        } else {
            val destination = matching.first().value
            destination += plan
            matching.drop(1).asReversed().forEach { indexed ->
                destination += indexed.value
                groups.removeAt(indexed.index)
            }
        }
    }
    return groups.map { group ->
        group.sortedWith(
            compareBy<MedicationPlan> {
                it.currentRevision.configuration?.startsOn
                    ?: it.currentRevision.startsOn
            }.thenBy(MedicationPlan::createdAtEpochMillis),
        )
    }
}

fun List<MedicationPlan>.identityPlansFor(plan: MedicationPlan): List<MedicationPlan> =
    groupByMedicationIdentity().singleOrNull { group ->
        group.any { it.id == plan.id }
    }.orEmpty()
