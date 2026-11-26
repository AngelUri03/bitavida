package com.bitavida.app.domain.model

import java.math.BigDecimal

/**
 * Current optional reference transcribed by the caregiver from a medical indication.
 *
 * It is a visual reference only and is never a measurement, diagnosis or computed target.
 */
data class WeightTarget(
    val kilograms: BigDecimal,
) {
    init {
        require(kilograms > BigDecimal.ZERO)
        require(kilograms.scale().coerceAtLeast(0) <= 2)
    }
}
