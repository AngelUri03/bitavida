package com.bitavida.app.ui.medical

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bitavida.app.ui.components.BitavidaDateTimePickerController
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.domain.validation.CaptureLimit
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationReminderPreference
import com.bitavida.app.domain.model.MedicationReminderRuntimeState
import com.bitavida.app.domain.validation.MedicationValueRules
import com.bitavida.app.ui.components.BitavidaConnectedSurface
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.components.BitavidaStatusPill
import com.bitavida.app.ui.components.BitavidaThreadAction
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun MedicationSection(
    state: MedicalPlanUiState,
    motionAllowed: Boolean,
    reminderRuntimeState: MedicationReminderRuntimeState,
    onRequestReminderAccess: () -> Unit,
    onAction: (MedicalPlanAction) -> Unit,
    onHelp: () -> Unit = {},
) {
    val now = java.time.LocalDateTime.now()
    val groupOrder = listOf(
        MedicationPlanDisplayState.CURRENT,
        MedicationPlanDisplayState.UPCOMING,
        MedicationPlanDisplayState.SUSPENDED,
        MedicationPlanDisplayState.COMPLETED,
    )
    val plansWithState = currentMedicationPeriodsForList(
        plans = state.overview.medicationPlans,
        at = now,
        indicationStatus = { plan ->
            plan.medicalIndicationId?.let { indicationId ->
                state.overview.indications
                    .singleOrNull { it.id == indicationId }
                    ?.currentRevision
                    ?.status
            }
        },
    )
    val groups = groupOrder.map { displayState ->
        plansWithState
            .filter { it.second == displayState }
            .map { it.first }
            .let { displayState to it }
    }
    if (plansWithState.isEmpty()) {
        MedicationEmptyState(motionAllowed)
    } else {
        MedicationModularList(
            groups = groups,
            motionAllowed = motionAllowed,
            onOpen = {
                onAction(MedicalPlanAction.OpenMedication(it.id))
            },
        )
    }
    val remindersRequested = plansWithState.any { (plan, _) ->
        plan.currentRevision.configuration?.reminderPreference
            ?.let { it != MedicationReminderPreference.NONE } == true
    }
    if (
        remindersRequested &&
        reminderRuntimeState == MedicationReminderRuntimeState.PERMISSION_REQUIRED
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(
                    start = BitavidaSpacing.Medium,
                    end = BitavidaSpacing.Small,
                ),
                horizontalArrangement = Arrangement.spacedBy(
                    BitavidaSpacing.Small,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Falta permitir los avisos de Bitavida.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onRequestReminderAccess,
                    modifier = Modifier.heightIn(
                        min = BitavidaSpacing.MinimumTouchTarget,
                    ),
                ) {
                    Text("Permitir avisos")
                }
            }
        }
    }
    com.bitavida.app.ui.measurements.MeasurementGuideActions(
        addLabel = "Agregar medicamento",
        onAdd = { onAction(MedicalPlanAction.OpenMedicationRegistration) },
        onHelp = onHelp,
    )
}

