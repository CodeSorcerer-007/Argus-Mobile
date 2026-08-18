Add-Type -AssemblyName System.Drawing

$sourcePath = "e:\Argus Mobile\Argus Logo.png"
if (-not (Test-Path $sourcePath)) {
    Write-Error "Source file not found: $sourcePath"
    exit 1
}

$srcImage = [System.Drawing.Image]::FromFile($sourcePath)

function Resize-Image {
    param(
        [System.Drawing.Image]$Image,
        [int]$Width,
        [int]$Height,
        [string]$DestinationPath
    )

    $destRect = New-Object System.Drawing.Rectangle(0, 0, $Width, $Height)
    $destImage = New-Object System.Drawing.Bitmap($Width, $Height)
    $destImage.SetResolution($Image.HorizontalResolution, $Image.VerticalResolution)

    $graphics = [System.Drawing.Graphics]::FromImage($destImage)
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.DrawImage($Image, $destRect, 0, 0, $Image.Width, $Image.Height, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.Dispose()

    $dir = [System.IO.Path]::GetDirectoryName($DestinationPath)
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }

    $destImage.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $destImage.Dispose()
    Write-Host "Generated: $DestinationPath ($Width x $Height)"
}

# 1. Main High-Res Drawables
Resize-Image -Image $srcImage -Width 512 -Height 512 -DestinationPath "e:\Argus Mobile\android\app\src\main\res\drawable\argus_logo.png"
Resize-Image -Image $srcImage -Width 432 -Height 432 -DestinationPath "e:\Argus Mobile\android\app\src\main\res\drawable\ic_launcher_foreground.png"

# 2. Mipmap Icon Densities (Standard + Round)
$densities = @(
    @{ Folder = "mipmap-mdpi"; Size = 48 },
    @{ Folder = "mipmap-hdpi"; Size = 72 },
    @{ Folder = "mipmap-xhdpi"; Size = 96 },
    @{ Folder = "mipmap-xxhdpi"; Size = 144 },
    @{ Folder = "mipmap-xxxhdpi"; Size = 192 }
)

foreach ($d in $densities) {
    $folder = $d.Folder
    $size = $d.Size
    $basePath = "e:\Argus Mobile\android\app\src\main\res\$folder"

    # Remove old webp files if they exist
    Remove-Item -Path "$basePath\ic_launcher.webp" -ErrorAction SilentlyContinue
    Remove-Item -Path "$basePath\ic_launcher_round.webp" -ErrorAction SilentlyContinue

    # Generate PNG icons
    Resize-Image -Image $srcImage -Width $size -Height $size -DestinationPath "$basePath\ic_launcher.png"
    Resize-Image -Image $srcImage -Width $size -Height $size -DestinationPath "$basePath\ic_launcher_round.png"
}

# 3. Assets for README & Docs
Resize-Image -Image $srcImage -Width 256 -Height 256 -DestinationPath "e:\Argus Mobile\docs\images\argus_logo.png"

$srcImage.Dispose()
Write-Host "All icons and logos generated successfully!"
