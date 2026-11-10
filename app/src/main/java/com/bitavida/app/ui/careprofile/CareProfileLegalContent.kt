package com.bitavida.app.ui.careprofile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors

private const val LegalVersion = "Versión 2026.11 · Actualizada el 9 de noviembre de 2026"

private enum class CareProfileLegalDocumentType {
    TERMS,
    PRIVACY_NOTICE,
}

private data class LegalSection(
    val title: String,
    val body: String,
)

private data class CareProfileLegalDocument(
    val title: String,
    val version: String,
    val introduction: String,
    val sections: List<LegalSection>,
)

private val TermsDocument = CareProfileLegalDocument(
    title = "Términos y condiciones de uso",
    version = LegalVersion,
    introduction = "Estos términos delimitan el uso responsable de Bitavida y el alcance real de " +
        "las funciones disponibles en la aplicación.",
    sections = listOf(
        LegalSection(
            "1. Qué es Bitavida",
            "Bitavida es una herramienta local para organizar información cotidiana de cuidado, " +
                "preparar conversaciones con profesionales y mantener referencias declaradas por " +
                "la persona cuidadora.",
        ),
        LegalSection(
            "2. Límites sanitarios",
            "Bitavida no diagnostica enfermedades, no interpreta mediciones clínicamente, no " +
                "calcula ni recomienda dosis, no prescribe ni modifica tratamientos y no sustituye " +
                "la atención de profesionales de la salud. No garantiza resultados de salud.",
        ),
        LegalSection(
            "3. Emergencias",
            "Bitavida no es un servicio de emergencias. Ante una urgencia debe utilizarse el canal " +
                "de atención oficial o profesional que corresponda; la aplicación no debe usarse " +
                "para solicitar auxilio inmediato.",
        ),
        LegalSection(
            "4. Registros y notificaciones",
            "Los registros corresponden a declaraciones de la persona usuaria. Una notificación " +
                "enviada, ignorada o descartada no acredita que una indicación haya sido cumplida " +
                "u omitida.",
        ),
        LegalSection(
            "5. Exactitud y origen",
            "La persona usuaria debe revisar la exactitud, vigencia y procedencia de lo capturado y " +
                "conservar como referencia la indicación original. Bitavida no valida por sí misma " +
                "la identidad, representación legal ni autoridad clínica de quien introduce datos.",
        ),
        LegalSection(
            "6. Información de otras personas",
            "No debe registrarse información de una persona distinta sin contar con la autorización " +
                "o base jurídica aplicable. Una declaración del cuidador no sustituye la acreditación " +
                "de representación ni el consentimiento de la persona titular cuando sean exigibles.",
        ),
        LegalSection(
            "7. Seguridad y uso del dispositivo",
            "La persona usuaria debe proteger el acceso físico a su dispositivo y mantener sus " +
                "credenciales seguras. La protección local reduce riesgos, pero ningún mecanismo " +
                "elimina por completo la posibilidad de pérdida, acceso indebido o fallo técnico.",
        ),
        LegalSection(
            "8. Propiedad intelectual",
            "La identidad, textos y componentes propios de Bitavida se protegen conforme a la " +
                "legislación aplicable. Las bibliotecas y tipografías de terceros conservan sus " +
                "respectivas licencias; dichos avisos deberán permanecer disponibles.",
        ),
        LegalSection(
            "9. Uso permitido y cambios",
            "No se permite utilizar Bitavida para vulnerar derechos, alterar controles de seguridad " +
                "o tratar información de forma ilícita. Las funciones y estos términos podrán " +
                "actualizarse con aviso claro y versión identificable, sin reducir derechos que la " +
                "ley reconozca.",
        ),
        LegalSection(
            "10. Contacto y reclamaciones",
            "Esta versión no habilita tratamiento persistente ni un canal externo de reclamaciones. " +
                "Antes de activar esas funciones, Bitavida mostrará la identidad, el domicilio y los " +
                "medios efectivos del responsable dentro de estos documentos.",
        ),
    ),
)

