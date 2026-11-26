package com.bitavida.app.ui.report

import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.bitavida.app.domain.model.GlucoseContext
import androidx.compose.ui.geometry.Offset
import com.bitavida.app.domain.model.MeasurementValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.medical.CareSectionHeader
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.bitavida.app.R
import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.ui.components.BitavidaIconContainer
import com.bitavida.app.ui.theme.bitavidaColors
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationFollowUp
import com.bitavida.app.domain.model.WeightTarget
import com.bitavida.app.domain.usecase.ReportMeasurementSchedule
import com.bitavida.app.domain.usecase.MeasurementReportHistory
import com.bitavida.app.domain.usecase.MeasurementScheduleReportHistory
import com.bitavida.app.domain.usecase.recordsAt
import com.bitavida.app.domain.usecase.schedulesAt
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.followUpAvailability
import com.bitavida.app.domain.usecase.FollowUpAvailability
import com.bitavida.app.domain.usecase.ConsultationIntervalKind
import com.bitavida.app.domain.usecase.buildFollowUpReport
import com.bitavida.app.domain.usecase.firstFollowUpEventDate
import com.bitavida.app.domain.usecase.monthlyFollowUpPeriod
import com.bitavida.app.domain.usecase.consultationFollowUpPeriods
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.platform.report.PreparedReportShare
import com.bitavida.app.platform.report.openReportShareChooser
import com.bitavida.app.platform.report.prepareReportShare
import com.bitavida.app.platform.report.reportShareFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Ephemeral selection retained by the protected host across tab changes; never saved to disk. */
@Stable
class FollowUpReportSession(today: LocalDate = LocalDate.now()) {
    var startText by mutableStateOf<String?>(null)
    var endText by mutableStateOf(today.toString())
    var initialPeriodResolved by mutableStateOf(false)
    var mode by mutableStateOf("month")
    var rootRequest by mutableIntStateOf(0)
    var intervalIndex by mutableIntStateOf(0)
    var includeObservationPhotos by mutableStateOf(true)
}

