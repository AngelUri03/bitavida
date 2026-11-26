param(
    [Parameter(Mandatory = $true)][string] $InputDer,
    [Parameter(Mandatory = $true)][string] $OutputBin
)
# Converts only an RSA public SubjectPublicKeyInfo DER to the fixture receiver
# format. No private or symmetric key is read, printed, or written.
$ErrorActionPreference = 'Stop'

function Read-Node([byte[]] $data, [ref] $offset, [byte] $tag) {
    if ($offset.Value + 2 -gt $data.Length -or $data[$offset.Value] -ne $tag) {
        throw 'Invalid public DER.'
    }
    $offset.Value += 1
    $first = [int]$data[$offset.Value]
    $offset.Value += 1
    if ($first -lt 128) {
        $length = $first
    } else {
        $count = $first -band 127
        if ($count -lt 1 -or $count -gt 4 -or $offset.Value + $count -gt $data.Length) {
            throw 'Invalid public DER.'
        }
        $length = 0
        for ($i = 0; $i -lt $count; $i++) {
            $length = ($length -shl 8) -bor $data[$offset.Value]
            $offset.Value += 1
        }
    }
    if ($length -lt 1 -or $offset.Value + $length -gt $data.Length) {
        throw 'Invalid public DER.'
    }
    $value = [byte[]]::new($length)
    [Array]::Copy($data, $offset.Value, $value, 0, $length)
    $offset.Value += $length
    return ,$value
}

function Write-BigEndianInt([System.IO.BinaryWriter] $writer, [int] $value) {
    $encoded = [BitConverter]::GetBytes($value)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($encoded) }
    $writer.Write($encoded)
}

try {
    $inputBytes = [IO.File]::ReadAllBytes($InputDer)
    if ($inputBytes.Length -lt 200 -or $inputBytes.Length -gt 8192) { throw 'Invalid public DER.' }
    $offset = 0
    $outer = Read-Node $inputBytes ([ref]$offset) 0x30
    if ($offset -ne $inputBytes.Length) { throw 'Invalid public DER.' }
    $offset = 0
    $algorithm = Read-Node $outer ([ref]$offset) 0x30
    $expected = [byte[]]@(6,9,42,134,72,134,247,13,1,1,1,5,0)
    if ([Convert]::ToBase64String($algorithm) -ne [Convert]::ToBase64String($expected)) {
        throw 'Invalid public DER.'
    }
    $bitString = Read-Node $outer ([ref]$offset) 0x03
    if ($offset -ne $outer.Length -or $bitString[0] -ne 0) { throw 'Invalid public DER.' }
    $rsaBytes = [byte[]]::new($bitString.Length - 1)
    [Array]::Copy($bitString, 1, $rsaBytes, 0, $rsaBytes.Length)
    $offset = 0
    $sequence = Read-Node $rsaBytes ([ref]$offset) 0x30
    if ($offset -ne $rsaBytes.Length) { throw 'Invalid public DER.' }
    $offset = 0
    $modulus = Read-Node $sequence ([ref]$offset) 0x02
    $exponent = Read-Node $sequence ([ref]$offset) 0x02
    if ($offset -ne $sequence.Length -or ($modulus[0] -band 0x80) -ne 0 -or ($exponent[0] -band 0x80) -ne 0) {
        throw 'Invalid public DER.'
    }
    if ($modulus.Length -gt 1 -and $modulus[0] -eq 0) { $modulus = $modulus[1..($modulus.Length - 1)] }
    if ($exponent.Length -gt 1 -and $exponent[0] -eq 0) { $exponent = $exponent[1..($exponent.Length - 1)] }
    if ($modulus.Length -lt 256 -or $modulus.Length -gt 1024 -or $exponent.Length -lt 1 -or $exponent.Length -gt 8) {
        throw 'Invalid public DER.'
    }
    $stream = [IO.File]::Open($OutputBin, [IO.FileMode]::CreateNew)
    $writer = [IO.BinaryWriter]::new($stream)
    try {
        Write-BigEndianInt $writer $modulus.Length
        $writer.Write([byte[]]$modulus)
        Write-BigEndianInt $writer $exponent.Length
        $writer.Write([byte[]]$exponent)
        $writer.Flush()
        $stream.Flush($true)
    } finally { $writer.Dispose(); $stream.Dispose() }
    Write-Output 'Public receiver key converted.'
} catch {
    Write-Error 'Public receiver conversion failed.'
    exit 1
}
