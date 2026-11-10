package com.bitavida.app.ui.careprofile

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.validation.CARE_PROFILE_NAME_COUNTER_THRESHOLD
import com.bitavida.app.domain.validation.CARE_PROFILE_NAME_MAX_LENGTH
import com.bitavida.app.domain.validation.CareProfileField
import com.bitavida.app.domain.validation.CareProfileValidationError
import com.bitavida.app.domain.validation.CareProfileValidator
import com.bitavida.app.ui.brand.BitavidaMark
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val FlowStepCount = 6

@Composable
fun CareProfileFlowRoute(
    viewModel: CareProfileViewModel,
    onExit: (CareProfileEffect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.effect) {
        val effect = state.effect ?: return@LaunchedEffect
        viewModel.onAction(CareProfileAction.EffectConsumed)
        onExit(effect)
    }

    CareProfileFlowScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
fun CareProfileFlowScreen(
    state: CareProfileUiState,
    onAction: (CareProfileAction) -> Unit,
    modifier: Modifier = Modifier,
    motionEnabledOverride: Boolean? = null,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(motionEnabledOverride)
    CareProfileFlowScaffold(
        state = state,
        onAction = onAction,
        modifier = modifier,
        motionAllowed = motionAllowed,
    )
}

@Composable
private fun CareProfileFlowScaffold(
    state: CareProfileUiState,
    onAction: (CareProfileAction) -> Unit,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var advancePulse by remember { mutableIntStateOf(0) }
    val hasUnsavedDraft = state.createdProfile == null &&
        (
            state.caregiverName.isNotBlank() ||
                state.personUnderCareName.isNotBlank() ||
                state.authorizationDeclared ||
                state.termsAccepted ||
                state.privacyNoticeAcknowledged
            )
    val requestExit = {
        if (hasUnsavedDraft) {
            showExitConfirmation = true
        } else {
            onAction(CareProfileAction.Cancel)
        }
    }

    BackHandler {
        when {
            state.step == CareProfileStep.COMPLETED ->
                onAction(CareProfileAction.ContinueToToday)
            state.step != CareProfileStep.WELCOME ->
                onAction(CareProfileAction.Back)
            else -> requestExit()
        }
    }

    BitavidaFluidBackground(
        modifier = modifier.fillMaxSize(),
        scrollPixels = { scrollState.value.toFloat() },
        motionAllowed = motionAllowed,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("care_profile_flow"),
        ) {
            CareProfileHeader(
                step = state.step,
                advancePulse = advancePulse,
                onBack = { onAction(CareProfileAction.Back) },
                onExit = requestExit,
                motionAllowed = motionAllowed,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(
                        start = BitavidaSpacing.ScreenHorizontal,
                        end = BitavidaSpacing.ScreenHorizontal,
                        top = BitavidaSpacing.Medium,
                        bottom = BitavidaSpacing.Large,
                    ),
            ) {
                CareProfileContentSurface(
                    state = state,
                    onAction = onAction,
                    onAdvance = { action ->
                        advancePulse += 1
                        onAction(action)
                    },
                    motionAllowed = motionAllowed,
                )
            }
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("¿Salir de la configuración?") },
            text = {
                Text(
                    "La información capturada en esta sesión se descartará. " +
                        "Puedes continuar para revisarla antes de salir.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmation = false
                        onAction(CareProfileAction.Cancel)
                    },
                ) {
                    Text("Salir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("Continuar aquí")
                }
            },
        )
    }
}

