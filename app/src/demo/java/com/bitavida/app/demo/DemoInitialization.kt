package com.bitavida.app.demo

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Metadatos no sensibles de la instalación Demo. El marcador se escribe solo
 * después de verificar todos los registros y archivos del escenario.
 */
internal class DemoInitialization(context: Context) {
    private val preferences = context.getSharedPreferences("demo_initialization", Context.MODE_PRIVATE)

    fun reference(): DemoReference {
        val savedInstant = preferences.getString("reference_instant", null)
        val savedZone = preferences.getString("reference_zone", null)
        check((savedInstant == null) == (savedZone == null))
        if (savedInstant != null && savedZone != null) {
            return DemoReference(Instant.parse(savedInstant), ZoneId.of(savedZone))
        }

        val reference = DemoReference(Instant.now(), ZoneId.systemDefault())
        check(preferences.edit()
            .putString("reference_instant", reference.instant.toString())
            .putString("reference_zone", reference.zone.id)
            .commit())
        return reference
    }

    fun isComplete(version: Int): Boolean = preferences.getInt("completed_version", 0) == version

    fun markComplete(version: Int) {
        require(version > 0)
        check(preferences.edit().putInt("completed_version", version).commit())
    }
}

/** La comparación de instantes impide crear hechos después del corte, incluso hoy. */
internal data class DemoReference(val instant: Instant, val zone: ZoneId) {
    val local: ZonedDateTime get() = instant.atZone(zone)
    val firstDate: LocalDate get() = local.toLocalDate().withDayOfMonth(1).minusMonths(2)
    val lastDate: LocalDate get() = local.toLocalDate()

    fun includesOccurredAt(value: LocalDateTime): Boolean =
        !value.atZone(zone).toInstant().isAfter(instant)
}
