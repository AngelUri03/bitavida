package com.bitavida.app.ui.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.bitavida.app.R
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.measurements.GlucoseCaptureChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private data class PreviewLink(val bounds: List<RectF>, val page: Int? = null, val y: Float = 0f, val uri: Uri? = null)
private data class PreviewPage(val bitmap: Bitmap, val width: Int, val height: Int, val links: List<PreviewLink>)
private data class PdfZoomGesture(val factor: Float, val focus: Offset, val pan: Offset = Offset.Zero, val toggle: Boolean = false)

/** Preserve the content coordinate under the gesture while changing its scale. */
internal fun reportZoomScrollAdjustment(
    oldZoom: Float, newZoom: Float, currentX: Float, focus: Offset, pan: Offset,
): Offset {
    val ratio = newZoom / oldZoom
    return Offset(currentX + focus.x * (ratio - 1f) - pan.x, focus.y * (ratio - 1f) - pan.y)
}

internal data class ReportZoomPosition(val zoom: Float, val horizontal: Float, val verticalDelta: Float)

/** Compose queued scale and pan events without dropping movement between frames. */
internal fun accumulateReportZoom(
    current: ReportZoomPosition, factor: Float, focus: Offset, pan: Offset, toggle: Boolean,
): ReportZoomPosition {
    val next = if (toggle) {
        if (current.zoom > 1.01f) 1f else 2f
    } else (current.zoom * factor).coerceIn(1f, 3f)
    val adjusted = reportZoomScrollAdjustment(current.zoom, next, current.horizontal, focus, pan)
    return ReportZoomPosition(next, adjusted.x, current.verticalDelta + adjusted.y)
}

internal data class PreviewShareSource(val writeTo: (OutputStream) -> Unit, val counts: ReportPhotoCounts)

