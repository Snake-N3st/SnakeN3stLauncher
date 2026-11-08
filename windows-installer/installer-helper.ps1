<#
.SYNOPSIS
    Everything the installer needs that NSIS itself can't reliably do - invoked by installer.nsi
    via nsExec, once per action, so each call's exit code is a single clear pass/fail the .nsi can
    branch on.

.DESCRIPTION
    ALL network access lives here, not in NSIS. The original design used the bundled NSISdl
    plugin for downloads, but its own documentation says outright: "HTTPS is not supported, only
    plain HTTP" - confirmed for real, not just read, by a direct Wine test where a plain
    NSISdl::download against the real https:// site hung indefinitely. The real site is
    HTTPS-only, so NSISdl was never actually usable here. PowerShell's own
    Invoke-RestMethod/System.Net.WebClient (.NET Framework, present by default on any Windows
    8+/10/11 machine) handle TLS correctly, so all HTTP(S) - JSON endpoints and file downloads
    alike - goes through here instead. This is also why image conversion and ZIP extraction were
    already here: keeping every non-trivial operation behind ONE trust boundary (Windows' own
    PowerShell/.NET) instead of mixing in third-party NSIS plugins for some of it.

    Actions that fetch JSON write a small `key=value` manifest (one per line, UTF-8) rather than
    leaving NSIS to parse JSON itself - NSIS has no JSON support without a third-party plugin,
    but line-based `key=value` text is trivial for it to read back with plain string instructions.

    SiteInfo       Fetches /api/launcher-auth/site-info, downloads the logo+background images to
                   the given paths, writes a manifest with `name`.
    BootstrapInfo  Fetches /api/launcher-auth/releases/bootstrap/latest, writes a manifest with
                   `version`, `sha256`, `size`, `url`, and `signature_p256` (hex, empty when absent).
    JreInfo        Fetches Eclipse Temurin's "latest JRE 21 for Windows x64" API, writes a
                   manifest with `sha256`, `size`, `url`.
    Download       Downloads one file from an arbitrary URL (used for the bootstrap jar and the
                   JRE zip, once *Info above has resolved the real URL).
    Icon           PNG -> a real multi-resolution .ico (16/32/48/256) - the installer window's own
                   icon (set at runtime: the logo comes from the site, so it can't be baked into
                   the .exe at compile time) and later the desktop/Programs-and-Features shortcut
                   icon.
    Banner         PNG -> a .bmp cropped/scaled to "cover" a target width/height - the same visual
                   treatment the site's own homepage CSS (`background: ... center / cover`)
                   already gives this exact image.
    VerifySignature  Checks the ECDSA P-256 signature the site made of a bootstrap release. The statement
                   is REBUILT from -Type/-Version/-Sha256/-Size (never taken from the server as a string),
                   and checked against -PublicKey (hex, the 65-byte uncompressed point) - a key this
                   installer was built with, not one fetched from the site, which would prove only that
                   the site agrees with itself. Exit 0 valid, 10 not valid, 1 could not run.
    Unzip          Extracts the downloaded JRE archive - .NET's ZipFile class instead of a
                   third-party NSIS unzip plugin, same reasoning as everything else here.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File installer-helper.ps1 -Action SiteInfo -Url https://site/api/launcher-auth/site-info -OutputPath manifest.txt -LogoPath logo.png -BannerPath banner.png
    powershell -NoProfile -ExecutionPolicy Bypass -File installer-helper.ps1 -Action BootstrapInfo -Url https://site/api/launcher-auth/releases/bootstrap/latest -OutputPath manifest.txt
    powershell -NoProfile -ExecutionPolicy Bypass -File installer-helper.ps1 -Action JreInfo -Url https://api.adoptium.net/... -OutputPath manifest.txt
    powershell -NoProfile -ExecutionPolicy Bypass -File installer-helper.ps1 -Action Download -Url https://... -OutputPath file.bin
    powershell -NoProfile -ExecutionPolicy Bypass -File installer-helper.ps1 -Action Icon -InputPath logo.png -OutputPath logo.ico
    powershell -NoProfile -ExecutionPolicy Bypass -File installer-helper.ps1 -Action Banner -InputPath banner.png -OutputPath banner.bmp -Width 450 -Height 130
    powershell -NoProfile -ExecutionPolicy Bypass -File installer-helper.ps1 -Action Unzip -InputPath jre.zip -OutputPath C:\...\jre-21
    powershell -NoProfile -ExecutionPolicy Bypass -File installer-helper.ps1 -Action VerifySignature -Type bootstrap -Version 2.4.0 -Sha256 <64 hex> -Size 543602 -Signature <128 hex> -PublicKey <130 hex>
