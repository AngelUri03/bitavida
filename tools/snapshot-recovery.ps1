param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('create', 'rewrap-fixture')]
    [string] $Action,
    [Parameter(Mandatory = $true)]
    [string] $Directory,
    [string] $WrappedKeyFile,
    [string] $ReceiverPublicFile,
    [string] $OutputFile
)

# Local-only development helper. Never print key material and never send the
# protected private key to Android. DPAPI CurrentUser ties recovery to this
# Windows user profile; retain a separately protected recovery method if needed.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security

function Join-Bytes([byte[][]] $parts) {
    $stream = [System.IO.MemoryStream]::new()
    try {
        foreach ($part in $parts) { $stream.Write($part, 0, $part.Length) }
        return ,$stream.ToArray()
    } finally { $stream.Dispose() }
}

function Der-Length([int] $length) {
    if ($length -lt 128) { return ,[byte[]]@($length) }
    $result = [System.Collections.Generic.List[byte]]::new()
    while ($length -gt 0) { $result.Insert(0, [byte]($length -band 255)); $length = $length -shr 8 }
    return ,([byte[]]@([byte](128 + $result.Count)) + $result.ToArray())
}

function Der-Tlv([byte] $tag, [byte[]] $payload) {
    return ,(Join-Bytes @([byte[]]@($tag), (Der-Length $payload.Length), $payload))
}

function Der-Integer([byte[]] $value) {
    if (($value[0] -band 128) -ne 0) { $value = [byte[]]@([byte]0) + $value }
    return ,(Der-Tlv 2 $value)
}

function Public-Spki([System.Security.Cryptography.RSAParameters] $parameters) {
    $rsa = Der-Tlv 48 (Join-Bytes @((Der-Integer $parameters.Modulus), (Der-Integer $parameters.Exponent)))
    $bits = Der-Tlv 3 ([byte[]]@([byte]0) + $rsa)
    $algorithm = [byte[]]@(48,13,6,9,42,134,72,134,247,13,1,1,1,5,0)
    return ,(Der-Tlv 48 (Join-Bytes @($algorithm, $bits)))
}

function Clear-PrivateParameters([System.Security.Cryptography.RSAParameters] $parameters) {
    foreach ($name in @('D', 'P', 'Q', 'DP', 'DQ', 'InverseQ')) {
        $value = $parameters.$name
        if ($null -ne $value) { [Array]::Clear($value, 0, $value.Length) }
    }
}

function Pack-Parameters([System.Security.Cryptography.RSAParameters] $parameters) {
    $stream = [System.IO.MemoryStream]::new()
    $writer = [System.IO.BinaryWriter]::new($stream)
    try {
        foreach ($name in @('Modulus', 'Exponent', 'D', 'P', 'Q', 'DP', 'DQ', 'InverseQ')) {
            [byte[]] $value = $parameters.$name
            $writer.Write([int]$value.Length)
            $writer.Write($value)
        }
        $writer.Flush()
        return ,$stream.ToArray()
    } finally { $writer.Dispose(); $stream.Dispose() }
}

function Unpack-Parameters([byte[]] $encoded) {
    $stream = [System.IO.MemoryStream]::new($encoded)
    $reader = [System.IO.BinaryReader]::new($stream)
    try {
        $parameters = [System.Security.Cryptography.RSAParameters]::new()
        foreach ($name in @('Modulus', 'Exponent', 'D', 'P', 'Q', 'DP', 'DQ', 'InverseQ')) {
            $length = $reader.ReadInt32()
            if ($length -le 0 -or $length -gt 1024) { throw 'Invalid protected key.' }
            $value = $reader.ReadBytes($length)
            if ($value.Length -ne $length) { throw 'Invalid protected key.' }
            $parameters.$name = $value
        }
        if ($stream.Position -ne $stream.Length) { throw 'Invalid protected key.' }
        return $parameters
    } finally { $reader.Dispose(); $stream.Dispose() }
}

function Read-BigEndianInt([byte[]] $bytes, [ref] $position) {
    if ($position.Value + 4 -gt $bytes.Length) { throw 'Invalid public key.' }
    $n = 0
    for ($i = 0; $i -lt 4; $i++) {
        $n = ($n -shl 8) -bor $bytes[$position.Value]
        $position.Value = $position.Value + 1
    }
    return $n
}