private class PreviewDocument(private val renderer: PdfRenderer, private val shareDescriptor: ParcelFileDescriptor,
    val counts: ReportPhotoCounts) {
    val pageCount = renderer.pageCount
    private var closed = false

    @Synchronized fun render(index: Int, requestedWidth: Int): PreviewPage? {
        if (closed) return null
        return renderer.openPage(index).use { page ->
            val width = requestedWidth.coerceIn(1, 1800)
            val height = (width.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val scale = width.toFloat() / page.width
            val matrix = Matrix().apply { setScale(scale, scale) }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            // PDF link extraction is available in the platform from Android 15.
            val links = if (Build.VERSION.SDK_INT >= 35) {
                runCatching {
                    page.gotoLinks.map { PreviewLink(it.bounds, it.destination.pageNumber, it.destination.yCoordinate) } +
                        page.linkContents.map { PreviewLink(it.bounds, uri = it.uri) }
                }.getOrDefault(emptyList())
            } else emptyList()
            PreviewPage(bitmap, page.width, page.height, links)
        }
    }

    @Synchronized fun copyTo(output: OutputStream) {
        check(!closed)
        val buffer = ByteArray(16 * 1024)
        try {
            var offset = 0L
            while (true) {
                val count = Os.pread(shareDescriptor.fileDescriptor, buffer, 0, buffer.size, offset)
                if (count == 0) break
                check(count > 0)
                output.write(buffer, 0, count)
                offset += count
            }
        } finally { buffer.fill(0) }
    }

    @Synchronized fun close() {
        if (!closed) {
            closed = true
            renderer.close()
            shareDescriptor.close()
        }
    }
}

/** Uses the export writer and a private unlinked temporary file; pages are rendered on demand. */
@Composable
internal fun FollowUpPdfPreview(
    report: FollowUpReport,
    caregiver: String,
    patient: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    generatedOn: LocalDate = LocalDate.now(),
    photos: ReportObservationPhotos = ReportObservationPhotos.omitted(),
    onShare: ((PreviewShareSource) -> Unit)? = null,
    sharing: Boolean = false,
) {
    val context = LocalContext.current
    val motion = rememberBitavidaMotionAllowed(null)
    var document by remember(report, caregiver, patient, generatedOn, photos) { mutableStateOf<PreviewDocument?>(null) }
    var pageLinks by remember(report, caregiver, patient, generatedOn, photos) { mutableStateOf<List<FollowUpPdfLink>>(emptyList()) }
    var failed by remember(report, caregiver, patient, generatedOn, photos) { mutableStateOf(false) }
    var sectionsMode by remember { mutableStateOf(false) }
    val model = remember(report, caregiver, patient, generatedOn) {
        buildFollowUpDocument(report, patient, caregiver, generatedOn)
    }
    val pdfScroll = rememberLazyListState()
    val pdfHorizontalScroll = rememberScrollState()
    val zoom = remember(report) { mutableFloatStateOf(1f) }
    val sectionScroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activeScroll = if (sectionsMode) sectionScroll else pdfScroll
    val scrollThreshold = with(LocalDensity.current) { 400.dp.roundToPx() }
    val showScrollToTop by remember(activeScroll, scrollThreshold) {
        derivedStateOf { activeScroll.firstVisibleItemIndex > 0 ||
            activeScroll.firstVisibleItemScrollOffset > scrollThreshold }
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(report, caregiver, patient, generatedOn, photos) {
        var opened: PreviewDocument? = null
        var generatedLinks: List<FollowUpPdfLink> = emptyList()
        var photoCounts: ReportPhotoCounts? = null
        try {
            withContext(Dispatchers.IO + NonCancellable) {
                val temporary = File.createTempFile("bitavida-preview-", ".pdf", context.cacheDir)
                try {
                    val regular = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_regular))
                    val bold = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_bold))
                    val descriptor = ParcelFileDescriptor.open(
                        temporary, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE
                    )
                    try {
                        // Unlink the still-empty file before writing sensitive content.
                        check(temporary.delete()) { "No se pudo retirar el archivo temporal" }
                        ParcelFileDescriptor.AutoCloseOutputStream(
                            ParcelFileDescriptor.dup(descriptor.fileDescriptor)
                        ).use { writeFollowUpPdf(it, report, caregiver, patient, regular, bold, generatedOn,
                            onPageLinks = { links -> generatedLinks = links }, photos = photos,
                            onPhotoCounts = { photoCounts = it }) }
                        Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET)
                        val duplicate = ParcelFileDescriptor.dup(descriptor.fileDescriptor)
                        try {
                            opened = PreviewDocument(PdfRenderer(descriptor), duplicate, requireNotNull(photoCounts))
                        } catch (error: Exception) {
                            duplicate.close()
                            throw error
                        }
                    } catch (error: Exception) {
                        descriptor.close()
                        throw error
                    }
                } finally {
                    if (temporary.exists()) temporary.delete()
                }
            }
            pageLinks = generatedLinks
            document = opened
            awaitCancellation()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        } finally {
            document = null
            withContext(Dispatchers.IO + NonCancellable) { opened?.close() }
        }
    }
    Column(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (LocalDensity.current.fontScale >= 1.5f) {
                CurrentMedicationBack(onBack, motionAllowed = motion, enabled = true, label = "Informe")
                Text("Vista previa", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CurrentMedicationBack(onBack, motionAllowed = motion, enabled = true, label = "Informe")
                    Text("Vista previa", Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge)
                }
            }
            if (onShare != null) {
                OutlinedButton(onClick = {
                    val current = document ?: return@OutlinedButton
                    onShare(PreviewShareSource(current::copyTo, current.counts))
                }, enabled = document != null && !sharing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(painterResource(android.R.drawable.ic_menu_share), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (sharing) "Preparando…" else "Compartir PDF")
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlucoseCaptureChip("Vista PDF", !sectionsMode, true, motion, { sectionsMode = false })
                GlucoseCaptureChip("Por secciones", sectionsMode, true, motion, { sectionsMode = true })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (sectionsMode) {
                ReportSectionsPreview(
                    report = report, patient = patient, caregiver = caregiver, generatedOn = generatedOn,
                    model = model, listState = sectionScroll,
                )
            } else when {
                failed -> Text("No fue posible preparar la vista PDF. Puedes consultar el informe por secciones.",
                    Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
                document == null -> CircularProgressIndicator(Modifier.padding(20.dp))
                else -> ReportPdfPages(document!!, pdfScroll, pdfHorizontalScroll, zoom, motion, pageLinks)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollToTop,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
                enter = if (motion) fadeIn() else EnterTransition.None,
                exit = if (motion) fadeOut() else ExitTransition.None,
            ) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (!sectionsMode) {
                                zoom.floatValue = 1f
                                pdfHorizontalScroll.scrollTo(0)
                            }
                            if (motion) activeScroll.animateScrollToItem(0) else activeScroll.scrollToItem(0)
                        }
                    },
                    modifier = Modifier.size(56.dp).testTag("report_pdf_to_top"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(painterResource(R.drawable.ic_v2_arrow_back), contentDescription = "Volver al inicio",
                        modifier = Modifier.size(24.dp).rotate(90f))
                }
            }
        }
    }
}