#>

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('SiteInfo', 'BootstrapInfo', 'JreInfo', 'Download', 'Icon', 'Banner', 'Unzip', 'VerifySignature')]
    [string]$Action,

    [string]$Url,
    [string]$InputPath,
    [string]$OutputPath,
    [string]$LogoPath,
    [string]$BannerPath,

    # Only meaningful for -Action Banner.
    [int]$Width = 0,
    [int]$Height = 0,
    [string]$Title = '',
    [string]$Subtitle = '',

    # Only meaningful for -Action VerifySignature. The statement is rebuilt from these - never taken from the
    # server as a string - so a signature only ever vouches for exactly the values the installer is about to use.
    [string]$Type = '',
    [string]$Version = '',
    [string]$Sha256 = '',
    [string]$Size = '',
    [string]$Signature = '',
    [string]$PublicKey = ''
)

$ErrorActionPreference = 'Stop'
# TLS 1.2 isn't always the default negotiated protocol on older Windows 10 builds' .NET
# Framework - forced explicitly so a fresh machine doesn't silently fail the handshake against a
# TLS-1.2-only site.
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

function Write-Manifest {
    param([string]$Path, [hashtable]$Values)
    $lines = foreach ($key in $Values.Keys) { "$key=$($Values[$key])" }
    # NOT `Set-Content -Encoding UTF8` - Windows PowerShell 5.1 writes a UTF-8 BOM with that,
    # which would land as three stray bytes at the very start of the file, corrupting the first
    # key NSIS reads back (a site name can genuinely contain non-ASCII characters, e.g. accents,
    # so plain ASCII isn't a safe alternative encoding either). UTF8Encoding($false) is UTF-8
    # without a BOM.
    [System.IO.File]::WriteAllLines($Path, [string[]]$lines, (New-Object System.Text.UTF8Encoding $false))
}

function Get-JsonFrom {
    param([string]$Uri)
    Invoke-RestMethod -Uri $Uri -UseBasicParsing
}

# Decodes strictly: lowercase or uppercase hex, even length, nothing else. `$null` for anything off-format.
function ConvertFrom-StrictHex {
    param([string]$Hex)
    # \z, not $: in .NET `$` also matches just before a trailing newline, which would let "ab`n" through.
    if ($Hex -notmatch '^(?:[0-9a-fA-F]{2})+\z') { return $null }
    $bytes = New-Object byte[] ($Hex.Length / 2)
    for ($i = 0; $i -lt $bytes.Length; $i++) { $bytes[$i] = [Convert]::ToByte($Hex.Substring(2 * $i, 2), 16) }
    , $bytes
}

# The bytes the site signs, rebuilt here byte for byte (mirror of SigningStatement.php and the Java
# SignedArtifactStatement, pinned by a shared vector): `sn3.artifact.v1`, then type, version, sha256 and size,
# joined by LF, NO trailing newline, US-ASCII. Rejects - never normalises - anything off-format, because
# "fixing" an uppercase digest would verify a statement the site never signed. `-cmatch`: case-sensitive,
# since -match is not, and the digest must be lowercase.
function New-ArtifactStatement {
    param([string]$Type, [string]$Version, [string]$Sha256, [string]$Size)
    if ($Type -cnotmatch '^(?:launcher|bootstrap|deb)\z') { return $null }
    if ($Version -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._+~-]{0,63}\z') { return $null }
    if ($Sha256 -cnotmatch '^[0-9a-f]{64}\z') { return $null }
    if ($Size -cnotmatch '^[0-9]{1,15}\z') { return $null }
    $text = "sn3.artifact.v1`n" + "type=$Type`n" + "version=$Version`n" + "sha256=$Sha256`n" + "size=$Size"
    , [System.Text.Encoding]::ASCII.GetBytes($text)
}