/**
 * Reads existing patient-scoped snapshots. The initial date remains unset when there is no
 * recorded consultation to use; exporting is a separate explicit action after a sensitivity warning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpReportRoute(
    caregiverName: String,
    patientName: String,
    personUnderCareId: String,
    overview: MedicalCareOverview,
    measurements: List<MeasurementRecord>,
    observations: List<ObservationRecord>,
    onBack: () -> Unit,
    onOpenPlan: () -> Unit = {},
    reminders: List<CareReminder> = emptyList(),
    events: List<CareEvent> = emptyList(),
    careEventHistory: com.bitavida.app.domain.usecase.CareEventReportHistory? = null,
    measurementSchedules: List<ReportMeasurementSchedule> = emptyList(),
    observationFollowUps: List<ObservationFollowUp> = emptyList(),
    observationRevisions: List<com.bitavida.app.domain.model.ObservationRecordRevision> = emptyList(),
    observationPhoto: (@Composable (String) -> Unit)? = null,
    loadObservationPhoto: suspend (String, String) -> ByteArray? = { _, _ -> null },
    currentWeightTarget: WeightTarget? = null,
    supplementaryRefreshKey: Any? = null,
    loadSupplementaryHistory: suspend () -> com.bitavida.app.domain.usecase.ReportSupplementaryHistory =
        { com.bitavida.app.domain.usecase.ReportSupplementaryHistory() },
    loadMeasurementHistories: suspend () -> Pair<MeasurementReportHistory, MeasurementScheduleReportHistory> = {
        check(measurements.isEmpty() && measurementSchedules.isEmpty())
        MeasurementReportHistory(emptyList(), emptyList(), emptyList()) to MeasurementScheduleReportHistory(emptyList())
    },
    sourcesReady: Boolean = true,
    sourcesLoading: Boolean = false,
    onRetrySources: () -> Unit = {},
    modifier: Modifier = Modifier,
    session: FollowUpReportSession = remember { FollowUpReportSession() },
    bottomBar: @Composable (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<String?>(null) }
    val motion = rememberBitavidaMotionAllowed(null)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entranceAlpha by animateFloatAsState(if (entered || !motion) 1f else 0f,
        animationSpec = if (motion) tween(240) else snap(), label = "Entrada de informe")
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var supplementaryRetry by remember { mutableStateOf(0) }
    var supplementaryResult by remember(personUnderCareId, supplementaryRefreshKey, reminders, sourcesReady, supplementaryRetry) {
        mutableStateOf<Result<com.bitavida.app.domain.usecase.ReportSupplementaryHistory>?>(null)
    }
    LaunchedEffect(personUnderCareId, supplementaryRefreshKey, reminders, sourcesReady, supplementaryRetry) {
        supplementaryResult = null
        if (sourcesReady) {
            supplementaryResult = try {
                Result.success(loadSupplementaryHistory())
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }
    var measurementHistoryResult by remember(personUnderCareId, measurements, measurementSchedules, sourcesReady, supplementaryRetry) {
        mutableStateOf<Result<Pair<MeasurementReportHistory, MeasurementScheduleReportHistory>>?>(null)
    }
    LaunchedEffect(personUnderCareId, measurements, measurementSchedules, sourcesReady, supplementaryRetry) {
        measurementHistoryResult = null
        if (sourcesReady) {
            measurementHistoryResult = try { Result.success(loadMeasurementHistories()) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
        }
    }
    val allSourcesReady = sourcesReady && supplementaryResult?.isSuccess == true && measurementHistoryResult?.isSuccess == true
    val allSourcesLoading = sourcesLoading || (sourcesReady && (supplementaryResult == null || measurementHistoryResult == null))
    LaunchedEffect(session.rootRequest) {
        if (session.rootRequest > 0 && !exporting) { preview = false; detail = null; sheet = null }
    }
    // The isolated Demo marks the whole scenario after its verified initialization.
    // This is a global warning, not an attribution of individual source identities.
    val containsSimulatedScenario = remember(context) {
        context.packageName == "com.bitavida.app.demo" &&
            context.getSharedPreferences("demo_initialization", android.content.Context.MODE_PRIVATE)
                .getInt("completed_version", 0) > 0
    }
    val today = LocalDate.now()
    val firstEvent = remember(personUnderCareId, overview, measurements, observations, reminders, events, today) {
        firstFollowUpEventDate(personUnderCareId, overview, measurements, observations, reminders, events, today,
            java.time.LocalDateTime.now())
    }
    val consultationChoices = remember(firstEvent, events, personUnderCareId, today) {
        consultationFollowUpPeriods(firstEvent, events, personUnderCareId, today)
    }
    fun setPeriod(startDate: LocalDate, endDate: LocalDate) {
        session.startText = startDate.toString()
        session.endText = endDate.toString()
    }
    LaunchedEffect(allSourcesReady, session) {
        if (allSourcesReady && !session.initialPeriodResolved) {
            val initial = monthlyFollowUpPeriod(YearMonth.from(firstEvent ?: today))
            setPeriod(initial.start, initial.end)
            session.mode = "month"
            session.initialPeriodResolved = true
        }
    }
    val start = session.startText?.let(LocalDate::parse)
    val end = LocalDate.parse(session.endText)
    // Freeze one timestamp whenever the period or any source snapshot changes. All
    // projections and both document representations use this same boundary.
    val snapshotAt = remember(start, end, personUnderCareId, overview, measurements, observations,
        events, careEventHistory, allSourcesReady, measurementSchedules, observationFollowUps,
        observationRevisions, currentWeightTarget, supplementaryResult, measurementHistoryResult)  { java.time.LocalDateTime.now() }
    val reportEvents = remember(careEventHistory, events, end, snapshotAt) {
        val cutoff = minOf(end, snapshotAt.toLocalDate())
        careEventHistory?.revisionsByEvent?.values?.mapNotNull { versions ->
            com.bitavida.app.domain.usecase.careEventVersionAtEnd(
                versions.filter { !it.modifiedAt.isAfter(snapshotAt) }, cutoff)
        } ?: events
    }
    val reportState = remember(start, end, personUnderCareId, overview, measurements, observations, reportEvents, allSourcesReady, measurementSchedules, observationFollowUps, observationRevisions, currentWeightTarget, snapshotAt, supplementaryResult, measurementHistoryResult) {
        mutableStateOf<Result<FollowUpReport>?>(null)
    }
    LaunchedEffect(reportState) {
        reportState.value = if (!allSourcesReady || start == null || end.isBefore(start)) null else try {
            Result.success(withContext(Dispatchers.Default) {
                val projectionContext = kotlinx.coroutines.currentCoroutineContext()
                val cutoff = minOf(end.atTime(java.time.LocalTime.MAX), snapshotAt)
                val histories = requireNotNull(measurementHistoryResult).getOrThrow()
                val historicalMeasurements = histories.first.recordsAt(cutoff)
                val historicalSchedules = histories.second.schedulesAt(cutoff)
                buildFollowUpReport(start, end, personUnderCareId, overview, historicalMeasurements, observations, reportEvents,
                    measurementSchedules = historicalSchedules, observationFollowUps = observationFollowUps,
                    currentWeightTarget = currentWeightTarget, observationRevisions = observationRevisions,
                    now = snapshotAt, checkCancellation = { projectionContext.ensureActive() },
                    supplementaryHistory = requireNotNull(supplementaryResult).getOrThrow(),
                    containsSimulatedScenario = containsSimulatedScenario)
            })
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
          catch (error: Exception) { Result.failure(error) }
    }
    val report = reportState.value?.getOrNull().takeIf { allSourcesReady }
    val reportFailed = reportState.value?.isFailure == true
    var photoRetry by remember { mutableIntStateOf(0) }
    val photoState = remember(report, session.includeObservationPhotos, photoRetry) {
        mutableStateOf<Result<ReportObservationPhotos>?>(null)
    }
    LaunchedEffect(report, session.includeObservationPhotos, photoRetry, photoState) {
        photoState.value = report?.let {
            try { Result.success(prepareReportObservationPhotos(it, session.includeObservationPhotos, loadObservationPhoto)) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
        }
    }
    val preparedPhotos = photoState.value?.getOrNull()
    val photosFailed = photoState.value?.isFailure == true
    val medicationDetailState = remember(start, end) { ReportMedicationUiState() }
    val measurementDetailState = remember(start, end) { ReportMeasurementUiState() }
    val observationDetailState = remember(start, end) { ReportObservationsState() }
    val eventDetailState = remember(start, end) { ReportEventsState() }
    val generatedOn = report?.snapshotAt?.toLocalDate() ?: snapshotAt.toLocalDate()
    val latestSourcesReady by rememberUpdatedState(allSourcesReady)
    val latestReport by rememberUpdatedState(report)
    val latestCaregiver by rememberUpdatedState(caregiverName)
    val latestPatient by rememberUpdatedState(patientName)
    val latestPhotos by rememberUpdatedState(preparedPhotos)
    var shareRequest by remember { mutableStateOf<PendingReportShare?>(null) }
    var sharePhotos by remember { mutableStateOf(true) }
    var shareRetry by remember { mutableIntStateOf(0) }
    var shareResult by remember { mutableStateOf<Result<ReadyReportShare>?>(null) }
    var shareLaunchError by remember { mutableStateOf(false) }
    fun requestShare(source: PreviewShareSource? = null) {
        val current = report ?: return
        val photos = preparedPhotos ?: return
        sharePhotos = photos.included
        shareResult = null
        shareLaunchError = false
        shareRequest = PendingReportShare(PendingReportExport(current, caregiverName, patientName, generatedOn, photos), source)
        sheet = "share"
    }
    LaunchedEffect(shareRequest, sharePhotos, shareRetry) {
        val request = shareRequest ?: return@LaunchedEffect
        shareResult = null
        val selected = sharePhotos
        val outcome = try {
            val base = request.content
            var counts: ReportPhotoCounts? = null
            val exactPreview = request.preview?.takeIf { selected == base.photos.included }
            val shareFile = if (exactPreview != null) {
                counts = exactPreview.counts
                prepareReportShare(context, reportShareFileName(base.report.start, base.report.end, base.patient), exactPreview.writeTo)
            } else {
                val selectedPhotos = if (selected == base.photos.included) base.photos
                    else prepareReportObservationPhotos(base.report, selected, loadObservationPhoto)
                prepareReportShare(context, reportShareFileName(base.report.start, base.report.end, base.patient)) { output ->
                    val regular = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_regular))
                    val bold = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_bold))
                    writeFollowUpPdf(output, base.report, base.caregiver, base.patient, regular, bold, base.generatedOn,
                        photos = selectedPhotos, onPhotoCounts = { counts = it })
                }
            }
            Result.success(ReadyReportShare(shareFile, requireNotNull(counts), selected))
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
          catch (error: Exception) { Result.failure(error) }
        if (shareRequest === request && sharePhotos == selected) shareResult = outcome
    }
    @Composable fun ShareSheet() {
        val request = shareRequest ?: return
        val matches = latestSourcesReady && latestReport === request.content.report &&
            latestCaregiver == request.content.caregiver && latestPatient == request.content.patient &&
            latestPhotos === request.content.photos
        val ready = shareResult?.getOrNull()?.takeIf { it.includePhotos == sharePhotos }
        ModalBottomSheet(onDismissRequest = { sheet = null; shareRequest = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Compartir información sensible", style = MaterialTheme.typography.titleMedium)
                Text("El PDF contiene nombres e información de cuidado, como medicamentos, mediciones, observaciones y citas. Elige con cuidado a quién enviarlo.",
                    style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sharePhotos, onCheckedChange = {
                        shareResult = null
                        sharePhotos = it
                        shareLaunchError = false
                    },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = "Incluir fotografías en este envío" })
                    Text("Incluir fotografías de observaciones", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Esta elección solo se aplica a este envío.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    !matches -> Text("Los datos cambiaron. Cierra esta ventana y vuelve a preparar el informe.",
                        style = MaterialTheme.typography.bodyMedium)
                    shareResult == null -> Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Preparando PDF…", style = MaterialTheme.typography.bodyMedium)
                    }
                    shareResult?.isFailure == true -> {
                        Text("No se pudo preparar el PDF. Inténtalo de nuevo.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { shareRetry++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") }
                    }
                    ready != null -> {
                        val counts = ready.counts
                        Text(if (sharePhotos) "Fotografías incluidas: ${counts.included}. No disponibles: ${counts.unavailable}."
                            else "Fotografías omitidas de este envío: ${counts.omitted}.",
                            style = MaterialTheme.typography.bodyMedium)
                        if (request.preview != null && sharePhotos != request.content.photos.included)
                            Text("Se preparó otra versión del mismo periodo; la vista previa conserva su selección.",
                                style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (shareLaunchError) Text("No se pudo abrir el selector de aplicaciones. Inténtalo de nuevo.",
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    val current = shareResult?.getOrNull()?.takeIf { it.includePhotos == sharePhotos }
                    if (shareRequest === request && latestSourcesReady && latestReport === request.content.report &&
                        latestCaregiver == request.content.caregiver && latestPatient == request.content.patient &&
                        latestPhotos === request.content.photos && current != null) {
                        try {
                            openReportShareChooser(context, current.prepared)
                            sheet = null
                            shareRequest = null
                        } catch (_: Exception) { shareLaunchError = true }
                    }
                }, enabled = matches && ready != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Elegir aplicación") }
                OutlinedButton(onClick = { sheet = null; shareRequest = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text("Cancelar") }
            }
        }
    }
    var pendingExport by remember { mutableStateOf<PendingReportExport?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val current = pendingExport
        pendingExport = null
        if (uri != null && current != null && !exporting && latestSourcesReady && latestReport === current.report &&
            latestCaregiver == current.caregiver && latestPatient == current.patient && latestPhotos === current.photos) {
            exporting = true
            message = null
            scope.launch {
                var photoCounts: ReportPhotoCounts? = null
                val completed = try {
                    withContext(Dispatchers.IO) {
                        val regular = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_regular))
                        val bold = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_bold))
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            writeFollowUpPdf(stream, current.report, current.caregiver, current.patient, regular, bold, current.generatedOn,
                                photos = current.photos, onPhotoCounts = { photoCounts = it })
                        } ?: error("Archivo no disponible")
                    }
                    true
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) { false }
                exporting = false
                message = if (completed) {
                    val counts = photoCounts
                    if (counts == null || counts.detected == 0) "Informe guardado."
                    else "Informe guardado. Fotografías: ${counts.detected} detectadas, ${counts.included} incluidas, " +
                        "${counts.unavailable} no disponibles y ${counts.omitted} omitidas."
                } else "No se pudo guardar el informe. El archivo elegido podría estar incompleto; revísalo antes de compartirlo."
            }
        } else if (uri != null && current != null) {
            message = "Los datos cambiaron o no están disponibles. Revisa la vista previa antes de guardar."
        }
    }
    if (preview && report != null && preparedPhotos != null) {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = { bottomBar(!exporting && pendingExport == null && sheet == null) }) { previewPadding ->
            Column(Modifier.fillMaxSize().padding(previewPadding)) {
                FollowUpPdfPreview(
                    report, caregiverName, patientName, onBack = { if (sheet == null) preview = false },
                    generatedOn = generatedOn, photos = preparedPhotos,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onShare = { requestShare(it) }, sharing = sheet != null || exporting,
                )
                message?.let { Text(it, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (sheet == "share") ShareSheet()
        return
    }
    BackHandler {
        if (!exporting) { if (preview) preview = false else if (detail != null) detail = null else onBack() }
    }
    // Each visit starts at the top; a configuration change can still restore the active destination.
    val reportListState = key(detail, preview, session.rootRequest) { rememberLazyListState() }
    val canSwitchDestination = !exporting && pendingExport == null && sheet == null
    Scaffold(modifier.fillMaxSize(), containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = { bottomBar(canSwitchDestination) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).graphicsLayer { alpha = entranceAlpha },
            state = reportListState, contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("heading") {
                if (!preview && detail == null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        BitavidaIconContainer(painter = painterResource(R.drawable.ic_v2_chart),
                            contentDescription = null, containerColor = MaterialTheme.bitavidaColors.paleBlue)
                        Column(Modifier.weight(1f)) {
                            Text("Informe", Modifier.semantics { heading() },
                                style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                            Text("Acompañando a $patientName", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    CareSectionHeader(title = if (preview) "Vista previa" else detail ?: "Informe",
                        person = patientName, iconRes = R.drawable.ic_v2_chart,
                        onBack = { if (preview) preview = false else detail = null },
                        motionAllowed = motion, subtitle = "Informe de seguimiento", backLabel = "Informe")
                }
            }
            if (!preview && detail == null) item("intro") {
                Column {
                    Text("Informe de seguimiento", Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text("Prepara la información para tu próxima consulta.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!allSourcesReady) {
                item("source_status") {
                    Text(if (allSourcesLoading) "Cargando información para el informe…"
                        else "No se pudo cargar toda la información necesaria. Inténtalo de nuevo.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    if (!allSourcesLoading) OutlinedButton(onClick = {
                        supplementaryRetry++
                        onRetrySources()
                    },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text("Reintentar") }
                }
            } else if (!preview && detail == null) {
                val availability = report?.let {
                    followUpAvailability(personUnderCareId, overview, it.measurements, observations,
                        reminders, events, it, measurementSchedules, observationFollowUps,
                        notes = supplementaryResult?.getOrNull()?.notes.orEmpty().map { history -> history.current })
                }
                val useful = availability == FollowUpAvailability.AVAILABLE
                if (start != null) item("hero") {
                    val currentInterval = if (session.mode == "consultations")
                        consultationChoices.intervals.firstOrNull { it.period.start == start && it.period.end == end }
                    else null
                    val periodContext = when (currentInterval?.kind) {
                        ConsultationIntervalKind.FIRST_CONFIRMED, ConsultationIntervalKind.IN_PROGRESS ->
                            currentInterval.nextConsultationDate?.let {
                                "En curso · Próxima consulta confirmada: " + it.format(
                                    DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", java.util.Locale.forLanguageTag("es-MX")))
                            } ?: "Seguimiento en curso"
                        ConsultationIntervalKind.NO_APPOINTMENTS -> "Desde el inicio del seguimiento · Aún no hay consultas de seguimiento registradas"
                        else -> null
                    }
                    ReportHero(start = start, end = end, mode = session.mode, periodContext = periodContext,
                        actionsEnabled = useful && preparedPhotos != null, exporting = exporting,
                        onSelectPeriod = { sheet = "period" },
                        onPreview = { preview = true }, onExport = { sheet = "export" },
                        onShare = { requestShare() })
                }
                if (report != null && useful) item("photo_option") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = session.includeObservationPhotos,
                            onCheckedChange = { session.includeObservationPhotos = it },
                            enabled = !exporting && pendingExport == null && sheet == null,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics { contentDescription = "Incluir fotografías de observaciones" })
                        Text("Incluir fotografías de observaciones", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (preparedPhotos == null) {
                        Text(if (photosFailed) "No se pudieron consultar las fotografías autorizadas." else "Preparando fotografías…",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (photosFailed) OutlinedButton(onClick = { photoRetry++ },
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar fotografías") }
                    }
                }
                if (report == null) item("loading_report") {
                    Text(if (reportFailed) "No se pudo reconstruir el historial para este informe." else "Preparando el informe…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    if (reportFailed) OutlinedButton(onClick = { supplementaryRetry++ },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") }
                }
                if (report != null && !useful) item("empty_report") {
                    val anyData = availability == FollowUpAvailability.EMPTY_PERIOD
                    ReportPanel {
                        Text(if (anyData) "Sin información para este periodo" else "Tu seguimiento comienza aquí",
                            style = MaterialTheme.typography.titleLarge)
                        Text(if (anyData) "Puedes consultar otro periodo o continuar registrando el seguimiento."
                            else "Cuando registres actividades de cuidado, Bitavida podrá reunirlas para tus consultas.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!anyData) OutlinedButton(onClick = onOpenPlan, modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) { Text("Ir a Plan de cuidado") }
                    }
                }
                if (useful && report != null) item("activity") {
                    ReportActivity(report = report)
                }
                item("sections") {
                    ReportSections(enabled = report != null, onOpen = { detail = it })
                }
                if (report != null) item("trends") {
                    ReportPanel {
                        ReportQuickTrends(report = report, onExplore = { detail = "Tendencias" })
                    }
                }
            } else if (report == null) {
                item("preparing") {
                    Text(if (reportFailed) "No se pudo reconstruir el historial para este informe." else "Preparando el informe…",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    if (reportFailed) OutlinedButton(onClick = { supplementaryRetry++ },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") }
                }
            } else if (detail != null) {
                item("detail_period") {
                    Text(reportPeriodTitle(session.mode, report.start) + " · " +
                        reportPeriodDescription(report.start, report.end),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (detail) {
                    "Tendencias" -> reportMeasurementItems(report, measurementDetailState)
                    "Medicamentos y seguimiento" -> reportMedicationItems(report, overview, personUnderCareId, medicationDetailState)
                    "Observaciones" -> reportObservationItems(report, observationDetailState, photoContent = observationPhoto)
                    "Citas" -> reportCareEventItems(report, reportEvents, personUnderCareId, eventDetailState,
                        careEventHistory?.revisionsByEvent.orEmpty())
                }
            }
            if (message != null) item("message") {
                Text(message!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
    when (sheet) {
        "period" -> if (start != null) {
            ReportPeriodSheet(
                appliedMode = session.mode,
                appliedPeriod = com.bitavida.app.domain.usecase.FollowUpPeriod(start, end),
                choices = consultationChoices,
                appliedIntervalIndex = consultationChoices.intervals.indexOfFirst { it.period.start == start && it.period.end == end }
                    .takeIf { it >= 0 } ?: consultationChoices.selectedIndex,
                onDismiss = { sheet = null },
                onApply = { mode, period, intervalIndex ->
                    session.mode = mode
                    session.intervalIndex = intervalIndex
                    setPeriod(period.start, period.end)
                },
            )
        }
        "share" -> ShareSheet()
        "export" -> ModalBottomSheet(onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Información sensible", style = MaterialTheme.typography.titleMedium)
                Text("El PDF contiene información personal y de cuidado. Elige con atención dónde guardarlo y con quién compartirlo.",
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    val current = report
                    if (current != null && preparedPhotos != null) {
                        pendingExport = PendingReportExport(current, caregiverName, patientName, generatedOn, preparedPhotos)
                        sheet = null
                        launcher.launch("Informe-Bitavida.pdf")
                    }
                }, enabled = report != null && preparedPhotos != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Elegir dónde guardar") }
                OutlinedButton(onClick = { sheet = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Text("Cancelar")
                }
            }
        }
    }
}

private data class PendingReportExport(val report: FollowUpReport, val caregiver: String, val patient: String,
    val generatedOn: LocalDate, val photos: ReportObservationPhotos)
private data class PendingReportShare(val content: PendingReportExport, val preview: PreviewShareSource?)
private data class ReadyReportShare(val prepared: PreparedReportShare, val counts: ReportPhotoCounts,
    val includePhotos: Boolean)

@Composable
private fun ReportPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, widthDp = 320)
@Preview(showBackground = true, fontScale = 2f)
@Composable private fun FollowUpReportPreview() {
    BitavidaTheme {
        FollowUpReportRoute("Cuidadora de ejemplo", "Persona de ejemplo", "preview",
            MedicalCareOverview(emptyList(), emptyList()), emptyList(), emptyList(), {})
    }
}
