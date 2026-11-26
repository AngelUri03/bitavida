package com.bitavida.app.ui.report

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bitavida.app.R
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.ui.theme.BitavidaTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders only a locally constructed, fictitious long report. The test does not open a
 * repository, personal profile, Room database, or a real export destination.
 */
@RunWith(AndroidJUnit4::class)
class FollowUpPdfLongViewerGestureTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var report: FollowUpReport
    private lateinit var fixture: FixturePdf
    private val backCalls = AtomicInteger()

    @Before fun prepareSyntheticViewer() {
        report = fixtureReport("long")
        fixture = generateSyntheticPdfMetadata(report)
        assertTrue("Se necesita un informe ficticio largo", fixture.pageCount >= 6)
        compose.setContent {
            BitavidaTheme {
                FollowUpPdfPreview(
                    report = report,
                    caregiver = "Cuidadora ficticia",
                    patient = "Paciente ficticio",
                    onBack = { backCalls.incrementAndGet() },
                    generatedOn = report.end,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        waitForPage(0)
        compose.onNodeWithText("Página 1 de ${fixture.pageCount}").assertIsDisplayed()
    }

    @Test fun longDocumentScrollPinchPanDoubleTapAndBackToTop() {
        swipeToLastPage()
        returnToTop()

        val center = visiblePagePoint(.5f, .45f)
        val distance = visiblePageWidth() * .12f
        compose.onNodeWithTag(pageTag(0)).performTouchInput {
            pinch(
                start0 = Offset(center.x - distance, center.y),
                end0 = Offset(center.x - distance * 1.7f, center.y),
                start1 = Offset(center.x + distance, center.y),
                end1 = Offset(center.x + distance * 1.7f, center.y),
                durationMillis = 600,
            )
        }
        compose.waitUntil(timeoutMillis = 15_000) { zoomPercentage() > 100 }

        compose.waitUntil(timeoutMillis = 15_000) { horizontalRange()?.maxValue?.invoke()?.let { it > 1f } == true }
        val range = requireNotNull(horizontalRange())
        val beforePan = range.value()
        // A focal-point zoom can legitimately leave the page at either edge.
        // Drag toward the remaining scrollable area and verify actual scroll state.
        val dragLeft = beforePan < range.maxValue() / 2f
        val dragStart = visiblePagePoint(if (dragLeft) .78f else .22f, .35f)
        val dragEnd = visiblePagePoint(if (dragLeft) .22f else .78f, .35f)
        compose.onNodeWithTag(pageTag(0)).performTouchInput {
            swipe(start = dragStart, end = dragEnd, durationMillis = 500)
        }
        compose.waitForIdle()
        val afterPan = requireNotNull(horizontalRange()).value()
        assertTrue("La página ampliada debe permitir desplazamiento horizontal: antes=$beforePan, después=$afterPan",
            if (dragLeft) afterPan - beforePan > 1f else beforePan - afterPan > 1f)

        val zoomPoint = visiblePagePoint(.5f, .4f)
        compose.onNodeWithTag(pageTag(0)).performTouchInput { doubleClick(zoomPoint) }
        compose.waitUntil(timeoutMillis = 15_000) { zoomPercentage() == 100 }
        val fitPoint = visiblePagePoint(.5f, .4f)
        compose.onNodeWithTag(pageTag(0)).performTouchInput { doubleClick(fitPoint) }
        compose.waitUntil(timeoutMillis = 15_000) { zoomPercentage() > 100 }

        swipeToLastPage()
        returnToTop()
        compose.waitUntil(timeoutMillis = 15_000) { zoomPercentage() == 100 }
        compose.onNodeWithText("Informe", substring = false).assertIsDisplayed().performClick()
        assertEquals(1, backCalls.get())
    }

    @Test fun swipeOnLinkDoesNotActivateItAndTapOpensItsDestination() {
        val link = fixture.links.filter { it.sourcePage == 0 && it.targetPage >= 2 }
            .maxByOrNull { it.targetPage }
        requireNotNull(link) { "El fixture debe tener un enlace a un detalle posterior" }

        val sourcePoint = bringSourceLinkIntoView(link)
        val viewport = compose.onNodeWithTag("report_pdf_document").fetchSemanticsNode().boundsInRoot
        val distance = minOf(100f, viewport.height * .12f)
        compose.onNodeWithTag(pageTag(0)).performTouchInput {
            swipe(sourcePoint, Offset(sourcePoint.x, sourcePoint.y - distance), durationMillis = 500)
        }
        compose.waitForIdle()
        assertTrue("Un arrastre sobre el enlace no debe abandonar la primera página",
            compose.onNodeWithTag(pageTag(0)).isDisplayed())
        assertFalse("Un arrastre no debe activar el enlace",
            compose.onNodeWithTag(pageTag(link.targetPage)).isDisplayed())

        val zoomPoint = bringSourceLinkIntoView(link)
        compose.onNodeWithTag(pageTag(0)).performTouchInput { doubleClick(zoomPoint) }
        compose.waitUntil(timeoutMillis = 15_000) { zoomPercentage() > 100 }
        assertFalse("Ampliar sobre el enlace no debe activarlo",
            compose.onNodeWithTag(pageTag(link.targetPage)).isDisplayed())
        val restorePoint = bringSourceLinkIntoView(link)
        compose.onNodeWithTag(pageTag(0)).performTouchInput { doubleClick(restorePoint) }
        compose.waitUntil(timeoutMillis = 15_000) { zoomPercentage() == 100 }

        val clickPoint = bringSourceLinkIntoView(link)
        compose.onNodeWithTag(pageTag(0)).performTouchInput { click(clickPoint) }
        waitForPage(link.targetPage)
        // The title of the page can scroll above the viewport when the link targets
        // a section lower on that page; the rendered page itself must be visible.
        compose.onNodeWithText("Página ${link.targetPage + 1} de ${fixture.pageCount}").assertExists()

        val destination = compose.onNodeWithTag(pageTag(link.targetPage)).fetchSemanticsNode()
        val documentBounds = compose.onNodeWithTag("report_pdf_document").fetchSemanticsNode().boundsInRoot
        val anchorY = destination.positionInRoot.y + destination.size.height * (link.targetY / 842f)
        assertTrue("El título enlazado debe quedar dentro del documento visible",
            anchorY >= documentBounds.top - 30f && anchorY <= documentBounds.bottom)
    }

    private fun waitForPage(index: Int) {
        compose.waitUntil(timeoutMillis = 90_000) { compose.onNodeWithTag(pageTag(index)).isDisplayed() }
        compose.onNodeWithTag(pageTag(index)).assertIsDisplayed()
    }

    private fun swipeToLastPage() {
        val last = fixture.pageCount - 1
        repeat(fixture.pageCount * 12 + 24) {
            if (compose.onNodeWithTag(pageTag(last)).isDisplayed()) {
                val page = compose.onNodeWithTag(pageTag(last)).fetchSemanticsNode()
                val viewport = compose.onNodeWithTag("report_pdf_document").fetchSemanticsNode().boundsInRoot
                val pageBottom = page.positionInRoot.y + page.size.height
                if (pageBottom in viewport.top..viewport.bottom) {
                    compose.onNodeWithTag(pageTag(last)).assertIsDisplayed()
                    return
                }
            }
            compose.onNodeWithTag("report_pdf_document").performTouchInput { swipeUp(durationMillis = 300) }
            compose.waitForIdle()
        }
        throw AssertionError("No se alcanzó la última página ficticia mediante deslizamientos")
    }

    private fun returnToTop() {
        compose.onNodeWithContentDescription("Volver al inicio").assertIsDisplayed().performClick()
        waitForPage(0)
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onNodeWithText("Página 1 de ${fixture.pageCount}").isDisplayed()
        }
        compose.onNodeWithText("Página 1 de ${fixture.pageCount}").assertIsDisplayed()
    }

    private fun zoomPercentage(): Int {
        val node = compose.onAllNodesWithTag(pageTag(0)).fetchSemanticsNodes().firstOrNull() ?: return -1
        val text = node.config.getOrNull(SemanticsProperties.StateDescription) ?: return -1
        return Regex("""(\d+)""").find(text)?.value?.toIntOrNull() ?: -1
    }

    private fun visiblePagePoint(horizontal: Float, vertical: Float): Offset {
        val node = compose.onNodeWithTag(pageTag(0)).fetchSemanticsNode()
        val bounds = node.boundsInRoot
        // performTouchInput is relative to the clipped semantics bounds, not
        // the unclipped layout origin after the page has been panned or scrolled.
        return Offset(bounds.width * horizontal, bounds.height * vertical)
    }

    private fun visiblePageWidth(): Float =
        compose.onNodeWithTag(pageTag(0)).fetchSemanticsNode().boundsInRoot.width

    private fun horizontalRange() = compose.onAllNodesWithTag("report_pdf_horizontal_0")
        .fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)

    private fun bringSourceLinkIntoView(link: FollowUpPdfLink): Offset {
        repeat(18) {
            val page = compose.onNodeWithTag(pageTag(0)).fetchSemanticsNode()
            val viewport = compose.onNodeWithTag("report_pdf_document").fetchSemanticsNode().boundsInRoot
            val local = Offset(
                page.size.width * ((link.left + link.right) / 2f / 595f),
                page.size.height * ((link.top + link.bottom) / 2f / 842f),
            )
            val root = page.positionInRoot + local
            val margin = minOf(110f, viewport.height * .15f)
            val fab = compose.onAllNodesWithTag("report_pdf_to_top")
                .fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            val bottom = if (fab != null && root.x >= fab.left - 16f && root.x <= fab.right + 16f)
                minOf(viewport.bottom - margin, fab.top - 16f) else viewport.bottom - margin
            val left = viewport.left + 10f
            val right = viewport.right - 10f
            if (root.x in left..right && root.y in (viewport.top + margin)..bottom) {
                // TouchInjectionScope.localToRoot adds boundsInRoot.topLeft.
                return root - page.boundsInRoot.topLeft
            }
            if (root.y !in (viewport.top + margin)..bottom) {
                val down = root.y < viewport.top + margin
                compose.onNodeWithTag("report_pdf_document").performTouchInput {
                    val from = if (down) height * .35f else height * .65f
                    val to = if (down) height * .65f else height * .35f
                    swipe(Offset(width * .5f, from), Offset(width * .5f, to), durationMillis = 350)
                }
            } else {
                val dragRight = root.x < left
                val start = visiblePagePoint(if (dragRight) .25f else .75f, .5f)
                val end = visiblePagePoint(if (dragRight) .75f else .25f, .5f)
                compose.onNodeWithTag(pageTag(0)).performTouchInput {
                    swipe(start, end, durationMillis = 350)
                }
            }
            compose.waitForIdle()
        }
        throw AssertionError("No se pudo situar el enlace ficticio en el área visible")
    }

    private fun pageTag(index: Int) = "report_pdf_page_$index"

    private data class FixturePdf(val pageCount: Int, val links: List<FollowUpPdfLink>)

    private fun generateSyntheticPdfMetadata(report: FollowUpReport): FixturePdf {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val regular = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_regular))
        val bold = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_bold))
        val output = ByteArrayOutputStream()
        var links = emptyList<FollowUpPdfLink>()
        writeFollowUpPdf(output, report, "Cuidadora ficticia", "Paciente ficticio",
            regular, bold, report.end, onPageLinks = { links = it })
        val temporary = File.createTempFile("synthetic-viewer-", ".pdf", context.cacheDir)
        return try {
            temporary.outputStream().use { it.write(output.toByteArray()) }
            val descriptor = ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY)
            val count = PdfRenderer(descriptor).use { it.pageCount }
            FixturePdf(count, links)
        } finally {
            temporary.delete()
        }
    }
}
