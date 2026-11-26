package com.bitavida.app.ui.settings

import android.os.SystemClock
import com.bitavida.app.domain.access.AppAccessPolicy

/** A process-local capability, never persisted or sent in an Intent. */
internal object DevelopmentDiagnosticGrant {
    private var issuedAt: Long? = null

    @Synchronized fun issue() { issuedAt = SystemClock.elapsedRealtime() }

    @Synchronized fun isValid(): Boolean =
        diagnosticGrantValid(issuedAt, SystemClock.elapsedRealtime())

    @Synchronized fun revoke() { issuedAt = null }
}

internal fun diagnosticGrantValid(issuedAt: Long?, now: Long): Boolean =
    issuedAt != null && now >= issuedAt &&
        now - issuedAt < AppAccessPolicy.DEFAULT_BACKGROUND_TIMEOUT_MILLIS
