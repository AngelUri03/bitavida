package com.bitavida.app.ui.report

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises only the debug fixture, which never reads or writes real care data. */
class FollowUpPdfViewerGestureTest {
    @get:Rule
    val compose = createAndroidComposeRule<ReportFixtureActivity>()

    private fun waitForFirstPage() {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithTag("report_pdf_page_0").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pageCount(): Int {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodes(hasText("Solo datos ficticios", substring = true))
                .fetchSemanticsNodes().any { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains("páginas") } == true
                }
        }
        val node = compose.onNodeWithText("Solo datos ficticios", substring = true).fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].joinToString(" ") { it.text }
        return requireNotNull(Regex("""(\d+) páginas""").find(text)).groupValues[1].toInt()
    }

    private fun zoomPercentage(): Int {
        val node = compose.onAllNodesWithTag("report_pdf_page_0").fetchSemanticsNodes().firstOrNull()
            ?: return -1
        val text = node.config.getOrNull(SemanticsProperties.StateDescription) ?: return -1
        return Regex("""(\d+)""").find(text)?.value?.toIntOrNull() ?: -1
    }

    private fun swipeToLastPage(pageCount: Int) {
        val tag = "report_pdf_page_${pageCount - 1}"
        repeat(pageCount * 8 + 12) {
            if (compose.onNodeWithTag(tag).isDisplayed()) {
                compose.onNodeWithTag(tag).assertIsDisplayed()
                return
            }
            compose.onNodeWithTag("report_pdf_document").performTouchInput { swipeUp(durationMillis = 400) }
            compose.waitForIdle()
        }
        throw AssertionError("Una secuencia de deslizamientos no llegó a la última página")
    }

    private fun returnToFirstPage() {
        compose.onNodeWithContentDescription("Volver al inicio").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onNodeWithTag("report_pdf_page_0").isDisplayed()
        }
        compose.onNodeWithTag("report_pdf_page_0").assertIsDisplayed()
    }

    @Test
    fun scrollPinchDoubleTapAndReturnToTopWorkAcrossDocumentAndSections() {
        waitForFirstPage()
        val count = pageCount()
        assertTrue("La prueba necesita varias páginas", count > 1)

        swipeToLastPage(count)
        returnToFirstPage()

        compose.onNodeWithTag("report_pdf_page_0").performTouchInput {
            doubleClick(Offset(width * 0.5f, height * 0.2f))
        }
        compose.waitUntil(timeoutMillis = 10_000) { zoomPercentage() > 100 }
        swipeToLastPage(count)
        returnToFirstPage()
        compose.waitUntil(timeoutMillis = 10_000) { zoomPercentage() == 100 }

        compose.onNodeWithTag("report_pdf_page_0").performTouchInput {
            pinch(
                start0 = Offset(width * 0.44f, height * 0.18f),
                end0 = Offset(width * 0.30f, height * 0.18f),
                start1 = Offset(width * 0.56f, height * 0.18f),
                end1 = Offset(width * 0.70f, height * 0.18f),
                durationMillis = 600,
            )
        }
        compose.waitUntil(timeoutMillis = 10_000) { zoomPercentage() > 100 }
        swipeToLastPage(count)
        returnToFirstPage()

        compose.onNodeWithText("Por secciones").performClick()
        // LazyColumn only composes sections when they approach the viewport.
        compose.onNodeWithTag("report_sections_list").performScrollToNode(hasText("Medicamentos"))
        compose.onNodeWithText("Medicamentos", substring = false).assertIsDisplayed().performClick()
        compose.onNodeWithTag("report_sections_list").performTouchInput { swipeUp(durationMillis = 400) }
        compose.onNodeWithContentDescription("Volver al inicio").assertIsDisplayed().performClick()
        compose.onNodeWithText("Resumen para la consulta").assertIsDisplayed()
    }
}