private val PrivacyDocument = CareProfileLegalDocument(
    title = "Aviso de privacidad integral",
    version = LegalVersion,
    introduction = "Este aviso considera la Ley Federal de Protección de Datos Personales en " +
        "Posesión de los Particulares, publicada el 20-03-2025 y reformada el 14-11-2025.",
    sections = listOf(
        LegalSection(
            "1. Responsable",
            "Bitavida no conserva datos personales de forma persistente en esta versión. Antes de " +
                "habilitar esa función, este aviso identificará al responsable y su domicilio; hasta " +
                "entonces no se utilizará para obtener consentimiento sobre datos persistentes.",
        ),
        LegalSection(
            "2. Datos tratados",
            "El flujo actual solicita nombres para identificar a la persona cuidadora y a la persona " +
                "atendida, además de una declaración de autorización y el estado del consentimiento. " +
                "Bitavida no solicita CURP, NSS ni datos del IMSS en este flujo.",
        ),
        LegalSection(
            "3. Datos sensibles",
            "La información sobre salud presente o futura es un dato personal sensible. Su captura " +
                "permanecerá deshabilitada hasta contar con finalidad justificada, acceso seguro, " +
                "persistencia protegida y un mecanismo separado de consentimiento expreso y por escrito.",
        ),
        LegalSection(
            "4. Finalidades",
            "La finalidad necesaria del perfil es identificar localmente la relación de cuidado y " +
                "personalizar la organización dentro de la aplicación. No existen finalidades " +
                "secundarias aprobadas, publicidad, analítica ni elaboración de perfiles comerciales.",
        ),
        LegalSection(
            "5. Almacenamiento actual",
            "El perfil se mantiene únicamente en memoria durante la ejecución de la aplicación. " +
                "No está conectado al almacenamiento cifrado y no se presenta como conservación " +
                "definitiva de datos.",
        ),
        LegalSection(
            "6. Protección y límites",
            "Bitavida opera sin permiso de Internet, excluye copias automáticas y no registra el " +
                "contenido del perfil en logs. La infraestructura cifrada existente aún no se usa " +
                "para estos datos. Las medidas reducen riesgos, pero no constituyen una certificación.",
        ),
        LegalSection(
            "7. Transferencias, exportación y compartición",
            "No se realizan transferencias ni envíos a servicios externos en el alcance actual. Una " +
                "exportación futura requerirá una acción expresa, advertencia de sensibilidad y un " +
                "destino elegido por la persona usuaria.",
        ),
        LegalSection(
            "8. Conservación y supresión",
            "El estado en memoria termina con el proceso de la aplicación, por lo que esta versión no " +
                "aplica un periodo de conservación persistente. Antes de habilitar almacenamiento se " +
                "publicarán sus criterios de conservación, bloqueo, supresión y eliminación.",
        ),
        LegalSection(
            "9. Derechos ARCO y revocación",
            "La persona titular o su representante acreditado podrá ejercer acceso, rectificación, " +
                "cancelación u oposición y revocar el consentimiento cuando correspondan. Antes de " +
                "conservar datos, Bitavida mostrará el canal y procedimiento efectivos para solicitarlos.",
        ),
        LegalSection(
            "10. Consentimientos diferenciados",
            "La aceptación de términos, el reconocimiento de este aviso, la declaración de autorización " +
                "del cuidador y el consentimiento para datos sensibles son acciones distintas. Ninguna " +
                "casilla estará premarcada ni la declaración del cuidador acreditará automáticamente " +
                "representación legal.",
        ),
        LegalSection(
            "11. Cambios al aviso",
            "Toda modificación deberá mostrar una nueva versión y un medio efectivo para consultarla. " +
                "Cuando el cambio altere finalidades o bases del tratamiento, se solicitará la decisión " +
                "que corresponda antes de continuar.",
        ),
        LegalSection(
            "12. Contacto de privacidad",
            "Esta versión no habilita tratamiento persistente ni solicitudes externas de privacidad. " +
                "El canal efectivo se incorporará antes de conservar datos; este aviso no habilita por " +
                "sí mismo el tratamiento de información de salud.",
        ),
    ),
)

