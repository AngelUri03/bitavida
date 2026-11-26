package com.bitavida.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Preview(name = "Fecha · claro", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(
    name = "Fecha · oscuro",
    widthDp = 390,
    heightDp = 760,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Fecha · 320 dp", widthDp = 320, heightDp = 760, showBackground = true)
@Preview(
    name = "Fecha · texto 200 %",
    widthDp = 320,
    heightDp = 1100,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun BitavidaDatePickerPreview() {
    PickerPreviewSheet {
        BitavidaDatePicker(
            selectedDate = LocalDate.of(2026, 11, 17),
            constraints = BitavidaDatePickerConstraints(
                minDate = LocalDate.of(2025, 1, 1),
                maxDate = LocalDate.of(2026, 11, 17),
                yearRange = 2025..2026,
            ),
            locale = Locale.forLanguageTag("es-MX"),
            motionAllowedOverride = true,
            onBack = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Mes y año · claro", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(
    name = "Mes y año · oscuro",
    widthDp = 390,
    heightDp = 760,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Mes y año · 320 dp", widthDp = 320, heightDp = 820, showBackground = true)
@Composable
private fun BitavidaMonthYearPickerPreview() {
    PickerPreviewSheet {
        BitavidaDatePicker(
            selectedDate = LocalDate.of(2026, 11, 17),
            constraints = BitavidaDatePickerConstraints(
                minDate = LocalDate.of(1900, 1, 1),
                maxDate = LocalDate.of(2030, 12, 31),
                yearRange = 1900..2030,
            ),
            locale = Locale.forLanguageTag("es-MX"),
            motionAllowedOverride = true,
            initialMonthYearVisible = true,
            onBack = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Hora ruedas · claro", widthDp = 390, heightDp = 560, showBackground = true)
@Preview(
    name = "Hora ruedas · oscuro",
    widthDp = 390,
    heightDp = 560,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Hora ruedas · 320 dp", widthDp = 320, heightDp = 620, showBackground = true)
@Preview(
    name = "Hora ruedas · texto 200 %",
    widthDp = 320,
    heightDp = 900,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun BitavidaTimePickerPreview() {
    PickerPreviewSheet {
        BitavidaTimePicker(
            selectedTime = LocalTime.of(9, 15),
            constraints = BitavidaTimePickerConstraints(minuteStep = 5),
            locale = Locale.forLanguageTag("es-MX"),
            motionAllowedOverride = true,
            onBack = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Hora escrita · claro", widthDp = 390, heightDp = 520, showBackground = true)
@Preview(
    name = "Hora escrita · oscuro",
    widthDp = 390,
    heightDp = 520,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Hora escrita · 320 dp", widthDp = 320, heightDp = 620, showBackground = true)
@Composable
private fun BitavidaTimePickerManualPreview() {
    PickerPreviewSheet {
        BitavidaTimePicker(
            selectedTime = LocalTime.of(9, 15),
            constraints = BitavidaTimePickerConstraints(minuteStep = 5),
            locale = Locale.forLanguageTag("es-MX"),
            motionAllowedOverride = true,
            initialDirectInput = true,
            onBack = {},
            onConfirm = {},
        )
    }
}

@Preview(
    name = "Selectores · movimiento reducido",
    widthDp = 390,
    heightDp = 560,
    showBackground = true,
)
@Composable
private fun BitavidaTimePickerReducedMotionPreview() {
    PickerPreviewSheet {
        BitavidaTimePicker(
            selectedTime = LocalTime.of(9, 15),
            locale = Locale.forLanguageTag("es-MX"),
            motionAllowedOverride = false,
            onBack = {},
            onConfirm = {},
        )
    }
}

@Composable
private fun PickerPreviewSheet(content: @Composable () -> Unit) {
    BitavidaTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        BitavidaPickerDragHandle()
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .padding(
                                    start = BitavidaSpacing.ScreenHorizontal,
                                    end = BitavidaSpacing.ScreenHorizontal,
                                    bottom = BitavidaSpacing.Default,
                                ),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}
