# Dependencia de imágenes de Observaciones

Decisión registrada el 18 de noviembre de 2026, antes de incorporar la dependencia.

## Necesidad y alternativa

La importación de fotografías necesita leer su orientación antes de generar la copia privada. Android Lint señala el uso de `android.media.ExifInterface` por los problemas de su implementación en versiones antiguas del sistema. Suprimir ese aviso conservaría el problema en dispositivos admitidos por Bitavida. Omitir la orientación produciría fotografías giradas o reflejadas; implementar un lector EXIF propio ampliaría innecesariamente el código que procesa archivos externos.

Se adopta exclusivamente `androidx.exifinterface:exifinterface:1.4.2` para leer orientación. La [referencia oficial de la clase del sistema](https://developer.android.com/reference/android/media/ExifInterface) recomienda AndroidX por su facilidad de actualización. La [página oficial de versiones](https://developer.android.com/jetpack/androidx/releases/exifinterface) identifica 1.4.2 como estable. No se adopta la rama 1.5 beta.

## Compatibilidad y licencia

La biblioteca es compatible con el mínimo API 26 de Bitavida; su línea 1.4 usa mínimo API 21. La integración conserva compileSdk 37, Kotlin, AGP y las versiones existentes. Gradle resolvió la dependencia y la compilación final, las pruebas y lint pasaron con las versiones existentes. Usa licencia Apache-2.0, declarada en el [POM publicado en Google Maven](https://dl.google.com/dl/android/maven2/androidx/exifinterface/exifinterface/1.4.2/exifinterface-1.4.2.pom). La publicación final debe conservar los avisos de terceros correspondientes.

## Tamaño y verificación

Mediciones locales con la configuración debug vigente:

| Artefacto | Bytes |
|---|---:|
| AAR ExifInterface 1.4.2 resuelto | 75 550 |
| APK antes de sustituir el lector nativo | 25 838 934 |
| APK inmediatamente después del cambio a AndroidX | 25 083 077 |
| APK final, tras mejoras de semántica de formularios | 25 458 585 |

La diferencia inmediata fue −755 857 bytes; no se atribuye como ahorro de ExifInterface: Gradle reconstruyó el grafo y reempaquetó DEX respecto del APK incremental previo. El AAR mide aproximadamente 73.8 KiB y es una medida de descarga de esa biblioteca, no su contribución comprimida al APK. El costo marginal aislado y el impacto release/AAB requieren compilaciones limpias comparables con su configuración específica; no se inventa ese valor.

Validación ejecutada conjuntamente: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Resultado final satisfactorio; el informe de lint indica `No issues found`, sin ExifInterface ni UseKtx. Se mantuvieron los límites de lectura, reducción de dimensiones y recodificación JPEG.

## Alcance y privacidad

La biblioteca solo lee orientación en memoria. Bitavida mantiene los límites de entrada y dimensiones, genera un JPEG nuevo sin metadatos y almacena únicamente esa copia limitada en SQLCipher. No conserva URI, GPS ni EXIF original; no añade permisos, red, telemetría ni exportación. El escalado usa la extensión `Bitmap.scale` de Core KTX ya presente en el proyecto.