@Composable
internal fun CareProfileLegalAccess(
    termsAccepted: Boolean,
    privacyNoticeAcknowledged: Boolean,
    termsMissing: Boolean,
    privacyNoticeMissing: Boolean,
    onTermsAcceptanceChanged: (Boolean) -> Unit,
    onPrivacyNoticeAcknowledgementChanged: (Boolean) -> Unit,
    motionAllowed: Boolean,
) {
    var openDocument by rememberSaveable {
        mutableStateOf<CareProfileLegalDocumentType?>(null)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(BitavidaSpacing.Default),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Text(
                text = "Documentos legales",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Consulta cada documento antes de decidir. Estas decisiones son distintas " +
                    "de tu declaración como persona cuidadora.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LegalDecisionToggle(
                checked = termsAccepted,
                label = "Acepto los Términos y Condiciones de Uso, versión 2026.11.",
                error = if (termsMissing) {
                    "Acepta los términos de uso para continuar."
                } else {
                    null
                },
                onCheckedChange = onTermsAcceptanceChanged,
                motionAllowed = motionAllowed,
            )
            TextButton(
                onClick = { openDocument = CareProfileLegalDocumentType.TERMS },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            ) {
                Text("Consultar términos completos")
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            LegalDecisionToggle(
                checked = privacyNoticeAcknowledged,
                label = "Reconozco que pude consultar el Aviso de Privacidad, versión 2026.11.",
                error = if (privacyNoticeMissing) {
                    "Reconoce el aviso de privacidad para continuar."
                } else {
                    null
                },
                onCheckedChange = onPrivacyNoticeAcknowledgementChanged,
                motionAllowed = motionAllowed,
            )
            TextButton(
                onClick = {
                    openDocument = CareProfileLegalDocumentType.PRIVACY_NOTICE
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            ) {
                Text("Consultar aviso completo")
            }
            Text(
                text = "Estas selecciones permanecen en memoria durante esta sesión. No equivalen " +
                    "al consentimiento para tratar datos de salud, que se solicitará por separado " +
                    "antes de habilitar su captura.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    openDocument?.let { documentType ->
        CareProfileLegalDocumentSheet(
            document = when (documentType) {
                CareProfileLegalDocumentType.TERMS -> TermsDocument
                CareProfileLegalDocumentType.PRIVACY_NOTICE -> PrivacyDocument
            },
            onDismiss = { openDocument = null },
        )
    }
}

@Composable
private fun LegalDecisionToggle(
    checked: Boolean,
    label: String,
    error: String?,
    onCheckedChange: (Boolean) -> Unit,
    motionAllowed: Boolean,
) {
    val shape = MaterialTheme.shapes.medium
    val duration = if (motionAllowed) BitavidaFluidMotion.Selection else 0
    Column(
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                .clip(shape)
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                ),
            shape = shape,
            color = if (checked) {
                MaterialTheme.bitavidaColors.mint
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                width = 1.dp,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(BitavidaSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
        ) {
            Text(
                text = error.orEmpty(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareProfileLegalDocumentSheet(
    document: CareProfileLegalDocument,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = BitavidaSpacing.ScreenHorizontal,
                    end = BitavidaSpacing.ScreenHorizontal,
                    bottom = BitavidaSpacing.ExtraLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
        ) {
            Text(
                text = document.title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = document.version,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = document.introduction,
                style = MaterialTheme.typography.bodyLarge,
            )
            document.sections.forEach { section ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            ) {
                Text("Cerrar documento")
            }
        }
    }
}
