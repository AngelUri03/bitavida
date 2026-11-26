package com.bitavida.app.ui.medical

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.usecase.MedicalCareField
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.components.BitavidaPickerDragHandle
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.components.rememberBitavidaDateTimePickerController
import com.bitavida.app.ui.theme.BitavidaSpacing

private data class MedicationLifecycleSheetSpec(
    val title: String,
    val subtitle: String?,
    val reasonLabel: String,
    val confirmLabel: String,
    val confirmIcon: Int,
    val destructive: Boolean,
    val consequences: List<MedicationLifecycleConsequence>,
)

private data class MedicationLifecycleConsequence(
    val icon: Int,
    val title: String,
    val body: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicationLifecycleConfirmationSheet(
    draft: MedicationLifecycleDraft,
    medication: MedicationPlan,
    errors: Set<MedicalCareField>,
    saving: Boolean,
    motionAllowed: Boolean,
    onChange: (MedicationLifecycleDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spec = draft.targetStatus.lifecycleSheetSpec()
    val reasonValid = draft.reason.isNotBlank()
    val canConfirm = reasonValid && !saving
    val destructiveFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        errorTextColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = MaterialTheme.colorScheme.error,
        errorCursorColor = MaterialTheme.colorScheme.error,
        focusedBorderColor = MaterialTheme.colorScheme.error,
        unfocusedBorderColor = MaterialTheme.colorScheme.error,
        errorBorderColor = MaterialTheme.colorScheme.error,
        focusedLabelColor = MaterialTheme.colorScheme.error,
        unfocusedLabelColor = MaterialTheme.colorScheme.error,
        errorLabelColor = MaterialTheme.colorScheme.error,
        focusedSupportingTextColor = MaterialTheme.colorScheme.error,
        unfocusedSupportingTextColor = MaterialTheme.colorScheme.error,
        errorSupportingTextColor = MaterialTheme.colorScheme.error,
    )

    MedicationLifecycleSheetFrame(
        medication = medication,
        title = spec.title,
        subtitle = spec.subtitle,
        saving = saving,
        motionAllowed = motionAllowed,
        onDismiss = onDismiss,
    ) {
            MedicationLifecycleConsequences(
                consequences = spec.consequences,
                destructive = spec.destructive,
            )

            BitavidaLimitedTextField(
                value = draft.reason,
                limit = CaptureLimit.REASON,
                onValueChange = {
                    if (!saving) onChange(draft.copy(reason = it))
                },
                label = { Text(spec.reasonLabel) },
                placeholder = { Text("Escribe un motivo breve") },
                supportingText = {
                    Text(
                        if (MedicalCareField.CHANGE_REASON in errors) {
                            "Escribe un motivo para continuar"
                        } else {
                            "Obligatorio"
                        },
                    )
                },
                isError = MedicalCareField.CHANGE_REASON in errors,
                minLines = 2,
                maxLines = 5,
                shape = MaterialTheme.shapes.medium,
                colors = if (spec.destructive) {
                    destructiveFieldColors
                } else {
                    OutlinedTextFieldDefaults.colors()
                },
                modifier = Modifier.fillMaxWidth(),
            )

        MedicationLifecyclePrimaryAction(
            label = spec.confirmLabel,
            icon = spec.confirmIcon,
            enabled = canConfirm,
            saving = saving,
            destructive = spec.destructive,
            motionAllowed = motionAllowed,
            onClick = onConfirm,
        )
        MedicationLifecycleCancelAction(
            enabled = !saving,
            onClick = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicationLifecycleSheetFrame(
    medication: MedicationPlan?,
    title: String,
    subtitle: String? = null,
    saving: Boolean,
    motionAllowed: Boolean,
    onDismiss: () -> Unit,
    contentHorizontalPadding: androidx.compose.ui.unit.Dp = BitavidaSpacing.ScreenHorizontal,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val dateTimePicker = rememberBitavidaDateTimePickerController()
    val selectorActive = dateTimePicker.isActive
    BackHandler(enabled = selectorActive) { dateTimePicker.dismiss() }
    var revealed by remember { mutableStateOf(!motionAllowed) }
    LaunchedEffect(Unit) {
        revealed = true
    }
    val revealProgress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(duration),
        label = "medication lifecycle content",
    )
    ModalBottomSheet(
        onDismissRequest = {
            when {
                selectorActive -> dateTimePicker.dismiss()
                !saving -> onDismiss()
            }
        },
        sheetState = sheetState,
        sheetGesturesEnabled = !selectorActive,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f),
        dragHandle = {
            if (selectorActive) {
                BitavidaPickerDragHandle()
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.width(72.dp).height(5.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ) {}
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(
                    start = contentHorizontalPadding,
                    end = contentHorizontalPadding,
                    bottom = BitavidaSpacing.Default,
                )
                .graphicsLayer {
                    alpha = revealProgress
                    translationY = (1f - revealProgress) * 12.dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            AnimatedContent(
                targetState = selectorActive,
                transitionSpec = {
                    if (!motionAllowed) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)))
                            .using(SizeTransform(clip = false) { _, _ -> tween(duration) })
                    }
                },
                label = "inline date time picker",
            ) { showingPicker ->
                CompositionLocalProvider(
                    LocalBitavidaDateTimePickerController provides dateTimePicker,
                ) {
                    if (showingPicker) {
                        dateTimePicker.Content()
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                        ) {
                            medication?.let { MedicationLifecycleIdentity(it) }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                            ) {
                                Text(
                                    title,
                                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                subtitle?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MedicationLifecyclePrimaryAction(
    label: String,
    icon: Int,
    enabled: Boolean,
    saving: Boolean,
    destructive: Boolean,
    motionAllowed: Boolean,
    onClick: () -> Unit,
) {
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    val interaction = remember { MutableInteractionSource() }
    val activeContainer = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val activeContent = if (destructive) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val container by animateColorAsState(
        targetValue = if (enabled || saving) {
            activeContainer
        } else {
            activeContainer.copy(alpha = 0.32f)
        },
        animationSpec = tween(duration),
        label = "medication lifecycle confirmation color",
    )
    val foreground by animateColorAsState(
        targetValue = if (enabled || saving) {
            activeContent
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
        },
        animationSpec = tween(duration),
        label = "medication lifecycle confirmation content",
    )
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) {
            BitavidaFluidMotion.PressScale
        } else {
            1f
        },
        animationSpec = tween(
            if (motionAllowed) {
                if (pressed) BitavidaFluidMotion.Press
                else BitavidaFluidMotion.Release
            } else {
                0
            },
        ),
        label = "medication lifecycle confirmation press",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = foreground,
            disabledContainerColor = container,
            disabledContentColor = foreground,
        ),
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        if (saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = foreground,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(BitavidaSpacing.Small))
            Text("Procesando…")
        } else {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(foreground),
            )
            Spacer(Modifier.size(BitavidaSpacing.Small))
            Text(label)
        }
    }
}

@Composable
internal fun MedicationLifecycleCancelAction(
    enabled: Boolean,
    label: String = "Cancelar",
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
    ) {
        Text(label)
    }
}

@Composable
internal fun MedicationRegistrationExitSheet(
    editingExisting: Boolean,
    motionAllowed: Boolean,
    onDiscard: () -> Unit,
    onContinueEditing: () -> Unit,
) {
    MedicationLifecycleSheetFrame(
        medication = null,
        title = if (editingExisting) {
            "¿Descartar los cambios?"
        } else {
            "¿Salir del registro?"
        },
        saving = false,
        motionAllowed = motionAllowed,
        onDismiss = onContinueEditing,
    ) {
        Text(
            if (editingExisting) {
                "El medicamento conservará su versión actual. Los cambios provisionales no se guardarán."
            } else {
                "La información ingresada aún no se ha guardado. Puedes seguir editando o descartarla."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MedicationLifecyclePrimaryAction(
            label = "Descartar",
            icon = R.drawable.ic_v2_close,
            enabled = true,
            saving = false,
            destructive = true,
            motionAllowed = motionAllowed,
            onClick = onDiscard,
        )
        MedicationLifecycleCancelAction(
            enabled = true,
            label = "Seguir editando",
            onClick = onContinueEditing,
        )
    }
}

@Composable
internal fun MedicationLifecycleIdentity(medication: MedicationPlan) {
    val configuration = medication.currentRevision.configuration
    val presentation = listOfNotNull(
        configuration?.form?.displayName(),
        configuration?.let {
            listOfNotNull(it.concentrationValue, it.concentrationUnit)
                .joinToString(" ")
                .takeIf(String::isNotBlank)
        },
    ).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            MedicationListProductIcon(
                configuration?.form,
                Modifier.padding(10.dp).size(30.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                medication.currentRevision.medicationName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (presentation.isNotBlank()) {
                Text(
                    presentation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MedicationLifecycleConsequences(
    consequences: List<MedicationLifecycleConsequence>,
    destructive: Boolean,
) {
    val accent = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (destructive) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        },
        border = BorderStroke(
            1.dp,
            if (destructive) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.34f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(BitavidaSpacing.Medium)) {
            consequences.forEachIndexed { index, consequence ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = BitavidaSpacing.Medium),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.12f),
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(consequence.icon),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                colorFilter = ColorFilter.tint(accent),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement =
                            Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                    ) {
                        Text(
                            consequence.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            consequence.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun MedicationTreatmentStatus.lifecycleSheetSpec(): MedicationLifecycleSheetSpec =
    when (this) {
        MedicationTreatmentStatus.SUSPENDED -> MedicationLifecycleSheetSpec(
            title = "¿Suspender el medicamento?",
            subtitle = null,
            reasonLabel = "¿Por qué lo suspendes?",
            confirmLabel = "Suspender medicamento",
            confirmIcon = R.drawable.ic_v2_pause,
            destructive = false,
            consequences = listOf(
                MedicationLifecycleConsequence(
                    icon = R.drawable.ic_v2_pause,
                    title = "Se pausan las próximas tomas",
                    body = "Al confirmar, dejarán de aparecer en Mi día y se pausarán sus avisos",
                ),
                MedicationLifecycleConsequence(
                    icon = R.drawable.ic_v2_history,
                    title = "Tu historial se conserva",
                    body = "Podrás consultar los registros y reactivar el medicamento desde Suspendidos",
                ),
            ),
        )
        MedicationTreatmentStatus.REMOVED -> MedicationLifecycleSheetSpec(
            title = "¿Eliminar el medicamento?",
            subtitle = "Esta acción es permanente",
            reasonLabel = "¿Por qué lo eliminas?",
            confirmLabel = "Eliminar medicamento",
            confirmIcon = R.drawable.ic_medication_delete,
            destructive = true,
            consequences = listOf(
                MedicationLifecycleConsequence(
                    icon = R.drawable.ic_medication_delete,
                    title = "Se retira del plan",
                    body = "Desaparecerá de Medicamentos y Mi día. Se cancelarán las tomas futuras y sus avisos",
                ),
                MedicationLifecycleConsequence(
                    icon = R.drawable.ic_v2_file,
                    title = "Registros anteriores",
                    body = "Solo podrás consultarlos en el reporte PDF",
                ),
            ),
        )
        else -> error("Lifecycle sheet is only available for suspension or removal")
    }
