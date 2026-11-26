# Pruebas ficticias de respaldo y recuperación cruzada

Este procedimiento corresponde exclusivamente a herramientas de desarrollo y a
datos ficticios. No es una función de respaldo ni de restauración para la
persona cuidadora. Los ejemplos no incluyen claves ni datos personales.

Al redactar este documento, **el respaldo real y la restauración de la base
original no habían sido ejecutados ni verificados mediante estas herramientas**.
La recuperación del ejemplo en Android no demuestra que un equipo Windows
pueda abrir SQLCipher localmente ni que sea posible restaurar la instalación
original. No instalar una actualización de la aplicación ni reparar su base
basándose solo en estas pruebas.

## Qué se comprueba

- El fixture de SQLCipher crea sus propias bases, claves, filas y BLOB en un
  directorio temporal. Comprueba apertura de solo lectura, exportación cifrada,
  WAL con escritor abierto, contenido y esquema, versión, `auto_vacuum`,
  integridad y rechazo de un BLOB alterado.
- Otra prueba mantiene abierta una conexión de observación de una sola conexión
  y comprueba que `PRAGMA data_version` cambia cuando otra conexión confirma
  una escritura. No prueba un `COMMIT` en un instante concreto dentro de
  `sqlcipher_export`.
- El fixture cruzado genera una base y un archivo asociado ficticios en Android.
  El equipo recibe únicamente esos archivos cifrados y una clave simétrica
  cifrada para la clave pública del equipo. Windows recupera esa clave en
  memoria usando la clave privada protegida por DPAPI y la vuelve a cifrar para
  una clave temporal de Android Keystore. El fixture devuelto se abre y verifica
  en Android. La clave privada de Windows y la clave simétrica en claro no se
  envían al teléfono.
- El archivo asociado de prueba usa el formato de desarrollo BVA2, con fragmentos
  AES-GCM de hasta 64 KiB, longitud y número autenticados y final autenticado.
  Su inventario de nombres codificados, tamaños y SHA-256 va dentro del archivo
  cifrado. La prueba comprueba el contenido, una alteración y un truncamiento.
  Este formato no ha pasado una auditoría criptográfica independiente.

## Qué se comprobó y qué falta

El fixture de SQLCipher ejecutado en el Samsung pasó sus tres pruebas. El APK
con el archivo asociado BVA2 y la recuperación cruzada compiló, pero esas
pruebas todavía deben ejecutarse y registrar su resultado por separado.
Generar una clave de prueba en Windows funcionó; aún deben comprobarse juntos
el DER, el desenvolvimiento mediante DPAPI y el reenvío cifrado.

## Requisitos y preparación

Usar únicamente un dispositivo cuya utilización esté autorizada. Comprobar
modelo, serie, paquete y firma antes de instalar el APK de pruebas. El paquete
de pruebas debe ser compatible con la aplicación instalada. Instalar el APK de
pruebas es diferente de actualizar o desinstalar la aplicación principal, pero
la instrumentación puede detener o reiniciar su proceso e iniciar proveedores.

La serie debe proporcionarse expresamente en **cada** operación ADB. En
PowerShell, desde la raíz del proyecto:

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$serial = '<SERIE-EXACTA-AUTORIZADA>'
if ($serial -notmatch '^[A-Za-z0-9._-]+$') { throw 'Serie inválida.' }

