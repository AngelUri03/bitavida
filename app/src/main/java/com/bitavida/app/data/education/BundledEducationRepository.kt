package com.bitavida.app.data.education

import com.bitavida.app.domain.education.EducationRepository
import com.bitavida.app.domain.education.EducationTopic
import com.bitavida.app.domain.education.MultinomialNaiveBayes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BundledEducationRepository(private val loadModelText: () -> String) : EducationRepository {
    private var loadReminderModelText: (() -> String)? = null

    constructor(loadModelText: () -> String, loadReminderModelText: () -> String) : this(loadModelText) {
        this.loadReminderModelText = loadReminderModelText
    }
    private val classifier by lazy {
        MultinomialNaiveBayes(EducationModelCodec.decode(loadModelText()))
    }

    override suspend fun classify(question: String) = withContext(Dispatchers.Default) {
        classifier.classify(question)
    }

    private val reminderClassifier by lazy {
        loadReminderModelText?.let { MultinomialNaiveBayes(EducationModelCodec.decode(it())) }
    }

    override suspend fun classify(question: String, context: com.bitavida.app.domain.education.EducationGroup?) =
        if (context == com.bitavida.app.domain.education.EducationGroup.REMINDERS) {
            withContext(Dispatchers.Default) {
                reminderClassifier?.classify(question)
                    ?: com.bitavida.app.domain.education.IntentPrediction(null, 0.0, 0.0)
            }
        } else classify(question)

    override fun entry(topic: EducationTopic) =
        LocalEducationLibrary.entries[topic]
            ?: ObservationEducationLibrary.entries[topic]
            ?: ReminderEducationLibrary.entries.getValue(topic)

    private val drugCatalog by lazy { DrugCatalogCore.entries + DrugCatalogExtended.entries }

    override fun drugs() = drugCatalog

    private val procedureCatalog by lazy {
        ProcedureGuideCatalog.entries + ProcedureGuideAdditionalCatalog.entries + ProcedureGuideSurgeryCatalog.entries
    }

    override fun procedures() = procedureCatalog
}