@Composable
private fun ReportPdfPages(
    document: PreviewDocument,
    listState: androidx.compose.foundation.lazy.LazyListState,
    horizontalState: androidx.compose.foundation.ScrollState,
    zoom: MutableFloatState,
    motion: Boolean,
    generatedLinks: List<FollowUpPdfLink>,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gestures = remember(document) { Channel<PdfZoomGesture>(Channel.UNLIMITED) }
    DisposableEffect(gestures) { onDispose { gestures.close() } }
    LaunchedEffect(gestures) {
        // One scroll mutation per frame. Coalesce every queued event rather than
        // launching competing scrolls or dropping movement under rapid pinches.
        for (first in gestures) {
            var position = ReportZoomPosition(zoom.floatValue, horizontalState.value.toFloat(), 0f)
            var gesture: PdfZoomGesture? = first
            while (gesture != null) {
                position = accumulateReportZoom(position, gesture.factor, gesture.focus, gesture.pan, gesture.toggle)
                gesture = gestures.tryReceive().getOrNull()
            }
            zoom.floatValue = position.zoom
            withFrameNanos { }
            horizontalState.scrollTo(position.horizontal.roundToInt().coerceAtLeast(0))
            listState.scrollBy(position.verticalDelta)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = (maxWidth - 32.dp).coerceAtLeast(1.dp)
        val renderWidth = with(density) {
            (viewport.toPx() * (ceil(zoom.floatValue * 2f) / 2f)).roundToInt().coerceIn(1, 1800)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("report_pdf_document"),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(document.pageCount, key = { it }) { index ->
                val result by produceState<Result<PreviewPage?>?>(null, document, index, renderWidth) {
                    value = try {
                        Result.success(withContext(Dispatchers.IO) { document.render(index, renderWidth) })
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Página ${index + 1} de ${document.pageCount}", style = MaterialTheme.typography.labelLarge)
                    val page = result?.getOrNull()
                    if (page != null) {
                        // The horizontal scroller consumes horizontal movement only. Vertical
                        // one-finger swipes remain available to the document LazyColumn.
                        Box(Modifier.fillMaxWidth().testTag("report_pdf_horizontal_$index").horizontalScroll(horizontalState)) {
                            Image(
                                page.bitmap.asImageBitmap(), contentDescription = "Página ${index + 1} del informe",
                                modifier = Modifier.width(viewport * zoom.floatValue)
                                    .aspectRatio(page.width.toFloat() / page.height)
                                    .testTag("report_pdf_page_$index")
                                    .semantics { stateDescription = "Ampliación ${(zoom.floatValue * 100).roundToInt()} %" }
                                    .pointerInput(document, index) {
                                        detectTapGestures(
                                            onDoubleTap = { point ->
                                                gestures.trySend(PdfZoomGesture(1f, point, toggle = true))
                                            },
                                            onTap = { point ->
                                                val px = point.x * page.width / size.width
                                                val py = point.y * page.height / size.height
                                                val generated = generatedLinks.firstOrNull {
                                                    it.sourcePage == index && px >= it.left && px <= it.right &&
                                                        py >= it.top && py <= it.bottom
                                                }
                                                val link = page.links.firstOrNull { item ->
                                                    item.bounds.any { it.contains(px, py) }
                                                }
                                                if (generated != null && generated.targetPage in 0 until document.pageCount) {
                                                    scope.launch {
                                                        val offset = (generated.targetY / page.width * viewport.toPx() *
                                                            zoom.floatValue).roundToInt().coerceAtLeast(0)
                                                        if (motion) listState.animateScrollToItem(generated.targetPage, offset)
                                                        else listState.scrollToItem(generated.targetPage, offset)
                                                    }
                                                } else if (link?.page != null && link.page in 0 until document.pageCount) {
                                                    scope.launch {
                                                        val offset = (link.y / page.height * viewport.toPx() * zoom.floatValue *
                                                            page.height / page.width).roundToInt().coerceAtLeast(0)
                                                        if (motion) listState.animateScrollToItem(link.page, offset)
                                                        else listState.scrollToItem(link.page, offset)
                                                    }
                                                } else if (link?.uri != null && link.uri.scheme in setOf("http", "https")) {
                                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, link.uri)) }
                                                    catch (_: android.content.ActivityNotFoundException) { }
                                                    catch (_: SecurityException) { }
                                                }
                                            },
                                        )
                                    }
                                    .pointerInput(document, index) {
                                        // Consume only a multi-touch gesture. A simple vertical
                                        // drag must always reach the parent document scroller.
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            do {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (event.changes.count { it.pressed } >= 2) {
                                                    val centroid = event.calculateCentroid(useCurrent = false)
                                                    val pan = event.calculatePan()
                                                    if (centroid != Offset.Unspecified) {
                                                        gestures.trySend(PdfZoomGesture(event.calculateZoom(), centroid, pan))
                                                    }
                                                    event.changes.forEach { it.consume() }
                                                }
                                            } while (event.changes.any { it.pressed })
                                        }
                                    },
                            )
                        }
                    } else if (result?.isFailure == true) {
                        Text("No fue posible mostrar esta página. Consulta el informe por secciones.")
                    } else {
                        Box(Modifier.fillMaxWidth().horizontalScroll(horizontalState)) {
                            Box(Modifier.width(viewport * zoom.floatValue).aspectRatio(595f / 842f),
                                contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSectionsPreview(
    report: FollowUpReport,
    patient: String,
    caregiver: String,
    generatedOn: LocalDate,
    model: FollowUpDocumentModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val expanded = remember(report) { mutableStateMapOf<String, Boolean>() }
    val format = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-MX")) }
    LazyColumn(state = listState, modifier = Modifier.testTag("report_sections_list"),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("summary") {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Resumen para la consulta", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    model.sourceWarning?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    Text("Paciente: $patient", style = MaterialTheme.typography.bodyMedium)
                    Text("Cuidador: $caregiver", style = MaterialTheme.typography.bodyMedium)
                    Text("${report.start.format(format)} – ${report.end.format(format)}", style = MaterialTheme.typography.bodySmall)
                    Text("Generado: ${generatedOn.format(format)}", style = MaterialTheme.typography.bodySmall)
                    val cutoffFormat = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("es-MX"))
                    Text("Corte: ${model.cutoffAt.format(cutoffFormat)}", style = MaterialTheme.typography.bodySmall)
                    model.summary.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        model.sections.forEachIndexed { sectionIndex, section ->
            item("heading_$sectionIndex") {
                val open = expanded[section.title] == true
                Surface(onClick = { expanded[section.title] = !open },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { stateDescription = if (open) "Expandida" else "Contraída" },
                    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall)
                            Text("${section.rows.size} registros", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (open) "−" else "+", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (expanded[section.title] == true) {
                itemsIndexed(section.rows, key = { index, _ -> "row_${sectionIndex}_$index" }) { _, row ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall)
                        val pattern = if (row.timeKnown) "d MMM yyyy · HH:mm" else "d MMM yyyy"
                        Text(row.occurredAt.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("es-MX"))),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row.detail, style = MaterialTheme.typography.bodyMedium)
                        Text(row.provenance, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        if (model.sections.isEmpty()) item("empty") {
            Text("No hay datos registrados para este periodo.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
