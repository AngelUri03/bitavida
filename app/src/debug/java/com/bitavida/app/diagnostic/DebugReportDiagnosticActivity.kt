package com.bitavida.app.diagnostic

import android.app.KeyguardManager
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room3.withReadTransaction
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.data.local.secure.SecureSpikeDatabase
import com.bitavida.app.ui.settings.DevelopmentDiagnosticGrant
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Development-only, non-exported entry. The unlocked Settings screen is its only UI entry.
 * Exports no keys or BLOBs. The chosen JSON is sensitive and remains under the user's control.
 */
class DebugReportDiagnosticActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 || !diagnosticAuthorized()) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            BitavidaTheme {
                var busy by remember { mutableStateOf(false) }
                var completed by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf<String?>(null) }
                val chooseDestination = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null && !busy) {
                        busy = true
                        message = null
                        lifecycleScope.launch {
                            var complete = false
                            try {
                                withContext(Dispatchers.IO) {
                                    check(diagnosticAuthorized())
                                    val database = SecureDatabaseProvider.open(applicationContext)
                                    val snapshot = buildDiagnosticSnapshot(database)
                                    check(diagnosticAuthorized())
                                    val stream = contentResolver.openOutputStream(uri, "wt")
                                        ?: error("Destino no disponible")
                                    val bytes = snapshot.toByteArray(Charsets.UTF_8)
                                    try {
                                        stream.use { destination ->
                                            var offset = 0
                                            while (offset < bytes.size) {
                                                check(diagnosticAuthorized())
                                                val count = minOf(32 * 1024, bytes.size - offset)
                                                destination.write(bytes, offset, count)
                                                offset += count
                                            }
                                            check(diagnosticAuthorized())
                                        }
                                    } finally {
                                        bytes.fill(0)
                                    }
                                }
                                complete = true
                                completed = true
                                DevelopmentDiagnosticGrant.revoke()
                                message = "Instantánea guardada en la ubicación elegida."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                message = "No fue posible guardar la instantánea."
                            } finally {
                                if (!complete) {
                                    val deleted = withContext(Dispatchers.IO + NonCancellable) { deleteIncomplete(uri) }
                                    if (!deleted) message = "No se pudo eliminar el archivo incompleto; elimínalo desde la ubicación elegida."
                                }
                                busy = false
                            }
                        }
                    }
                }
                Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
                    Column(
                        Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Diagnóstico de desarrollo", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "La instantánea contiene información personal y de salud, incluidos registros e identificadores. " +
                                "El archivo guardado no estará cifrado por Bitavida. Guárdalo solo en una ubicación de confianza y compártelo únicamente si lo decides.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Se capturan las relaciones en una transacción de lectura. No se incluyen fotografías, " +
                                "otros datos binarios, claves ni rutas privadas. Esta herramienta solo existe en desarrollo.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                if (diagnosticAuthorized()) chooseDestination.launch("bitavida-diagnostico.json")
                                else {
                                    message = "La autorización caducó. Vuelve a Configuración para desbloquear y volver a intentarlo."
                                }
                            },
                            enabled = !busy && !completed,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(if (busy) "Preparando…" else "Elegir dónde guardar") }
                        OutlinedButton(
                            onClick = { finish() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Volver") }
                        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) DevelopmentDiagnosticGrant.revoke()
        super.onDestroy()
    }

    private fun diagnosticAuthorized(): Boolean =
        DevelopmentDiagnosticGrant.isValid() &&
            !getSystemService(KeyguardManager::class.java).isDeviceLocked

    private fun deleteIncomplete(uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(contentResolver, uri)
    } catch (_: Exception) {
        false
    }
}

/** Explicit list: internal or future tables must not silently enter a diagnostic export. */
internal val diagnosticTables = listOf(
    "caregivers", "persons_under_care", "care_relationships", "care_profile_name_revisions",
    "medical_indications", "medical_indication_revisions",
    "medication_plans", "medication_plan_revisions", "scheduled_doses",
    "medication_configurations", "medication_administrations",
    "medication_intake_records", "medication_intake_revisions",
    "glucose_measurements", "glucose_revisions", "glucose_audit",
    "glucose_follow_ups", "glucose_follow_up_revisions", "glucose_follow_up_slots",
    "glucose_occurrence_decisions", "glucose_occurrence_decision_revisions",
    "blood_pressure_measurements", "blood_pressure_revisions", "blood_pressure_audit",
    "blood_pressure_follow_ups", "blood_pressure_follow_up_revisions", "blood_pressure_follow_up_slots",
    "blood_pressure_occurrence_decisions", "blood_pressure_occurrence_decision_revisions",
    "weight_measurements", "weight_revisions", "weight_audit", "weight_targets",
    "weight_follow_ups", "weight_follow_up_revisions", "weight_follow_up_slots",
    "weight_occurrence_decisions", "weight_occurrence_decision_revisions",
    "observation_follow_ups", "observation_follow_up_revisions", "observation_records",
    "observation_record_revisions", "observation_photos", "observation_record_photos",
    "care_reminders", "care_reminder_revisions", "notes", "note_revisions", "note_images",
    "care_events", "care_event_revisions", "care_event_photos",
)

private val omittedColumns = setOf("jpeg", "path", "filePath", "localPath", "uri", "contentUri")

internal fun diagnosticColumnAllowed(name: String, sqliteType: Int): Boolean =
    sqliteType != SQLITE_DATA_BLOB &&
        omittedColumns.none { it.equals(name, ignoreCase = true) } &&
        !name.endsWith("path", ignoreCase = true) && !name.endsWith("uri", ignoreCase = true)

internal suspend fun buildDiagnosticSnapshot(database: SecureSpikeDatabase): String =
    database.withReadTransaction {
        val capturedAt = Instant.now().toString()
        val schemaVersion = usePrepared("PRAGMA user_version") { statement ->
            check(statement.step())
            statement.getLong(0)
        }
        val tables = JSONObject()
        for (table in diagnosticTables) {
            val rows = JSONArray()
            usePrepared("SELECT * FROM `$table`") { statement ->
                while (statement.step()) {
                    val row = JSONObject()
                    for (column in 0 until statement.getColumnCount()) {
                        val name = statement.getColumnName(column)
                        val columnType = statement.getColumnType(column)
                        if (!diagnosticColumnAllowed(name, columnType)) continue
                        val value: Any = when (columnType) {
                            SQLITE_DATA_NULL -> JSONObject.NULL
                            SQLITE_DATA_INTEGER -> statement.getLong(column)
                            SQLITE_DATA_FLOAT -> statement.getDouble(column)
                            SQLITE_DATA_BLOB -> continue
                            else -> statement.getText(column)
                        }
                        row.put(name, value)
                    }
                    rows.put(row)
                }
            }
            tables.put(table, rows)
        }
        JSONObject()
            .put("formatVersion", 1)
            .put("schemaVersion", schemaVersion)
            .put("capturedAtUtc", capturedAt)
            .put("zoneId", ZoneId.systemDefault().id)
            .put("excluded", JSONArray(listOf("BLOB", "claves", "rutas privadas")))
            .put("tables", tables)
            .toString()
    }
