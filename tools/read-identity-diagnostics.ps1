param(
    [Parameter(Mandatory = $true)][string] $PrivateKeyFile,
    [Parameter(Mandatory = $true)][string] $WrappedKeyFile,
    [Parameter(Mandatory = $true)][string] $DiagnosticFile,
    [Parameter(Mandatory = $true)][string] $ExpectedSha256,
    [Parameter(Mandatory = $true)][string] $JavaExe
)
# Offline development reader: the secret is unwrapped in memory and passed to
# the short-lived Java process through an anonymous binary pipe, never argv,
# an environment variable, a temporary file, or console output.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security

function Clear-PrivateParameters([System.Security.Cryptography.RSAParameters] $parameters) {
    foreach ($name in @('D', 'P', 'Q', 'DP', 'DQ', 'InverseQ')) {
        $value = $parameters.$name
        if ($null -ne $value) { [Array]::Clear($value, 0, $value.Length) }
    }
}
function Unpack-Parameters([byte[]] $encoded) {
    $stream = [System.IO.MemoryStream]::new($encoded)
    $reader = [System.IO.BinaryReader]::new($stream)
    try {
        $parameters = [System.Security.Cryptography.RSAParameters]::new()
        foreach ($name in @('Modulus', 'Exponent', 'D', 'P', 'Q', 'DP', 'DQ', 'InverseQ')) {
            $length = $reader.ReadInt32()
            if ($length -le 0 -or $length -gt 1024) { throw 'Invalid key.' }
            $value = $reader.ReadBytes($length)
            if ($value.Length -ne $length) { throw 'Invalid key.' }
            $parameters.$name = $value
        }
        if ($stream.Position -ne $stream.Length) { throw 'Invalid key.' }
        return $parameters
    } finally { $reader.Dispose(); $stream.Dispose() }
}
$stage = 'entrada'
try {
    if ($ExpectedSha256 -cnotmatch '^[0-9a-f]{64}$') { throw 'Invalid hash.' }
    $source = (Resolve-Path (Join-Path $PSScriptRoot 'ReadIdentityDiagnostics.java')).Path
    $file = (Resolve-Path $DiagnosticFile).Path
    $java = (Resolve-Path $JavaExe).Path
    $stage = 'dpapi'
    $protected = [IO.File]::ReadAllBytes($PrivateKeyFile)
    $packed = [Security.Cryptography.ProtectedData]::Unprotect(
        $protected, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser
    )
    $parameters = Unpack-Parameters $packed
    $rsa = [Security.Cryptography.RSACng]::new()
    $secret = $null
    try {
        $stage = 'clave'
        $rsa.ImportParameters($parameters)
        $secret = $rsa.Decrypt(
            [IO.File]::ReadAllBytes($WrappedKeyFile),
            [Security.Cryptography.RSAEncryptionPadding]::OaepSHA256
        )
        if ($secret.Length -ne 32) { throw 'Invalid recovery key.' }
        $stage = 'proceso'
        $info = [Diagnostics.ProcessStartInfo]::new()
        $info.FileName = $java
        $info.Arguments = '"{0}" "{1}" {2}' -f $source, $file, $ExpectedSha256
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardInput = $true
        $info.RedirectStandardOutput = $true
        $info.RedirectStandardError = $true
        $process = [Diagnostics.Process]::Start($info)
        try {
            $stdout = $process.StandardOutput.ReadToEndAsync()
            $stderr = $process.StandardError.ReadToEndAsync()
            $process.StandardInput.BaseStream.Write($secret, 0, $secret.Length)
            $process.StandardInput.Close()
            $process.WaitForExit()
            $summary = $stdout.GetAwaiter().GetResult()
            $readerError = $stderr.GetAwaiter().GetResult().Trim()
            if ($process.ExitCode -ne 0 -or $summary.Length -gt 10000) {
                if ($readerError -cmatch '^Fallo del lector en etapa (entrada|clave|descifrado|lectura|agregacion)\.$') {
                    $stage = $Matches[1]
                }
                throw 'Analysis failed.'
            }
            Write-Output $summary.TrimEnd()
        } finally { $process.Dispose() }
    } finally {
        if ($null -ne $secret) { [Array]::Clear($secret, 0, $secret.Length) }
        [Array]::Clear($packed, 0, $packed.Length)
        Clear-PrivateParameters $parameters
        $rsa.Dispose()
    }
} catch {
    # Suppress detailed errors: inputs may reference sensitive material.
    Write-Error "No fue posible verificar y analizar el diagnóstico cifrado (etapa: $stage)."
    exit 1
}
