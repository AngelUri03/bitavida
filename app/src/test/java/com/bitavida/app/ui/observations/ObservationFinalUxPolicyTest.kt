package com.bitavida.app.ui.observations

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationFinalUxPolicyTest {
    @Test
    fun productionSourcesDoNotUseToastOrSuccessSnackbarInfrastructure() {
        val sourceRoot = projectFile("app/src/main/java")
        val source = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(source.contains("android.widget.Toast"))
        assertFalse(source.contains("Toast.makeText"))
        assertFalse(source.contains("showSnackbar("))
        assertFalse(source.contains("SnackbarHostState"))
    }

    @Test
    fun observationPhotoEditingUsesTheApprovedSheetAndAccessibleCloseIcon() {
        val source = projectFile(
            "app/src/main/java/com/bitavida/app/ui/observations/ObservationPhotos.kt",
        ).readText()

        assertTrue(source.contains("MedicationLifecycleSheetFrame("))
        assertTrue(source.contains("title = \"Editar fotografía\""))
        assertTrue(source.contains("label = \"Quitar fotografía\""))
        assertFalse(source.contains("DropdownMenu("))
        assertTrue(source.contains("R.drawable.ic_v2_close"))
        assertTrue(source.contains(".size(48.dp)"))
        assertFalse(source.contains("\"Cerrar\",\n"))
    }

    @Test
    fun newObservationCaptureOffersOnlyKnownOrUnknownTime() {
        val source = projectFile(
            "app/src/main/java/com/bitavida/app/ui/observations/ObservationRegistrationSheet.kt",
        ).readText()

        assertTrue(source.contains("\"No sé la hora\""))
        assertTrue(source.contains("selectTime(ObservationTimePrecision.EXACT)"))
        assertFalse(source.contains("\"Hora aproximada\""))
    }

    @Test
    fun observationReviewMatchesTheConnectedMedicationReviewPattern() {
        val source = projectFile(
            "app/src/main/java/com/bitavida/app/ui/observations/ObservationScheduleContent.kt",
        ).readText()

        assertTrue(source.contains("BitavidaConnectedSurface("))
        assertTrue(source.contains("MaterialTheme.bitavidaColors.mint"))
        assertTrue(source.contains("GuidedRegistrationReviewRow("))
        assertTrue(source.contains("BitavidaThreadAction("))
        assertFalse(source.contains("continúa hasta que lo suspendas"))
    }

    @Test
    fun planReselectionResetsTheRootAndObservationCatalog() {
        val source = projectFile(
            "app/src/main/java/com/bitavida/app/ui/medical/MedicalPlanScreen.kt",
        ).readText()

        val navigation = source.substringAfter("BitavidaThreadNavigation(")
            .substringBefore("                        },\n                    )")
        val selection = navigation.substringAfter("onSelected = { index ->")
        assertTrue(navigation.contains("0, 1 -> true"))
        assertTrue(selection.contains("if (index == 0 || index == 1 ||"))
        val resetCatalog = selection.indexOf("ObservationsAction.EnterCatalog")
        val resetOverview = selection.indexOf("onAction(MedicalPlanAction.OpenOverview)")
        val dispatch = selection.indexOf("when (index)")
        assertTrue(resetCatalog >= 0)
        assertTrue(resetOverview > resetCatalog)
        assertTrue(dispatch > resetOverview)
        assertTrue(selection.contains("0 -> onTodaySelected()"))
    }

    @Test
    fun deletedObservationFollowUpIsNotProjectedAsOperationalDetail() {
        val source = projectFile(
            "app/src/main/java/com/bitavida/app/ui/observations/ObservationDetailContent.kt",
        ).readText()

        assertTrue(source.contains("val activeFollowUp = storedFollowUp?.takeIf"))
        assertTrue(source.contains("\"Eliminar seguimiento\""))
        assertFalse(source.contains("\"Reactivar seguimiento\""))
        assertFalse(source.contains("\"Suspender seguimiento\""))
    }

    private fun projectFile(path: String): File =
        File(path).takeIf { it.exists() } ?: File("..", path)
}
