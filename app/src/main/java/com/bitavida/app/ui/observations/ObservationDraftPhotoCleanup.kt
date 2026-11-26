package com.bitavida.app.ui.observations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bitavida.app.data.observations.ObservationPhotoStore
import com.bitavida.app.domain.model.ObservationRecord
import kotlinx.coroutines.CancellationException

/**
 * Owned by the stable protected-content host, so closing a capture cannot dispose its cleanup.
 * Rotation and navigation alone never discard the current draft's photos.
 */
@Composable
internal fun ObservationDraftPhotoCleanup(viewModel: ObservationsViewModel) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ObservationPhotoStore(context) }
    LaunchedEffect(viewModel, store) {
        var previous = viewModel.state.value.draft
        viewModel.state.collect { state ->
            val discarded = closedObservationDraftPhotoIds(previous, state.draft)
            previous = state.draft
            if (discarded.isNotEmpty()) {
                // Finish after a form is removed from composition. The DAO only deletes unlinked images;
                // photos already committed to the record or its audit history remain untouched.
                viewModel.launchPhotoWork {
                    discarded.forEach { id ->
                        try {
                            store.discard(viewModel.actor, id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Preserve storage on failure; never log private attachment identifiers.
                        }
                    }
                }
            }
        }
    }
}

internal fun closedObservationDraftPhotoIds(
    previous: ObservationRecord?,
    current: ObservationRecord?,
): List<String> = if (previous != null && previous.id != current?.id) {
    previous.localPhotoIds.distinct()
} else emptyList()
