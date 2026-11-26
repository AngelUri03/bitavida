package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.WeightFakeRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightViewModelTargetTest {
    private val now = LocalDateTime.of(2026, 11, 18, 14, 4)
    private val actor = GlucoseActor(
        "profile-target",
        "caregiver-target",
        "person-target",
        authorizationCurrent = true,
    )

    @Test
    fun targetIsCreatedUpdatedRemovedAndReloadedWithoutMeasurements() =
        runBlocking {
            val repository = WeightFakeRepository().apply {
                consent = false
            }
            val viewModel = WeightViewModel(
                actor = actor,
                repository = repository,
                coroutineScopeOverride = this,
                now = { now },
            )
            yield()

            viewModel.onAction(WeightAction.OpenTarget)
            viewModel.onAction(WeightAction.TargetKilograms("68,5"))
            viewModel.onAction(WeightAction.SaveTarget)
            yield()

            assertTrue(viewModel.state.value.consentVisible)
            assertNull(viewModel.state.value.target)
            viewModel.onAction(WeightAction.AcceptConsent(true))
            viewModel.onAction(WeightAction.ConfirmConsent)
            yield()

            assertEquals(
                BigDecimal("68.5"),
                viewModel.state.value.target?.kilograms,
            )
            assertFalse(viewModel.state.value.targetSheetVisible)
            assertTrue(viewModel.state.value.records.isEmpty())

            viewModel.onAction(WeightAction.OpenTarget)
            viewModel.onAction(WeightAction.TargetKilograms("70.25"))
            viewModel.onAction(WeightAction.SaveTarget)
            yield()
            assertEquals(
                BigDecimal("70.25"),
                viewModel.state.value.target?.kilograms,
            )

            val reloaded = WeightViewModel(
                actor = actor,
                repository = repository,
                coroutineScopeOverride = this,
                now = { now },
            )
            yield()
            assertEquals(
                BigDecimal("70.25"),
                reloaded.state.value.target?.kilograms,
            )

            reloaded.onAction(WeightAction.OpenTarget)
            reloaded.onAction(WeightAction.RemoveTarget)
            yield()
            assertNull(reloaded.state.value.target)
            assertNull(repository.target)
            assertTrue(reloaded.state.value.records.isEmpty())
        }
}