@Composable
private fun CareProfileHeader(
    step: CareProfileStep,
    advancePulse: Int,
    onBack: () -> Unit,
    onExit: () -> Unit,
    motionAllowed: Boolean,
) {
    val position = if (step == CareProfileStep.COMPLETED) {
        FlowStepCount
    } else {
        step.ordinal + 1
    }
    val targetProgress = position / FlowStepCount.toFloat()
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0,
        ),
        label = "care_profile_progress",
    )
    val progressGlow = remember { Animatable(0f) }
    LaunchedEffect(advancePulse, motionAllowed) {
        if (advancePulse > 0 && motionAllowed) {
            progressGlow.snapTo(1f)
            progressGlow.animateTo(
                targetValue = 0f,
                animationSpec = tween(BitavidaFluidMotion.StateTransition),
            )
        } else {
            progressGlow.snapTo(0f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BitavidaSpacing.ScreenHorizontal,
                vertical = BitavidaSpacing.Medium,
            ),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BitavidaMark(
                modifier = Modifier.size(width = 38.dp, height = 50.dp),
                strokeWidth = 4.dp,
                eventRadius = 3.dp,
            )
            Text(
                text = "Bitavida",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.bitavidaColors.brand,
            )
            if (step != CareProfileStep.COMPLETED) {
                TextButton(
                    onClick = onExit,
                    modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text("Salir")
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != CareProfileStep.WELCOME && step != CareProfileStep.COMPLETED) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
                ) {
                    Text("Atrás")
                }
            }
            Text(
                text = if (step == CareProfileStep.COMPLETED) {
                    "Perfil listo"
                } else {
                    "Paso $position de $FlowStepCount"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = targetProgress,
                        range = 0f..1f,
                        steps = FlowStepCount - 1,
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.bitavidaColors.gradientStart,
                                MaterialTheme.bitavidaColors.gradientEnd,
                            ),
                        ),
                    ),
            )
            if (progressGlow.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.onPrimary.copy(
                                alpha = progressGlow.value * 0.22f,
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun CareProfileContentSurface(
    state: CareProfileUiState,
    onAction: (CareProfileAction) -> Unit,
    onAdvance: (CareProfileAction) -> Unit,
    motionAllowed: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        ),
    ) {
        Column {
            if (motionAllowed) {
                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (
                            fadeIn(tween(BitavidaFluidMotion.StateTransition)) +
                                slideInVertically(
                                    animationSpec = tween(BitavidaFluidMotion.StateTransition),
                                    initialOffsetY = { (it / 18) * direction },
                                )
                            ).togetherWith(
                            fadeOut(tween(BitavidaFluidMotion.StateTransition / 2)) +
                                slideOutVertically(
                                    animationSpec = tween(BitavidaFluidMotion.StateTransition),
                                    targetOffsetY = { (-it / 20) * direction },
                                ),
                        ).using(
                            SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ ->
                                    tween(BitavidaFluidMotion.StateTransition)
                                },
                            ),
                        )
                    },
                    label = "care_profile_step",
                ) { step ->
                    CareProfileStepBody(
                        step = step,
                        state = state,
                        onAction = onAction,
                        motionAllowed = motionAllowed,
                    )
                }
            } else {
                CareProfileStepBody(
                    step = state.step,
                    state = state,
                    onAction = onAction,
                    motionAllowed = false,
                )
            }
            CareProfileAdvanceAction(
                step = state.step,
                onAdvance = onAdvance,
                motionAllowed = motionAllowed,
                modifier = Modifier.padding(
                    start = BitavidaSpacing.Large,
                    end = BitavidaSpacing.Large,
                    bottom = BitavidaSpacing.Large,
                ),
            )
        }
    }
}

