package com.bitavida.app.ui.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.education.EducationTopic
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BitavidaAiOfflineContractTest {
    @Test fun applicationDoesNotRequestNetworkAndAllLibraryEntriesExistWithoutLoadingAModel() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        assertFalse("Offline app must not request INTERNET", manifest.contains("android.permission.INTERNET"))
        val repository = BundledEducationRepository { error("Library browsing must not load or download a model") }
        EducationTopic.entries.forEach { topic ->
            assertEquals(topic, repository.entry(topic).topic)
            assertTrue(repository.entry(topic).explanation.isNotBlank())
        }
        assertTrue(projectFile("app/src/main/res/raw/bitavida_education_model.tsv").length() > 0)
    }

    private fun projectFile(path: String): File =
        File(path).takeIf { it.isFile } ?: File("..", path)
}
