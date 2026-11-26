package com.bitavida.app.ui.appointments

import android.content.Context
import com.bitavida.app.R
import java.io.Reader
import java.text.Normalizer
import java.util.Locale

/**
 * Datos publicados por IMSS, empaquetados sin conexión. La clave es propia del catálogo,
 * derivada de los identificadores publicados; no es una CLUES.
 */
internal data class ImssUnit(
    val id: String,
    val name: String,
    val type: String,
    val state: String,
    val municipality: String,
    val address: String?,
    val alternate: String,
) {
    internal val searchText: String = normalizeImssText(
        "$name $type $state $municipality ${address.orEmpty()} $alternate"
    )
}

internal fun parseImssCatalog(reader: Reader): List<ImssUnit> =
    reader.buffered().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val fields = line.split('|')
            if (fields.size != 7 || fields.take(5).any(String::isBlank)) null
            else ImssUnit(
                id = fields[0], name = fields[1], type = fields[2], state = fields[3],
                municipality = fields[4], address = fields[5].ifBlank { null }, alternate = fields[6],
            )
        }.distinctBy(ImssUnit::id).toList()
    }

internal fun loadImssCatalog(context: Context): List<ImssUnit> =
    context.resources.openRawResource(R.raw.imss_units).bufferedReader(Charsets.UTF_8).use(::parseImssCatalog)

private fun normalizeImssText(value: String): String {
    val normalized = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    return normalized.split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" ") { word ->
        when {
            word == "clinica" || word == "clinicas" -> "umf"
            word == "xxi" -> "21"
            word.all(Char::isDigit) -> word.trimStart('0').ifEmpty { "0" }
            else -> word
        }
    }
}

internal fun searchImssUnits(units: List<ImssUnit>, query: String, limit: Int = 50): List<ImssUnit> {
    val terms = normalizeImssText(query).split(' ').filter(String::isNotBlank)
    if (terms.isEmpty()) return emptyList()
    return units.asSequence().filter { unit ->
        terms.all { term -> unit.searchText.split(' ').any { it.startsWith(term) } }
    }.take(limit.coerceIn(1, 100)).toList()
}
