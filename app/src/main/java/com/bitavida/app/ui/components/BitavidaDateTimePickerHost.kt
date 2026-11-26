package com.bitavida.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDate
import java.time.LocalTime

private sealed interface BitavidaDateTimePickerRequest {
    data class Date(
        val selectedDate: LocalDate,
        val constraints: BitavidaDatePickerConstraints,
        val onConfirm: (LocalDate) -> Unit,
    ) : BitavidaDateTimePickerRequest

    data class Time(
        val selectedTime: LocalTime,
        val constraints: BitavidaTimePickerConstraints,
        val onConfirm: (LocalTime) -> Unit,
    ) : BitavidaDateTimePickerRequest
}

@Stable
class BitavidaDateTimePickerController internal constructor() {
    private var request by mutableStateOf<BitavidaDateTimePickerRequest?>(null)

    val isActive: Boolean
        get() = request != null

    fun showDate(
        selectedDate: LocalDate,
        constraints: BitavidaDatePickerConstraints = BitavidaDatePickerConstraints(),
        onConfirm: (LocalDate) -> Unit,
    ) {
        request = BitavidaDateTimePickerRequest.Date(
            selectedDate = selectedDate,
            constraints = constraints,
            onConfirm = onConfirm,
        )
    }

    fun showTime(
        selectedTime: LocalTime,
        constraints: BitavidaTimePickerConstraints = BitavidaTimePickerConstraints(),
        onConfirm: (LocalTime) -> Unit,
    ) {
        request = BitavidaDateTimePickerRequest.Time(
            selectedTime = selectedTime,
            constraints = constraints,
            onConfirm = onConfirm,
        )
    }

    fun dismiss() {
        request = null
    }

    @Composable
    internal fun Content(modifier: Modifier = Modifier) {
        val current = request ?: return
        Column(modifier = modifier.fillMaxWidth()) {
            when (current) {
                is BitavidaDateTimePickerRequest.Date ->
                    BitavidaDatePicker(
                        selectedDate = current.selectedDate,
                        constraints = current.constraints,
                        onBack = ::dismiss,
                        onConfirm = { date ->
                            val callback =
                                (request as? BitavidaDateTimePickerRequest.Date)?.onConfirm
                                    ?: return@BitavidaDatePicker
                            request = null
                            callback(date)
                        },
                    )
                is BitavidaDateTimePickerRequest.Time ->
                    BitavidaTimePicker(
                        selectedTime = current.selectedTime,
                        constraints = current.constraints,
                        onBack = ::dismiss,
                        onConfirm = { time ->
                            val callback =
                                (request as? BitavidaDateTimePickerRequest.Time)?.onConfirm
                                    ?: return@BitavidaTimePicker
                            request = null
                            callback(time)
                        },
                    )
            }
        }
    }
}

@Composable
fun rememberBitavidaDateTimePickerController(): BitavidaDateTimePickerController =
    remember { BitavidaDateTimePickerController() }

val LocalBitavidaDateTimePickerController =
    staticCompositionLocalOf { BitavidaDateTimePickerController() }

@Composable
fun BitavidaDateTimePickerHost(
    content: @Composable () -> Unit,
) {
    val controller = rememberBitavidaDateTimePickerController()
    CompositionLocalProvider(
        LocalBitavidaDateTimePickerController provides controller,
    ) {
        content()
        if (controller.isActive) {
            BitavidaDateTimePickerSheet(
                controller = controller,
                onDismiss = controller::dismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BitavidaDateTimePickerSheet(
    controller: BitavidaDateTimePickerController,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f),
        dragHandle = { BitavidaPickerDragHandle() },
    ) {
        controller.Content(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(
                    start = BitavidaSpacing.ScreenHorizontal,
                    end = BitavidaSpacing.ScreenHorizontal,
                    bottom = BitavidaSpacing.Default,
                )
                .semantics { paneTitle = "Selector de fecha u hora" },
        )
    }
}

@Composable
internal fun BitavidaPickerDragHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(48.dp).height(4.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {}
    }
}