try {
    $privatePath = Join-Path $Directory 'private.dpapi'
    $publicPath = Join-Path $Directory 'public.der'
    if ($Action -eq 'create') {
        if ((Test-Path $privatePath) -or (Test-Path $publicPath)) { throw 'Key files already exist.' }
        [void][System.IO.Directory]::CreateDirectory($Directory)
        $rsa = [System.Security.Cryptography.RSACng]::new(3072)
        $parameters = $rsa.ExportParameters($true)
        $packed = $null
        try {
            $public = Public-Spki $parameters
            $packed = Pack-Parameters $parameters
            $protected = [System.Security.Cryptography.ProtectedData]::Protect(
                $packed, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser
            )
            # CreateNew prevents overwriting an earlier recovery key.
            foreach ($item in @(@($privatePath, $protected), @($publicPath, $public))) {
                $stream = [System.IO.File]::Open($item[0], [System.IO.FileMode]::CreateNew)
                try { $stream.Write($item[1], 0, $item[1].Length); $stream.Flush($true) }
                finally { $stream.Dispose() }
            }
        } finally {
            if ($null -ne $packed) { [Array]::Clear($packed, 0, $packed.Length) }
            Clear-PrivateParameters $parameters
            $rsa.Dispose()
        }
        Write-Output 'Protected local recovery key and public key created.'
    } else {
        if (-not $WrappedKeyFile -or -not $ReceiverPublicFile -or -not $OutputFile) {
            throw 'Fixture input files and output are required.'
        }
        $protected = [System.IO.File]::ReadAllBytes($privatePath)
        $packed = [System.Security.Cryptography.ProtectedData]::Unprotect(
            $protected, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser
        )
        $parameters = Unpack-Parameters $packed
        $rsa = [System.Security.Cryptography.RSACng]::new()
        $receiver = [System.Security.Cryptography.RSACng]::new()
        $secret = $null
        try {
            $rsa.ImportParameters($parameters)
            $secret = $rsa.Decrypt(
                [System.IO.File]::ReadAllBytes($WrappedKeyFile),
                [System.Security.Cryptography.RSAEncryptionPadding]::OaepSHA256
            )
            if ($secret.Length -ne 32) { throw 'Invalid recovery key.' }
            $public = [System.IO.File]::ReadAllBytes($ReceiverPublicFile)
            $position = 0
            $modulusLength = Read-BigEndianInt $public ([ref]$position)
            if ($modulusLength -lt 256 -or $modulusLength -gt 1024 -or $position + $modulusLength -gt $public.Length) {
                throw 'Invalid receiver public key.'
            }
            $receiverParameters = [System.Security.Cryptography.RSAParameters]::new()
            $receiverParameters.Modulus = $public[$position..($position + $modulusLength - 1)]
            $position += $modulusLength
            $exponentLength = Read-BigEndianInt $public ([ref]$position)
            if ($exponentLength -lt 1 -or $exponentLength -gt 8 -or $position + $exponentLength -ne $public.Length) {
                throw 'Invalid receiver public key.'
            }
            $receiverParameters.Exponent = $public[$position..($position + $exponentLength - 1)]
            $receiver.ImportParameters($receiverParameters)
            $result = $receiver.Encrypt($secret, [System.Security.Cryptography.RSAEncryptionPadding]::Pkcs1)
            $stream = [System.IO.File]::Open($OutputFile, [System.IO.FileMode]::CreateNew)
            try { $stream.Write($result, 0, $result.Length); $stream.Flush($true) }
            finally { $stream.Dispose() }
        } finally {
            if ($null -ne $secret) { [Array]::Clear($secret, 0, $secret.Length) }
            [Array]::Clear($packed, 0, $packed.Length)
            Clear-PrivateParameters $parameters
            $rsa.Dispose()
            $receiver.Dispose()
        }
        Write-Output 'Fixture recovery key rewrapped for the Android test key.'
    }
} catch {
    # Do not render nested crypto errors or data.
    Write-Error 'Recovery helper failed; no secret values were printed.'
    exit 1
}