@Composable
private fun MedicationEmptyState(motionAllowed: Boolean) {
    var entered by remember(motionAllowed) { mutableStateOf(!motionAllowed) }
    LaunchedEffect(motionAllowed) {
        entered = true
    }
    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(
            tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
        ) + slideInVertically(
            tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
        ) { it / 10 },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Text(
                text = "Aún no hay medicamentos registrados.",
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Agrega los medicamentos indicados para organizar su seguimiento.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}



@Composable
internal fun MedicationRegistration(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
    motionAllowed: Boolean,
) {
    val step = state.medicationRegistrationStep
    Column(
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        if (state.editingExisting) {
            Text(
                text = when (state.medicationEditPurpose) {
                    MedicationEditPurpose.CORRECTION -> "CORREGIR INFORMACIÓN"
                    MedicationEditPurpose.INDICATION_UPDATE -> "ACTUALIZAR INDICACIÓN"
                    null -> "REVISAR MEDICAMENTO"
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Start,
            )
        } else {
            Text(
                text = "Paso " + (step.ordinal + 1) + " de " +
                    MedicationRegistrationStep.entries.size,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
            MedicationStepProgress(
                step = step,
                motionAllowed = motionAllowed,
                completed = state.effect == MedicalPlanEffect.MedicationStored &&
                    step == MedicationRegistrationStep.REVIEW,
            )
            Spacer(Modifier.height(BitavidaSpacing.ExtraSmall))
        }
        GuidedRegistrationStepTransition(
            currentStep = step.ordinal,
            motionAllowed = motionAllowed,
        ) { currentStepIndex ->
            when (MedicationRegistrationStep.entries[currentStepIndex]) {
                MedicationRegistrationStep.NAME ->
                    MedicationNameStep(state, onAction, motionAllowed)
                MedicationRegistrationStep.FORM ->
                    MedicationFormStep(state, onAction, motionAllowed)
                MedicationRegistrationStep.DETAILS ->
                    MedicationDetailsStep(state, onAction, motionAllowed)
                MedicationRegistrationStep.SCHEDULE ->
                    MedicationScheduleStep(state, onAction, motionAllowed)
                MedicationRegistrationStep.REMINDERS ->
                    MedicationRemindersStep(state, onAction, motionAllowed)
                MedicationRegistrationStep.REVIEW ->
                    MedicationFinalReview(state, onAction, motionAllowed)
            }
        }
    }
}

@Composable
private fun MedicationStepProgress(
    step: MedicationRegistrationStep,
    motionAllowed: Boolean,
    completed: Boolean,
) {
    GuidedRegistrationStepProgress(
        currentStep = step.ordinal,
        totalSteps = MedicationRegistrationStep.entries.size,
        motionAllowed = motionAllowed,
        completed = completed,
    )
}


@Composable
private fun MedicationNameStep(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
    motionAllowed: Boolean,
) {
    val draft = state.guidedMedicationDraft
    fun change(next: GuidedMedicationDraft) =
        onAction(MedicalPlanAction.GuidedMedicationDraftChanged(next))
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        StepHeading(
            title = "¿Qué medicamento vas a agregar?",
            body = "Escribe el nombre tal como aparece en la caja, etiqueta o indicación.",
        )
        BitavidaLimitedTextField(
            value = draft.medicationName,
            limit = CaptureLimit.MEDICATION_NAME,
            onValueChange = { change(draft.copy(medicationName = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre del medicamento") },
            isError =
                MedicationRegistrationField.NAME in state.medicationRegistrationErrors,
            supportingText = {
                if (MedicationRegistrationField.NAME in state.medicationRegistrationErrors) {
                    Text("Escribe un nombre para continuar.")
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            minLines = 1,
            maxLines = Int.MAX_VALUE,
        )
        OptionalSectionHeader(
            title = "Concentración o potencia",
            expanded = draft.isConcentrationExpanded,
            motionAllowed = motionAllowed,
            summary = draft.concentrationSummary(),
            onToggle = {
                change(
                    draft.copy(
                        isConcentrationExpanded = !draft.isConcentrationExpanded,
                    ),
                )
            },
        )
        AnimatedVisibility(
            visible = draft.isConcentrationExpanded,
            enter = fadeIn(
                tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
            ) + expandVertically(
                tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
            ),
            exit = fadeOut(
                tween(if (motionAllowed) BitavidaFluidMotion.StateTransition / 2 else 0),
            ) + shrinkVertically(
                tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
            ),
        ) {
            BitavidaConnectedSurface(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.bitavidaColors.paleBlue,
            ) {
                Text(
                    text = "Encuéntrala en la caja, etiqueta o indicación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MedicationValueField(
                    value = draft.concentrationValue,
                    onValueChange = { change(draft.copy(concentrationValue = it)) },
                    label = "Valor",
                    isError =
                        MedicationRegistrationField.CONCENTRATION_VALUE in
                            state.medicationRegistrationErrors,
                )
                UnitSelector(
                    title = "Unidad o expresión",
                    options = concentrationUnits,
                    selected = draft.concentrationUnit,
                    onSelected = { change(draft.copy(concentrationUnit = it)) },
                    hasError =
                        MedicationRegistrationField.CONCENTRATION_UNIT in
                            state.medicationRegistrationErrors,
                )
                AnimatedVisibility(
                    visible = draft.concentrationUnit == CUSTOM_UNIT_OPTION,
                    enter = fadeIn(
                        tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
                    ) + expandVertically(
                        tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
                    ),
                    exit = fadeOut(
                        tween(
                            if (motionAllowed) {
                                BitavidaFluidMotion.StateTransition / 2
                            } else {
                                0
                            },
                        ),
                    ) + shrinkVertically(
                        tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
                    ),
                ) {
                    BitavidaLimitedTextField(
                        value = draft.customConcentrationUnit,
                        limit = CaptureLimit.UNIT,
                        onValueChange = {
                            change(draft.copy(customConcentrationUnit = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Expresión tal como fue indicada") },
                        isError =
                            MedicationRegistrationField.CONCENTRATION_UNIT in
                                state.medicationRegistrationErrors,
                        minLines = 1,
                        maxLines = 2,
                    )
                }
                if (
                    MedicationRegistrationField.CONCENTRATION_VALUE in
                        state.medicationRegistrationErrors ||
                    MedicationRegistrationField.CONCENTRATION_UNIT in
                        state.medicationRegistrationErrors
                ) {
                    Text(
                        text = "Completa valor y unidad, o deja ambos sin especificar.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationFormStep(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
    motionAllowed: Boolean,
) {
    val draft = state.guidedMedicationDraft
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        StepHeading(
            title = "¿Qué forma tiene?",
            body = "Elige la presentación. No cambia la dosis, la vía ni la concentración.",
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            MedicationForm.entries.chunked(2).forEach { rowForms ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    verticalAlignment = Alignment.Top,
                ) {
                    rowForms.forEach { form ->
                        MedicationFormOption(
                            form = form,
                            selected = form == draft.form,
                            onClick = {
                                onAction(
                                    MedicalPlanAction.GuidedMedicationDraftChanged(
                                        draft.copy(form = form),
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                            motionAllowed = motionAllowed,
                        )
                    }
                    if (rowForms.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (MedicationRegistrationField.FORM in state.medicationRegistrationErrors) {
            Text(
                text = "Selecciona una presentación para continuar.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MedicationFormOption(
    form: MedicationForm,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    motionAllowed: Boolean,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.bitavidaColors.mint
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(
            if (motionAllowed) BitavidaFluidMotion.Selection else 0,
        ),
        label = "medication_form_color",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(
            if (motionAllowed) BitavidaFluidMotion.Selection else 0,
        ),
        label = "medication_form_icon_scale",
    )
    BitavidaConnectedSurface(
        modifier = modifier
            .heightIn(min = 96.dp)
            .semantics {
                contentDescription = form.label()
                this.selected = selected
                stateDescription = if (selected) "Seleccionada" else "No seleccionada"
            },
        containerColor = containerColor,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            MedicationFormIcon(
                form = form,
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            )
            Text(
                text = form.gridLabel(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun MedicationFormIcon(
    form: MedicationForm,
    modifier: Modifier = Modifier,
) {
    MedicationProductIcon(
        form = com.bitavida.app.domain.model.MedicationProductForm.valueOf(
            form.name,
        ),
        modifier = modifier.size(34.dp),
    )
}

@Composable
private fun MedicationDetailsStep(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
    motionAllowed: Boolean,
) {
    val draft = state.guidedMedicationDraft
    fun change(next: GuidedMedicationDraft) =
        onAction(MedicalPlanAction.GuidedMedicationDraftChanged(next))
    val contextualUnits = draft.form.amountUnits()
    val unitNeedsReview =
        draft.amountUnit.isNotBlank() && draft.amountUnit !in contextualUnits
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        StepHeading(
            title = "¿Cuánto se administra en cada toma?",
            body = "Es la cantidad indicada para una administración, no la cantidad que contiene la caja.",
        )
        BitavidaConnectedSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            MedicationValueField(
                value = draft.amountValue,
                onValueChange = { change(draft.copy(amountValue = it)) },
                label = "Cantidad",
                isError =
                    MedicationRegistrationField.AMOUNT_VALUE in
                        state.medicationRegistrationErrors,
            )
            UnitSelector(
                title = "Unidad",
                options = (
                    contextualUnits +
                        listOfNotNull(draft.amountUnit.takeIf { it.isNotBlank() })
                    ).distinct(),
                selected = draft.amountUnit,
                onSelected = { change(draft.copy(amountUnit = it)) },
                hasError =
                    MedicationRegistrationField.AMOUNT_UNIT in
                        state.medicationRegistrationErrors,
            )
            AnimatedVisibility(
                visible = draft.amountUnit == CUSTOM_UNIT_OPTION,
                enter = fadeIn(
                    tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
                ) + expandVertically(
                    tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
                ),
                exit = fadeOut(
                    tween(
                        if (motionAllowed) {
                            BitavidaFluidMotion.StateTransition / 2
                        } else {
                            0
                        },
                    ),
                ) + shrinkVertically(
                    tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
                ),
            ) {
                BitavidaLimitedTextField(
                    value = draft.customAmountUnit,
                    limit = CaptureLimit.UNIT,
                    onValueChange = { change(draft.copy(customAmountUnit = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Unidad o expresión indicada") },
                    isError =
                        MedicationRegistrationField.AMOUNT_UNIT in
                            state.medicationRegistrationErrors,
                    minLines = 1,
                    maxLines = 2,
                )
            }
            if (
                MedicationRegistrationField.AMOUNT_UNIT in
                    state.medicationRegistrationErrors
            ) {
                Text(
                    text = "Selecciona o escribe la unidad indicada.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (unitNeedsReview) {
                Text(
                    text = "La unidad registrada se conservó al cambiar la presentación. Revísala antes de continuar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MedicationScheduleStep(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
    motionAllowed: Boolean,
) {
    val draft = state.guidedMedicationDraft
    fun change(next: GuidedMedicationDraft) =
        onAction(MedicalPlanAction.GuidedMedicationDraftChanged(next))

    val heading = when (state.medicationScheduleSubstep) {
        MedicationScheduleSubstep.FREQUENCY ->
            "¿Con qué frecuencia corresponde?"
        MedicationScheduleSubstep.PARAMETERS ->
            "Configura la frecuencia"
        MedicationScheduleSubstep.ADMINISTRATIONS ->
            "Administraciones"
        MedicationScheduleSubstep.VALIDITY ->
            "Vigencia y programación"
    }
    val supportingText = when (state.medicationScheduleSubstep) {
        MedicationScheduleSubstep.FREQUENCY ->
            "Elige únicamente la modalidad que aparece en la indicación recibida."
        MedicationScheduleSubstep.PARAMETERS ->
            "Registra solo los datos necesarios para la modalidad elegida."
        MedicationScheduleSubstep.ADMINISTRATIONS ->
            "Agrega y edita cada momento indicado sin cambiar la cantidad base."
        MedicationScheduleSubstep.VALIDITY ->
            "Define desde cuándo corresponde y revisa la programación capturada."
    }

    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        StepHeading(title = heading, body = supportingText)
        AnimatedContent(
            targetState = state.medicationScheduleSubstep,
            transitionSpec = {
                val duration =
                    if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
                (
                    fadeIn(tween(duration)) +
                        slideInHorizontally(tween(duration)) { it / 12 }
                    ).togetherWith(
                    fadeOut(tween(duration / 2)) +
                        slideOutHorizontally(tween(duration)) { -it / 16 },
                )
            },
            label = "medication_schedule_substep",
        ) { substep ->
            when (substep) {
                MedicationScheduleSubstep.FREQUENCY -> Column(
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    MedicationFrequencySelector(
                        selected = draft.frequency,
                        onSelected = { frequency ->
                            change(draft.copy(frequency = frequency))
                        },
                    )
                    if (
                        MedicationRegistrationField.FREQUENCY in
                        state.medicationRegistrationErrors
                    ) {
                        Text(
                            text = "Selecciona la frecuencia indicada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                MedicationScheduleSubstep.PARAMETERS ->
                    MedicationFrequencyParameters(
                        state = state,
                        draft = draft,
                        onChange = ::change,
                        motionAllowed = motionAllowed,
                    )
                MedicationScheduleSubstep.ADMINISTRATIONS -> {
                    val frequency = draft.frequency
                    if (frequency != null) {
                        MedicationAdministrationsSection(
                            state = state,
                            frequency = frequency,
                            draft = draft,
                            onAction = onAction,
                            onChange = ::change,
                            motionAllowed = motionAllowed,
                        )
                    }
                }
                MedicationScheduleSubstep.VALIDITY -> Column(
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                ) {
                    MedicationValiditySection(
                        draft = draft,
                        errors = state.medicationRegistrationErrors,
                        onChange = ::change,
                        motionAllowed = motionAllowed,
                    )
                    FrequencySummary(
                        title = "Programación registrada",
                        body = draft.scheduleReviewSummary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationFrequencyParameters(
    state: MedicalPlanUiState,
    draft: GuidedMedicationDraft,
    onChange: (GuidedMedicationDraft) -> Unit,
    motionAllowed: Boolean,
) {
    when (draft.frequency) {
        null -> Text(
            text = "Selecciona primero una frecuencia.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MedicationFrequency.EVERY_DAY -> Text(
            text = "Esta modalidad no necesita parámetros adicionales.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MedicationFrequency.SPECIFIC_DAYS -> Column(
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Text(
                text = "Días indicados",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            WeekdaySelector(
                selected = draft.selectedWeekdays,
                onToggle = { weekday ->
                    onChange(
                        draft.copy(
                            selectedWeekdays =
                                draft.selectedWeekdays.toggle(weekday),
                        ),
                    )
                },
            )
            if (
                MedicationRegistrationField.WEEKDAYS in
                state.medicationRegistrationErrors
            ) {
                Text(
                    text = "Selecciona al menos un día indicado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        MedicationFrequency.EVERY_N_DAYS ->
            EveryNDaysConfiguration(
                draft = draft,
                onChange = onChange,
            )
        MedicationFrequency.EVERY_N_HOURS ->
            EveryNHoursConfiguration(
                draft = draft,
                errors = state.medicationRegistrationErrors,
                onChange = onChange,
                motionAllowed = motionAllowed,
            )
        MedicationFrequency.CYCLE ->
            CycleConfiguration(
                state = state,
                draft = draft,
                onChange = onChange,
                motionAllowed = motionAllowed,
            )
        MedicationFrequency.AS_NEEDED ->
            AsNeededConfiguration(
                draft = draft,
                onChange = onChange,
                motionAllowed = motionAllowed,
            )
    }
}

@Composable
private fun MedicationFrequencySelector(
    selected: MedicationFrequency?,
    onSelected: (MedicationFrequency) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        FrequencyFamilyLabel("POR DÍAS")
        ScheduleChoiceRow(
            firstLabel = "Todos los días",
            secondLabel = "Días específicos",
            firstSelected = selected == MedicationFrequency.EVERY_DAY,
            secondSelected = selected == MedicationFrequency.SPECIFIC_DAYS,
            onFirst = { onSelected(MedicationFrequency.EVERY_DAY) },
            onSecond = { onSelected(MedicationFrequency.SPECIFIC_DAYS) },
        )
        ScheduleChoice(
            label = "Cada cierto número de días",
            selected = selected == MedicationFrequency.EVERY_N_DAYS,
            onClick = { onSelected(MedicationFrequency.EVERY_N_DAYS) },
            modifier = Modifier.fillMaxWidth(),
        )
        FrequencyFamilyLabel("POR INTERVALOS O CICLOS")
        ScheduleChoiceRow(
            firstLabel = "Cada cierto número de horas",
            secondLabel = "Por ciclos",
            firstSelected = selected == MedicationFrequency.EVERY_N_HOURS,
            secondSelected = selected == MedicationFrequency.CYCLE,
            onFirst = { onSelected(MedicationFrequency.EVERY_N_HOURS) },
            onSecond = { onSelected(MedicationFrequency.CYCLE) },
        )
        FrequencyFamilyLabel("CUANDO SEA NECESARIO")
        ScheduleChoice(
            label = "Sin programación periódica",
            selected = selected == MedicationFrequency.AS_NEEDED,
            onClick = { onSelected(MedicationFrequency.AS_NEEDED) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FrequencyFamilyLabel(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(top = BitavidaSpacing.ExtraSmall),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EveryNDaysConfiguration(
    draft: GuidedMedicationDraft,
    onChange: (GuidedMedicationDraft) -> Unit,
) {
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        BitavidaLimitedTextField(
            value = draft.everyNDaysInterval,
            limit = CaptureLimit.INTEGER,
            onValueChange = { onChange(draft.copy(everyNDaysInterval = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Número de días") },
            suffix = { Text("días") },
            isError = draft.everyNDaysInterval.isNotBlank() &&
                !draft.everyNDaysInterval.isPositiveWholeNumberText(),
            supportingText = {
                if (
                    draft.everyNDaysInterval.isNotBlank() &&
                    !draft.everyNDaysInterval.isPositiveWholeNumberText()
                ) {
                    Text("Escribe un número entero mayor que cero.")
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )
        DateSelectionField(
            label = "Fecha de referencia",
            date = draft.everyNDaysReferenceDate,
            onClick = {
                showMedicationDatePicker(
                    controller = dateTimePicker,
                    initial = draft.everyNDaysReferenceDate,
                ) { date ->
                    onChange(draft.copy(everyNDaysReferenceDate = date))
                }
            },
        )
        if (
            draft.everyNDaysInterval.isPositiveWholeNumberText() &&
            draft.everyNDaysReferenceDate != null
        ) {
            FrequencySummary(
                title = "Cada " + draft.everyNDaysInterval.trim() + " días",
                body = "Desde " + draft.everyNDaysReferenceDate.displayLabel(),
            )
        }
    }
}

@Composable
private fun EveryNHoursConfiguration(
    draft: GuidedMedicationDraft,
    errors: Set<MedicationRegistrationField>,
    onChange: (GuidedMedicationDraft) -> Unit,
    motionAllowed: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        BitavidaLimitedTextField(
            value = draft.hourlyInterval,
            limit = CaptureLimit.INTEGER,
            onValueChange = { onChange(draft.copy(hourlyInterval = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Número de horas") },
            suffix = { Text("horas") },
            isError = draft.hourlyInterval.isNotBlank() &&
                !draft.hourlyInterval.isPositiveWholeNumberText(),
            supportingText = {
                if (
                    draft.hourlyInterval.isNotBlank() &&
                    !draft.hourlyInterval.isPositiveWholeNumberText()
                ) {
                    Text("Escribe un número entero mayor que cero.")
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )
        Text(
            text = "¿El intervalo continúa durante la noche?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ScheduleChoiceRow(
            firstLabel = "Sí, intervalo continuo",
            secondLabel = "No, solo en el horario indicado",
            firstSelected = draft.hourlyMode == MedicationHourlyMode.CONTINUOUS,
            secondSelected = draft.hourlyMode == MedicationHourlyMode.DAILY_WINDOW,
            onFirst = {
                onChange(draft.copy(hourlyMode = MedicationHourlyMode.CONTINUOUS))
            },
            onSecond = {
                onChange(draft.copy(hourlyMode = MedicationHourlyMode.DAILY_WINDOW))
            },
        )
        AnimatedContent(
            targetState = draft.hourlyMode,
            transitionSpec = {
                val duration =
                    if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
                fadeIn(tween(duration)).togetherWith(fadeOut(tween(duration / 2)))
            },
            label = "hourly_interval_mode",
        ) { mode ->
            when (mode) {
                null -> Unit
                MedicationHourlyMode.CONTINUOUS -> Column(
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    DateSelectionField(
                        label = "Fecha de referencia",
                        date = draft.hourlyReferenceDate,
                        onClick = {
                            showMedicationDatePicker(
                                controller = dateTimePicker,
                                initial = draft.hourlyReferenceDate,
                            ) { date ->
                                onChange(draft.copy(hourlyReferenceDate = date))
                            }
                        },
                    )
                    TimeSelectionRow(
                        label = "Hora de referencia",
                        time = draft.hourlyReferenceTime,
                        onClick = {
                            showMedicationTimePicker(
                                controller = dateTimePicker,
                                initial = draft.hourlyReferenceTime,
                            ) { time ->
                                onChange(draft.copy(hourlyReferenceTime = time))
                            }
                        },
                    )
                    if (
                        draft.hourlyInterval.isPositiveWholeNumberText() &&
                        draft.hourlyReferenceDate != null &&
                        draft.hourlyReferenceTime != null
                    ) {
                        FrequencySummary(
                            title = "Cada " + draft.hourlyInterval.trim() + " horas",
                            body = "Intervalo continuo desde " +
                                draft.hourlyReferenceDate.displayLabel() + ", " +
                                draft.hourlyReferenceTime.displayLabel(context),
                        )
                    }
                }
                MedicationHourlyMode.DAILY_WINDOW -> Column(
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                ) {
                    ResponsiveTimePair(
                        firstLabel = "Desde",
                        firstTime = draft.hourlyWindowStart,
                        onFirstClick = {
                            showMedicationTimePicker(
                                controller = dateTimePicker,
                                initial = draft.hourlyWindowStart,
                            ) { time ->
                                onChange(draft.copy(hourlyWindowStart = time))
                            }
                        },
                        secondLabel = "Hasta",
                        secondTime = draft.hourlyWindowEnd,
                        onSecondClick = {
                            showMedicationTimePicker(
                                controller = dateTimePicker,
                                initial = draft.hourlyWindowEnd,
                            ) { time ->
                                onChange(draft.copy(hourlyWindowEnd = time))
                            }
                        },
                    )
                    NextDayToggle(
                        checked = draft.hourlyWindowEndsNextDay,
                        label = "El horario termina al día siguiente",
                        onCheckedChange = {
                            onChange(draft.copy(hourlyWindowEndsNextDay = it))
                        },
                    )
                    TimeSelectionRow(
                        label = "Primera administración",
                        time = draft.hourlyFirstAdministration,
                        onClick = {
                            showMedicationTimePicker(
                                controller = dateTimePicker,
                                initial = draft.hourlyFirstAdministration,
                            ) { time ->
                                onChange(
                                    draft.copy(
                                        hourlyFirstAdministration = time,
                                    ),
                                )
                            }
                        },
                    )
                    Text(
                        text = "Indica la primera hora expresamente; no se asume que coincide con el inicio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (
                        MedicationRegistrationField
                            .HOURLY_FIRST_ADMINISTRATION in errors
                    ) {
                        Text(
                            text = "La primera administración debe estar dentro del horario indicado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    AnimatedVisibility(visible = draft.hourlyWindowEndsNextDay) {
                        Text(
                            text = "Los días elegidos corresponden al inicio del horario.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (
                        draft.hourlyWindowStart != null &&
                        draft.hourlyWindowEnd != null &&
                        !draft.hourlyWindowEndsNextDay &&
                        draft.hourlyWindowEnd <= draft.hourlyWindowStart
                    ) {
                        Text(
                            text = "Si el horario cruza la medianoche, indica que termina al día siguiente.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = "Días aplicables",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ScheduleChoiceRow(
                        firstLabel = "Todos los días",
                        secondLabel = "Días específicos",
                        firstSelected = draft.hourlyApplicableDaysMode ==
                            MedicationApplicableDaysMode.EVERY_DAY,
                        secondSelected = draft.hourlyApplicableDaysMode ==
                            MedicationApplicableDaysMode.SPECIFIC_DAYS,
                        onFirst = {
                            onChange(
                                draft.copy(
                                    hourlyApplicableDaysMode =
                                        MedicationApplicableDaysMode.EVERY_DAY,
                                ),
                            )
                        },
                        onSecond = {
                            onChange(
                                draft.copy(
                                    hourlyApplicableDaysMode =
                                        MedicationApplicableDaysMode.SPECIFIC_DAYS,
                                ),
                            )
                        },
                    )
                    AnimatedVisibility(
                        visible = draft.hourlyApplicableDaysMode ==
                            MedicationApplicableDaysMode.SPECIFIC_DAYS,
                        enter = fadeIn(
                            tween(
                                if (motionAllowed) {
                                    BitavidaFluidMotion.StateTransition
                                } else {
                                    0
                                },
                            ),
                        ) + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        WeekdaySelector(
                            selected = draft.hourlySelectedWeekdays,
                            onToggle = { weekday ->
                                onChange(
                                    draft.copy(
                                        hourlySelectedWeekdays =
                                            draft.hourlySelectedWeekdays.toggle(weekday),
                                    ),
                                )
                            },
                        )
                    }
                    if (
                        draft.hourlyInterval.isPositiveWholeNumberText() &&
                        draft.hourlyWindowStart != null &&
                        draft.hourlyWindowEnd != null
                    ) {
                        FrequencySummary(
                            title = "Cada " + draft.hourlyInterval.trim() + " horas",
                            body = "Dentro de " +
                                draft.hourlyWindowStart.displayLabel(context) + " – " +
                                draft.hourlyWindowEnd.displayLabel(context) +
                                if (draft.hourlyWindowEndsNextDay) {
                                    " · termina al día siguiente"
                                } else {
                                    ""
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleConfiguration(
    state: MedicalPlanUiState,
    draft: GuidedMedicationDraft,
    onChange: (GuidedMedicationDraft) -> Unit,
    motionAllowed: Boolean,
) {
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    var cycleLimitExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            BitavidaLimitedTextField(
                value = draft.cycleActiveDays,
                limit = CaptureLimit.INTEGER,
                onValueChange = { onChange(draft.copy(cycleActiveDays = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Días activos") },
                isError = MedicationRegistrationField.CYCLE_ACTIVE_DAYS in
                    state.medicationRegistrationErrors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
            )
            BitavidaLimitedTextField(
                value = draft.cycleRestDays,
                limit = CaptureLimit.INTEGER,
                onValueChange = { onChange(draft.copy(cycleRestDays = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Días de descanso") },
                isError = MedicationRegistrationField.CYCLE_REST_DAYS in
                    state.medicationRegistrationErrors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
            )
        }
        DateSelectionField(
            label = "Fecha de inicio del ciclo",
            date = draft.cycleReferenceDate,
            onClick = {
                showMedicationDatePicker(dateTimePicker, draft.cycleReferenceDate) {
                    onChange(draft.copy(cycleReferenceDate = it))
                }
            },
        )
        OptionalSectionHeader(
            title = "Número de ciclos",
            expanded = cycleLimitExpanded,
            motionAllowed = motionAllowed,
            summary = draft.cycleCount.trim().let {
                if (it.isBlank()) "" else "$it ciclos"
            },
            onToggle = { cycleLimitExpanded = !cycleLimitExpanded },
        )
        AnimatedVisibility(
            visible = cycleLimitExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            BitavidaLimitedTextField(
                value = draft.cycleCount,
                limit = CaptureLimit.CYCLE_COUNT,
                onValueChange = { onChange(draft.copy(cycleCount = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Número de ciclos") },
                isError = MedicationRegistrationField.TREATMENT_END in
                    state.medicationRegistrationErrors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        }
        if (
            draft.cycleCount.isNotBlank() &&
            MedicationRegistrationField.TREATMENT_END in
            state.medicationRegistrationErrors
        ) {
            Text(
                text = if (!CaptureLimit.CYCLE_COUNT.acceptsStoredOrNew(
                        draft.cycleCount, state.originalGuidedMedicationDraft?.cycleCount,
                    )
                ) {
                    CaptureLimit.CYCLE_COUNT.message
                } else {
                    "Escribe un número de ciclos mayor que cero."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (
            draft.cycleActiveDays.isPositiveWholeNumberText() &&
            draft.cycleRestDays.isPositiveWholeNumberText() &&
            draft.cycleReferenceDate != null
        ) {
            val activeDays = draft.cycleActiveDays.trim().toLong()
            val restDays = draft.cycleRestDays.trim().toLong()
            val firstDay = draft.cycleReferenceDate
            val activeEnd = firstDay.plusDays(activeDays - 1)
            val restStart = activeEnd.plusDays(1)
            val restEnd = restStart.plusDays(restDays - 1)
            val includeYear = firstDay.year != restEnd.year
            val activePattern = if (activeDays == 1L) {
                "1 día activo"
            } else {
                "$activeDays días activos"
            }
            val restPattern = if (restDays == 1L) {
                "1 día de descanso"
            } else {
                "$restDays días de descanso"
            }
            val countText = draft.cycleCount.trim()
            val count = countText.toIntOrNull()
            val total = when {
                countText.isEmpty() -> "Sin número de ciclos definido"
                count == 1 -> "Ciclo único"
                count != null && count > 1 ->
                    "$count ciclos en total, incluido el primero"
                else -> "Revisa el número de ciclos"
            }
            FrequencySummary(
                title = "$activePattern y $restPattern por ciclo",
                body = "Días activos · " +
                    cyclePreviewDateRange(firstDay, activeEnd, includeYear) +
                    "\nDescanso · " +
                    cyclePreviewDateRange(restStart, restEnd, includeYear) +
                    "\n\n$total",
            )
        }
    }
}

private fun cyclePreviewDateRange(
    start: java.time.LocalDate,
    end: java.time.LocalDate,
    includeYear: Boolean,
): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern(
        if (includeYear || start.year != end.year) "d MMM uuuu" else "d MMM",
        java.util.Locale.forLanguageTag("es-MX"),
    )
    val endLabel = end.format(formatter)
    return when {
        start == end -> endLabel
        start.year == end.year && start.month == end.month ->
            "${start.dayOfMonth}–$endLabel"
        else -> "${start.format(formatter)}–$endLabel"
    }
}

@Composable
private fun AsNeededConfiguration(
    draft: GuidedMedicationDraft,
    onChange: (GuidedMedicationDraft) -> Unit,
    motionAllowed: Boolean,
) {
    var restrictionsExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        BitavidaLimitedTextField(
            value = draft.asNeededInstruction,
            limit = CaptureLimit.INSTRUCTION,
            onValueChange = {
                onChange(draft.copy(asNeededInstruction = it))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Condición o instrucción recibida") },
            supportingText = {
                Text("Regístrala tal como fue indicada, sin interpretarla.")
            },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        )
        OptionalSectionHeader(
            title = "Restricciones adicionales",
            expanded = restrictionsExpanded,
            motionAllowed = motionAllowed,
            summary = draft.asNeededRestrictions,
            onToggle = { restrictionsExpanded = !restrictionsExpanded },
        )
        AnimatedVisibility(
            visible = restrictionsExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            BitavidaLimitedTextField(
                value = draft.asNeededRestrictions,
                limit = CaptureLimit.INSTRUCTION,
                onValueChange = {
                    onChange(draft.copy(asNeededRestrictions = it))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Restricciones indicadas") },
                minLines = 1,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            )
        }
        Text(
            text = "Sin programación periódica.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MedicationValiditySection(
    draft: GuidedMedicationDraft,
    errors: Set<MedicationRegistrationField>,
    onChange: (GuidedMedicationDraft) -> Unit,
    motionAllowed: Boolean,
) {
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    var endExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        Text(
            text = "Inicio del tratamiento",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ScheduleChoiceRow(
            firstLabel = "Desde hoy",
            secondLabel = "Desde otra fecha",
            firstSelected = draft.treatmentStartMode ==
                MedicationTreatmentStartMode.TODAY,
            secondSelected = draft.treatmentStartMode ==
                MedicationTreatmentStartMode.SPECIFIC_DATE,
            onFirst = {
                onChange(
                    draft.copy(
                        treatmentStartMode =
                            MedicationTreatmentStartMode.TODAY,
                    ),
                )
            },
            onSecond = {
                onChange(
                    draft.copy(
                        treatmentStartMode =
                            MedicationTreatmentStartMode.SPECIFIC_DATE,
                    ),
                )
            },
        )
        AnimatedVisibility(
            visible = draft.treatmentStartMode ==
                MedicationTreatmentStartMode.SPECIFIC_DATE,
            enter = fadeIn(
                tween(
                    if (motionAllowed) {
                        BitavidaFluidMotion.StateTransition
                    } else {
                        0
                    },
                ),
            ) + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DateSelectionField(
                label = "Fecha de inicio",
                date = draft.treatmentStartDate,
                onClick = {
                    showMedicationDatePicker(
                        dateTimePicker,
                        draft.treatmentStartDate,
                    ) {
                        onChange(draft.copy(treatmentStartDate = it))
                    }
                },
            )
        }
        if (MedicationRegistrationField.TREATMENT_START in errors) {
            Text(
                text = "Selecciona cuándo inicia el tratamiento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OptionalSectionHeader(
            title = "Fecha de finalización",
            expanded = endExpanded,
            motionAllowed = motionAllowed,
            summary = draft.treatmentEndDate?.displayLabel().orEmpty(),
            onToggle = { endExpanded = !endExpanded },
        )
        AnimatedVisibility(
            visible = endExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                DateSelectionField(
                    label = "Fecha de finalización",
                    date = draft.treatmentEndDate,
                    onClick = {
                        showMedicationDatePicker(dateTimePicker, draft.treatmentEndDate) {
                            onChange(draft.copy(treatmentEndDate = it))
                        }
                    },
                )
                if (draft.treatmentEndDate != null) {
                    TextButton(
                        onClick = {
                            onChange(draft.copy(treatmentEndDate = null))
                        },
                        modifier = Modifier.heightIn(
                            min = BitavidaSpacing.MinimumTouchTarget,
                        ),
                    ) {
                        Text("Quitar fecha de finalización")
                    }
                }
            }
        }
        if (MedicationRegistrationField.TREATMENT_END in errors) {
            Text(
                text = if (
                    draft.frequency == MedicationFrequency.CYCLE &&
                    draft.cycleCount.isNotBlank()
                ) {
                    "La finalización no coincide con el número de ciclos registrado."
                } else {
                    "La fecha de finalización debe ser posterior al inicio."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MedicationAdministrationsSection(
    state: MedicalPlanUiState,
    frequency: MedicationFrequency,
    draft: GuidedMedicationDraft,
    onAction: (MedicalPlanAction) -> Unit,
    onChange: (GuidedMedicationDraft) -> Unit,
    motionAllowed: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    val timingType = draft.timingTypesByFrequency[frequency]
    val editor =
        draft.windowEditorsByFrequency[frequency] ?: MedicationWindowEditor()
    val administrations =
        draft.administrationsByFrequency[frequency].orEmpty()
    var editorExpanded by rememberSaveable(frequency.name) {
        mutableStateOf(editor.editingAdministrationId != null)
    }
    var editorSubmissionPending by rememberSaveable(frequency.name) {
        mutableStateOf(false)
    }
    LaunchedEffect(editor.editingAdministrationId) {
        if (editor.editingAdministrationId != null) {
            editorExpanded = true
        }
    }
    LaunchedEffect(
        administrations,
        state.medicationScheduleError,
        editor.editingAdministrationId,
    ) {
        if (editorSubmissionPending) {
            if (state.medicationScheduleError == null) {
                editorExpanded = false
            }
            editorSubmissionPending = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        if (administrations.isEmpty()) {
            Text(
                text = "Aún no hay administraciones configuradas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                administrations.forEach { administration ->
                    key(administration.id) {
                        MedicationAdministrationCard(
                            administration = administration,
                            originalAdministration = state.originalGuidedMedicationDraft
                                ?.administrationsByFrequency?.get(frequency)
                                ?.singleOrNull { it.id == administration.id },
                            showValidationErrors =
                                MedicationRegistrationField.ADMINISTRATIONS in
                                    state.medicationRegistrationErrors,
                            onEdit = {
                                when (administration.timingType) {
                                    MedicationTimingType.EXACT_TIME ->
                                        showMedicationTimePicker(
                                            controller = dateTimePicker,
                                            initial = administration.exactTime,
                                        ) { time ->
                                            onAction(
                                                MedicalPlanAction
                                                    .UpsertExactMedicationAdministration(
                                                        frequency = frequency,
                                                        editingAdministrationId =
                                                            administration.id,
                                                        time = time,
                                                    ),
                                            )
                                        }
                                    MedicationTimingType.TIME_WINDOW -> {
                                        editorExpanded = true
                                        onAction(
                                            MedicalPlanAction
                                                .EditMedicationWindow(
                                                    frequency = frequency,
                                                    administrationId =
                                                        administration.id,
                                                ),
                                        )
                                    }
                                    MedicationTimingType.ACTIVITY_ONLY -> Unit
                                }
                            },
                            onRemove = {
                                onAction(
                                    MedicalPlanAction
                                        .RemoveMedicationAdministration(
                                            frequency = frequency,
                                            administrationId =
                                                administration.id,
                                        ),
                                )
                            },
                            onDetailsChanged = {
                                onAction(
                                    MedicalPlanAction
                                        .UpdateMedicationAdministration(
                                            frequency = frequency,
                                            administration = it,
                                        ),
                                )
                            },
                            availableUnits = draft.form.amountUnits(),
                            context = context,
                            motionAllowed = motionAllowed,
                        )
                    }
                }
            }
        }

        if (
            MedicationRegistrationField.ADMINISTRATIONS in
            state.medicationRegistrationErrors
        ) {
            Text(
                text = if (administrations.isEmpty()) {
                    "Agrega al menos una administración indicada."
                } else {
                    "Revisa la cantidad, unidad, actividad u hora de aviso obligatoria."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (
            state.recentlyRemovedMedicationAdministration?.frequency ==
            frequency
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BitavidaSpacing.Medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Administración eliminada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        onClick = {
                            onAction(
                                MedicalPlanAction
                                    .UndoRemoveMedicationAdministration,
                            )
                        },
                        modifier = Modifier.heightIn(
                            min = BitavidaSpacing.MinimumTouchTarget,
                        ),
                    ) {
                        Text("Deshacer")
                    }
                }
            }
        }

        GuidedAdministrationEditorAction(
            expanded = editorExpanded,
            onToggle = { editorExpanded = !editorExpanded },
            modifier = Modifier.align(Alignment.End),
        )

        GuidedAdministrationEditorSurface(
            visible = editorExpanded,
            motionAllowed = motionAllowed,
        ) {
                    Text(
                        text = "Tipo de administración",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ScheduleChoiceRow(
                        firstLabel = "Hora exacta",
                        secondLabel = "Ventana horaria",
                        firstSelected =
                            timingType == MedicationTimingType.EXACT_TIME,
                        secondSelected =
                            timingType == MedicationTimingType.TIME_WINDOW,
                        onFirst = {
                            onChange(
                                draft.copy(
                                    timingTypesByFrequency =
                                        draft.timingTypesByFrequency +
                                            (
                                                frequency to
                                                    MedicationTimingType.EXACT_TIME
                                                ),
                                ),
                            )
                        },
                        onSecond = {
                            onChange(
                                draft.copy(
                                    timingTypesByFrequency =
                                        draft.timingTypesByFrequency +
                                            (
                                                frequency to
                                                    MedicationTimingType.TIME_WINDOW
                                                ),
                                ),
                            )
                        },
                    )
                    ScheduleChoice(
                        label = "Relación con actividad, sin hora exacta",
                        selected =
                            timingType == MedicationTimingType.ACTIVITY_ONLY,
                        onClick = {
                            onChange(
                                draft.copy(
                                    timingTypesByFrequency =
                                        draft.timingTypesByFrequency +
                                            (
                                                frequency to
                                                    MedicationTimingType.ACTIVITY_ONLY
                                                ),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible =
                            timingType == MedicationTimingType.EXACT_TIME,
                    ) {
                        TextButton(
                            onClick = {
                                showMedicationTimePicker(
                                    dateTimePicker,
                                    initial = null,
                                ) { time ->
                                    editorSubmissionPending = true
                                    onAction(
                                        MedicalPlanAction
                                            .UpsertExactMedicationAdministration(
                                                frequency = frequency,
                                                editingAdministrationId = null,
                                                time = time,
                                            ),
                                    )
                                }
                            },
                            modifier = Modifier.heightIn(
                                min = BitavidaSpacing.MinimumTouchTarget,
                            ),
                        ) {
                            Text("Elegir hora exacta")
                        }
                    }
                    AnimatedVisibility(
                        visible =
                            timingType == MedicationTimingType.ACTIVITY_ONLY,
                    ) {
                        TextButton(
                            onClick = {
                                editorSubmissionPending = true
                                onAction(
                                    MedicalPlanAction
                                        .AddActivityMedicationAdministration(
                                            frequency,
                                        ),
                                )
                            },
                            modifier = Modifier.heightIn(
                                min = BitavidaSpacing.MinimumTouchTarget,
                            ),
                        ) {
                            Text("Agregar relación")
                        }
                    }
                    AnimatedVisibility(
                        visible =
                            timingType == MedicationTimingType.TIME_WINDOW,
                    ) {
                        MedicationWindowEditor(
                            editor = editor,
                            error = state.medicationScheduleError,
                            onEditorChanged = { nextEditor ->
                                onChange(
                                    draft.copy(
                                        windowEditorsByFrequency =
                                            draft.windowEditorsByFrequency +
                                                (frequency to nextEditor),
                                    ),
                                )
                            },
                            onCommit = {
                                editorSubmissionPending = true
                                onAction(
                                    MedicalPlanAction
                                        .CommitMedicationWindow(frequency),
                                )
                            },
                            onCancel = {
                                onChange(
                                    draft.copy(
                                        windowEditorsByFrequency =
                                            draft.windowEditorsByFrequency -
                                                frequency,
                                    ),
                                )
                                editorSubmissionPending = false
                                editorExpanded = false
                            },
                        )
                    }
                    if (
                        state.medicationScheduleError in setOf(
                            MedicationScheduleError.DUPLICATE_TIME,
                            MedicationScheduleError.OVERLAPPING_SCHEDULE,
                        )
                    ) {
                        Text(
                            text = state.medicationScheduleError
                                ?.scheduleMessage().orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
        }
    }
}

@Composable
private fun FrequencySummary(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.bitavidaColors.paleBlue,
    ) {
        Column(
            modifier = Modifier.padding(BitavidaSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduleChoiceRow(
    firstLabel: String,
    secondLabel: String,
    firstSelected: Boolean,
    secondSelected: Boolean,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
) {
    GuidedRegistrationChoiceRow(
        firstLabel,
        secondLabel,
        firstSelected,
        secondSelected,
        onFirst,
        onSecond,
    )
}

@Composable
private fun ScheduleChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GuidedRegistrationChoice(label, selected, onClick, modifier)
}

@Composable
private fun WeekdaySelector(
    selected: Set<MedicationWeekday>,
    onToggle: (MedicationWeekday) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val oneRow = maxWidth >= 360.dp && fontScale <= 1.3f
        if (oneRow) {
            WeekdayRow(
                weekdays = MedicationWeekday.entries,
                selected = selected,
                onToggle = onToggle,
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                WeekdayRow(
                    weekdays = MedicationWeekday.entries.take(4),
                    selected = selected,
                    onToggle = onToggle,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                ) {
                    Spacer(Modifier.weight(0.5f))
                    MedicationWeekday.entries.drop(4).forEach { weekday ->
                        WeekdayChip(
                            weekday = weekday,
                            selected = weekday in selected,
                            onClick = { onToggle(weekday) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.weight(0.5f))
                }
            }
        }
    }
}

@Composable
private fun WeekdayRow(
    weekdays: List<MedicationWeekday>,
    selected: Set<MedicationWeekday>,
    onToggle: (MedicationWeekday) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        weekdays.forEach { weekday ->
            WeekdayChip(
                weekday = weekday,
                selected = weekday in selected,
                onClick = { onToggle(weekday) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekdayChip(
    weekday: MedicationWeekday,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .semantics {
                contentDescription = weekday.fullLabel()
                this.selected = selected
                stateDescription = if (selected) {
                    "Seleccionado"
                } else {
                    "No seleccionado"
                }
            },
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.bitavidaColors.mint
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Box(
            modifier = Modifier.heightIn(
                min = BitavidaSpacing.MinimumTouchTarget,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = weekday.shortLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun MedicationWindowEditor(
    editor: MedicationWindowEditor,
    error: MedicationScheduleError?,
    onEditorChanged: (MedicationWindowEditor) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    val windowError = error
        ?.takeIf {
            it == MedicationScheduleError.INCOMPLETE_WINDOW ||
                it == MedicationScheduleError.INVALID_WINDOW ||
                it == MedicationScheduleError.END_BEFORE_START_REQUIRES_NEXT_DAY
        }
        ?.scheduleMessage()
    val reminderError = error
        ?.takeIf { it == MedicationScheduleError.REMINDER_OUTSIDE_WINDOW }
        ?.scheduleMessage()
    GuidedTimeWindowEditor(
        editing = editor.editingAdministrationId != null,
        start = editor.start,
        end = editor.end,
        reminder = editor.reminder,
        endsNextDay = editor.endsNextDay,
        formatTime = { it.displayLabel(context) },
        onSelectTime = { initial, selected ->
            showMedicationTimePicker(dateTimePicker, initial, selected)
        },
        onStartChange = { onEditorChanged(editor.copy(start = it)) },
        onEndChange = { onEditorChanged(editor.copy(end = it)) },
        onReminderChange = {
            onEditorChanged(editor.copy(reminder = it))
        },
        onEndsNextDayChange = {
            onEditorChanged(editor.copy(endsNextDay = it))
        },
        windowError = windowError,
        reminderError = reminderError,
        reminderRequired = false,
        onClearReminder = {
            onEditorChanged(
                editor.copy(
                    reminder = null,
                    reminderNextDay = false,
                ),
            )
        },
        onCommit = onCommit,
        onCancel = onCancel,
    )
}

@Composable
private fun ResponsiveTimePair(
    firstLabel: String,
    firstTime: MedicationTime?,
    onFirstClick: () -> Unit,
    secondLabel: String,
    secondTime: MedicationTime?,
    onSecondClick: () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        if (maxWidth >= 320.dp && fontScale <= 1.3f) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                TimeSelectionRow(
                    label = firstLabel,
                    time = firstTime,
                    onClick = onFirstClick,
                    modifier = Modifier.weight(1f),
                )
                TimeSelectionRow(
                    label = secondLabel,
                    time = secondTime,
                    onClick = onSecondClick,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                TimeSelectionRow(
                    label = firstLabel,
                    time = firstTime,
                    onClick = onFirstClick,
                )
                TimeSelectionRow(
                    label = secondLabel,
                    time = secondTime,
                    onClick = onSecondClick,
                )
            }
        }
    }
}

@Composable
private fun DateSelectionField(
    label: String,
    date: java.time.LocalDate?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Medium,
                vertical = BitavidaSpacing.Small,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = date?.displayLabel() ?: "Elegir",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NextDayToggle(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .semantics {
                stateDescription = if (checked) "Seleccionado" else "No seleccionado"
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Small,
                vertical = BitavidaSpacing.ExtraSmall,
            ),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Checkbox(
                checked = checked,
                onCheckedChange = null,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TimeSelectionRow(
    label: String,
    time: MedicationTime?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Medium,
                vertical = BitavidaSpacing.Small,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = time?.displayLabel(
                    androidx.compose.ui.platform.LocalContext.current,
                ) ?: "Elegir",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MedicationAdministrationCard(
    administration: MedicationAdministrationDraft,
    originalAdministration: MedicationAdministrationDraft?,
    showValidationErrors: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDetailsChanged: (MedicationAdministrationDraft) -> Unit,
    availableUnits: List<String>,
    context: android.content.Context,
    motionAllowed: Boolean,
) {
    val activityRequired =
        administration.timingType == MedicationTimingType.ACTIVITY_ONLY
    var activityExpanded by rememberSaveable(administration.id, "activity") {
        mutableStateOf(activityRequired && administration.activity == null)
    }
    var noticeExpanded by rememberSaveable(administration.id, "notice") {
        mutableStateOf(activityRequired && administration.activityNoticeTime == null)
    }
    var amountExpanded by rememberSaveable(administration.id, "amount") {
        mutableStateOf(false)
    }
    val amountHasError = administration.amountOverride?.let {
        !MedicationValueRules.isValidReplacement(
            it.value, originalAdministration?.amountOverride?.value,
        ) || it.unit.isBlank()
    } == true
    val activityHasError = administration.activity == MedicationActivityOption.OTHER &&
        (administration.customActivity.isBlank() ||
            !CaptureLimit.SHORT_LABEL.acceptsStoredOrNew(
                administration.customActivity, originalAdministration?.customActivity,
            ))
    LaunchedEffect(showValidationErrors, amountHasError, activityHasError) {
        if (showValidationErrors && amountHasError) amountExpanded = true
        if (showValidationErrors && activityHasError) activityExpanded = true
    }
    val accessibleScheduleLabel = when (administration.timingType) {
        MedicationTimingType.EXACT_TIME ->
            administration.exactTime?.displayLabel(context).orEmpty()
        MedicationTimingType.TIME_WINDOW -> administration.windowLabel(context)
        MedicationTimingType.ACTIVITY_ONLY -> "relación con actividad"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Medium,
                vertical = BitavidaSpacing.Small,
            ),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            GuidedAdministrationHeader(
                title = when (administration.timingType) {
                    MedicationTimingType.EXACT_TIME ->
                        administration.exactTime?.displayLabel(context).orEmpty()
                    MedicationTimingType.TIME_WINDOW ->
                        administration.windowLabel(context)
                    MedicationTimingType.ACTIVITY_ONLY ->
                        administration.activity?.activityLabel(
                            administration.customActivity,
                        ) ?: "Actividad sin hora exacta"
                },
                subtitle = when (administration.timingType) {
                    MedicationTimingType.EXACT_TIME -> "Hora exacta"
                    MedicationTimingType.TIME_WINDOW -> "Ventana horaria"
                    MedicationTimingType.ACTIVITY_ONLY -> "Relación con actividad"
                },
                supportingText = administration.amountOverride?.let {
                    "Cantidad: ${it.value} ${it.unit}"
                },
                editDescription = "Editar horario $accessibleScheduleLabel",
                removeDescription = "Quitar horario $accessibleScheduleLabel",
                onEdit = onEdit.takeIf {
                    administration.timingType != MedicationTimingType.ACTIVITY_ONLY
                },
                onRemove = onRemove,
            )

            if (activityRequired) {
                OptionalSectionHeader(
                    title = "Actividad indicada",
                    expanded = activityExpanded,
                    motionAllowed = motionAllowed,
                    optional = false,
                    summary = administration.activity?.activityLabel(
                        administration.customActivity,
                    ).orEmpty(),
                    onToggle = { activityExpanded = !activityExpanded },
                )
                AnimatedVisibility(
                    visible = activityExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    AdministrationActivityEditor(
                        administration = administration,
                        originalActivity = originalAdministration?.customActivity,
                        showValidationErrors = showValidationErrors,
                        onDetailsChanged = onDetailsChanged,
                        onSelectionComplete = {
                            activityExpanded = false
                            if (administration.activityNoticeTime == null) {
                                noticeExpanded = true
                            }
                        },
                    )
                }
            }

            if (administration.timingType == MedicationTimingType.EXACT_TIME) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        BitavidaSpacing.Small,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Avisar en esta administración",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (administration.exactReminderEnabled) {
                                "El aviso usará la misma hora indicada."
                            } else {
                                "Esta administración no generará aviso."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = administration.exactReminderEnabled,
                        onCheckedChange = {
                            onDetailsChanged(
                                administration.copy(exactReminderEnabled = it),
                            )
                        },
                    )
                }
            } else {
                OptionalSectionHeader(
                    title = if (
                        administration.timingType ==
                        MedicationTimingType.TIME_WINDOW
                    ) {
                        "Aviso dentro de la ventana"
                    } else {
                        "Hora de aviso obligatoria"
                    },
                    expanded = noticeExpanded,
                    motionAllowed = motionAllowed,
                    optional = administration.timingType ==
                        MedicationTimingType.TIME_WINDOW,
                    summary = (
                        administration.reminderTime ?:
                        administration.activityNoticeTime
                        )?.displayLabel(context).orEmpty(),
                    onToggle = { noticeExpanded = !noticeExpanded },
                )
                AnimatedVisibility(
                    visible = noticeExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    AdministrationNoticeEditor(
                        administration = administration,
                        onDetailsChanged = onDetailsChanged,
                    )
                }
            }

            OptionalSectionHeader(
                title = "Cantidad distinta para esta administración",
                expanded = amountExpanded,
                motionAllowed = motionAllowed,
                summary = administration.amountOverride?.let {
                    "${it.value} ${it.unit}"
                }.orEmpty(),
                onToggle = { amountExpanded = !amountExpanded },
            )
            AnimatedVisibility(
                visible = amountExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AdministrationAmountEditor(
                    administration = administration,
                    originalValue = originalAdministration?.amountOverride?.value,
                    showValidationErrors = showValidationErrors,
                    availableUnits = availableUnits,
                    onDetailsChanged = onDetailsChanged,
                )
            }
        }
    }
}

@Composable
private fun AdministrationActivityEditor(
    administration: MedicationAdministrationDraft,
    originalActivity: String?,
    showValidationErrors: Boolean,
    onDetailsChanged: (MedicationAdministrationDraft) -> Unit,
    onSelectionComplete: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        UnitSelector(
            title = "Actividad indicada",
            selected = administration.activity?.activityLabel(
                administration.customActivity,
            ).orEmpty(),
            options = MedicationActivityOption.entries.map {
                it.activityLabel("")
            },
            allowClearSelection = false,
            onSelected = { label ->
                val selectedActivity = MedicationActivityOption.entries.single {
                    it.activityLabel("") == label
                }
                onDetailsChanged(
                    administration.copy(
                        activity = selectedActivity,
                        customActivity =
                            if (selectedActivity == MedicationActivityOption.OTHER) {
                                administration.customActivity
                            } else {
                                ""
                            },
                    ),
                )
                if (selectedActivity != MedicationActivityOption.OTHER) {
                    onSelectionComplete()
                }
            },
            hasError = false,
        )
        if (administration.activity == MedicationActivityOption.OTHER) {
            BitavidaLimitedTextField(
                value = administration.customActivity,
                limit = CaptureLimit.SHORT_LABEL,
                onValueChange = {
                    onDetailsChanged(
                        administration.copy(customActivity = it),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Otra actividad") },
                isError = administration.customActivity.isBlank() ||
                    (showValidationErrors && !CaptureLimit.SHORT_LABEL.acceptsStoredOrNew(
                        administration.customActivity, originalActivity,
                    )),
                supportingText = {
                    if (administration.customActivity.isBlank()) {
                        Text("Describe la actividad indicada.")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }
}

@Composable
private fun AdministrationNoticeEditor(
    administration: MedicationAdministrationDraft,
    onDetailsChanged: (MedicationAdministrationDraft) -> Unit,
) {
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    if (administration.timingType == MedicationTimingType.TIME_WINDOW) {
        var reminderOutsideWindow by remember(administration) {
            mutableStateOf(false)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            TimeSelectionRow(
                label = "Aviso dentro de la ventana",
                time = administration.reminderTime,
                onClick = {
                    reminderOutsideWindow = false
                    showMedicationTimePicker(
                        dateTimePicker,
                        administration.reminderTime ?: administration.windowStart,
                    ) { time ->
                        val start = administration.windowStart
                        val end = administration.windowEnd
                        val reminderNextDay =
                            administration.endsNextDay && start != null && time < start
                        if (
                            start != null && end != null &&
                            time.isWithinWindow(
                                start = start,
                                end = end,
                                endsNextDay = administration.endsNextDay,
                                reminderNextDay = reminderNextDay,
                            )
                        ) {
                            onDetailsChanged(
                                administration.copy(
                                    reminderTime = time,
                                    reminderNextDay = reminderNextDay,
                                ),
                            )
                        } else {
                            reminderOutsideWindow = true
                        }
                    }
                },
            )
            if (reminderOutsideWindow) {
                Text(
                    text = MedicationScheduleError.REMINDER_OUTSIDE_WINDOW
                        .scheduleMessage(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = "Se conserva como aviso; no crea otra administración.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            TimeSelectionRow(
                label = "Hora de aviso obligatoria",
                time = administration.activityNoticeTime,
                onClick = {
                    showMedicationTimePicker(
                        dateTimePicker,
                        administration.activityNoticeTime,
                    ) {
                        onDetailsChanged(
                            administration.copy(activityNoticeTime = it),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun AdministrationAmountEditor(
    administration: MedicationAdministrationDraft,
    originalValue: String?,
    showValidationErrors: Boolean,
    availableUnits: List<String>,
    onDetailsChanged: (MedicationAdministrationDraft) -> Unit,
) {
    val amount = administration.amountOverride
    val invalidAmount =
        showValidationErrors && amount != null &&
            !MedicationValueRules.isValidReplacement(amount.value, originalValue)
    val missingUnit =
        amount != null && amount.value.isNotBlank() && amount.unit.isBlank()
    Column(
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        MedicationValueField(
            value = amount?.value.orEmpty(),
            onValueChange = {
                onDetailsChanged(
                    administration.copy(
                        amountOverride = MedicationAdministrationAmount(
                            value = it,
                            unit = amount?.unit.orEmpty(),
                        ),
                    ),
                )
            },
            label = "Cantidad",
            isError = invalidAmount,
        )
        UnitSelector(
            title = "Unidad indicada",
            selected = amount?.unit.orEmpty(),
            options = availableUnits.filterNot {
                it == CUSTOM_UNIT_OPTION
            },
            onSelected = {
                onDetailsChanged(
                    administration.copy(
                        amountOverride = MedicationAdministrationAmount(
                            value = amount?.value.orEmpty(),
                            unit = it,
                        ),
                    ),
                )
            },
            hasError = missingUnit,
        )
        BitavidaLimitedTextField(
            value = amount?.unit.orEmpty(),
            limit = CaptureLimit.UNIT,
            onValueChange = {
                onDetailsChanged(
                    administration.copy(
                        amountOverride = MedicationAdministrationAmount(
                            value = amount?.value.orEmpty(),
                            unit = it,
                        ),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Otra unidad indicada") },
            isError = missingUnit,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        if (missingUnit) {
            Text(
                text = "Selecciona o escribe la unidad indicada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (amount != null) {
            TextButton(
                onClick = {
                    onDetailsChanged(
                        administration.copy(amountOverride = null),
                    )
                },
                modifier = Modifier.heightIn(
                    min = BitavidaSpacing.MinimumTouchTarget,
                ),
            ) {
                Text("Usar la cantidad base")
            }
        }
    }
}

private fun Set<MedicationWeekday>.toggle(
    weekday: MedicationWeekday,
): Set<MedicationWeekday> =
    if (weekday in this) this - weekday else this + weekday

private fun String.isPositiveWholeNumberText(): Boolean =
    trim().toIntOrNull()?.let { it > 0 } == true

internal fun showMedicationDatePicker(
    controller: BitavidaDateTimePickerController,
    initial: java.time.LocalDate?,
    onSelected: (java.time.LocalDate) -> Unit,
) {
    controller.showDate(
        selectedDate = initial ?: java.time.LocalDate.now(),
        onConfirm = onSelected,
    )
}

internal fun java.time.LocalDate.displayLabel(): String =
    format(
        java.time.format.DateTimeFormatter
            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            .withLocale(java.util.Locale.getDefault()),
    )

internal fun showMedicationTimePicker(
    controller: BitavidaDateTimePickerController,
    initial: MedicationTime?,
    onSelected: (MedicationTime) -> Unit,
) {
    val now = java.time.LocalTime.now()
    controller.showTime(
        selectedTime = java.time.LocalTime.of(
            initial?.hour ?: now.hour,
            initial?.minute ?: now.minute,
        ),
        onConfirm = { onSelected(MedicationTime(it.hour, it.minute)) },
    )
}

internal fun MedicationTime.displayLabel(context: android.content.Context): String {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}

private fun MedicationAdministrationDraft.windowLabel(
    context: android.content.Context,
): String = windowStart?.displayLabel(context).orEmpty() + " – " +
    windowEnd?.displayLabel(context).orEmpty() +
    if (endsNextDay) " · día siguiente" else ""

private fun MedicationWeekday.shortLabel(): String = when (this) {
    MedicationWeekday.MONDAY -> "L"
    MedicationWeekday.TUESDAY -> "Ma"
    MedicationWeekday.WEDNESDAY -> "Mi"
    MedicationWeekday.THURSDAY -> "J"
    MedicationWeekday.FRIDAY -> "V"
    MedicationWeekday.SATURDAY -> "S"
    MedicationWeekday.SUNDAY -> "D"
}

private fun MedicationWeekday.fullLabel(): String = when (this) {
    MedicationWeekday.MONDAY -> "Lunes"
    MedicationWeekday.TUESDAY -> "Martes"
    MedicationWeekday.WEDNESDAY -> "Miércoles"
    MedicationWeekday.THURSDAY -> "Jueves"
    MedicationWeekday.FRIDAY -> "Viernes"
    MedicationWeekday.SATURDAY -> "Sábado"
    MedicationWeekday.SUNDAY -> "Domingo"
}

private fun MedicationScheduleError.scheduleMessage(): String = when (this) {
    MedicationScheduleError.DUPLICATE_TIME ->
        "Ese horario ya está registrado."
    MedicationScheduleError.INCOMPLETE_WINDOW ->
        "Selecciona el inicio y el final de la ventana."
    MedicationScheduleError.INVALID_WINDOW ->
        "El inicio y el final deben ser diferentes."
    MedicationScheduleError.END_BEFORE_START_REQUIRES_NEXT_DAY ->
        "Indica que termina al día siguiente si la ventana cruza la medianoche."
    MedicationScheduleError.REMINDER_OUTSIDE_WINDOW ->
        "El momento del aviso debe estar dentro de la ventana indicada."
    MedicationScheduleError.OVERLAPPING_SCHEDULE ->
        "Ese horario se superpone con otro. Revísalos antes de continuar."
}

@Composable
private fun OptionalSectionHeader(
    title: String,
    expanded: Boolean,
    motionAllowed: Boolean,
    summary: String,
    onToggle: () -> Unit,
    optional: Boolean = true,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(
            if (motionAllowed) BitavidaFluidMotion.Selection else 0,
        ),
        label = "optional_section_chevron",
    )
    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .semantics {
                stateDescription = if (expanded) "Expandido" else "Contraído"
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Medium,
                vertical = BitavidaSpacing.ExtraSmall,
            ),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (optional) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.bitavidaColors.paleBlue,
                    ) {
                        Text(
                            text = "Opcional",
                            modifier = Modifier.padding(
                                horizontal = BitavidaSpacing.Small,
                                vertical = 4.dp,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_v2_chevron),
                    contentDescription = if (expanded) "Contraer" else "Expandir",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            if (!expanded && summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun UnitSelector(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    hasError: Boolean,
    allowClearSelection: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (hasError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                onSelected(
                                    if (isSelected && allowClearSelection) "" else option,
                                )
                            },
                        )
                        .semantics {
                            this.selected = isSelected
                            stateDescription = if (isSelected) {
                                "Seleccionada"
                            } else {
                                "No seleccionada"
                            }
                        },
                    shape = CircleShape,
                    color = if (isSelected) {
                        MaterialTheme.bitavidaColors.paleBlue
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                            .padding(horizontal = BitavidaSpacing.Medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationRemindersStep(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
    motionAllowed: Boolean,
) {
    val draft = state.guidedMedicationDraft
    fun change(next: GuidedMedicationDraft) =
        onAction(MedicalPlanAction.GuidedMedicationDraftChanged(next))
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        StepHeading(
            title = "Recordatorios",
            body = "Elige cómo deseas recibir avisos locales para este medicamento.",
        )
        if (draft.frequency == MedicationFrequency.AS_NEEDED) {
            ScheduleChoice(
                label = "Sin recordatorios",
                selected = draft.reminderOption ==
                    MedicationReminderOption.NONE,
                onClick = {
                    change(
                        draft.copy(
                            reminderOption = MedicationReminderOption.NONE,
                            secondReminderEnabled = false,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Sin un horario concreto no se inventará una hora de aviso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ScheduleChoiceRow(
                firstLabel = "Sin recordatorios",
                secondLabel = "Recordatorios normales",
                firstSelected = draft.reminderOption ==
                    MedicationReminderOption.NONE,
                secondSelected = draft.reminderOption ==
                    MedicationReminderOption.NORMAL,
                onFirst = {
                    change(
                        draft.copy(
                            reminderOption = MedicationReminderOption.NONE,
                            secondReminderEnabled = false,
                        ),
                    )
                },
                onSecond = {
                    change(
                        draft.copy(
                            reminderOption = MedicationReminderOption.NORMAL,
                        ),
                    )
                },
            )
            ScheduleChoice(
                label = "Recordatorios destacados",
                selected = draft.reminderOption ==
                    MedicationReminderOption.PROMINENT,
                onClick = {
                    change(
                        draft.copy(
                            reminderOption =
                                MedicationReminderOption.PROMINENT,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(
            visible = draft.reminderOption != null &&
                draft.reminderOption != MedicationReminderOption.NONE &&
                draft.frequency != MedicationFrequency.AS_NEEDED,
            enter = fadeIn(
                tween(
                    if (motionAllowed) {
                        BitavidaFluidMotion.StateTransition
                    } else {
                        0
                    },
                ),
            ) + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                NextDayToggle(
                    checked = draft.secondReminderEnabled,
                    label = "Conservar preferencia para un segundo aviso",
                    onCheckedChange = {
                        change(draft.copy(secondReminderEnabled = it))
                    },
                )
                if (draft.secondReminderEnabled) {
                    Text(
                        text = "Requiere poder verificar que la administración sigue sin confirmar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (
            draft.reminderOption != null &&
            draft.reminderOption != MedicationReminderOption.NONE
        ) {
            val includedAdministrations = draft.frequency?.let { frequency ->
                draft.administrationsByFrequency[frequency].orEmpty()
                    .filter { administration ->
                        when (administration.timingType) {
                            MedicationTimingType.EXACT_TIME ->
                                administration.exactReminderEnabled
                            MedicationTimingType.TIME_WINDOW ->
                                administration.reminderTime != null
                            MedicationTimingType.ACTIVITY_ONLY ->
                                administration.activityNoticeTime != null
                        }
                    }
            }.orEmpty()
            val participationText = when {
                draft.frequency == MedicationFrequency.EVERY_N_HOURS ->
                    "Incluidas: administraciones calculadas desde el intervalo y su referencia."
                includedAdministrations.isEmpty() ->
                    "Ninguna administración tiene aviso individual activado."
                else -> "Incluidas: " + includedAdministrations.joinToString(", ") {
                    administration ->
                    when (administration.timingType) {
                        MedicationTimingType.EXACT_TIME ->
                            administration.exactTime?.compactLabel().orEmpty()
                        MedicationTimingType.TIME_WINDOW ->
                            administration.reminderTime?.compactLabel().orEmpty()
                        MedicationTimingType.ACTIVITY_ONLY ->
                            administration.activity?.activityLabel(
                                administration.customActivity,
                            ) ?: "actividad indicada"
                    }
                }
            }
            Text(
                text = participationText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Android solicitará permiso cuando sea necesario. La precisión depende de los ajustes del sistema.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (
            MedicationRegistrationField.REMINDER_PREFERENCE in
            state.medicationRegistrationErrors
        ) {
            Text(
                text = "Elige una opción de recordatorio para continuar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MedicationFinalReview(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
    motionAllowed: Boolean,
) {
    val draft = state.guidedMedicationDraft
    val isNewPeriod = state.newMedicationPeriodSourcePlanId != null
    val stored = state.effect in setOf(
        MedicalPlanEffect.MedicationStored,
        MedicalPlanEffect.MedicationUpdated,
    )
    LaunchedEffect(stored, motionAllowed) {
        if (stored) {
            delay(
                (
                    if (motionAllowed) {
                        BitavidaFluidMotion.StateTransition + 120
                    } else {
                        80
                    }
                    ).milliseconds,
            )
            onAction(MedicalPlanAction.CompleteGuidedMedicationSuccess)
        }
    }
    val concentration = draft.concentrationSummary()
    val amount = draft.amountSummary()
    val administrationCount = draft.frequency
        ?.let { draft.administrationsByFrequency[it].orEmpty().size }
        ?: 0
    val administrationSummary = when (draft.frequency) {
        MedicationFrequency.EVERY_N_HOURS -> "Intervalo horario configurado"
        MedicationFrequency.AS_NEEDED -> "Uso cuando sea necesario"
        else -> if (administrationCount == 1) {
            "1 administración configurada"
        } else {
            "$administrationCount administraciones configuradas"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        StepHeading(
            title = when {
                isNewPeriod -> "Revisar nuevo periodo"
                state.editingExisting -> "Revisar medicamento"
                else -> "Revisa tu medicamento"
            },
            body = when {
                isNewPeriod ->
                    "Confirma la nueva indicación, su vigencia y horarios. El periodo anterior permanecerá cerrado."
                state.medicationEditPurpose == MedicationEditPurpose.CORRECTION ->
                    "Corrige únicamente lo que se capturó de forma incorrecta. La versión anterior se conservará."
                state.medicationEditPurpose == MedicationEditPurpose.INDICATION_UPDATE ->
                    "Registra la nueva indicación transcrita. Se aplicará desde esta revisión sin modificar registros anteriores."
                else -> "Confirma toda la información antes de agregarlo."
            },
        )
        BitavidaConnectedSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.bitavidaColors.mint,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedicationFormIcon(draft.form ?: MedicationForm.OTHER)
                Text(
                    text = draft.medicationName.trim(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!isNewPeriod) {
                    EditIconButton("Editar nombre") {
                        onAction(
                            MedicalPlanAction.EditMedicationRegistrationStep(
                                MedicationRegistrationStep.NAME,
                            ),
                        )
                    }
                }
            }
            if (concentration.isNotBlank()) {
                DraftPreviewRow(
                    title = "Concentración o potencia",
                    value = concentration,
                    editDescription = "Editar concentración",
                    onEdit = if (isNewPeriod) {
                        null
                    } else {
                        {
                            onAction(
                                MedicalPlanAction.EditMedicationRegistrationStep(
                                    MedicationRegistrationStep.NAME,
                                ),
                            )
                        }
                    },
                )
            }
            DraftPreviewRow(
                title = "Forma",
                value = draft.form?.label().orEmpty(),
                editDescription = "Editar forma",
                onEdit = if (isNewPeriod) {
                    null
                } else {
                    {
                        onAction(
                            MedicalPlanAction.EditMedicationRegistrationStep(
                                MedicationRegistrationStep.FORM,
                            ),
                        )
                    }
                },
            )
            DraftPreviewRow(
                "Cantidad por administración",
                amount,
                "Editar cantidad",
            ) {
                onAction(
                    MedicalPlanAction.EditMedicationRegistrationStep(
                        MedicationRegistrationStep.DETAILS,
                    ),
                )
            }
            DraftPreviewRow(
                "Frecuencia",
                draft.scheduleReviewSummary(),
                "Editar periodicidad",
            ) {
                onAction(
                    MedicalPlanAction.EditMedicationRegistrationStep(
                        step = MedicationRegistrationStep.SCHEDULE,
                        scheduleSubstep = MedicationScheduleSubstep.FREQUENCY,
                    ),
                )
            }
            DraftPreviewRow(
                "Administraciones",
                administrationSummary,
                "Editar administraciones",
            ) {
                onAction(
                    MedicalPlanAction.EditMedicationRegistrationStep(
                        step = MedicationRegistrationStep.SCHEDULE,
                        scheduleSubstep = MedicationScheduleSubstep.ADMINISTRATIONS,
                    ),
                )
            }
            DraftPreviewRow(
                "Vigencia",
                draft.validityReviewSummary(),
                "Editar vigencia",
            ) {
                onAction(
                    MedicalPlanAction.EditMedicationRegistrationStep(
                        step = MedicationRegistrationStep.SCHEDULE,
                        scheduleSubstep = MedicationScheduleSubstep.VALIDITY,
                    ),
                )
            }
            DraftPreviewRow(
                "Recordatorios",
                draft.reminderReviewSummary(),
                "Editar recordatorios",
            ) {
                onAction(
                    MedicalPlanAction.EditMedicationRegistrationStep(
                        MedicationRegistrationStep.REMINDERS,
                    ),
                )
            }
        }
        AnimatedVisibility(
            visible = stored,
            enter = fadeIn(
                tween(if (motionAllowed) BitavidaFluidMotion.Selection else 0),
            ) + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.bitavidaColors.paleBlue,
            ) {
                Text(
                    text = when {
                        isNewPeriod -> "Nuevo periodo iniciado"
                        state.editingExisting -> "Cambios guardados"
                        else -> "Medicamento agregado"
                    },
                    modifier = Modifier.padding(BitavidaSpacing.Small),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        state.medicationRegistrationSaveError?.let { message ->
            BitavidaConnectedSurface(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.bitavidaColors.roseStrong,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        BitavidaThreadAction(
            label = when {
                state.isSaving && isNewPeriod -> "Iniciando nuevo periodo…"
                state.isSaving && state.editingExisting -> "Guardando cambios…"
                state.isSaving -> "Agregando…"
                stored && isNewPeriod -> "Nuevo periodo iniciado"
                stored && state.editingExisting -> "Cambios guardados"
                stored -> "Medicamento agregado"
                isNewPeriod -> "Iniciar nuevo periodo"
                state.editingExisting -> "Confirmar cambios"
                else -> "Agregar medicamento"
            },
            iconRes = if (state.editingExisting || isNewPeriod) {
                R.drawable.ic_v2_pill
            } else {
                R.drawable.ic_v2_plus
            },
            onClick = {
                onAction(MedicalPlanAction.SaveGuidedMedication)
            },
            modifier = Modifier.fillMaxWidth(),
            emphasized = true,
            enabled = !state.isSaving && !stored &&
                (!state.editingExisting || state.hasMedicationChanges),
        )
    }
}

@Composable
private fun DraftPreviewRow(
    title: String,
    value: String,
    editDescription: String,
    onEdit: (() -> Unit)?,
) {
    GuidedRegistrationReviewRow(title, value, editDescription, onEdit)
}

@Composable
private fun EditIconButton(
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget),
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(android.R.drawable.ic_menu_edit),
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun StepHeading(
    title: String,
    body: String,
) {
    GuidedRegistrationStepHeading(title, body)
}



private fun GuidedMedicationDraft.scheduleReviewSummary(): String {
    val selected = frequency ?: return "Sin especificar"
    val heading = when (selected) {
        MedicationFrequency.EVERY_DAY -> "Todos los días"
        MedicationFrequency.SPECIFIC_DAYS ->
            selectedWeekdays.sortedBy { it.ordinal }
                .joinToString(", ") { it.shortLabel() }
        MedicationFrequency.EVERY_N_DAYS ->
            "Cada ${everyNDaysInterval.trim()} días desde " +
                (everyNDaysReferenceDate?.displayLabel() ?: "fecha pendiente")
        MedicationFrequency.EVERY_N_HOURS ->
            if (hourlyMode == MedicationHourlyMode.CONTINUOUS) {
                "Cada ${hourlyInterval.trim()} horas de forma continua"
            } else {
                "Cada ${hourlyInterval.trim()} horas dentro del horario indicado"
            }
        MedicationFrequency.CYCLE ->
            "${cycleActiveDays.trim()} días activos · " +
                "${cycleRestDays.trim()} de descanso"
        MedicationFrequency.AS_NEEDED ->
            "Cuando sea necesario: ${asNeededInstruction.trim()}"
    }
    val administrations = administrationsByFrequency[selected].orEmpty()
    if (administrations.isEmpty()) return heading
    val details = administrations.joinToString(" · ") { administration ->
        val timing = when (administration.timingType) {
            MedicationTimingType.EXACT_TIME ->
                administration.exactTime?.compactLabel().orEmpty()
            MedicationTimingType.TIME_WINDOW ->
                administration.windowStart?.compactLabel().orEmpty() + "–" +
                    administration.windowEnd?.compactLabel().orEmpty() +
                    if (administration.endsNextDay) {
                        " (día siguiente)"
                    } else {
                        ""
                    }
            MedicationTimingType.ACTIVITY_ONLY ->
                (administration.activity?.activityLabel(
                    administration.customActivity,
                ) ?: "Actividad pendiente") +
                    " · aviso " +
                    (administration.activityNoticeTime?.compactLabel()
                        ?: "pendiente")
        }
        val amount = administration.amountOverride?.let {
            " · ${it.value} ${it.unit}"
        }.orEmpty()
        timing + amount
    }
    return "$heading · $details"
}

private fun GuidedMedicationDraft.validityReviewSummary(): String {
    val start = when (treatmentStartMode) {
        MedicationTreatmentStartMode.TODAY ->
            "Desde hoy (${java.time.LocalDate.now().displayLabel()})"
        MedicationTreatmentStartMode.SPECIFIC_DATE ->
            "Desde " + (treatmentStartDate?.displayLabel() ?: "fecha pendiente")
        null -> "Inicio sin especificar"
    }
    return start + (treatmentEndDate?.let {
        " · hasta ${it.displayLabel()}"
    } ?: " · sin finalización registrada")
}

private fun GuidedMedicationDraft.reminderReviewSummary(): String = when (
    reminderOption
) {
    MedicationReminderOption.NONE -> "Sin recordatorios"
    MedicationReminderOption.NORMAL -> "Recordatorios normales"
    MedicationReminderOption.PROMINENT -> "Recordatorios destacados"
    null -> "Sin especificar"
}

internal fun MedicationReminderPreference.reminderRuntimeLabel(
    runtimeState: MedicationReminderRuntimeState,
): String = when (this) {
    MedicationReminderPreference.NONE -> "Avisos desactivados"
    else -> runtimeState.runtimeStatusLabel()
}

private fun MedicationReminderRuntimeState.runtimeStatusLabel(): String =
    when (this) {
        MedicationReminderRuntimeState.DISABLED -> "Avisos desactivados"
        MedicationReminderRuntimeState.PERMISSION_REQUIRED ->
            "Permiso de avisos pendiente"
        MedicationReminderRuntimeState.NO_SCHEDULED_NOTICE ->
            "Preferencia guardada · sin horario de aviso explícito"
        MedicationReminderRuntimeState.ACTIVE -> "Avisos activos"
        MedicationReminderRuntimeState.SYSTEM_LIMITED ->
            "Avisos con precisión limitada por el sistema"
    }

private fun MedicationTime.compactLabel(): String =
    "%02d:%02d".format(hour, minute)

private fun MedicationActivityOption.activityLabel(custom: String): String =
    when (this) {
        MedicationActivityOption.BEFORE_BREAKFAST -> "Antes del desayuno"
        MedicationActivityOption.WITH_BREAKFAST -> "Con el desayuno"
        MedicationActivityOption.AFTER_BREAKFAST -> "Después del desayuno"
        MedicationActivityOption.BEFORE_LUNCH -> "Antes de la comida"
        MedicationActivityOption.WITH_LUNCH -> "Con la comida"
        MedicationActivityOption.AFTER_LUNCH -> "Después de la comida"
        MedicationActivityOption.BEFORE_DINNER -> "Antes de la cena"
        MedicationActivityOption.WITH_DINNER -> "Con la cena"
        MedicationActivityOption.AFTER_DINNER -> "Después de la cena"
        MedicationActivityOption.BEFORE_SLEEP -> "Antes de dormir"
        MedicationActivityOption.OTHER -> custom.ifBlank { "Otra actividad" }
    }

private val concentrationUnits = listOf("mg", "g", "mcg", "mg/mL", "UI", CUSTOM_UNIT_OPTION)

private fun MedicationForm?.amountUnits(): List<String> = when (this) {
    MedicationForm.TABLET -> listOf("tableta", "tabletas", CUSTOM_UNIT_OPTION)
    MedicationForm.CAPSULE -> listOf("cápsula", "cápsulas", CUSTOM_UNIT_OPTION)
    MedicationForm.ORAL_LIQUID -> listOf("mL", CUSTOM_UNIT_OPTION)
    MedicationForm.DROPS -> listOf("gota", "gotas", "mL", CUSTOM_UNIT_OPTION)
    MedicationForm.INJECTABLE -> listOf("mL", "unidad", "unidades", CUSTOM_UNIT_OPTION)
    MedicationForm.INHALER ->
        listOf("inhalación", "inhalaciones", "aplicación", CUSTOM_UNIT_OPTION)
    MedicationForm.TOPICAL -> listOf("aplicación", "g", "cm", CUSTOM_UNIT_OPTION)
    MedicationForm.PATCH -> listOf("parche", "parches", CUSTOM_UNIT_OPTION)
    MedicationForm.POWDER -> listOf("sobre", "sobres", CUSTOM_UNIT_OPTION)
    MedicationForm.OTHER -> listOf(CUSTOM_UNIT_OPTION)
    null -> listOf(CUSTOM_UNIT_OPTION)
}

private fun MedicationForm.gridLabel(): String =
    if (this == MedicationForm.OTHER) "Otra" else label()

private fun MedicationForm.label(): String = when (this) {
    MedicationForm.TABLET -> "Tableta"
    MedicationForm.CAPSULE -> "Cápsula"
    MedicationForm.ORAL_LIQUID -> "Jarabe o solución"
    MedicationForm.DROPS -> "Gotas"
    MedicationForm.INJECTABLE -> "Inyectable"
    MedicationForm.INHALER -> "Inhalador"
    MedicationForm.TOPICAL -> "Crema o ungüento"
    MedicationForm.PATCH -> "Parche"
    MedicationForm.POWDER -> "Polvo o sobres"
    MedicationForm.OTHER -> "Otra presentación"
}

private fun GuidedMedicationDraft.concentrationSummary(): String {
    val unit = concentrationUnit.effectiveDisplayUnit(customConcentrationUnit)
    return if (concentrationValue.isNotBlank() && unit.isNotBlank()) {
        concentrationValue + " " + unit
    } else {
        ""
    }
}

private fun GuidedMedicationDraft.amountSummary(): String {
    val unit = amountUnit.effectiveDisplayUnit(customAmountUnit)
    return if (amountValue.isNotBlank() && unit.isNotBlank()) {
        amountValue + " " + unit
    } else {
        ""
    }
}

private fun String.effectiveDisplayUnit(customUnit: String): String =
    if (this == CUSTOM_UNIT_OPTION) customUnit.trim() else trim()

@Preview(
    name = "Alta medicamento · Claro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Preview(
    name = "Alta medicamento · Oscuro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Alta medicamento · Compacto",
    showBackground = true,
    widthDp = 320,
    heightDp = 720,
)
@Preview(
    name = "Alta medicamento · Texto 200%",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
)
@Composable
private fun MedicationRegistrationPreview() {
    BitavidaTheme {
        MedicalPlanScreen(
            state = MedicalPlanUiState(
                screen = MedicalPlanScreen.MEDICATION_REGISTRATION,
                selectedSection = PlanSection.MEDICATIONS,
                isLoading = false,
            ),
            caregiverDisplayName = "Ana Rivera",
            personUnderCareDisplayName = "Elena Rivera",
            onAction = {},
            onTodaySelected = {},
        )
    }
}

@Preview(
    name = "Revisar medicamento · Claro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Preview(
    name = "Revisar medicamento · Oscuro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Revisar medicamento · Compacto",
    showBackground = true,
    widthDp = 320,
    heightDp = 720,
)
@Preview(
    name = "Revisar medicamento · Texto 200%",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
)
@Composable
private fun MedicationEditReviewPreview() {
    val draft = GuidedMedicationDraft(
        medicationName = "Medicamento ficticio",
        form = MedicationForm.TABLET,
        amountValue = "1",
        amountUnit = "tableta",
        frequency = MedicationFrequency.EVERY_DAY,
        treatmentStartMode = MedicationTreatmentStartMode.TODAY,
        treatmentStartDate = java.time.LocalDate.of(2026, 11, 12),
        reminderOption = MedicationReminderOption.NORMAL,
    )
    BitavidaTheme {
        MedicalPlanScreen(
            state = MedicalPlanUiState(
                screen = MedicalPlanScreen.MEDICATION_REGISTRATION,
                selectedSection = PlanSection.MEDICATIONS,
                editingExisting = true,
                medicationEditPurpose = MedicationEditPurpose.CORRECTION,
                originalGuidedMedicationDraft = draft,
                guidedMedicationDraft = draft,
                medicationRegistrationStep = MedicationRegistrationStep.REVIEW,
                isLoading = false,
            ),
            caregiverDisplayName = "Ana Prueba",
            personUnderCareDisplayName = "Elena Prueba",
            onAction = {},
            onTodaySelected = {},
        )
    }
}
