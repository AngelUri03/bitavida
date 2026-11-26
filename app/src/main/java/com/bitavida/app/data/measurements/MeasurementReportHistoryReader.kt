package com.bitavida.app.data.measurements

import android.content.Context
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.GlucoseAuthorizationException
import com.bitavida.app.domain.repository.GLUCOSE_SENSITIVE_CONSENT_VERSION
import com.bitavida.app.domain.usecase.MeasurementReportHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only histories, scoped and authorized in each DAO transaction. */
class MeasurementReportHistoryReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun load(actor: GlucoseActor): MeasurementReportHistory = withContext(Dispatchers.IO) {
        if (!actor.authorizationCurrent) throw GlucoseAuthorizationException()
        val db = SecureDatabaseProvider.open(appContext)
        val glucose = db.glucoseDao().loadReportHistory(actor.profileId, actor.personUnderCareId,
            actor.caregiverId, GLUCOSE_SENSITIVE_CONSENT_VERSION)
        val pressure = db.bloodPressureDao().loadReportHistory(actor.profileId, actor.personUnderCareId,
            actor.caregiverId, GLUCOSE_SENSITIVE_CONSENT_VERSION)
        val weight = db.weightDao().loadReportHistory(actor.profileId, actor.personUnderCareId,
            actor.caregiverId, GLUCOSE_SENSITIVE_CONSENT_VERSION)
        val glucoseByRecord = glucose.second.groupBy { it.recordId }
        val pressureByRecord = pressure.second.groupBy { it.recordId }
        val weightByRecord = weight.second.groupBy { it.recordId }
        MeasurementReportHistory(
            glucose = glucose.first.map { record ->
                record.toDomain() to glucoseByRecord[record.id].orEmpty().map { it.toDomain() }
            },
            pressure = pressure.first.map { record ->
                record.toDomain() to pressureByRecord[record.id].orEmpty().map { it.toDomain() }
            },
            weight = weight.first.map { record ->
                record.toDomain() to weightByRecord[record.id].orEmpty().map { it.toDomain() }
            },
        )
    }
}
