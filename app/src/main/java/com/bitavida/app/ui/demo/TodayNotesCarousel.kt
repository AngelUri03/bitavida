package com.bitavida.app.ui.demo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bitavida.app.domain.model.Note
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.notes.NotesImage
import com.bitavida.app.ui.theme.BitavidaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The pager keeps only visible pages in memory; note records themselves are never copied. */
internal fun notesCarouselInitialPage(count: Int, selected: Int = 0): Int {
    if (count <= 1) return 0
    val midpoint = Int.MAX_VALUE / 2
    return midpoint - midpoint % count + selected.coerceIn(0, count - 1)
}

internal fun notesCarouselIndex(page: Int, count: Int): Int =
    if (count <= 1) 0 else Math.floorMod(page, count)

/** Independent of the activity thread, using real, locally stored notes and bounded previews. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodayNotesCarousel(
    notes: List<Note>,
    readImage: suspend (String) -> ByteArray?,
    onNoteSelected: (Note) -> Unit,
    onAllNotes: () -> Unit,
    motion: Boolean,
    enabled: Boolean = true,
    overlayVisible: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val visibleNotes = remember(notes) { notes.filter { it.deletedAt == null }.distinctBy { it.id } }
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var paused by remember { mutableStateOf(false) }
    var inViewport by remember { mutableStateOf(false) }
    var focusedPage by remember { mutableStateOf<Int?>(null) }
    var pressedPage by remember { mutableStateOf<Int?>(null) }
    val view = LocalView.current
    val manager = LocalContext.current.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    var accessibilityActive by remember(manager) { mutableStateOf(manager.isEnabled) }
    DisposableEffect(manager) {
        val listener = android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener { accessibilityActive = it }
        manager.addAccessibilityStateChangeListener(listener)
        onDispose { manager.removeAccessibilityStateChangeListener(listener) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val density = LocalDensity.current
    val titleHeight = with(density) { MaterialTheme.typography.titleSmall.lineHeight.toDp() }
    val bodyHeight = with(density) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
    // The same height is derived from the actual scalable type for every card in this configuration.
    val cardHeight = maxOf(160.dp, titleHeight * 2 + bodyHeight * 3 + 48.dp)

    Column(modifier.fillMaxWidth().testTag("today_notes"),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            Text("Notas importantes", Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            CurrentDetailButton(DetailButtonKind.OUTLINED, onAllNotes, enabled = enabled,
                strongOutline = true, motionAllowedOverride = motion,
                contentPadding = PaddingValues(horizontal = BitavidaSpacing.Medium, vertical = BitavidaSpacing.Small),
                modifier = Modifier.testTag("today_notes_all")) {
                Text("Ver todas", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (visibleNotes.isEmpty()) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().testTag("today_notes_empty")) {
                Text("Aún no hay notas. Usa Ver todas para agregar apuntes.",
                    Modifier.padding(BitavidaSpacing.Default), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else key(visibleNotes.map { it.id }) {
            val count = visibleNotes.size
            val initialIndex = visibleNotes.indexOfFirst { it.id == selectedNoteId }.coerceAtLeast(0)
            val pagerState = rememberPagerState(initialPage = notesCarouselInitialPage(count, initialIndex)) {
                if (count > 1) Int.MAX_VALUE else 1
            }
            val dragged by pagerState.interactionSource.collectIsDraggedAsState()
            var automaticScroll by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val currentIndex = notesCarouselIndex(pagerState.settledPage, count)
            LaunchedEffect(pagerState.settledPage, visibleNotes) {
                selectedNoteId = visibleNotes[currentIndex].id
                // Recenter only at a virtual boundary, after settling, on the identical real note.
                if (count > 1 && (pagerState.settledPage < count ||
                        pagerState.settledPage >= Int.MAX_VALUE - count)) {
                    pagerState.scrollToPage(notesCarouselInitialPage(count, currentIndex))
                }
            }
            val autoAllowed = motion && enabled && started && inViewport && !overlayVisible &&
                !paused && !accessibilityActive && focusedPage == null && pressedPage == null && !dragged &&
                (!pagerState.isScrollInProgress || automaticScroll) && count > 1
            LaunchedEffect(autoAllowed, pagerState.settledPage) {
                if (autoAllowed) {
                    delay(8_500)
                    automaticScroll = true
                    try {
                        pagerState.animateScrollToPage(pagerState.settledPage + 1)
                    } finally {
                        automaticScroll = false
                    }
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Center the active page with a consistent glimpse of both neighbours.
                val sidePadding = if (count > 1) (maxWidth * 0.10f).coerceIn(24.dp, 40.dp) else 0.dp
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().testTag("today_notes_carousel")
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            inViewport = bounds.bottom > 0f && bounds.top < view.height &&
                                bounds.right > 0f && bounds.left < view.width
                        }
                        .semantics {
                            stateDescription = "Nota ${currentIndex + 1} de $count"
                            horizontalScrollAxisRange = ScrollAxisRange(
                                value = { currentIndex.toFloat() }, maxValue = { (count - 1).toFloat() },
                            )
                            if (count > 1 && enabled) {
                                customActions = buildList {
                                    add(CustomAccessibilityAction("Nota anterior") {
                                        scope.launch {
                                            if (motion) pagerState.animateScrollToPage(pagerState.settledPage - 1)
                                            else pagerState.scrollToPage(pagerState.settledPage - 1)
                                        }
                                        true
                                    })
                                    add(CustomAccessibilityAction("Nota siguiente") {
                                        scope.launch {
                                            if (motion) pagerState.animateScrollToPage(pagerState.settledPage + 1)
                                            else pagerState.scrollToPage(pagerState.settledPage + 1)
                                        }
                                        true
                                    })
                                    if (motion) add(CustomAccessibilityAction(
                                        if (paused) "Reanudar desplazamiento automático" else "Pausar desplazamiento automático"
                                    ) { paused = !paused; true })
                                }
                            }
                        },
                    contentPadding = PaddingValues(horizontal = sidePadding, vertical = 5.dp),
                    pageSpacing = BitavidaSpacing.Small,
                    userScrollEnabled = enabled && count > 1,
                    key = { it },
                ) { page ->
                    val note = visibleNotes[notesCarouselIndex(page, count)]
                    val interaction = remember(page) { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    LaunchedEffect(pressed) {
                        if (pressed) pressedPage = page else if (pressedPage == page) pressedPage = null
                    }
                    DisposableEffect(page) {
                        onDispose {
                            if (pressedPage == page) pressedPage = null
                            if (focusedPage == page) focusedPage = null
                        }
                    }
                    val scale by animateFloatAsState(if (pressed && motion) 0.985f else 1f,
                        animationSpec = tween(if (motion) BitavidaFluidMotion.Selection else 0),
                        label = "pulsación de nota")
                    Surface(onClick = { onNoteSelected(note) }, enabled = enabled,
                        interactionSource = interaction,
                        modifier = Modifier.fillMaxWidth().height(cardHeight)
                            .onFocusChanged { if (it.hasFocus) focusedPage = page else if (focusedPage == page) focusedPage = null }
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .testTag("today_note_" + note.id),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(22.dp), shadowElevation = 3.dp) {
                        Row(Modifier.fillMaxSize().padding(BitavidaSpacing.Default),
                            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                                Text(note.title, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                note.content?.takeIf(String::isNotBlank)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            note.imageId?.let {
                                Box(Modifier.weight(0.85f).fillMaxHeight().clip(RoundedCornerShape(16.dp))) {
                                    NotesImage(it, readImage, modifier = Modifier.fillMaxSize(),
                                        thumbnail = true, cropThumbnailToBounds = true,
                                        description = "Imagen adjunta de la nota")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
