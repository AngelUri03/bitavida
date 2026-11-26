# Best practices and security alignment update: avisos de Recordatorios

> Histórico: la petición Cierre de Plan de cuidado y nuevo módulo de Notas retira estos avisos. ARCHITECTURE.md y SECURITY.md documentan el comportamiento vigente. Los receptores anteriores solo cancelan recursos propios, sin leer ni modificar recordatorios.

## Área, impacto y prioridad

Prioridad alta: limitar la finalización desde notificaciones a una acción nativa explícita y a una capacidad opaca vigente, preservando la autorización del repositorio, la privacidad de la pantalla bloqueada y la idempotencia. Mostrar o eliminar un aviso nunca confirma la realización de un pendiente.

Se aplicaron las skills [android-permissions-security](<C:/Users/angel/.android-studio/skills/android-permissions-security/SKILL.md>) y [android-intent-security](<C:/Users/angel/.android-studio/skills/android-intent-security/SKILL.md>). Sus ejemplos de logs no se incorporan: AGENTS.md y SECURITY.md prohíben registrar contenido, identificadores o excepciones de almacenamiento.

## Alcance y decisión explícita de producto

- Se reutiliza CareReminderReceiver, ya privado/no exportado. No hay receptores exportados nuevos, permisos, librerías, migraciones, servicios permanentes, red ni alarmas a pantalla completa.
- El adaptador de alarmas de Mediciones no se modifica: conserva su programación inexacta para pendientes de cuidado.
- Por petición explícita, la versión privada del aviso muestra únicamente el título del pendiente, sin paciente ni detalles. Los Intents y preferencias conservan solo capacidades SHA-256 y metadatos técnicos, nunca títulos ni IDs crudos.
- La acción nativa es una operación limitada independiente de la sesión visual de Bitavida: exige dispositivo desbloqueado y autorización cuidador/paciente vigente. No desactiva la protección de la aplicación ni abre datos. Tocar el cuerpo sí pasa por el gate de acceso de MainActivity.

## Implementación

Los PendingIntent son explícitos e inmutables. Las URI distinguen alarma, apertura y finalización; token, acción y URI deben coincidir. El parser rechaza tokens malformados, URI con query/fragment/ruta adicional, flags de concesión, selector y ClipData; no reenvía Intents ni ejecuta destinos anidados.

La apertura usa una capacidad opaca, resuelta contra recibo de entrega y revisión vigente en la base cifrada. La integración de MainActivity aplica el mismo parser en arranque y onNewIntent, conserva la protección visual y consume la navegación una sola vez. La raíz agrega abajo su diff exacto.

Completar reutiliza ManageCareReminders y Close con clave de operación estable por aviso. La revisión esperada impide que una acción antigua cierre una edición o posposición posterior. La fecha de cierre procede del reloj real, respetando la regla cronológica existente. La señal de cambio se emite después del commit y antes de cancelar notificaciones/alarmas; un error del SO posterior no oculta ni revierte un cierre confirmado.

El aviso nativo utiliza ongoing, autoCancel=false, onlyAlertOnce, VISIBILITY_PRIVATE y publicVersion genérica. La versión pública no contiene el título. La visibilidad final y las aplicaciones a las que el usuario conceda acceso a notificaciones siguen bajo control de Android y del usuario. La eliminación de un aviso conserva su recibo opaco y el registro pendiente; reconcile no vuelve a publicarlo. Posponer o editar invalida el token anterior y cancela el aviso antiguo antes de programar el vigente.

El receptor procesa IO con goAsync, tiempo acotado y finish en finally. Si el dispositivo está bloqueado no escribe. API 31+ solicita autenticación nativa de la acción; en API 26–30, la acción requiere que la persona desbloquee primero el dispositivo y vuelva a pulsar Completar. Cancelación, error de autenticación o fallo de almacenamiento dejan el pendiente sin finalizar.

## Compatibilidad verificada con documentación oficial

Android 14 permite descartar notificaciones ongoing en determinadas condiciones; ongoing no se presenta como una garantía de retención. [Cambios de Android 14](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications).

