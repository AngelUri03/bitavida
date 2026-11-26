# Ensayo aislado del esquema de la copia original

Este procedimiento está preparado, no ejecutado. Utiliza el respaldo cifrado
independiente ya validado; nunca abre ni migra la base activa, no cambia la
aplicación principal y no borra archivos. Una ejecución puede iniciar el
proceso de la app objetivo de la instrumentación; debe comprobarse aparte el
comportamiento de su inicialización.

La variante `audit` es una aplicación técnica independiente
`com.bitavida.app.audit`, sin launcher ni receptores de recordatorios.
Su instrumentación debe cargar el Room y las migraciones de ese APK, no las
de `com.bitavida.app`. La fuente confirmada tiene esquema 33 y la compilación
actual también declara 33: este ensayo comprobará apertura, contrato Room,
integridad y conservación 33→33; no demostrará rutas históricas 1→33.

## Compilación y procedencia

Con el árbol de fuentes inmóvil, guardar el estado de fuentes y ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat -PbitavidaAudit=true :app:assembleAudit :app:assembleAuditAndroidTest
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath | Out-File -Encoding utf8 build-debug-dependencies.txt
.\gradlew.bat -PbitavidaAudit=true :app:dependencies --configuration auditRuntimeClasspath | Out-File -Encoding utf8 build-audit-dependencies.txt
```

Ejecutar `tools/verify_candidate_audit_build.py` indicando ambos informes de
dependencias, las rutas locales de `aapt` y `apksigner`, y el APK de
pruebas audit. El script comprueba por SHA-256 todas las clases compiladas del
paquete de persistencia y las fuentes KSP, paquetes y certificados de los tres
APK, bibliotecas nativas SQLCipher y coordenadas Room/SQLite/SQLCipher. Si falta
algún artefacto o hay una diferencia, detener el ensayo e investigarla. Los
APK completos no deben tener el mismo hash porque sus manifests e identidades
son distintos. La igualdad de clases no demuestra igualdad del ciclo de vida
ni de permisos; las coordenadas no prueban por sí mismas el hash de todos los
JAR/AAR ni que un APK obsoleto se haya empaquetado con esas mismas clases.
Conservar los APK, hashes, informes, salida de compilación y comparación juntos.

## Preparación controlada de archivos

La operación siguiente requiere serial explícito en **cada** comando ADB; no
seleccionar un dispositivo implícito. Confirmar modelo y serial con el usuario
y fijar una variable de tarea, por ejemplo `$auditSerial`. No usar `install -r`
sobre `com.bitavida.app`: instalar solo el APK audit, su APK de pruebas y,
cuando sea necesario, el APK de pruebas separado de la app original. Comprobar
antes la firma de este último y que su test de reenvoltura solo usa APIs Android.

1. En `com.bitavida.app.audit.test`, ejecutar únicamente
   `CandidateAuditKeyTransferTest#prepareCandidateReceiver`, con
   `bitavida_analysis=prepare-candidate-receiver-v1` y
   `candidate_apk_sha256=<sha256-del-apk-audit>`. El método comprueba el hash
   del APK instalado, crea una clave privada en el Keystore del UID audit y
   publica solo su DER en
   `Android/data/com.bitavida.app.audit/files/bitavida-candidate-audit-receiver-v1/receiver-public.der`.
2. Obtener el DER público, calcular su SHA-256 y copiarlo, sin cambiar bytes,
   a `Android/data/com.bitavida.app/files/bitavida-candidate-audit-public-v1/receiver-public.der`.
   Crear únicamente ese directorio nuevo si aún no existe.
3. En `com.bitavida.app.test`, ejecutar únicamente
   `CandidateAuditKeyTransferTest#rewrapOriginalSnapshotKeyForCandidate`, con
   `bitavida_analysis=rewrap-candidate-key-v1`,
   `snapshot_sha256=<sha256-verificado-del-respaldo>` y
   `receiver_sha256=<sha256-del-DER>`. El test abre la clave de **la copia
   de retorno**, no `loadOrCreate` ni la clave de producción; reenvuelve esa
   clave en memoria y crea `candidate-key.rsa` en un directorio nuevo. No
   sobrescribe una salida anterior.
4. Transferir el `snapshot.db` cifrado verificado y solo la envoltura
   `candidate-key.rsa` a
   `Android/data/com.bitavida.app.audit/files/bitavida-candidate-audit-inbox-v1/`.
   Comprobar nuevamente SHA-256 del snapshot. No transferir claves privadas ni
   claves en claro. Si se utiliza la computadora como intermediaria, conservar
   únicamente los mismos bytes cifrados y el DER público.
5. En `com.bitavida.app.audit.test`, ejecutar únicamente
   `ReturnedSnapshotMigrationAuditTest#migrateIndependentEncryptedCopyAndCompareOriginalContents`
   con `bitavida_analysis=audit-copy-migration-v1`, `snapshot_schema=33`,
   los dos hashes anteriores y `candidate_apk_sha256`. El test copia el
   archivo cifrado a un directorio privado nuevo del UID audit, abre Room solo
   allí, fuerza el validador generado, comprueba integrity/cipher/FK y compara
   todas las tablas y columnas originales por clave primaria, incluidos BLOBs.
   El resultado mínimo sin información clínica queda junto a la copia cifrada.

No correr estos tests en lote: cada uno exige un flag explícito. Si falta una
clave, hay colisión de directorio, el archivo fuente tiene WAL/SHM, existe una
tabla original sin PK o cambia cualquier dato original, el ensayo falla y no
borra evidencia. La comparación no repara registros, no reconstruye vínculos
ausentes ni demuestra el comportamiento del visor o de la app principal.