# ECDSA P-256 / SHA-256, in the one shape that works on Windows PowerShell 5.1 (.NET Framework 4.x):
#  - the public key is the uncompressed point 04||X||Y; .NET Framework 4.x has no ImportSubjectPublicKeyInfo
#    (that is .NET Core 3.0+), so X and Y are wrapped in a BCrypt ECCPUBLICBLOB - magic 'ECS1' (0x31534345,
#    little endian), cbKey = 32, X, Y - and imported through CngKey. The same path works on PowerShell 7;
#  - the signature is IEEE P-1363 `r||s`, 64 bytes, which is exactly what ECDsaCng.VerifyData expects (and not
#    the DER that openssl produces - the site converts).
# Returns $false for anything malformed, including a point that is not on the curve (the import throws).
function Test-EcdsaP256Signature {
    param([byte[]]$PublicPoint, [byte[]]$Message, [byte[]]$Signature)
    if ($PublicPoint.Length -ne 65 -or $PublicPoint[0] -ne 4 -or $Signature.Length -ne 64) { return $false }
    $blob = New-Object System.Collections.Generic.List[byte]
    $blob.AddRange([System.BitConverter]::GetBytes([uint32]0x31534345))
    $blob.AddRange([System.BitConverter]::GetBytes([uint32]32))
    $blob.AddRange([byte[]]($PublicPoint[1..64]))
    try {
        $key = [System.Security.Cryptography.CngKey]::Import($blob.ToArray(), [System.Security.Cryptography.CngKeyBlobFormat]::EccPublicBlob)
    } catch {
        return $false
    }
    try {
        $ecdsa = New-Object System.Security.Cryptography.ECDsaCng($key)
        try { return $ecdsa.VerifyData($Message, $Signature) } finally { $ecdsa.Dispose() }
    } catch {
        return $false
    } finally {
        $key.Dispose()
    }
}

function Save-File {
    param([string]$Uri, [string]$Destination)
    $client = New-Object System.Net.WebClient
    try {
        $client.DownloadFile($Uri, $Destination)
    } finally {
        $client.Dispose()
    }
}

function ConvertTo-Icon {
    param([string]$SourcePng, [string]$DestIco, [int[]]$Sizes = @(16, 32, 48, 256))

    Add-Type -AssemblyName System.Drawing
    $source = [System.Drawing.Image]::FromFile((Resolve-Path $SourcePng))
    try {
        $pngBytesBySize = [System.Collections.Generic.List[byte[]]]::new()

        foreach ($size in $Sizes) {
            $bitmap = New-Object System.Drawing.Bitmap $size, $size
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.DrawImage($source, 0, 0, $size, $size)
            } finally {
                $graphics.Dispose()
            }

            $stream = New-Object System.IO.MemoryStream
            $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
            $pngBytesBySize.Add($stream.ToArray())
            $stream.Dispose()
            $bitmap.Dispose()
        }

        # .NET has no "build a multi-res .ico" API, so the ICO container (a 6-byte ICONDIR header
        # plus one 16-byte ICONDIRENTRY per size) is written by hand here - but each entry's image
        # data is itself just a normal PNG (Windows Vista+ accepts PNG-encoded entries inside a
        # .ico directly), so only the outer container is custom, not the pixels underneath.
        $writer = New-Object System.IO.BinaryWriter([System.IO.File]::Create($DestIco))
        try {
            $writer.Write([UInt16]0)
            $writer.Write([UInt16]1)
            $writer.Write([UInt16]$Sizes.Count)

            $dataOffset = 6 + (16 * $Sizes.Count)

            for ($i = 0; $i -lt $Sizes.Count; $i++) {
                $size = $Sizes[$i]
                $pngBytes = $pngBytesBySize[$i]
                $dim = if ($size -eq 256) { 0 } else { $size } # 0 means 256 per the ICO format
                $writer.Write([byte]$dim)
                $writer.Write([byte]$dim)
                $writer.Write([byte]0)
                $writer.Write([byte]0)
                $writer.Write([UInt16]1)
                $writer.Write([UInt16]32)
                $writer.Write([UInt32]$pngBytes.Length)
                $writer.Write([UInt32]$dataOffset)
                $dataOffset += $pngBytes.Length
            }

            foreach ($pngBytes in $pngBytesBySize) {
                $writer.Write($pngBytes)
            }
        } finally {
            $writer.Close()
        }
    } finally {
        $source.Dispose()
    }
}