& $adb devices -l
& $adb -s $serial shell getprop ro.product.model
& $adb -s $serial shell getprop ro.serialno
& $adb -s $serial install -r 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
```

No ejecutar la suite instrumentada completa: otras pruebas pueden tener sus
propios efectos. Ejecutar solo las clases o métodos indicados, con la serie
explícita. La prueba de respaldo real
`OriginalDatabaseSnapshotInstrumentedTest` tiene una bandera explícita y no
forma parte de este procedimiento.

En un directorio local de prueba que no exista aún, crear el par RSA:

```powershell
$work = Join-Path (Get-Location) 'build\snapshot-cross-proof'
.\tools\snapshot-recovery.ps1 -Action create -Directory $work
```

La clave privada queda en `private.dpapi`, protegida con DPAPI CurrentUser; la
pública se escribe en `public.der`. El helper no imprime claves y se niega a
sobrescribir archivos existentes. Este directorio se usa únicamente para el
fixture; no es una ubicación duradera recomendada para un respaldo real.
DPAPI depende del perfil de Windows: perderlo puede impedir la recuperación.
No subir ni copiar la clave privada al teléfono.

## Transferencia binaria de fixtures

Evitar tuberías y redirecciones de texto de PowerShell para archivos binarios.
Estas funciones envían o reciben bytes por los streams del proceso ADB y limitan
los nombres remotos a los fixtures de este documento:

```powershell
function Send-FixtureFile([string]$Local, [string]$RemoteName) {
    if ($RemoteName -notmatch '^bitavida-[a-z0-9.-]+$') { throw 'Nombre inválido.' }
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $adb
    $info.Arguments = "-s $serial exec-in run-as com.bitavida.app tee cache/$RemoteName"
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($info)
    $drain = $process.StandardOutput.BaseStream.CopyToAsync([IO.Stream]::Null)
    try {
        $input = [IO.File]::OpenRead($Local)
        try { $input.CopyTo($process.StandardInput.BaseStream) } finally { $input.Dispose() }
        $process.StandardInput.Close()
        $drain.GetAwaiter().GetResult()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw 'Falló la transferencia del fixture.' }
    } finally { $process.Dispose() }
}

function Get-FixtureFile([string]$Remote, [string]$Local) {
    if ($Remote -notmatch '^cache/bitavida-[a-z0-9./-]+$') { throw 'Ruta inválida.' }
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $adb
    $info.Arguments = "-s $serial exec-out run-as com.bitavida.app cat $Remote"
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($info)
    try {
        $output = [IO.File]::Open($Local, [IO.FileMode]::CreateNew)
        try { $process.StandardOutput.BaseStream.CopyTo($output) } finally { $output.Dispose() }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw 'Falló la extracción del fixture.' }
    } finally { $process.Dispose() }
}
```

Estas funciones no están diseñadas para extraer información real ni deben usarse
con claves sin cifrar. Si una transferencia falla, tratar el archivo local o
remoto incompleto como inválido; `CreateNew` evita sobrescribir el local.

## Ejecución sintética

1. Ejecutar las pruebas independientes de SQLCipher y del archivo asociado:

```powershell
$runner = 'com.bitavida.app.test/androidx.test.runner.AndroidJUnitRunner'
& $adb -s $serial shell am instrument -w -e class com.bitavida.app.data.local.secure.SqlCipherSnapshotFixtureTest $runner
& $adb -s $serial shell am instrument -w -e class com.bitavida.app.data.local.secure.AssociatedFilesBackupTest $runner
```

Verificar explícitamente el resultado de cada ejecución. La compilación o la
salida de instalación no sustituyen los resultados de instrumentación.

2. Enviar únicamente la clave pública ficticia y generar el fixture:

```powershell
Send-FixtureFile (Join-Path $work 'public.der') 'bitavida-snapshot-public.der'
& $adb -s $serial shell am instrument -w -e bitavida_fixture recover-v1 -e class com.bitavida.app.data.local.secure.PublicKeyRecoveryFixtureTest $runner
```

La salida está bajo `cache/bitavida-recovery-fixture-v1/` y contiene
`snapshot.db`, `recovery-key.rsa` y `associated-files.enc`, todos procedentes
del fixture. El test no sobrescribe un directorio de salida anterior. Si existe,
conservarlo o retirar únicamente ese directorio de prueba identificado antes de
repetir; no borrar caché ni datos generales de la aplicación.

3. Crear una clave receptora temporal de prueba en Android Keystore y extraer
su parte pública y el fixture cifrado:

```powershell
& $adb -s $serial shell am instrument -w -e bitavida_fixture receiver-v1 -e class 'com.bitavida.app.data.local.secure.CrossDeviceFixtureRecoveryTest#createFixtureReceiverPublicKey' $runner

