package com.bitavida.app.domain.usecase

/** An immutable, module-qualified persisted identity; labels and timestamps are not identities. */
data class ReportSourceIdentity(val module: String, val id: String) {
    init {
        require(module.isNotBlank())
        require(id.isNotBlank())
    }
}

enum class ReportSourceOrigin { REAL, SIMULATED }

/** A simulated source always identifies the scenario that created it. */
data class ReportSourceAttribution(
    val origin: ReportSourceOrigin,
    val scenarioId: String? = null,
) {
    init {
        require(
            if (origin == ReportSourceOrigin.SIMULATED) !scenarioId.isNullOrBlank()
            else scenarioId == null
        )
    }
}

/**
 * Evidence supplied alongside an immutable report snapshot. The producer must enumerate every
 * source used by the snapshot, including projected schedules and supplementary history.
 * An absent attribution is unknown, never implicitly real. This contract does not itself
 * persist attribution or guarantee that a producer has enumerated every source.
 */
class ReportSourceCoverage(
    requiredSources: Set<ReportSourceIdentity>,
    attribution: Map<ReportSourceIdentity, ReportSourceAttribution>,
) {
    private val required = requiredSources.toSet()
    private val evidence = attribution.filterKeys { it in required }.toMap()

    val missingSources: Set<ReportSourceIdentity> get() = required - evidence.keys
    val isComplete: Boolean get() = required.isNotEmpty() && missingSources.isEmpty()
    val hasSimulated: Boolean get() = evidence.values.any { it.origin == ReportSourceOrigin.SIMULATED }
    val hasReal: Boolean get() = evidence.values.any { it.origin == ReportSourceOrigin.REAL }
}
