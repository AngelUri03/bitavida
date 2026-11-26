package com.bitavida.app.ui.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.brand.AnimatedBitavidaMark
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.theme.BitavidaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Prepared operational-screen coverage; no synthetic clinical result is persisted. */
class TodayDemoScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun fixedWindowAndContextSchedulesKeepOneWholeMedicationActionAtBothTextScales() {
        val schedule = mutableStateOf("10:00 a. m.")
        val icon = mutableIntStateOf(com.bitavida.app.R.drawable.ic_v2_clock)
        val fontScale = androidx.compose.runtime.mutableFloatStateOf(1f)
        var registrations = 0
        var optionRequests = 0
        composeRule.setContent {
            BitavidaTheme {
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides
                        androidx.compose.ui.unit.Density(density.density, fontScale.floatValue),
                ) {
                    TodayTypography {
                        Box(Modifier.width(320.dp).padding(20.dp)) {
                            TodayMedicationSurface(
                                "homogeneous", "Medicamento de ejemplo", "1 tableta",
                                schedule.value, enabled = true, motion = false,
                                onTaken = { registrations++ }, onOptions = { optionRequests++ },
                                scheduleIcon = icon.intValue,
                            )
                        }
                    }
                }
            }
        }
        val schedules = listOf(
            "10:00 a. m." to com.bitavida.app.R.drawable.ic_v2_clock,
            "11:00 a. m. – 12:00 p. m." to com.bitavida.app.R.drawable.ic_v2_clock,
            "12:00 p. m. – 1:00 p. m." to com.bitavida.app.R.drawable.ic_v2_clock,
            "Con el desayuno" to com.bitavida.app.R.drawable.ic_v2_utensils,
        )
        listOf(1f, 2f).forEach { scale ->
            schedules.forEach { (label, resource) ->
                composeRule.runOnIdle {
                    fontScale.floatValue = scale
                    schedule.value = label
                    icon.intValue = resource
                }
                val button = composeRule.onNodeWithTag("today_medication_taken_homogeneous")
                button.assertHasClickAction().assertHeightIsAtLeast(48.dp)
                composeRule.onNodeWithTag("today_medication_schedule_homogeneous", useUnmergedTree = true)
                    .assertExists().assertHasNoClickAction()
                button.assert(hasContentDescription(label, substring = true))
                composeRule.onNodeWithTag("today_medication_action_homogeneous", useUnmergedTree = true)
                    .assertExists().assertHasNoClickAction()
                composeRule.onAllNodes(
                    hasClickAction() and hasAnyAncestor(hasTestTag("today_medication_taken_homogeneous")),
                    useUnmergedTree = true,
                ).assertCountEquals(0)
                button.performClick()
            }
        }
        composeRule.onNodeWithTag("today_medication_taken_homogeneous")
            .performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(8, registrations)
            assertEquals(1, optionRequests)
        }
    }

    @Test fun pendingSectionsStaySeparateAndResolveThroughTheirModuleCallbacks() {
        val projection = mutableStateOf(todayPreviewProjection(multiple = false))
        var notPerformedMeasurement: TodayMeasurementItem? = null
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    state = TodayDemoState.HasPlan, currentDateTime = todayPreviewNow,
                    projection = projection.value, motionEnabledOverride = false,
                    onMedicationTaken = {
                        projection.value = projection.value.copy(medications = emptyList())
                    },
                    onMeasurementNotPerformed = { notPerformedMeasurement = it },
                )
            }
        }
        composeRule.onNodeWithTag("today_pending_summary").assertDoesNotExist()
        composeRule.onNodeWithTag("today_medication_taken_preview-dose")
            .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onAllNodes(hasText("Hoy tienes", substring = true)).assertCountEquals(0)
        composeRule.onNodeWithTag("today_medications").assertDoesNotExist()
        composeRule.onNodeWithTag("today_thread_node_medications").assertDoesNotExist()
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_measurement_not_performed_CAPILLARY_GLUCOSE:preview-reading"))
        composeRule.onNodeWithTag("today_measurement_not_performed_CAPILLARY_GLUCOSE:preview-reading").performClick()
        composeRule.runOnIdle { assertEquals("preview-reading", notPerformedMeasurement?.occurrence?.id) }
        composeRule.onNodeWithText("Resueltos de hoy").assertDoesNotExist()
        composeRule.onNodeWithText("Registros del día").assertDoesNotExist()
    }

    @Test fun eachCategoryExpandsWithoutLeavingTodayOrChangingOtherModules() {
        var called = false
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    state = TodayDemoState.HasPlan, currentDateTime = todayPreviewNow,
                    projection = todayPreviewProjection(), motionEnabledOverride = false,
                    onMedicationTaken = { called = it.id == "preview-dose3" },
                )
            }
        }
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_more_medications"))
        composeRule.onNodeWithText("Ver 1 pendiente más").assertExists()
        composeRule.onNodeWithTag("today_more_medications").performClick()
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_medication_taken_preview-dose3"))
        composeRule.onNodeWithTag("today_medication_taken_preview-dose3").performClick()
        composeRule.runOnIdle { assertEquals(true, called) }
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_more_medications"))
        composeRule.onNodeWithText("Mostrar menos").assertExists()
        composeRule.onNodeWithTag("today_more_medications").performClick()
        composeRule.onNodeWithTag("today_medication_taken_preview-dose3").assertDoesNotExist()
        composeRule.onNodeWithText("Ver 1 pendiente más").assertExists()
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_measurements"))
        composeRule.onNodeWithTag("today_measurements").assertExists()
    }

    @Test fun multipleExpandedSectionsScrollAndCollapseIndependentlyWithoutNavigatingOrRecording() {
        val fixture = todayThreadPreviewFixture(TodayThreadPreviewCase.MULTIPLE_EXPANDED)
        var navigations = 0
        var registrations = 0
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    state = TodayDemoState.HasPlan, currentDateTime = todayPreviewNow,
                    projection = fixture.projection, notes = fixture.notes,
                    initialExpandedSections = fixture.expandedSections,
                    motionEnabledOverride = false,
                    onPlanSelected = { navigations++ },
                    onMedicationTaken = { registrations++ },
                    onObservationRegister = { registrations++ },
                )
            }
        }
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(
            hasTestTag("today_observation_register_thread-fictional-observation-7"),
        )
        composeRule.onNodeWithTag("today_observation_register_thread-fictional-observation-7")
            .assertHasClickAction().assertIsDisplayed()
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_more_medications"))
        composeRule.onNodeWithTag("today_more_medications").assertTextContains("Mostrar menos").performClick()
        composeRule.onNodeWithTag("today_more_medications").assertTextContains("Ver 2 pendientes más")

        composeRule.onNodeWithTag("today_scroll").performScrollToNode(
            hasTestTag("today_observation_register_thread-fictional-observation-7"),
        )
        composeRule.onNodeWithTag("today_observation_register_thread-fictional-observation-7").assertIsDisplayed()
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_more_observations"))
        composeRule.onNodeWithTag("today_more_observations").assertTextContains("Mostrar menos").performClick()
        composeRule.onNodeWithTag("today_more_observations").assertTextContains("Ver 5 pendientes más")
        composeRule.runOnIdle {
            assertEquals(0, navigations)
            assertEquals(0, registrations)
        }
    }

    @Test fun withoutMedicationTheFirstRealModuleIsMeasurements() {
        val fixture = todayThreadPreviewFixture(TodayThreadPreviewCase.WITHOUT_MEDICATIONS)
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    state = TodayDemoState.HasPlan, currentDateTime = todayPreviewNow,
                    projection = fixture.projection, notes = fixture.notes, motionEnabledOverride = false,
                )
            }
        }
        composeRule.onNodeWithTag("today_pending_summary").assertDoesNotExist()
        composeRule.onNodeWithTag("today_thread_node_medications").assertDoesNotExist()
        composeRule.onNodeWithTag("today_module_surface_medications").assertDoesNotExist()
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_thread_node_measurements"))
        composeRule.onNodeWithTag("today_thread_node_measurements")
            .assertHasNoClickAction().assertHeightIsEqualTo(16.dp)
        composeRule.onNodeWithText("Mediciones", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(
            hasTestTag("today_measurement_register_CAPILLARY_GLUCOSE:thread-fictional-measurement-0"),
        )
        composeRule.onNodeWithTag("today_measurement_register_CAPILLARY_GLUCOSE:thread-fictional-measurement-0")
            .assertHasClickAction().assertHeightIsAtLeast(48.dp)
    }

    @Test fun onlyMedicationNeverInventsOtherModuleNodesOrNotes() {
        val fixture = todayThreadPreviewFixture(TodayThreadPreviewCase.MEDICATIONS_ONLY)
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    state = TodayDemoState.HasPlan, currentDateTime = todayPreviewNow,
                    projection = fixture.projection, notes = fixture.notes, motionEnabledOverride = false,
                )
            }
        }
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_more_medications"))
        composeRule.onNodeWithTag("today_more_medications").assertTextContains("Ver 1 pendiente más")
        listOf("measurements", "observations", "reminders", "notes").forEach {
            composeRule.onNodeWithTag("today_thread_node_$it").assertDoesNotExist()
        }
        composeRule.onNodeWithTag("today_notes_carousel").assertDoesNotExist()
    }

    @Test fun allScreenStatesOmitTheOldGeneralPendingSummary() {
        val screenState = mutableStateOf(TodayDemoState.HasPlan)
        val projection = mutableStateOf(todayPreviewProjection(multiple = false))
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = screenState.value,
                    projection = projection.value, notes = todayPreviewNotes(), motionEnabledOverride = false,
                )
            }
        }
        listOf(
            TodayDemoState.Loading to todayPreviewProjection(multiple = false),
            TodayDemoState.Error to todayPreviewProjection(multiple = false),
            TodayDemoState.Empty to TodayCareProjection(),
            TodayDemoState.HasPlan to TodayCareProjection(hasScheduledActivities = true),
            TodayDemoState.HasPlan to todayPreviewProjection(multiple = false),
        ).forEach { (state, data) ->
            composeRule.runOnIdle { screenState.value = state; projection.value = data }
            composeRule.onNodeWithTag("today_pending_summary").assertDoesNotExist()
            composeRule.onAllNodes(hasText("Hoy tienes", substring = true)).assertCountEquals(0)
        }
    }

    @Test fun medicationExpansionControlUsesTheWholeModuleWidth() {
        val fixture = todayThreadPreviewFixture(TodayThreadPreviewCase.MEDICATIONS_ONLY)
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = fixture.projection, notes = fixture.notes, motionEnabledOverride = false,
                )
            }
        }
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_more_medications"))
        val action = composeRule.onNodeWithTag("today_more_medications")
            .assertHasClickAction().assertHeightIsAtLeast(48.dp)
        val row = composeRule.onNodeWithTag("today_medication_taken_thread-fictional-dose-2")
        val actionBounds = action.getUnclippedBoundsInRoot()
        val rowBounds = row.getUnclippedBoundsInRoot()
        assertEquals(rowBounds.left.value, actionBounds.left.value, 1f)
        assertEquals(rowBounds.right.value, actionBounds.right.value, 1f)
    }

    @Test fun noScheduleAndResolvedDayDifferAndBothKeepNotesIndependentFromActivityStatus() {
        val projection = mutableStateOf(TodayCareProjection())
        var opened: String? = null
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = projection.value, notes = todayPreviewNotes(), motionEnabledOverride = false,
                    onNoteSelected = { opened = it.id },
                )
            }
        }
        composeRule.onNodeWithText("Hoy no tienes actividades programadas").assertExists()
        composeRule.onNodeWithText("¡Día al corriente!").assertDoesNotExist()
        composeRule.onNodeWithTag("today_pending_summary").assertDoesNotExist()
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_note_preview-note-one"))
        composeRule.onNodeWithTag("today_note_preview-note-one").performClick()
        composeRule.runOnIdle {
            assertEquals("preview-note-one", opened)
            projection.value = TodayCareProjection(hasScheduledActivities = true)
        }
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasText("¡Día al corriente!"))
        composeRule.onNodeWithText("No quedan pendientes por resolver hoy").assertExists()
        composeRule.onNodeWithText("Registros del día").assertDoesNotExist()
    }

    @Test fun medicationTapAndLongPressDispatchDifferentActions() {
        var registrations = 0
        var options = 0
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = todayPreviewProjection(multiple = false), motionEnabledOverride = false,
                    onMedicationTaken = { registrations++ }, onMedicationOptions = { options++ },
                )
            }
        }
        composeRule.onNodeWithTag("today_scroll")
            .performScrollToNode(hasTestTag("today_medication_taken_preview-dose"))
        val row = composeRule.onNodeWithTag("today_medication_taken_preview-dose")
        row.assertHasClickAction().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Registrar toma", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(row.fetchSemanticsNode().config[SemanticsActions.OnClick].label.orEmpty().startsWith("Registrar toma"))
        row.performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(0, registrations)
            assertEquals(1, options)
        }
        row.performClick()
        composeRule.runOnIdle {
            assertEquals(1, registrations)
            assertEquals(1, options)
        }
        composeRule.onNodeWithText("Se tomó").assertDoesNotExist()
        composeRule.onNodeWithText("No se tomó").assertDoesNotExist()
    }

    @Test fun visibleMedicationActionDispatchesOnceAndNeverRegistersOnRecomposition() {
        var registrations = 0
        val recomposition = mutableIntStateOf(0)
        composeRule.setContent {
            recomposition.intValue
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = todayPreviewProjection(multiple = false), motionEnabledOverride = false,
                    onMedicationTaken = { registrations++ },
                )
            }
        }
        composeRule.runOnIdle { assertEquals(0, registrations) }
        composeRule.onNodeWithTag("today_scroll")
            .performScrollToNode(hasTestTag("today_medication_taken_preview-dose"))
        composeRule.onNodeWithText("Registrar toma", useUnmergedTree = true)
            .assertIsDisplayed().performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, registrations)
            recomposition.intValue++
        }
        composeRule.runOnIdle { assertEquals(1, registrations) }
    }

    @Test fun moduleSurfacesAndDecorativeNodesKeepActionsSpecificAndAccessible() {
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = todayPreviewProjection(multiple = false), motionEnabledOverride = false,
                )
            }
        }
        val modules = listOf(
            Triple("medications", "Medicamentos", "today_medication_taken_preview-dose"),
            Triple("measurements", "Mediciones", "today_measurement_register_CAPILLARY_GLUCOSE:preview-reading"),
            Triple("observations", "Observaciones", "today_observation_register_preview-observation"),
            Triple("reminders", "Recordatorios del cuidado", "today_reminder_done_preview-reminder"),
        )
        modules.forEach { (module, title, action) ->
            composeRule.onNodeWithTag("today_scroll")
                .performScrollToNode(hasTestTag("today_thread_node_$module"))
            composeRule.onNodeWithTag("today_thread_node_$module")
                .assertHasNoClickAction().assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
            composeRule.onNodeWithText(title, useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag("today_scroll")
                .performScrollToNode(hasTestTag("today_module_surface_$module"))
            composeRule.onNodeWithTag("today_module_surface_$module").assertExists()
            composeRule.onNodeWithTag(action).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        }
        composeRule.onNodeWithText("Medicamentos pendientes").assertDoesNotExist()
        composeRule.onNodeWithText("Mediciones pendientes").assertDoesNotExist()
        composeRule.onNodeWithText("Observaciones pendientes").assertDoesNotExist()
    }

    @Test fun unavailableActionsDoNotDispatchMedicationCallbacks() {
        var registrations = 0
        var options = 0
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = todayPreviewProjection(multiple = false), motionEnabledOverride = false,
                    actionsEnabled = false, medicationSavingId = "preview-dose",
                    onMedicationTaken = { registrations++ }, onMedicationOptions = { options++ },
                )
            }
        }
        composeRule.onNodeWithTag("today_scroll")
            .performScrollToNode(hasTestTag("today_medication_taken_preview-dose"))
        val row = composeRule.onNodeWithTag("today_medication_taken_preview-dose")
        row.assertIsNotEnabled()
        row.performTouchInput { click(); longClick() }
        composeRule.runOnIdle {
            assertEquals(0, registrations)
            assertEquals(0, options)
        }
    }

    @Test fun pendingAndSavingMedicationNeverShowTheConfirmedVisualState() {
        val saving = mutableStateOf(false)
        val confirmed = mutableStateOf(false)
        var registrations = 0
        composeRule.setContent {
            BitavidaTheme {
                TodayMedicationSurface(
                    id = "fictional-visual-dose",
                    name = "Medicamento ficticio",
                    amount = "1 tableta",
                    schedule = "09:00",
                    enabled = true,
                    motion = false,
                    onTaken = { registrations++ },
                    onOptions = {},
                    saving = saving.value,
                    confirmed = confirmed.value,
                )
            }
        }
        composeRule.onNodeWithText("Registrar toma", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Se tomó", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("✓", useUnmergedTree = true).assertDoesNotExist()

        composeRule.runOnIdle { saving.value = true }

        composeRule.onNodeWithText("Guardando…", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Se tomó", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("✓", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("today_medication_taken_fictional-visual-dose").assertIsNotEnabled()
        composeRule.runOnIdle { saving.value = false; confirmed.value = true }

        composeRule.onNodeWithText("Se tomó", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("today_medication_taken_fictional-visual-dose")
            .assertIsNotEnabled().performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(0, registrations) }
    }

    @Test fun medicationMicroconfirmationStartsOnlyWhenTheMatchingPersistedReceiptArrives() {
        composeRule.mainClock.autoAdvance = false
        val projection = mutableStateOf(todayPreviewProjection(multiple = false))
        val savingId = mutableStateOf<String?>(null)
        val confirmedId = mutableStateOf<String?>(null)
        var registrations = 0
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    state = TodayDemoState.HasPlan, currentDateTime = todayPreviewNow,
                    projection = projection.value, motionEnabledOverride = true,
                    medicationSavingId = savingId.value, medicationConfirmedId = confirmedId.value,
                    onMedicationTaken = { registrations++; savingId.value = it.id },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("today_scroll")
            .performScrollToNode(hasTestTag("today_medication_taken_preview-dose"))
        composeRule.onNodeWithTag("today_medication_taken_preview-dose").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Se tomó", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Guardando…", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle { assertEquals(1, registrations) }

        composeRule.runOnIdle {
            projection.value = projection.value.copy(medications = emptyList())
            savingId.value = null
            confirmedId.value = "preview-dose"
        }
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.onNodeWithText("Se tomó", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("today_medication_taken_preview-dose").assertIsNotEnabled()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("today_medication_taken_preview-dose").assertDoesNotExist()
        composeRule.onNodeWithTag("today_medications").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, registrations) }
    }

    @Test fun undoDuringContractionRestoresThePendingRowAndCancelsItsVisualExit() {
        composeRule.mainClock.autoAdvance = false
        val original = todayPreviewProjection(multiple = false)
        val projection = mutableStateOf(original)
        val confirmedId = mutableStateOf<String?>(null)
        val feedback = mutableStateOf<String?>(null)
        var registrations = 0
        var undos = 0
        val confirmationDuration = BitavidaFluidMotion.ThreadReveal + BitavidaFluidMotion.Selection
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = projection.value, motionEnabledOverride = true,
                    medicationConfirmedId = confirmedId.value, medicationFeedback = feedback.value,
                    onMedicationTaken = { registrations++ },
                    onUndoMedication = {
                        undos++
                        projection.value = original
                        confirmedId.value = null
                        feedback.value = null
                    },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("today_scroll")
            .performScrollToNode(hasTestTag("today_medication_taken_preview-dose"))
        composeRule.onNodeWithTag("today_medication_taken_preview-dose").performClick()
        composeRule.runOnIdle {
            projection.value = original.copy(medications = emptyList())
            confirmedId.value = "preview-dose"
            feedback.value = "Toma registrada"
        }
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.mainClock.advanceTimeBy((confirmationDuration * 0.8f).toLong())
        composeRule.onNodeWithTag("today_undo_medication").assertIsDisplayed().performClick()
        composeRule.mainClock.advanceTimeBy(32)

        val row = composeRule.onNodeWithTag("today_medication_taken_preview-dose")
        row.assertIsEnabled().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Registrar toma", useUnmergedTree = true).assertExists()
        composeRule.mainClock.advanceTimeBy(confirmationDuration * 2L)
        row.assertIsEnabled().assertHeightIsAtLeast(48.dp)
        composeRule.runOnIdle {
            assertEquals(1, registrations)
            assertEquals(1, undos)
        }
    }

    @Test fun aFailedMedicationWriteKeepsThePendingActionAvailableWithoutSuccessFeedback() {
        val saving = mutableStateOf<String?>(null)
        val failed = mutableStateOf(false)
        var registrations = 0
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = todayPreviewProjection(multiple = false), motionEnabledOverride = false,
                    medicationSavingId = saving.value, medicationFeedbackIsError = failed.value,
                    medicationFeedback = if (failed.value) "No se pudo guardar la toma" else null,
                    onMedicationTaken = { registrations++; saving.value = it.id },
                )
            }
        }
        composeRule.onNodeWithTag("today_scroll")
            .performScrollToNode(hasTestTag("today_medication_taken_preview-dose"))
        val row = composeRule.onNodeWithTag("today_medication_taken_preview-dose")
        row.performClick()
        row.assertIsNotEnabled()
        composeRule.runOnIdle { saving.value = null; failed.value = true }
        row.assertIsEnabled()
        composeRule.onNodeWithText("Registrar toma", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Se tomó", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("No se pudo guardar la toma").assertIsDisplayed()
        row.performClick()
        composeRule.runOnIdle { assertEquals(2, registrations) }
    }

    @Test fun draggingMedicationRowScrollsWithoutRegisteringOrOpeningOptions() {
        var registrations = 0
        var options = 0
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = todayPreviewProjection(), motionEnabledOverride = false,
                    onMedicationTaken = { registrations++ }, onMedicationOptions = { options++ },
                )
            }
        }
        composeRule.onNodeWithTag("today_scroll")
            .performScrollToNode(hasTestTag("today_medication_taken_preview-dose1"))
        composeRule.onNodeWithTag("today_medication_taken_preview-dose1").performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y - 180f), durationMillis = 300)
        }
        composeRule.runOnIdle {
            assertEquals(0, registrations)
            assertEquals(0, options)
        }
    }

    @Test fun medicationOptionsAreAvailableAsAnExplicitAccessibilityAction() {
        var opened: String? = null
        var registrations = 0
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = todayPreviewProjection(multiple = false), motionEnabledOverride = false,
                    onMedicationTaken = { registrations++ }, onMedicationOptions = { opened = it.id },
                )
            }
        }
        composeRule.onNodeWithTag("today_scroll")
            .performScrollToNode(hasTestTag("today_medication_taken_preview-dose"))
        val actions = composeRule.onNodeWithTag("today_medication_taken_preview-dose")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        composeRule.runOnIdle {
            assertEquals(1, actions.size)
            assertEquals(true, actions.single().label.startsWith("Opciones de Medicamento ficticio"))
            assertEquals(true, actions.single().action())
            assertEquals("preview-dose", opened)
            assertEquals(0, registrations)
        }
    }

    @Test fun confirmedFeedbackAndUndoStayVisibleAfterResolvingAnExpandedMedication() {
        val projection = mutableStateOf(todayPreviewProjection())
        val feedback = mutableStateOf<String?>(null)
        var undone = false
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = projection.value, motionEnabledOverride = false,
                    medicationFeedback = feedback.value, onUndoMedication = { undone = true },
                    onMedicationTaken = { selected ->
                        projection.value = projection.value.copy(
                            medications = projection.value.medications.filterNot { it.id == selected.id },
                        )
                        feedback.value = "Toma registrada"
                    },
                )
            }
        }
        composeRule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag("today_more_medications"))
        composeRule.onNodeWithTag("today_more_medications").performClick()
        composeRule.onNodeWithTag("today_scroll")
            .performScrollToNode(hasTestTag("today_medication_taken_preview-dose3"))
        composeRule.onNodeWithTag("today_medication_taken_preview-dose3").performClick()
        composeRule.onNodeWithTag("today_medication_feedback").assertIsDisplayed()
        composeRule.onNodeWithText("Deshacer").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(true, undone) }
    }

    @Test fun animatedMarkDoesNotRestartAfterRecomposition() {
        composeRule.mainClock.autoAdvance = false
        val trigger = mutableIntStateOf(0)
        var completions = 0
        composeRule.setContent {
            trigger.intValue
            BitavidaTheme {
                AnimatedBitavidaMark(Modifier.size(120.dp), motionEnabledOverride = true,
                    onAnimationFinished = { completions++ })
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(1, completions) }
        composeRule.runOnUiThread { trigger.intValue++ }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(1, completions) }
    }
}
