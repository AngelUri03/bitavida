package com.bitavida.app.ui.demo

import androidx.compose.material3.Typography
import com.bitavida.app.ui.theme.BitavidaTypography
import com.bitavida.app.ui.theme.KodchasanFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TodayTypographyTest {
    @Test fun allMaterialRolesUseKodchasanWithinToday() {
        val typography = todayTypography(BitavidaTypography)
        listOf(
            typography.displayLarge, typography.displayMedium, typography.displaySmall,
            typography.headlineLarge, typography.headlineMedium, typography.headlineSmall,
            typography.titleLarge, typography.titleMedium, typography.titleSmall,
            typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
            typography.labelLarge, typography.labelMedium, typography.labelSmall,
        ).forEach { assertEquals(KodchasanFamily, it.fontFamily) }
    }

    @Test fun approvedStylesKeepTheirSizesWeightsAndLineHeight() {
        val source = BitavidaTypography
        val today = todayTypography(source)
        assertEquals(source.titleMedium, today.titleMedium)
        assertEquals(source.bodyMedium, today.bodyMedium)
        assertEquals(source.labelLarge, today.labelLarge)
        assertEquals(source.headlineSmall, today.headlineMedium)
        assertEquals(source.displaySmall, today.displayLarge)
        // A local copy never mutates the global/default Material family.
        assertNotEquals(KodchasanFamily, Typography().headlineMedium.fontFamily)
    }
}