La acción utiliza un BroadcastReceiver y PendingIntent inmutable, y separa la acción del cuerpo de la notificación. La privacidad usa la versión pública nativa. [Crear notificaciones y acciones](https://developer.android.com/develop/ui/compose/notifications/create-notification#add-action-buttons).

setAuthenticationRequired solicita desbloqueo antes de enviar la acción desde API 31. [NotificationCompat.Action.Builder](https://developer.android.com/reference/androidx/core/app/NotificationCompat.Action.Builder#setAuthenticationRequired(boolean)).

POST_NOTIFICATIONS se comprueba al publicar desde API 33; denegarlo no impide guardar el registro. No se solicita acceso a alarmas exactas. [Permiso de notificación](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## Archivos de esta implementación

- app/src/main/java/com/bitavida/app/platform/reminder/CareReminderScheduler.kt
- app/src/main/java/com/bitavida/app/domain/usecase/CareReminderNoticeActions.kt
- app/src/test/java/com/bitavida/app/domain/usecase/CareReminderNoticeActionsTest.kt
- app/src/androidTest/java/com/bitavida/app/platform/reminder/CareReminderNotificationContractTest.kt
- MainActivity.kt se integra por la raíz, sin cambios de AndroidManifest.xml.

## Pruebas y límites de verificación

Se añadieron once pruebas JVM para cierre/fecha real, idempotencia, capacidad inválida/no entregada, revisión antigua, estado cerrado, aviso desactivado, ámbito de paciente, autorización, conflicto concurrente, recibos tras descarte/reinicio, cambio de reloj, reprogramación y permiso denegado. Se añadieron cuatro pruebas nativas preparadas para presentación privada/pública, flags, acciones separadas, identidad de PendingIntent y parsing de entrada fría/caliente. Estas últimas construyen notificaciones pero no las publican, no programan alarmas y no abren la base.

Validación coordinada final: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` ejecutados juntos con BUILD SUCCESSFUL. Las 468 pruebas JVM pasan (13 nuevas), sin fallos ni omitidas. Lint conserva tres avisos previos: dos versiones de dependencias y el icono ic_v2_more sin uso. Las nuevas pruebas nativas compilan y no se ejecutaron. Las previews de biblioteca, ficha educativa, detalle y Plan se inspeccionaron con ejemplos claros/oscuros y biblioteca al 200 %. Este informe no atribuye a pruebas JVM la entrega real, el proceso terminado, el desbloqueo del dispositivo, el reinicio, la persistencia SQLCipher real ni el comportamiento de un fabricante. Las instrumentadas y las pruebas manuales de Samsung no se ejecutaron desde esta tarea.

## Integración protegida de MainActivity

La entrada fría/caliente usa el mismo parser y espera al acceso local y al estado cargado. El token opaco pendiente se conserva solo durante recreación y se consume sin serializar texto o borradores. La invalidación del scheduler recarga el ViewModel compartido y su conteo de Plan; los guardados en curso se respetan. No se modifica AndroidManifest.xml ni se agregan dependencias.

```diff
--- a/app/src/main/java/com/bitavida/app/MainActivity.kt
+++ b/app/src/main/java/com/bitavida/app/MainActivity.kt
@@ -61,6 +61,7 @@
 import com.bitavida.app.domain.usecase.projectUpcomingMedicationAdministrations
 import com.bitavida.app.platform.access.AndroidLocalAuthenticator
 import com.bitavida.app.platform.reminder.MedicationReminderScheduler
+import com.bitavida.app.platform.reminder.CareReminderScheduler
 import com.bitavida.app.ui.access.AppAccessNotice
 import com.bitavida.app.ui.access.AppAccessViewModel
 import com.bitavida.app.ui.access.AppUnlockScreen
@@ -82,6 +83,7 @@
 import com.bitavida.app.ui.theme.BitavidaTheme
 import kotlinx.coroutines.Dispatchers
 import kotlinx.coroutines.withContext
+import kotlinx.coroutines.flow.first
 
 class MainActivity : FragmentActivity() {
     private val appAccessViewModel: AppAccessViewModel by viewModels()
@@ -113,15 +115,23 @@
     private lateinit var appProtectionPreferences: AppProtectionPreferences
     private var protectionEnabled by mutableStateOf(false)
     private var reminderAccessRevision by mutableIntStateOf(0)
+    private var pendingCareReminderToken by mutableStateOf<String?>(null)
     private var notificationPermissionRequested = false
 
     private companion object {
         const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1042
+        const val PENDING_CARE_NOTICE_STATE = "pending_opaque_care_notice"
     }
 
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         enableEdgeToEdge()
+        if (savedInstanceState == null) captureCareReminderIntent(intent)
+        else pendingCareReminderToken = runCatching {
+            savedInstanceState.getString(PENDING_CARE_NOTICE_STATE)
+        }.getOrNull()?.takeIf { token ->
+            token.length == 64 && token.all { it in '0'..'9' || it in 'a'..'f' }
+        }
         careProfileViewModel = ViewModelProvider(
             owner = this,
             factory = CareProfileViewModel.factory(
@@ -338,6 +348,14 @@
                                 val observationsViewModel = observationsViewModel(ready.profile)
                                 val remindersViewModel = remindersViewModel(ready.profile)
                                 val remindersState by remindersViewModel.state.collectAsState()
+                                val reminderContentRevision by CareReminderScheduler.changes.collectAsState()
+                                LaunchedEffect(reminderAccessRevision, reminderContentRevision,
+                                    remindersViewModel, destination) {
+                                    if (destination == MainDestination.PROTECTED_CONTENT) {
+                                        remindersViewModel.state.first { !it.saving }
+                                        remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Reload)
+                                    }
+                                }
                                 LaunchedEffect(remindersViewModel) {
                                     remindersViewModel.effects.collect { effect ->
                                         when (effect) {
@@ -356,7 +374,7 @@
                                     }
                                 }
                                 LaunchedEffect(reminderAccessRevision, remindersViewModel, remindersState.loading) {
-                                    val scheduler = com.bitavida.app.platform.reminder.CareReminderScheduler(applicationContext)
+                                    val scheduler = CareReminderScheduler(applicationContext)
                                     try {
                                         withContext(Dispatchers.IO) { scheduler.reconcileLatest(remindersViewModel.actor) }
                                         if (remindersState.active.any { it.notificationsEnabled }) {
@@ -378,6 +396,34 @@
                                         remindersOpenedFromToday = false
                                     }
                                 }
+                                LaunchedEffect(pendingCareReminderToken, remindersState.loading,
+                                    remindersState.loadFailed, remindersState.saving, destination) {
+                                    val token = pendingCareReminderToken ?: return@LaunchedEffect
+                                    if (destination != MainDestination.PROTECTED_CONTENT ||
+                                        remindersState.loading || remindersState.loadFailed || remindersState.saving ||
+                                        remindersViewModel.state.value.loading) return@LaunchedEffect
+                                    val recordId = try {
+                                        withContext(Dispatchers.IO) {
+                                            CareReminderScheduler(applicationContext).resolveNotificationTarget(token)
+                                        }
+                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
+                                        throw cancelled
+                                    } catch (_: Exception) { null }
+                                    if (pendingCareReminderToken != token ||
+                                        (protectionEnabled && !appAccessViewModel.isUnlocked)) return@LaunchedEffect
+                                    if (recordId != null) {
+                                        if (remindersViewModel.state.value.records.none { it.id == recordId }) {
+                                            remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Reload)
+                                            return@LaunchedEffect
+                                        }
+                                        remindersOpenedFromToday = false
+                                        remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.OpenMain)
+                                        remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Open(recordId))
+                                        planViewModel.onAction(MedicalPlanAction.OpenSection(com.bitavida.app.ui.medical.PlanSection.REMINDERS))
+                                        protectedContentDestination = ProtectedContentDestination.PLAN
+                                    }
+                                    consumeCareReminderIntent(token)
+                                }
                                 LaunchedEffect(observationState.snapshot, observationState.loading,
                                     observationState.loadError, reminderAccessRevision) {
                                     if (!observationState.loading && observationState.loadError == null) {
@@ -538,6 +584,9 @@
                                                 bitavidaAiViewModel = bitavidaAiViewModel,
                                                 observationsViewModel = observationsViewModel,
                                                 remindersViewModel = remindersViewModel,
+                                                activeReminderCount = remindersState.active.size.takeUnless {
+                                                    remindersState.loading || remindersState.loadFailed
+                                                },
                                                 onRemindersBack = {
                                                     planViewModel.onAction(MedicalPlanAction.Back)
                                                     if (remindersOpenedFromToday) {
@@ -585,6 +634,31 @@
         }
     }
 
+    override fun onNewIntent(intent: Intent) {
+        super.onNewIntent(intent)
+        setIntent(intent)
+        captureCareReminderIntent(intent)
+        reminderAccessRevision++
+    }
+
+    override fun onSaveInstanceState(outState: Bundle) {
+        // Only an opaque navigation capability; titles, details and drafts remain outside saved state.
+        outState.putString(PENDING_CARE_NOTICE_STATE, pendingCareReminderToken)
+        super.onSaveInstanceState(outState)
+    }
+
+    private fun captureCareReminderIntent(intent: Intent?) {
+        CareReminderScheduler.tokenFromOpenIntent(intent)?.let { pendingCareReminderToken = it }
+    }
+
+    private fun consumeCareReminderIntent(token: String) {
+        if (pendingCareReminderToken == token) pendingCareReminderToken = null
+        if (CareReminderScheduler.tokenFromOpenIntent(intent) == token) {
+            // Prevent a consumed notification destination from reopening on recreation.
+            setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
+        }
+    }
+
     override fun onRequestPermissionsResult(
         requestCode: Int,
         permissions: Array<out String>,
@@ -731,7 +805,7 @@
         val actor = com.bitavida.app.domain.repository.CareReminderActor(
             profile.id, profile.caregiver.id, profile.personUnderCare.id, profile.consent.isCurrent,
         )
-        val scheduler = com.bitavida.app.platform.reminder.CareReminderScheduler(applicationContext)
+        val scheduler = CareReminderScheduler(applicationContext)
         return ViewModelProvider(
             this,
             object : ViewModelProvider.Factory {
```

## Diff exacto del adaptador y sus nuevas reglas/pruebas

```diff
--- a/app/src/main/java/com/bitavida/app/platform/reminder/CareReminderScheduler.kt
+++ b/app/src/main/java/com/bitavida/app/platform/reminder/CareReminderScheduler.kt
@@ -1,19 +1,29 @@
 package com.bitavida.app.platform.reminder
 
+import android.Manifest
+import android.app.KeyguardManager
+import android.app.Notification
+import android.app.NotificationChannel
 import android.app.NotificationManager
+import android.app.PendingIntent
 import android.content.BroadcastReceiver
 import android.content.Context
 import android.content.Intent
+import android.content.pm.PackageManager
+import android.net.Uri
+import android.os.Build
+import androidx.core.app.NotificationCompat
+import com.bitavida.app.MainActivity
 import com.bitavida.app.R
 import com.bitavida.app.data.careprofile.EncryptedCareProfileRepository
 import com.bitavida.app.data.reminders.EncryptedCareReminderRepository
 import com.bitavida.app.domain.model.CareReminder
-import com.bitavida.app.domain.model.CareReminderStatus
 import com.bitavida.app.domain.repository.CareReminderActor
+import com.bitavida.app.domain.usecase.CompleteCareReminderNotice
-import com.bitavida.app.domain.usecase.careReminderNoticeIdentity
 import com.bitavida.app.domain.usecase.careReminderNoticeIsDue
+import com.bitavida.app.domain.usecase.isCareReminderNoticeToken
+import com.bitavida.app.domain.usecase.pendingCareReminderForNotice
+import com.bitavida.app.domain.usecase.planCareReminderNoticeState
-import com.bitavida.app.domain.usecase.careReminderNoticeIdentitiesAwaitingDelivery
-import com.bitavida.app.domain.usecase.planCareReminderNotices
 import java.security.MessageDigest
 import java.time.LocalDateTime
 import java.time.ZoneId
@@ -21,56 +31,60 @@
 import kotlinx.coroutines.CoroutineScope
 import kotlinx.coroutines.Dispatchers
 import kotlinx.coroutines.SupervisorJob
+import kotlinx.coroutines.flow.MutableStateFlow
+import kotlinx.coroutines.flow.asStateFlow
+import kotlinx.coroutines.flow.update
 import kotlinx.coroutines.launch
 import kotlinx.coroutines.sync.Mutex
 import kotlinx.coroutines.sync.withLock
+import kotlinx.coroutines.withTimeout
+import kotlin.time.Duration.Companion.seconds
 
+/** Local one-shot errands reuse the alarm adapter without requesting exact-alarm access. */
-/** Reuses the local alarm/notification adapter; care errands never request exact-alarm access. */
 class CareReminderScheduler(context: Context) {
     private val appContext = context.applicationContext
     private val delegate = MeasurementReminderScheduler(appContext, CONFIG)
+    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
     private val deliveredRegistry = appContext.getSharedPreferences("care_notice_delivery", Context.MODE_PRIVATE)
 
+    fun notificationsAllowed(): Boolean =
+        delegate.notificationsAllowed() &&
+            notificationManager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
-    fun notificationsAllowed(): Boolean {
-        val manager = appContext.getSystemService(NotificationManager::class.java)
-        return delegate.notificationsAllowed() &&
-            (manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE)
-    }
 
+    /** Every entry revalidates committed encrypted state under a process-wide lock. */
-    /** All paths reload the committed encrypted state under the same process-wide lock. */
     suspend fun reconcileLatest(actor: CareReminderActor, restoreElapsed: Boolean = false) {
         reconciliationMutex.withLock {
+            reconcile(EncryptedCareReminderRepository(appContext).load(actor), restoreElapsed)
-            val reminders = EncryptedCareReminderRepository(appContext).load(actor)
-            reconcile(reminders, restoreElapsed)
         }
     }
 
     suspend fun restore() {
         val actor = currentActor()
+        if (actor == null) reconciliationMutex.withLock {
+            deliveredTokens().forEach(::cancelNotice)
+            delegate.reconcilePlanned(emptyList())
+        } else reconcileLatest(actor, restoreElapsed = true)
-        if (actor == null) reconciliationMutex.withLock { delegate.reconcilePlanned(emptyList()) }
-        else reconcileLatest(actor, restoreElapsed = true)
     }
 
+    // Receipts contain only opaque capabilities, never the displayed title or raw identifiers.
-    // Called on Dispatchers.IO by the receiver; synchronous receipts prevent duplicate delivery.
     @android.annotation.SuppressLint("ApplySharedPref", "UseKtx")
     suspend fun deliver(token: String) {
+        if (!isCareReminderNoticeToken(token)) return
         val actor = currentActor() ?: return
         reconciliationMutex.withLock {
             val reminders = EncryptedCareReminderRepository(appContext).load(actor)
             val now = LocalDateTime.now()
+            val reminder = pendingCareReminderForNotice(reminders, actor.personUnderCareId, token, ::tokenFor)
+                ?.takeIf { careReminderNoticeIsDue(it, now) }
+            val delivered = deliveredTokens()
-            val reminder = reminders.firstOrNull {
-                tokenFor(careReminderNoticeIdentity(it)) == token && careReminderNoticeIsDue(it, now)
-            }
-            val delivered = deliveredRegistry.getStringSet(KEY_DELIVERED, emptySet()).orEmpty()
             if (reminder != null && token !in delivered && notificationsAllowed()) {
+                val next = delivered + token
+                // Commit before notifying: another delivery after dismissal cannot alert again.
-                // Persist opaque receipt first. A repeated broadcast cannot alert again after dismissal.
-                val next = delivered.filter { previous ->
-                    reminders.any { it.status == CareReminderStatus.PENDING &&
-                        tokenFor(careReminderNoticeIdentity(it)) == previous }
-                }.toSet() + token
                 if (deliveredRegistry.edit().putStringSet(KEY_DELIVERED, next).commit()) {
                     try {
+                        if (!showReminder(token, reminder)) {
+                            deliveredRegistry.edit().putStringSet(KEY_DELIVERED, delivered).commit()
+                        }
-                        delegate.showReminder(token)
                     } catch (error: Exception) {
                         deliveredRegistry.edit().putStringSet(KEY_DELIVERED, delivered).commit()
                         throw error
@@ -81,21 +95,133 @@
         }
     }
 
+    /** Explicit native action. Dismissal never enters this operation. */
+    suspend fun completeFromNotification(token: String): Boolean {
+        if (!isCareReminderNoticeToken(token) ||
+            appContext.getSystemService(KeyguardManager::class.java).isDeviceLocked) return false
+        val actor = currentActor() ?: return false
+        return reconciliationMutex.withLock {
+            val repository = EncryptedCareReminderRepository(appContext)
+            val closed = CompleteCareReminderNotice(repository, ::tokenFor)(
+                actor, token, deliveredTokens(), LocalDateTime.now(),
+            )
+            if (closed != null) {
+                // The database is authoritative even if a subsequent OS cancellation fails.
+                mutableChanges.update { it + 1L }
+                try { cancelNotice(token) }
+                catch (error: CancellationException) { throw error }
+                catch (_: Exception) { /* Retry cancellation at the next reconciliation. */ }
+            }
+            try { reconcile(repository.load(actor)) }
+            catch (error: CancellationException) { throw error }
+            catch (_: Exception) { /* Never roll back or hide an already committed completion. */ }
+            closed != null
+        }
+    }
+
+    /** Returns an identifier only in process memory, after the UI has passed its own access gate. */
+    suspend fun resolveNotificationTarget(token: String): String? {
+        if (!isCareReminderNoticeToken(token)) return null
+        val actor = currentActor() ?: return null
+        return reconciliationMutex.withLock {
+            if (token !in deliveredTokens()) return@withLock null
+            pendingCareReminderForNotice(
+                EncryptedCareReminderRepository(appContext).load(actor), actor.personUnderCareId, token, ::tokenFor,
+            )?.id
+        }
+    }
+
+    @android.annotation.SuppressLint("ApplySharedPref", "UseKtx")
     private fun reconcile(reminders: List<CareReminder>, restoreElapsed: Boolean = false) {
         val zoneId = ZoneId.systemDefault()
+        val delivered = deliveredTokens()
+        val plan = planCareReminderNoticeState(reminders, LocalDateTime.now(zoneId),
+            notificationsAllowed(), delivered, ::tokenFor)
+        plan.tokensToCancel.forEach(::cancelNotice)
+        if (plan.retainedReceipts != delivered) {
+            deliveredRegistry.edit().putStringSet(KEY_DELIVERED, plan.retainedReceipts).commit()
+        }
-        val now = LocalDateTime.now(zoneId)
-        val available = notificationsAllowed()
-        val planned = planCareReminderNotices(reminders, now, available)
-        val delivered = deliveredRegistry.getStringSet(KEY_DELIVERED, emptySet()).orEmpty()
-        val awaiting = careReminderNoticeIdentitiesAwaitingDelivery(reminders, now, available)
-            .map(::tokenFor).filterNot { it in delivered }.toSet()
         delegate.reconcilePlanned(
+            plan.futureNotices.map { tokenFor(it.identity) to it.scheduledAt.atZone(zoneId).toInstant().toEpochMilli() },
+            preserveElapsedTokens = plan.awaitingDeliveryTokens,
-            planned.map { tokenFor(it.identity) to it.scheduledAt.atZone(zoneId).toInstant().toEpochMilli() },
-            preserveElapsedTokens = awaiting,
             restoreElapsed = restoreElapsed,
         )
     }
 
+    private fun deliveredTokens(): Set<String> =
+        deliveredRegistry.getStringSet(KEY_DELIVERED, emptySet()).orEmpty().toSet()
+
+    private fun showReminder(token: String, reminder: CareReminder): Boolean {
+        if (!notificationsAllowed()) return false
+        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
+            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
+        createChannel()
+        notificationManager.notify(notificationTag(token), NOTIFICATION_ID, buildNotification(token, reminder))
+        return true
+    }
+
+    /** Native Android presentation; system lock-screen policy remains under the user's control. */
+    internal fun buildNotification(token: String, reminder: CareReminder): Notification {
+        require(isCareReminderNoticeToken(token))
+        val content = PendingIntent.getActivity(
+            appContext, 0,
+            Intent(appContext, MainActivity::class.java).apply {
+                action = ACTION_OPEN_REMINDER
+                data = capabilityUri(OPEN_AUTHORITY, token)
+                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
+                putExtra(EXTRA_TOKEN, token)
+            },
+            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
+        )
+        val complete = PendingIntent.getBroadcast(
+            appContext, 0,
+            Intent(appContext, CareReminderReceiver::class.java).apply {
+                action = ACTION_COMPLETE
+                data = capabilityUri(COMPLETE_AUTHORITY, token)
+                putExtra(EXTRA_TOKEN, token)
+            },
+            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
+        )
+        val publicVersion = NotificationCompat.Builder(appContext, CHANNEL_ID)
+            .setSmallIcon(R.drawable.ic_v2_clock)
+            .setContentTitle("Recordatorio pendiente")
+            .setContentText("Abre Bitavida para consultar tu pendiente.")
+            .build()
+        return NotificationCompat.Builder(appContext, CHANNEL_ID)
+            .setSmallIcon(R.drawable.ic_v2_clock)
+            .setContentTitle("Recordatorio pendiente")
+            .setContentText(reminder.title)
+            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.title))
+            .setCategory(NotificationCompat.CATEGORY_REMINDER)
+            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
+            .setPublicVersion(publicVersion)
+            .setContentIntent(content)
+            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_v2_clipboard, "Completar", complete)
+                .setAuthenticationRequired(true)
+                .setShowsUserInterface(false)
+                .build())
+            .setAllowSystemGeneratedContextualActions(false)
+            .setOngoing(true)
+            .setAutoCancel(false)
+            .setOnlyAlertOnce(true)
+            .build()
+    }
+
+    private fun cancelNotice(token: String) {
+        notificationManager.cancel(notificationTag(token), NOTIFICATION_ID)
+        // Remove a previous-version informational notice if it still exists.
+        notificationManager.cancel(31 * token.hashCode() + "care".hashCode())
+    }
+
+    private fun createChannel() {
+        notificationManager.createNotificationChannel(
+            NotificationChannel(CHANNEL_ID, "Recordatorios del cuidado", NotificationManager.IMPORTANCE_DEFAULT).apply {
+                description = "Avisos opcionales para pendientes cotidianos"
+                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
+            },
+        )
+    }
+
     private suspend fun currentActor(): CareReminderActor? {
         val profile = EncryptedCareProfileRepository(appContext).get() ?: return null
         return CareReminderActor(profile.id, profile.caregiver.id, profile.personUnderCare.id,
@@ -104,27 +230,56 @@
 
     companion object {
         internal const val ACTION_REMINDER = "com.bitavida.app.action.CARE_REMINDER"
+        internal const val ACTION_COMPLETE = "com.bitavida.app.action.COMPLETE_CARE_REMINDER"
+        internal const val ACTION_OPEN_REMINDER = "com.bitavida.app.action.OPEN_CARE_REMINDER"
         internal const val EXTRA_TOKEN = "opaque_care_reminder_token"
+        internal const val NOTIFICATION_ID = 0
         private const val CHANNEL_ID = "care_errands"
         private const val KEY_DELIVERED = "opaque_delivered_tokens"
+        private const val OPEN_AUTHORITY = "care-reminder-open"
+        private const val COMPLETE_AUTHORITY = "care-reminder-complete"
         private val reconciliationMutex = Mutex()
+        private val mutableChanges = MutableStateFlow(0L)
+        val changes = mutableChanges.asStateFlow()
         internal val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
+        internal val RECEIVER_TIMEOUT = 8.seconds
         private val CONFIG = MeasurementReminderConfig(
+            namespace = "care", channelId = CHANNEL_ID, channelName = "Recordatorios del cuidado",
-            namespace = "care",
-            channelId = CHANNEL_ID,
-            channelName = "Recordatorios del cuidado",
             channelDescription = "Avisos opcionales para pendientes cotidianos",
+            reminderAction = ACTION_REMINDER, tokenExtra = EXTRA_TOKEN,
+            receiverClass = CareReminderReceiver::class.java, icon = R.drawable.ic_v2_clock,
+            privateTitle = "Recordatorio del cuidado", privateText = "Abre Bitavida para consultar tu pendiente.",
+            publicTitle = "Recordatorio de Bitavida", preferExactAlarms = false, opaquePendingIntentIdentity = true,
-            reminderAction = ACTION_REMINDER,
-            tokenExtra = EXTRA_TOKEN,
-            receiverClass = CareReminderReceiver::class.java,
-            icon = R.drawable.ic_v2_clock,
-            privateTitle = "Recordatorio del cuidado",
-            privateText = "Abre Bitavida para consultar tu pendiente.",
-            publicTitle = "Recordatorio de Bitavida",
-            preferExactAlarms = false,
-            opaquePendingIntentIdentity = true,
         )
 
+        fun tokenFromOpenIntent(intent: Intent?): String? =
+            intent?.takeIf { it.action == ACTION_OPEN_REMINDER }?.let { tokenFromIntent(it, OPEN_AUTHORITY) }
+
+        internal fun tokenFromReceiverIntent(intent: Intent): String? = when (intent.action) {
+            ACTION_REMINDER -> tokenFromIntent(intent, "care-reminder")
+            ACTION_COMPLETE -> tokenFromIntent(intent, COMPLETE_AUTHORITY)
+            else -> null
+        }
+
+        private fun tokenFromIntent(intent: Intent, authority: String): String? = try {
+            val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
+                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
+            if (intent.flags and grants != 0 || intent.selector != null || intent.clipData != null) null
+            else {
+                val uri = intent.data
+                val token = intent.getStringExtra(EXTRA_TOKEN)
+                token?.takeIf {
+                    isCareReminderNoticeToken(it) && uri?.scheme == "bitavida" && uri.authority == authority &&
+                        uri.pathSegments == listOf(it) && uri.query == null && uri.fragment == null
+                }
+            }
+        } catch (_: RuntimeException) { null }
+
+        internal fun notificationTag(token: String): String = "care:$token"
+
+        private fun capabilityUri(authority: String, token: String): Uri =
+            Uri.Builder().scheme("bitavida").authority(authority).appendPath(token).build()
+
         internal fun tokenFor(identity: String): String =
             MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                 .joinToString("") { byte -> "%02x".format(byte) }
@@ -133,14 +288,18 @@
 
 class CareReminderReceiver : BroadcastReceiver() {
     override fun onReceive(context: Context, intent: Intent) {
+        val token = CareReminderScheduler.tokenFromReceiverIntent(intent) ?: return
-        if (intent.action != CareReminderScheduler.ACTION_REMINDER) return
-        val token = intent.getStringExtra(CareReminderScheduler.EXTRA_TOKEN)
-            ?.takeIf { it.length == 64 && it.all { char -> char in '0'..'9' || char in 'a'..'f' } } ?: return
         val pending = goAsync()
+        val appContext = context.applicationContext
         CareReminderScheduler.receiverScope.launch {
+            try {
+                withTimeout(CareReminderScheduler.RECEIVER_TIMEOUT) {
+                    val scheduler = CareReminderScheduler(appContext)
+                    if (intent.action == CareReminderScheduler.ACTION_COMPLETE) scheduler.completeFromNotification(token)
+                    else scheduler.deliver(token)
+                }
+            } catch (error: CancellationException) { throw error }
+            catch (_: Exception) { /* No text, identifiers or storage exceptions in logs/notifications. */ }
-            try { CareReminderScheduler(context).deliver(token) }
-            catch (error: CancellationException) { throw error }
-            catch (_: Exception) { /* No record content or storage errors in logs/notifications. */ }
             finally { pending.finish() }
         }
     }
@@ -150,10 +309,11 @@
     override fun onReceive(context: Context, intent: Intent) {
         if (intent.action !in ACTIONS) return
         val pending = goAsync()
+        val appContext = context.applicationContext
         CareReminderScheduler.receiverScope.launch {
+            try { withTimeout(CareReminderScheduler.RECEIVER_TIMEOUT) { CareReminderScheduler(appContext).restore() } }
-            try { CareReminderScheduler(context).restore() }
             catch (error: CancellationException) { throw error }
+            catch (_: Exception) { /* Retry at app entry; never replace encrypted data. */ }
-            catch (_: Exception) { /* Retry at the next app entry; never replace encrypted data. */ }
             finally { pending.finish() }
         }
     }
```

```diff
--- /dev/null
+++ b/app/src/main/java/com/bitavida/app/domain/usecase/CareReminderNoticeActions.kt
@@ -0,0 +1,83 @@
+package com.bitavida.app.domain.usecase
+
+import com.bitavida.app.domain.model.CareReminder
+import com.bitavida.app.domain.model.CareReminderStatus
+import com.bitavida.app.domain.repository.CareReminderActor
+import com.bitavida.app.domain.repository.CareReminderRepository
+import java.time.LocalDateTime
+
+/** A notice capability contains no raw patient/reminder identifier or free text. */
+fun isCareReminderNoticeToken(token: String): Boolean =
+    token.length == 64 && token.all { it in '0'..'9' || it in 'a'..'f' }
+
+/** Only the current, still-enabled revision can be addressed by a delivered notice. */
+fun pendingCareReminderForNotice(
+    reminders: List<CareReminder>,
+    personUnderCareId: String,
+    token: String,
+    tokenForIdentity: (String) -> String,
+): CareReminder? {
+    if (!isCareReminderNoticeToken(token)) return null
+    return reminders.singleOrNull {
+        it.personUnderCareId == personUnderCareId && it.status == CareReminderStatus.PENDING &&
+            it.notificationsEnabled && tokenForIdentity(careReminderNoticeIdentity(it)) == token
+    }
+}
+
+/** Receipt policy survives dismissal/reboot without treating dismissal as completion or re-alerting. */
+data class CareReminderNoticeState(
+    val retainedReceipts: Set<String>,
+    val tokensToCancel: Set<String>,
+    val futureNotices: List<PlannedCareReminderNotice>,
+    val awaitingDeliveryTokens: Set<String>,
+)
+
+fun planCareReminderNoticeState(
+    reminders: List<CareReminder>,
+    now: LocalDateTime,
+    notificationsAvailable: Boolean,
+    deliveredTokens: Set<String>,
+    tokenForIdentity: (String) -> String,
+): CareReminderNoticeState {
+    val currentTokens = reminders.asSequence()
+        .filter { it.status == CareReminderStatus.PENDING && it.notificationsEnabled }
+        .map { tokenForIdentity(careReminderNoticeIdentity(it)) }.toSet()
+    val retained = deliveredTokens.intersect(currentTokens)
+    return CareReminderNoticeState(
+        retainedReceipts = retained,
+        tokensToCancel = deliveredTokens - currentTokens,
+        futureNotices = planCareReminderNotices(reminders, now, notificationsAvailable)
+            .filterNot { tokenForIdentity(it.identity) in retained },
+        awaitingDeliveryTokens = careReminderNoticeIdentitiesAwaitingDelivery(reminders, now, notificationsAvailable)
+            .map(tokenForIdentity).filterNot { it in retained }.toSet(),
+    )
+}
+
+/** A deliberate native notification action uses the same transactional close operation as the UI. */
+class CompleteCareReminderNotice(
+    private val repository: CareReminderRepository,
+    private val tokenForIdentity: (String) -> String,
+) {
+    suspend operator fun invoke(
+        actor: CareReminderActor,
+        token: String,
+        deliveredTokens: Set<String>,
+        now: LocalDateTime,
+    ): CareReminder? {
+        if (!isCareReminderNoticeToken(token) || token !in deliveredTokens) return null
+        val reminder = pendingCareReminderForNotice(repository.load(actor), actor.personUnderCareId,
+            token, tokenForIdentity) ?: return null
+        return try {
+            ManageCareReminders(repository)(actor, CareReminderCommand.Close(
+                operationId = "notice-complete:$token",
+                id = reminder.id,
+                expectedRevision = reminder.revision,
+                status = CareReminderStatus.DONE,
+            ), now)
+        } catch (error: CareReminderValidationException) {
+            // A concurrent edit/close wins. A stale notice must never close a newer revision.
+            if (error.problem in setOf(CareReminderProblem.CONFLICT, CareReminderProblem.CLOSED,
+                    CareReminderProblem.NOT_FOUND)) null else throw error
+        }
+    }
+}
```

```diff
--- /dev/null
+++ b/app/src/test/java/com/bitavida/app/domain/usecase/CareReminderNoticeActionsTest.kt
@@ -0,0 +1,168 @@
+package com.bitavida.app.domain.usecase
+
+import com.bitavida.app.domain.model.CareReminder
+import com.bitavida.app.domain.model.CareReminderStatus
+import com.bitavida.app.domain.repository.CareReminderActor
+import com.bitavida.app.domain.repository.CareReminderAuthorizationException
+import com.bitavida.app.domain.repository.CareReminderRepository
+import java.security.MessageDigest
+import java.time.LocalDateTime
+import kotlinx.coroutines.runBlocking
+import org.junit.Assert.*
+import org.junit.Test
+
+class CareReminderNoticeActionsTest {
+    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
+    private val actor = CareReminderActor("profile-example", "caregiver-example", "person-example", true)
+    private val pending = CareReminder("reminder-example", actor.personUnderCareId,
+        "Preparar documentos ficticios", "Detalle ficticio", now.minusMinutes(5), true,
+        CareReminderStatus.PENDING, now.minusDays(1), now.minusDays(1), null, 1)
+    private fun token(identity: String): String =
+        MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
+            .joinToString("") { "%02x".format(it) }
+    private fun notice(record: CareReminder = pending) = token(careReminderNoticeIdentity(record))
+
+    @Test fun explicitActionClosesAtRealRequestedTimeAndReleasesTheActiveSlot() = runBlocking {
+        val repository = NoticeRepository(listOf(pending))
+        val result = CompleteCareReminderNotice(repository, ::token)(actor, notice(), setOf(notice()), now)!!
+        assertEquals(CareReminderStatus.DONE, result.status)
+        assertEquals(now, result.closedAt)
+        assertEquals(now, result.modifiedAt)
+        assertEquals(2, result.revision)
+        assertEquals(pending.originallyScheduledAt, result.originallyScheduledAt)
+        assertTrue(activeCareReminders(repository.rows, actor.personUnderCareId).isEmpty())
+        assertEquals(listOf(result), previousCareReminders(repository.rows, actor.personUnderCareId))
+        val plan = planCareReminderNoticeState(repository.rows, now, true, setOf(notice()), ::token)
+        assertEquals(setOf(notice()), plan.tokensToCancel)
+        assertTrue(plan.futureNotices.isEmpty())
+        assertTrue(plan.awaitingDeliveryTokens.isEmpty())
+    }
+
+    @Test fun repeatedCompleteActionDoesNotWriteAnotherRevision() = runBlocking {
+        val repository = NoticeRepository(listOf(pending))
+        val complete = CompleteCareReminderNotice(repository, ::token)
+        val delivered = setOf(notice())
+        assertNotNull(complete(actor, notice(), delivered, now))
+        assertNull(complete(actor, notice(), delivered, now.plusMinutes(1)))
+        assertEquals(1, repository.commands.size)
+        assertEquals("notice-complete:" + notice(), repository.commands.single().operationId)
+        assertEquals(now, repository.rows.single().closedAt)
+    }
+
+    @Test fun invalidOrUndeliveredCapabilitiesNeverLoadOrWriteRecords() = runBlocking {
+        val repository = NoticeRepository(listOf(pending))
+        val complete = CompleteCareReminderNotice(repository, ::token)
+        listOf("", "a".repeat(63), "g".repeat(64), "A".repeat(64), notice()).forEach {
+            assertNull(complete(actor, it, emptySet(), now))
+        }
+        assertEquals(0, repository.loads)
+        assertTrue(repository.commands.isEmpty())
+    }
+
+    @Test fun oldRevisionClosedDisabledAndAnotherPatientCannotBeCompleted() = runBlocking {
+        val variants = listOf(
+            pending.copy(revision = 2, title = "Título modificado ficticio"),
+            pending.copy(status = CareReminderStatus.DONE, revision = 2, closedAt = now),
+            pending.copy(status = CareReminderStatus.DISCARDED, revision = 2, closedAt = now),
+            pending.copy(notificationsEnabled = false),
+            pending.copy(personUnderCareId = "another-person-example"),
+        )
+        variants.forEach { current ->
+            val repository = NoticeRepository(listOf(current))
+            assertNull(CompleteCareReminderNotice(repository, ::token)(actor, notice(), setOf(notice()), now))
+            assertEquals(listOf(current), repository.rows)
+            assertTrue(repository.commands.isEmpty())
+        }
+    }
+
+    @Test fun concurrentEditWinsInsteadOfClosingTheUpdatedReminder() = runBlocking {
+        val repository = NoticeRepository(listOf(pending))
+        repository.beforeApply = {
+            repository.rows = listOf(pending.copy(revision = 2, title = "Título corregido ficticio"))
+        }
+        assertNull(CompleteCareReminderNotice(repository, ::token)(actor, notice(), setOf(notice()), now))
+        assertEquals(CareReminderStatus.PENDING, repository.rows.single().status)
+        assertEquals(2, repository.rows.single().revision)
+        assertNull(repository.rows.single().closedAt)
+    }
+
+    @Test fun authorizationIsStillEnforcedForNativeActions() = runBlocking {
+        val repository = NoticeRepository(listOf(pending))
+        try {
+            CompleteCareReminderNotice(repository, ::token)(actor.copy(authorizationCurrent = false),
+                notice(), setOf(notice()), now)
+            fail("Expected authorized repository boundary")
+        } catch (_: CareReminderAuthorizationException) { }
+        assertTrue(repository.commands.isEmpty())
+        assertEquals(CareReminderStatus.PENDING, repository.rows.single().status)
+    }
+
+    @Test fun dismissalAndRestartReceiptPolicyNeverRealertOrCompleteTheReminder() {
+        val delivered = setOf(notice())
+        val plan = planCareReminderNoticeState(listOf(pending), now.plusDays(1), true, delivered, ::token)
+        assertEquals(delivered, plan.retainedReceipts)
+        assertTrue(plan.tokensToCancel.isEmpty())
+        assertTrue(plan.futureNotices.isEmpty())
+        assertTrue(plan.awaitingDeliveryTokens.isEmpty())
+        assertEquals(CareReminderStatus.PENDING, pending.status)
+        assertNull(pending.closedAt)
+        assertEquals(listOf(pending), activeCareReminders(listOf(pending), actor.personUnderCareId))
+        assertTrue(previousCareReminders(listOf(pending), actor.personUnderCareId).isEmpty())
+    }
+
+    @Test fun deliveredNoticeIsNotScheduledAgainAfterTheClockMovesBack() {
+        val plan = planCareReminderNoticeState(listOf(pending), now.minusHours(1), true, setOf(notice()), ::token)
+        assertTrue(plan.futureNotices.isEmpty())
+        assertEquals(setOf(notice()), plan.retainedReceipts)
+    }
+
+    @Test fun postponedNoticeCancelsItsOldCapabilityAndSchedulesOnlyTheNewRevision() {
+        val postponed = pending.copy(revision = 2, scheduledAt = now.plusHours(1))
+        val plan = planCareReminderNoticeState(listOf(postponed), now, true, setOf(notice()), ::token)
+        assertEquals(setOf(notice()), plan.tokensToCancel)
+        assertTrue(plan.retainedReceipts.isEmpty())
+        assertEquals(listOf(careReminderNoticeIdentity(postponed)), plan.futureNotices.map { it.identity })
+        assertTrue(plan.awaitingDeliveryTokens.isEmpty())
+    }
+
+    @Test fun disabledNotificationAndPermissionDenialKeepTheRecordPending() {
+        val disabled = pending.copy(notificationsEnabled = false)
+        val disabledPlan = planCareReminderNoticeState(listOf(disabled), now, true, setOf(notice()), ::token)
+        assertEquals(setOf(notice()), disabledPlan.tokensToCancel)
+        assertTrue(disabledPlan.futureNotices.isEmpty())
+        val deniedPlan = planCareReminderNoticeState(listOf(pending), now, false, setOf(notice()), ::token)
+        assertEquals(setOf(notice()), deniedPlan.retainedReceipts)
+        assertTrue(deniedPlan.awaitingDeliveryTokens.isEmpty())
+        assertTrue(deniedPlan.futureNotices.isEmpty())
+        assertEquals(CareReminderStatus.PENDING, pending.status)
+    }
+
+    @Test fun undeliveredPastAlarmRemainsEligibleForOneShotRecovery() {
+        val plan = planCareReminderNoticeState(listOf(pending), now, true, emptySet(), ::token)
+        assertEquals(setOf(notice()), plan.awaitingDeliveryTokens)
+        assertTrue(plan.futureNotices.isEmpty())
+        assertTrue(plan.retainedReceipts.isEmpty())
+    }
+
+    private class NoticeRepository(var rows: List<CareReminder>) : CareReminderRepository {
+        var loads = 0
+        val commands = mutableListOf<CareReminderCommand>()
+        var beforeApply: (() -> Unit)? = null
+
+        override suspend fun load(actor: CareReminderActor): List<CareReminder> {
+            if (!actor.authorizationCurrent) throw CareReminderAuthorizationException()
+            loads++
+            return rows
+        }
+
+        override suspend fun apply(actor: CareReminderActor, command: CareReminderCommand,
+            now: LocalDateTime): CareReminder {
+            if (!actor.authorizationCurrent) throw CareReminderAuthorizationException()
+            beforeApply?.invoke()
+            val result = applyCareReminderCommand(rows, actor.personUnderCareId, command, now)
+            rows = rows.map { if (it.id == result.id) result else it }
+            commands += command
+            return result
+        }
+    }
+}
```

```diff
--- /dev/null
+++ b/app/src/androidTest/java/com/bitavida/app/platform/reminder/CareReminderNotificationContractTest.kt
@@ -0,0 +1,124 @@
+package com.bitavida.app.platform.reminder
+
+import android.app.Notification
+import android.content.ClipData
+import android.content.Intent
+import android.net.Uri
+import android.os.Build
+import androidx.core.app.NotificationCompat
+import androidx.test.ext.junit.runners.AndroidJUnit4
+import androidx.test.platform.app.InstrumentationRegistry
+import com.bitavida.app.MainActivity
+import com.bitavida.app.domain.model.CareReminder
+import com.bitavida.app.domain.model.CareReminderStatus
+import java.time.LocalDateTime
+import org.junit.Assert.*
+import org.junit.Test
+import org.junit.runner.RunWith
+
+/** Prepared native contract checks. They never post notifications, schedule alarms or open a database. */
+@RunWith(AndroidJUnit4::class)
+class CareReminderNotificationContractTest {
+    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
+    private val token = "a".repeat(64)
+    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
+    private val reminder = CareReminder("notice-example", "person-example", "Preparar documentos ficticios",
+        "Detalles privados ficticios", now, true, CareReminderStatus.PENDING, now, now, null, 1)
+
+    @Test fun pendingNoticeUsesNativeOngoingPrivatePresentationAndASeparateCompletionAction() {
+        val notification = CareReminderScheduler(context).buildNotification(token, reminder)
+        val complete = NotificationCompat.getAction(notification, 0)!!
+        try {
+            assertEquals("Recordatorio pendiente", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
+            assertEquals(reminder.title, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
+            assertEquals(reminder.title, notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
+            assertNotEquals(reminder.details, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
+            assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
+            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
+            assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
+            assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
+            assertEquals(1, NotificationCompat.getActionCount(notification))
+            assertEquals("Completar", complete.title.toString())
+            assertFalse(complete.showsUserInterface)
+            assertTrue(complete.isAuthenticationRequired)
+            assertNotEquals(notification.contentIntent, complete.actionIntent)
+            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
+                assertTrue(notification.contentIntent.isImmutable)
+                assertTrue(complete.actionIntent!!.isImmutable)
+            }
+            val publicVersion = notification.publicVersion!!
+            assertEquals("Recordatorio pendiente", publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
+            assertEquals("Abre Bitavida para consultar tu pendiente.",
+                publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
+            assertEquals(0, NotificationCompat.getActionCount(publicVersion))
+        } finally {
+            notification.contentIntent.cancel()
+            complete.actionIntent?.cancel()
+        }
+    }
+
+    @Test fun capabilitiesHaveDistinctPendingIntentsAndTagsEvenWhenShownTogether() {
+        val scheduler = CareReminderScheduler(context)
+        val first = scheduler.buildNotification(token, reminder)
+        val secondToken = "b".repeat(64)
+        val second = scheduler.buildNotification(secondToken, reminder.copy(id = "another-notice-example"))
+        try {
+            assertNotEquals(first.contentIntent, second.contentIntent)
+            assertNotEquals(NotificationCompat.getAction(first, 0)!!.actionIntent,
+                NotificationCompat.getAction(second, 0)!!.actionIntent)
+            assertNotEquals(CareReminderScheduler.notificationTag(token), CareReminderScheduler.notificationTag(secondToken))
+        } finally {
+            listOf(first, second).forEach {
+                it.contentIntent.cancel()
+                NotificationCompat.getAction(it, 0)!!.actionIntent?.cancel()
+            }
+        }
+    }
+
+    @Test fun coldAndWarmEntryParserAcceptsOnlyTheSameBoundedOpaqueCapability() {
+        val accepted = openIntent(token)
+        assertEquals(token, CareReminderScheduler.tokenFromOpenIntent(accepted))
+        assertEquals(token, CareReminderScheduler.tokenFromOpenIntent(Intent(accepted)))
+        assertNull(CareReminderScheduler.tokenFromOpenIntent(null))
+        assertNull(CareReminderScheduler.tokenFromOpenIntent(Intent(context, MainActivity::class.java)))
+        val invalid = listOf(
+            openIntent(token).setAction(CareReminderScheduler.ACTION_COMPLETE),
+            openIntent(token).setData(null),
+            openIntent(token).setData(Uri.parse("https://example.invalid/$token")),
+            openIntent(token).setData(Uri.parse("bitavida://care-reminder-complete/$token")),
+            openIntent(token).setData(Uri.parse("bitavida://care-reminder-open/$token/extra")),
+            openIntent(token).setData(Uri.parse("bitavida://care-reminder-open/$token?extra=value")),
+            openIntent(token).setData(Uri.parse("bitavida://care-reminder-open/$token#fragment")),
+            openIntent(token).putExtra(CareReminderScheduler.EXTRA_TOKEN, "b".repeat(64)),
+            openIntent(token).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
+            openIntent("A".repeat(64)),
+            openIntent("a".repeat(63)),
+        )
+        invalid.forEach { assertNull(CareReminderScheduler.tokenFromOpenIntent(it)) }
+        val withClip = openIntent(token).apply { clipData = ClipData.newPlainText("Ficticio", "Ficticio") }
+        assertNull(CareReminderScheduler.tokenFromOpenIntent(withClip))
+        val withSelector = openIntent(token).apply { selector = Intent(Intent.ACTION_VIEW) }
+        assertNull(CareReminderScheduler.tokenFromOpenIntent(withSelector))
+    }
+
+    @Test fun deliveryAndCompletionReceiversRequireTheirOwnExplicitCapabilityShape() {
+        fun receiver(actionName: String, authority: String) = Intent(context, CareReminderReceiver::class.java).apply {
+            action = actionName
+            data = Uri.parse("bitavida://$authority/$token")
+            putExtra(CareReminderScheduler.EXTRA_TOKEN, token)
+        }
+        val alarm = receiver(CareReminderScheduler.ACTION_REMINDER, "care-reminder")
+        val complete = receiver(CareReminderScheduler.ACTION_COMPLETE, "care-reminder-complete")
+        assertEquals(token, CareReminderScheduler.tokenFromReceiverIntent(alarm))
+        assertEquals(token, CareReminderScheduler.tokenFromReceiverIntent(complete))
+        assertNull(CareReminderScheduler.tokenFromReceiverIntent(openIntent(token)))
+        assertNull(CareReminderScheduler.tokenFromReceiverIntent(Intent(complete).setData(alarm.data)))
+        assertNull(CareReminderScheduler.tokenFromReceiverIntent(Intent(alarm).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)))
+    }
+
+    private fun openIntent(value: String) = Intent(context, MainActivity::class.java).apply {
+        action = CareReminderScheduler.ACTION_OPEN_REMINDER
+        data = Uri.parse("bitavida://care-reminder-open/$value")
+        putExtra(CareReminderScheduler.EXTRA_TOKEN, value)
+    }
+}
```

