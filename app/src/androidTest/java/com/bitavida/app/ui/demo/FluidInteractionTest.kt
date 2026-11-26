package com.bitavida.app.ui.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.components.BitavidaNodeStyle
import com.bitavida.app.ui.components.BitavidaProgressiveReveal
import com.bitavida.app.ui.components.BitavidaThreadAction
import com.bitavida.app.ui.components.BitavidaTimelineNode
import com.bitavida.app.ui.theme.BitavidaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class FluidInteractionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun controlledClock_pressCancelReleaseAndSingleCallback() {
        var clicks = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BitavidaTheme {
                Box(Modifier.size(320.dp, 120.dp).testTag("stage")) {
                    BitavidaThreadAction(
                        "Acción ficticia",
                        R.drawable.ic_v2_chevron,
                        { clicks++ },
                        Modifier.testTag("action"),
                        emphasized = true,
                        motionEnabledOverride = true,
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(400)
        val action = rule.onNodeWithTag("action")
        action.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(224)
        rule.runOnIdle { assertEquals(0, clicks) }
        action.performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(300)
        rule.runOnIdle { assertEquals(0, clicks) }
        action.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(160)
        action.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
        rule.runOnIdle { assertEquals(1, clicks) }
        action.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun reducedMotion_disabledActionCannotDispatch() {
        var clicks = 0
        rule.setContent {
            BitavidaTheme {
                BitavidaThreadAction(
                    "Acción ficticia deshabilitada",
                    R.drawable.ic_v2_chevron,
                    { clicks++ },
                    Modifier.testTag("action"),
                    enabled = false,
                    motionEnabledOverride = false,
                )
            }
        }
        rule.onNodeWithTag("action").assertIsNotEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun shortTapDoubleTapAndDragOutsideRemainDistinctActions() {
        var clicks = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BitavidaTheme {
                Box(Modifier.size(320.dp, 120.dp).testTag("stage")) {
                    BitavidaThreadAction(
                        "Acción ficticia",
                        R.drawable.ic_v2_chevron,
                        { clicks++ },
                        Modifier.testTag("action"),
                        emphasized = true,
                        motionEnabledOverride = true,
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        val action = rule.onNodeWithTag("action")
        action.performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, clicks) }
        rule.mainClock.advanceTimeBy(728)
        action.performTouchInput { doubleClick() }
        rule.mainClock.advanceTimeBy(600)
        rule.runOnIdle { assertEquals(3, clicks) }
        action.performTouchInput {
            down(center)
            moveTo(androidx.compose.ui.geometry.Offset(-100f, -100f), 200)
            up()
        }
        rule.mainClock.advanceTimeBy(600)
        rule.runOnIdle { assertEquals(3, clicks) }
    }

    @Test
    fun reducedMotionEnabledActionKeepsItsVisualBounds() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BitavidaTheme {
                BitavidaThreadAction(
                    "Acción ficticia",
                    R.drawable.ic_v2_chevron,
                    {},
                    Modifier.testTag("action"),
                    motionEnabledOverride = false,
                )
            }
        }
        rule.mainClock.advanceTimeBy(500)
        val action = rule.onNodeWithTag("action")
        val before = action.fetchSemanticsNode().boundsInRoot
        action.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(200)
        assertEquals(before, action.fetchSemanticsNode().boundsInRoot)
        action.performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(300)
        assertEquals(before, action.fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun expandedTodayCategorySurvivesRestoration() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = todayPreviewProjection(), motionEnabledOverride = false,
                )
            }
        }
        rule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_more_medications"))
        rule.onNodeWithTag("today_more_medications").performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Mostrar menos").assertExists()
        rule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_medication_taken_preview-dose3"))
        rule.onNodeWithTag("today_medication_taken_preview-dose3").assertIsDisplayed()
        rule.onNodeWithContentDescription("Mi día").assertIsSelected()
    }

    @Test
    fun nodeEntryChangesOnlyWhenItsSectionBecomesVisible() {
        val entered = AtomicBoolean(false)
        val scroll = androidx.compose.foundation.ScrollState(0)
        var offset = 0f
        rule.setContent {
            offset = with(androidx.compose.ui.platform.LocalDensity.current) { 3000.dp.toPx() }
            BitavidaTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .size(160.dp)
                        .testTag("node_stage")
                        .clipToBounds()
                        .verticalScroll(scroll),
                ) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(3000.dp))
                    BitavidaProgressiveReveal(
                        modifier = Modifier.onVisibilityChanged(minFractionVisible = 0.01f) {
                            entered.set(it)
                        },
                        order = 2,
                        motionEnabledOverride = true,
                    ) {
                        Box(Modifier.size(80.dp)) {
                            BitavidaTimelineNode(
                                BitavidaNodeStyle.Filled,
                                Modifier.testTag("thread_node_2"),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { scroll.dispatchRawDelta(offset) }
        rule.waitUntil(3000) { entered.get() }
        rule.mainClock.advanceTimeBy(532)
        assertTrue("The entering node must become visible in the viewport", entered.get())
    }

    @Test
    fun todayNavigationAndReminderDispatchThroughTheirRealCallbacks() {
        var planSelections = 0
        var postponeRequests = 0
        rule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = todayPreviewProjection(multiple = false), motionEnabledOverride = false,
                    onPlanSelected = { planSelections++ },
                    onReminderPostpone = { postponeRequests++ },
                )
            }
        }
        rule.onNodeWithContentDescription("Plan").performClick()
        rule.runOnIdle { assertEquals(1, planSelections) }
        rule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_reminder_postpone_preview-reminder"))
        rule.onNodeWithTag("today_reminder_postpone_preview-reminder").performClick()
        rule.runOnIdle { assertEquals(1, postponeRequests) }
        rule.onNodeWithContentDescription("Mi día").assertIsSelected()
    }
}