@Composable
private fun CareProfileStepBody(
    step: CareProfileStep,
    state: CareProfileUiState,
    onAction: (CareProfileAction) -> Unit,
    motionAllowed: Boolean,
) {
    Column(
        modifier = Modifier.padding(
            start = BitavidaSpacing.Large,
            end = BitavidaSpacing.Large,
            top = BitavidaSpacing.Large,
            bottom = BitavidaSpacing.Default,
        ),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
    ) {
        when (step) {
            CareProfileStep.WELCOME -> WelcomeStep()
            CareProfileStep.PRIVACY -> PrivacyStep(
                state = state,
                onAction = onAction,
                motionAllowed = motionAllowed,
            )
            CareProfileStep.AUTHORIZATION -> AuthorizationStep(
                state = state,
                onAction = onAction,
                motionAllowed = motionAllowed,
            )
            CareProfileStep.CAREGIVER -> CaregiverStep(state, onAction, motionAllowed)
            CareProfileStep.PERSON_UNDER_CARE ->
                PersonUnderCareStep(state, onAction, motionAllowed)
            CareProfileStep.REVIEW -> ReviewStep(state, onAction)
            CareProfileStep.COMPLETED -> CompletedStep(state, motionAllowed)
        }

        state.globalError?.let {
            Text(
                text = "Ya existe un perfil de cuidado. Continúa a Mi día para seguir.",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Eyebrow("TU RED DE CUIDADO")
    StepTitle("Cuidar con más claridad")
    Text(
        text = "Bitavida te ayuda a reunir la información cotidiana de cuidado y mantenerla " +
            "lista para cuando la necesites.",
        style = MaterialTheme.typography.bodyLarge,
    )
    PurposeCallout(
        title = "Un punto de partida compartido",
        body = "Comenzaremos identificando a la persona cuidadora y a quien recibe el cuidado.",
    )
    Text(
        text = "Bitavida organiza información y acompaña el seguimiento diario. No sustituye " +
            "la atención profesional ni las indicaciones médicas.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PrivacyStep(
    state: CareProfileUiState,
    onAction: (CareProfileAction) -> Unit,
    motionAllowed: Boolean,
) {
    Eyebrow("PRIVACIDAD DESDE EL INICIO")
    StepTitle("Tu información, bajo tu control")
    Text(
        text = "El perfil permite personalizar la experiencia y mantener clara la relación de cuidado.",
        style = MaterialTheme.typography.bodyLarge,
    )
    ProductPrinciple(
        number = "01",
        title = "Tratamiento local",
        body = "La información se procesa en tu dispositivo y no se envía a servicios externos.",
    )
    ProductPrinciple(
        number = "02",
        title = "Acciones explícitas",
        body = "Tú decides qué registrar, corregir o compartir.",
    )
    ProductPrinciple(
        number = "03",
        title = "Finalidad clara",
        body = "Bitavida ayuda a organizar el cuidado; no interpreta datos ni recomienda tratamientos.",
    )
    CareProfileLegalAccess(
        termsAccepted = state.termsAccepted,
        privacyNoticeAcknowledged = state.privacyNoticeAcknowledged,
        termsMissing = CareProfileLegalDecision.TERMS_OF_USE in state.missingLegalDecisions,
        privacyNoticeMissing =
            CareProfileLegalDecision.PRIVACY_NOTICE in state.missingLegalDecisions,
        onTermsAcceptanceChanged = {
            onAction(CareProfileAction.TermsAcceptanceChanged(it))
        },
        onPrivacyNoticeAcknowledgementChanged = {
            onAction(CareProfileAction.PrivacyNoticeAcknowledgementChanged(it))
        },
        motionAllowed = motionAllowed,
    )
}

@Composable
private fun AuthorizationStep(
    state: CareProfileUiState,
    onAction: (CareProfileAction) -> Unit,
    motionAllowed: Boolean,
) {
    Eyebrow("RELACIÓN DE CUIDADO")
    StepTitle("Confirma tu autorización")
    Text(
        text = "La información de este perfil corresponde a tu declaración como persona cuidadora.",
        style = MaterialTheme.typography.bodyLarge,
    )
    val hasError = state.fieldErrors.containsKey(CareProfileField.AUTHORIZATION_DECLARATION)
    val authorizationShape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .clip(authorizationShape)
            .toggleable(
                value = state.authorizationDeclared,
                role = Role.Checkbox,
                onValueChange = {
                    onAction(CareProfileAction.AuthorizationChanged(it))
                },
            ),
        shape = authorizationShape,
        color = if (state.authorizationDeclared) {
            MaterialTheme.bitavidaColors.mint
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(BitavidaSpacing.Default),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = state.authorizationDeclared,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Text(
                text = "Declaro que cuento con autorización para organizar la información de " +
                    "la persona atendida en Bitavida.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    val errorDuration = if (motionAllowed) BitavidaFluidMotion.Selection else 0
    AnimatedVisibility(
        visible = hasError,
        enter = fadeIn(tween(errorDuration)) + expandVertically(tween(errorDuration)),
        exit = fadeOut(tween(errorDuration)) + shrinkVertically(tween(errorDuration)),
    ) {
        AuthorizationValidationMessage()
    }
    Text(
        text = "Bitavida registra esta declaración, pero no acredita por sí misma representación " +
            "legal. El consentimiento puede mantenerse vigente o revocarse.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CaregiverStep(
    state: CareProfileUiState,
    onAction: (CareProfileAction) -> Unit,
    motionAllowed: Boolean,
) {
    Eyebrow("PERSONA CUIDADORA")
    StepTitle("¿Cómo te llamas?")
    Text(
        text = "Este nombre identificará a la persona que registra y revisa la información de cuidado.",
        style = MaterialTheme.typography.bodyLarge,
    )
    BitavidaNameField(
        value = state.caregiverName,
        label = "Tu nombre",
        error = state.fieldErrors[CareProfileField.CAREGIVER_NAME],
        onValueChange = {
            onAction(CareProfileAction.CaregiverNameChanged(it))
        },
        onFocusLost = { onAction(CareProfileAction.CaregiverNameFocusLost) },
        testTag = "caregiver_name",
        motionAllowed = motionAllowed,
    )
    Text(
        text = "Solo necesitamos un nombre para identificarte dentro de Bitavida. No solicitamos " +
            "CURP, NSS ni datos del IMSS.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PersonUnderCareStep(
    state: CareProfileUiState,
    onAction: (CareProfileAction) -> Unit,
    motionAllowed: Boolean,
) {
    Eyebrow("PERSONA ATENDIDA")
    StepTitle("¿A quién acompañas?")
    Text(
        text = "Usa el nombre con el que deseas identificar a la persona dentro de su perfil de cuidado.",
        style = MaterialTheme.typography.bodyLarge,
    )
    BitavidaNameField(
        value = state.personUnderCareName,
        label = "Nombre de la persona",
        error = state.fieldErrors[CareProfileField.PERSON_UNDER_CARE_NAME],
        onValueChange = {
            onAction(CareProfileAction.PersonUnderCareNameChanged(it))
        },
        onFocusLost = { onAction(CareProfileAction.PersonUnderCareNameFocusLost) },
        testTag = "person_under_care_name",
        motionAllowed = motionAllowed,
    )
    Text(
        text = "Este dato se registra como una declaración de la persona cuidadora.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReviewStep(
    state: CareProfileUiState,
    onAction: (CareProfileAction) -> Unit,
) {
    Eyebrow("REVISIÓN FINAL")
    StepTitle("Confirma tu perfil de cuidado")
    Text(
        text = "Verifica los nombres y la relación antes de continuar.",
        style = MaterialTheme.typography.bodyLarge,
    )
    ReviewValue(
        index = "01",
        label = "Persona cuidadora",
        value = CareProfileValidator.normalizeName(state.caregiverName),
        supporting = "Información declarada por la persona cuidadora",
        onEdit = { onAction(CareProfileAction.EditCaregiver) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ReviewValue(
        index = "02",
        label = "Persona atendida",
        value = CareProfileValidator.normalizeName(state.personUnderCareName),
        supporting = "Identidad registrada según tu declaración",
        onEdit = { onAction(CareProfileAction.EditPersonUnderCare) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ReviewValue(
        index = "03",
        label = "Autorización",
        value = "Declarada y vigente",
        supporting = "Bitavida no acredita por sí misma representación legal",
        onEdit = null,
    )
    PurposeCallout(
        title = "Antes de confirmar",
        body = "Podrás continuar a Mi día después de crear el perfil.",
    )
}

@Composable
private fun CompletedStep(
    state: CareProfileUiState,
    motionAllowed: Boolean,
) {
    var successVisible by remember { mutableStateOf(!motionAllowed) }
    LaunchedEffect(motionAllowed) {
        successVisible = true
    }
    AnimatedVisibility(
        visible = successVisible,
        enter = fadeIn(tween(BitavidaFluidMotion.StateTransition)) +
            scaleIn(
                initialScale = 0.86f,
                animationSpec = tween(BitavidaFluidMotion.StateTransition),
            ),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.bitavidaColors.mint),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.bitavidaColors.successText,
            )
        }
    }
    Eyebrow("CONFIGURACIÓN COMPLETA")
    StepTitle("Tu perfil de cuidado está listo")
    Text(
        text = "Ya puedes comenzar a organizar el cuidado diario desde Mi día.",
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyLarge,
    )
    state.createdProfile?.let { profile ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(BitavidaSpacing.Default),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            ) {
                LabeledStatement(
                    title = "Persona cuidadora",
                    body = profile.caregiver.displayName,
                )
                LabeledStatement(
                    title = "Persona atendida",
                    body = profile.personUnderCare.displayName,
                )
                LabeledStatement(
                    title = "Consentimiento",
                    body = if (profile.consent.isCurrent) {
                        "Vigente · autorización declarada"
                    } else {
                        "Revocado"
                    },
                )
            }
        }
    }
    Text(
        text = "Bitavida organiza la información registrada y no sustituye la atención médica.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BitavidaNameField(
    value: String,
    label: String,
    error: CareProfileValidationError?,
    onValueChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    testTag: String,
    motionAllowed: Boolean,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    var fieldValue by rememberSaveable(testTag, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
        )
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusManager = LocalFocusManager.current
    val characterCount = CareProfileValidator.nameLength(fieldValue.text)
    val errorText = when (error) {
        CareProfileValidationError.REQUIRED -> "Escribe un nombre para continuar."
        CareProfileValidationError.TOO_SHORT -> "Escribe al menos 2 letras."
        CareProfileValidationError.TOO_LONG -> "Usa un máximo de 80 caracteres."
        CareProfileValidationError.INVALID_CHARACTERS ->
            "Usa letras, espacios, apóstrofos o guiones."
        CareProfileValidationError.CONTROL_CHARACTERS_NOT_ALLOWED ->
            "El nombre no puede incluir saltos de línea ni caracteres invisibles."
        CareProfileValidationError.AUTHORIZATION_REQUIRED ->
            "Confirma la declaración para continuar."
        null -> null
    }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = fieldValue.copy(
                text = value,
                selection = TextRange(
                    start = fieldValue.selection.start.coerceAtMost(value.length),
                    end = fieldValue.selection.end.coerceAtMost(value.length),
                ),
                composition = null,
            )
        }
    }
    LaunchedEffect(focused) {
        if (focused) {
            if (motionAllowed) delay(180L)
            bringIntoViewRequester.bringIntoView()
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { updatedValue ->
            val limitedText = CareProfileValidator.limitNameInput(updatedValue.text)
            fieldValue = if (limitedText == updatedValue.text) {
                updatedValue
            } else {
                updatedValue.copy(
                    text = limitedText,
                    selection = TextRange(
                        start = updatedValue.selection.start.coerceAtMost(limitedText.length),
                        end = updatedValue.selection.end.coerceAtMost(limitedText.length),
                    ),
                    composition = null,
                )
            }
            onValueChange(limitedText)
        },
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                val wasFocused = focused
                focused = focusState.isFocused
                if (wasFocused && !focused) onFocusLost()
            }
            .then(
                if (motionAllowed) {
                    Modifier.animateContentSize(tween(BitavidaFluidMotion.Selection))
                } else {
                    Modifier
                },
            )
            .testTag(testTag),
        enabled = enabled,
        label = { Text(label) },
        trailingIcon = if (fieldValue.text.isNotBlank() && error == null && !focused) {
            {
                Text(
                    text = "Listo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.bitavidaColors.successText,
                )
            }
        } else {
            null
        },
        supportingText = when {
            errorText != null -> {
                {
                    Text(
                        text = errorText,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Assertive
                        },
                    )
                }
            }
            characterCount >= CARE_PROFILE_NAME_COUNTER_THRESHOLD -> {
                {
                    Text(
                        text = "$characterCount/$CARE_PROFILE_NAME_MAX_LENGTH",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                }
            }
            else -> null
        },
        isError = error != null,
        minLines = 1,
        maxLines = 2,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            errorTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            errorCursorColor = MaterialTheme.colorScheme.error,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            errorLabelColor = MaterialTheme.colorScheme.error,
            errorSupportingTextColor = MaterialTheme.colorScheme.error,
        ),
    )
}

@Composable
private fun ReviewValue(
    index: String,
    label: String,
    value: String,
    supporting: String,
    onEdit: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.bitavidaColors.paleBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            onEdit?.let {
                TextButton(
                    onClick = it,
                    modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
                ) {
                    Text("Editar")
                }
            }
        }
    }
}

@Composable
private fun ProductPrinciple(
    number: String,
    title: String,
    body: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = number,
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        LabeledStatement(
            title = title,
            body = body,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PurposeCallout(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.bitavidaColors.paleBlue,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(BitavidaSpacing.Default),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.bitavidaColors.gradientStart),
            )
            LabeledStatement(
                title = title,
                body = body,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LabeledStatement(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AuthorizationValidationMessage() {
    Text(
        text = "Confirma la declaración para continuar.",
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun CareProfileAdvanceAction(
    step: CareProfileStep,
    onAdvance: (CareProfileAction) -> Unit,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val activation = remember { Animatable(0f) }
    val waveProgress = remember { Animatable(0f) }
    var advancing by remember { mutableStateOf(false) }
    val actionShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 28.dp,
        bottomEnd = 28.dp,
        bottomStart = 18.dp,
    )
    val label = when (step) {
        CareProfileStep.WELCOME -> "Comenzar"
        CareProfileStep.REVIEW -> "Crear perfil de cuidado"
        CareProfileStep.COMPLETED -> "Ir a Mi día"
        else -> "Continuar"
    }
    val action = when (step) {
        CareProfileStep.REVIEW -> CareProfileAction.Confirm
        CareProfileStep.COMPLETED -> CareProfileAction.ContinueToToday
        else -> CareProfileAction.Continue
    }
    val energy = 1f - abs(activation.value * 2f - 1f)
    val actionScale = 1f - (1f - BitavidaFluidMotion.PressScale) * energy

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionThreadLink(activation = activation.value)
        Button(
            onClick = {
                if (advancing) return@Button
                focusManager.clearFocus()
                if (!motionAllowed) {
                    advancing = true
                    coroutineScope.launch {
                        waveProgress.snapTo(1f)
                        onAdvance(action)
                        delay(BitavidaFluidMotion.Press.toLong())
                        waveProgress.snapTo(0f)
                        advancing = false
                    }
                    return@Button
                }
                advancing = true
                coroutineScope.launch {
                    activation.snapTo(0f)
                    waveProgress.snapTo(0f)
                    activation.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(BitavidaFluidMotion.Selection),
                    )
                    // Present the point's completed propagation before the wave starts.
                    withFrameNanos { }
                    waveProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(320),
                    )
                    onAdvance(action)
                    delay(BitavidaFluidMotion.Press.toLong())
                    activation.snapTo(0f)
                    waveProgress.snapTo(0f)
                    advancing = false
                }
            },
            enabled = !advancing,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = BitavidaSpacing.PrimaryControlHeight)
                .graphicsLayer {
                    scaleX = actionScale
                    scaleY = actionScale
                }
                .bitavidaFluidInteraction(
                    source = interactionSource,
                    enabled = !advancing,
                    motionAllowed = motionAllowed,
                    shape = actionShape,
                    restingElevationDp = 0f,
                ),
            interactionSource = interactionSource,
            shape = actionShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            val waveColor = MaterialTheme.bitavidaColors.gradientEnd
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BitavidaSpacing.PrimaryControlHeight)
                    .clip(actionShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .clearAndSetSemantics { },
                ) {
                    if (waveProgress.value > 0f) {
                        val feather = size.width * 0.28f
                        val front = -feather +
                            (size.width + feather * 2f) * waveProgress.value
                        val solidEnd = (front - feather).coerceIn(0f, size.width)
                        val gradientEnd = (front + feather).coerceIn(0f, size.width)
                        if (solidEnd > 0f) {
                            drawRect(
                                color = waveColor.copy(alpha = 0.42f),
                                size = Size(solidEnd, size.height),
                            )
                        }
                        if (gradientEnd > solidEnd) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        waveColor.copy(alpha = 0.42f),
                                        waveColor.copy(alpha = 0.28f),
                                        Color.Transparent,
                                    ),
                                    startX = solidEnd,
                                    endX = gradientEnd,
                                ),
                                topLeft = Offset(solidEnd, 0f),
                                size = Size(gradientEnd - solidEnd, size.height),
                            )
                        }
                    }
                }
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        fadeIn(tween(BitavidaFluidMotion.Selection)).togetherWith(
                            fadeOut(tween(BitavidaFluidMotion.Selection / 2)),
                        )
                    },
                    label = "care_profile_action_label",
                ) { currentLabel ->
                    Text(
                        text = currentLabel,
                        modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionThreadLink(
    activation: Float,
) {
    val threadColor = MaterialTheme.colorScheme.outline
    val nodeColor = MaterialTheme.bitavidaColors.gradientStart
    val glowColor = MaterialTheme.bitavidaColors.gradientEnd
    val energy = 1f - abs(activation * 2f - 1f)
    Canvas(
        modifier = Modifier
            .size(width = 34.dp, height = BitavidaSpacing.PrimaryControlHeight)
            .clearAndSetSemantics { },
    ) {
        val nodeX = 8.dp.toPx()
        val nodeY = size.height / 2f
        val stroke = 1.5.dp.toPx()
        drawLine(
            color = threadColor,
            start = Offset(nodeX, 0f),
            end = Offset(nodeX, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = threadColor,
            start = Offset(nodeX, nodeY),
            end = Offset(size.width, nodeY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (activation > 0f && activation < 1f) {
            val flowEnd = nodeX + (size.width - nodeX) * activation
            drawLine(
                color = glowColor.copy(alpha = 0.35f + energy * 0.55f),
                start = Offset(nodeX, nodeY),
                end = Offset(flowEnd, nodeY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = nodeColor,
            radius = (4.dp + 1.dp * energy).toPx(),
            center = Offset(nodeX, nodeY),
        )
    }
}

private val PreviewState = CareProfileUiState(
    step = CareProfileStep.REVIEW,
    caregiverName = "Ana Rivera",
    personUnderCareName = "Elena Rivera",
    authorizationDeclared = true,
)

@Preview(name = "Perfil · Claro", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CareProfileLightPreview() {
    BitavidaTheme {
        CareProfileFlowScreen(
            state = PreviewState,
            onAction = {},
        )
    }
}

@Preview(
    name = "Perfil · Oscuro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CareProfileDarkPreview() {
    BitavidaTheme(darkTheme = true) {
        CareProfileFlowScreen(
            state = PreviewState,
            onAction = {},
        )
    }
}

@Preview(name = "Perfil · Compacto", showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun CareProfileCompactPreview() {
    BitavidaTheme {
        CareProfileFlowScreen(
            state = PreviewState.copy(step = CareProfileStep.AUTHORIZATION),
            onAction = {},
        )
    }
}

@Preview(
    name = "Perfil · Texto 200%",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
)
@Composable
private fun CareProfileLargeTextPreview() {
    BitavidaTheme {
        CareProfileFlowScreen(
            state = PreviewState.copy(step = CareProfileStep.CAREGIVER),
            onAction = {},
        )
    }
}

@Preview(name = "Perfil · Movimiento reducido", widthDp = 390, heightDp = 844)
@Composable
private fun CareProfileReducedMotionPreview() {
    BitavidaTheme {
        CareProfileFlowScreen(
            state = PreviewState,
            onAction = {},
            motionEnabledOverride = false,
        )
    }
}