Get-FixtureFile 'cache/bitavida-fixture-receiver-public.bin' (Join-Path $work 'receiver-public.bin')
Get-FixtureFile 'cache/bitavida-recovery-fixture-v1/snapshot.db' (Join-Path $work 'snapshot.db')
Get-FixtureFile 'cache/bitavida-recovery-fixture-v1/recovery-key.rsa' (Join-Path $work 'recovery-key.rsa')
Get-FixtureFile 'cache/bitavida-recovery-fixture-v1/associated-files.enc' (Join-Path $work 'associated-files.enc')
```

4. Recuperar la clave del fixture en memoria en Windows y volver a cifrarla para
la clave pública temporal del dispositivo:

```powershell
.\tools\snapshot-recovery.ps1 -Action rewrap-fixture -Directory $work -WrappedKeyFile (Join-Path $work 'recovery-key.rsa') -ReceiverPublicFile (Join-Path $work 'receiver-public.bin') -OutputFile (Join-Path $work 'fixture-rewrapped.rsa')
```

La clave privada DPAPI permanece en Windows. El resultado
`fixture-rewrapped.rsa` sigue cifrado y sirve exclusivamente para la clave
temporal del fixture.

5. Enviar de vuelta **los mismos archivos ficticios extraídos**, comparar
SHA-256 de los archivos cifrados antes y después del traslado, y ejecutar:

```powershell
Send-FixtureFile (Join-Path $work 'snapshot.db') 'bitavida-fixture-roundtrip.db'
Send-FixtureFile (Join-Path $work 'associated-files.enc') 'bitavida-fixture-associated.enc'
Send-FixtureFile (Join-Path $work 'fixture-rewrapped.rsa') 'bitavida-fixture-rewrapped.rsa'

& $adb -s $serial shell am instrument -w -e bitavida_fixture verify-returned-v1 -e class 'com.bitavida.app.data.local.secure.CrossDeviceFixtureRecoveryTest#reopenReturnedFixtureUsingHostRecoveredKey' $runner
```

El test comprueba versión, `auto_vacuum`, integridad, fila y BLOB ficticios, y
descifra y comprueba el adjunto y su inventario. Al finalizar elimina únicamente
su alias temporal de Keystore, el fichero de clave pública temporal y el fichero
de clave recifrada. Si falla antes de llegar a esa fase pueden quedar artefactos
ficticios: no borrar ningún archivo o alias que no se haya identificado como
propio de la prueba.

## Límites para una eventual copia real

- `sqlcipher_export` produce una copia lógica, no una copia byte a byte. La
  comparación de tablas, esquema y valores, la integridad y la detección de
  commits necesitan verificarse sobre cada copia; una compilación no los prueba.
- La observación de `data_version` requiere la misma conexión durante toda la
  operación. La prueba usa `OPEN_READONLY` sin activar la bandera de pool WAL;
  no equivale a bloquear todas las escrituras de la aplicación.
- El archivo asociado propuesto incluye `files`, `no_backup` y
  `shared_prefs` y excluye su propio directorio de salida. La caché, medios o
  exportaciones fuera del almacenamiento privado y cualquier almacenamiento
  externo no están incluidos. Se rechazan enlaces simbólicos. Hay que comprobar
  el inventario real y cualquier ubicación adicional antes de decir que están
  respaldados todos los archivos.
- La captura de archivos comprueba dos lecturas y sus hashes, pero no existe una
  transacción atómica conjunta entre el sistema de archivos y SQLite. Un cambio
  fuera de esa ventana puede requerir una nueva captura.
- La clave RSA del respaldo y el archivo cifrado deben conservarse juntos de
  forma segura; el sobre original de Android Keystore por sí solo no permite
  recuperación independiente. La protección DPAPI no reemplaza una estrategia de
  recuperación del perfil de Windows.
- No se ha implementado ni probado una restauración completa de la aplicación.
  No borrar, sobrescribir ni recrear la base original para simularla.