function ConvertTo-BannerBitmap {
    param([string]$SourcePng, [string]$DestBmp, [int]$TargetWidth, [int]$TargetHeight, [string]$Title = '', [string]$Subtitle = '')

    Add-Type -AssemblyName System.Drawing
    $source = [System.Drawing.Image]::FromFile((Resolve-Path $SourcePng))
    try {
        $scale = [Math]::Max($TargetWidth / $source.Width, $TargetHeight / $source.Height)
        $scaledWidth = [Math]::Ceiling($source.Width * $scale)
        $scaledHeight = [Math]::Ceiling($source.Height * $scale)
        $offsetX = [Math]::Floor(($TargetWidth - $scaledWidth) / 2)
        $offsetY = [Math]::Floor(($TargetHeight - $scaledHeight) / 2)

        $bitmap = New-Object System.Drawing.Bitmap $TargetWidth, $TargetHeight
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $graphics.DrawImage($source, $offsetX, $offsetY, $scaledWidth, $scaledHeight)

            # Title text is baked directly into the bitmap here rather than left as a separate
            # nsDialogs label overlaid on top of it - a real-machine test showed a "transparent"
            # nsDialogs static control never actually composites over a sibling bitmap control
            # (that needs WS_EX_TRANSPARENT handling nsDialogs doesn't give it), so the text was
            # simply invisible. Baking it into the image itself sidesteps that entirely.
            if ($Title) {
                $scrimHeight = [Math]::Ceiling($TargetHeight * 0.45)
                $scrimRect = New-Object System.Drawing.Rectangle 0, ($TargetHeight - $scrimHeight), $TargetWidth, $scrimHeight
                $scrimBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
                    $scrimRect,
                    [System.Drawing.Color]::FromArgb(0, 0, 0, 0),
                    [System.Drawing.Color]::FromArgb(160, 0, 0, 0),
                    [System.Drawing.Drawing2D.LinearGradientMode]::Vertical)
                try {
                    $graphics.FillRectangle($scrimBrush, $scrimRect)
                } finally {
                    $scrimBrush.Dispose()
                }

                $titleFont = New-Object System.Drawing.Font('Segoe UI', 16, [System.Drawing.FontStyle]::Bold)
                $subtitleFont = New-Object System.Drawing.Font('Segoe UI', 10, [System.Drawing.FontStyle]::Regular)
                $whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
                try {
                    $textX = 16
                    $titleY = $TargetHeight - $scrimHeight + 10
                    $graphics.DrawString($Title, $titleFont, $whiteBrush, $textX, $titleY)
                    if ($Subtitle) {
                        $titleHeight = $graphics.MeasureString($Title, $titleFont).Height
                        $graphics.DrawString($Subtitle, $subtitleFont, $whiteBrush, $textX, $titleY + $titleHeight + 2)
                    }
                } finally {
                    $titleFont.Dispose()
                    $subtitleFont.Dispose()
                    $whiteBrush.Dispose()
                }
            }
        } finally {
            $graphics.Dispose()
        }

        # Plain 24bpp BMP - matches what nsDialogs' ${NSD_CreateBitmap}/STM_SETIMAGE expects, no
        # palette/alpha complications for an opaque banner.
        $bitmap.Save($DestBmp, [System.Drawing.Imaging.ImageFormat]::Bmp)
        $bitmap.Dispose()
    } finally {
        $source.Dispose()
    }
}

function Expand-DownloadedZip {
    param([string]$ZipPath, [string]$DestDir)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (Test-Path $DestDir) {
        Remove-Item -Recurse -Force $DestDir
    }
    [System.IO.Compression.ZipFile]::ExtractToDirectory((Resolve-Path $ZipPath), $DestDir)
}

try {

switch ($Action) {
    'SiteInfo' {
        $info = Get-JsonFrom -Uri $Url
        if ($LogoPath) { Save-File -Uri $info.logo -Destination $LogoPath }
        if ($BannerPath) { Save-File -Uri $info.background -Destination $BannerPath }
        Write-Manifest -Path $OutputPath -Values @{ name = $info.name }
    }
    'BootstrapInfo' {
        $release = Get-JsonFrom -Uri $Url
        # `signature_p256` is empty when the site does not sign (or has no P-256 key yet): the caller decides
        # what that means - see installer.nsi, which knows whether this build pins a key.
        $p256 = ''
        if ($release.signature_p256 -and $release.signature_p256.alg -ceq 'ecdsa-p256') { $p256 = [string]$release.signature_p256.value }
        Write-Manifest -Path $OutputPath -Values @{
            version        = $release.version
            sha256         = $release.sha256
            size           = $release.size
            url            = $release.download_url
            signature_p256 = $p256
        }
    }
    'VerifySignature' {
        # Exit 0 = valid. Exit 10 = not valid (wrong, malformed, or made for other values) - never conflated with
        # exit 1, which the catch below reserves for "could not run". The caller treats both as a refusal, but a
        # log reader can tell a tampered bootstrap from a broken machine.
        $statement = New-ArtifactStatement -Type $Type -Version $Version -Sha256 $Sha256 -Size $Size
        $publicPoint = ConvertFrom-StrictHex -Hex $PublicKey
        $raw = ConvertFrom-StrictHex -Hex $Signature

        if ($null -eq $statement -or $null -eq $publicPoint -or $null -eq $raw -or -not (Test-EcdsaP256Signature -PublicPoint $publicPoint -Message $statement -Signature $raw)) {
            # Not Write-Error: under $ErrorActionPreference = 'Stop' that throws, and the catch below would turn
            # this verdict into exit 1 ("could not run"), which is exactly the distinction exit 10 exists for.
            [Console]::Error.WriteLine('The signature does not match.')
            exit 10
        }
    }
    'JreInfo' {
        # Adoptium's "assets/latest" endpoint returns an array - the first (only, for this
        # feature/os/arch/vendor combination) entry is the release actually wanted.
        $release = (Get-JsonFrom -Uri $Url)[0]
        Write-Manifest -Path $OutputPath -Values @{
            sha256 = $release.binary.package.checksum
            size   = $release.binary.package.size
            url    = $release.binary.package.link
        }
    }
    'Download' {
        Save-File -Uri $Url -Destination $OutputPath
    }
    'Icon' {
        ConvertTo-Icon -SourcePng $InputPath -DestIco $OutputPath
    }
    'Banner' {
        if ($Width -le 0 -or $Height -le 0) {
            throw "-Width and -Height are required (and must be positive) for -Action Banner."
        }
        ConvertTo-BannerBitmap -SourcePng $InputPath -DestBmp $OutputPath -TargetWidth $Width -TargetHeight $Height -Title $Title -Subtitle $Subtitle
    }
    'Unzip' {
        Expand-DownloadedZip -ZipPath $InputPath -DestDir $OutputPath
    }
}

} catch {
    # Explicit, rather than relying on PowerShell's own (version-inconsistent) default exit code
    # for an uncaught terminating error - nsExec::ExecToLog on the NSIS side checks this exit code
    # strictly, so it has to be unambiguous.
    Write-Error $_.Exception.Message
    exit 1
}

exit 0
