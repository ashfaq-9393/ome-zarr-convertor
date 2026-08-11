# PowerShell script to assemble isolated Python 3.12 embedded runtime for OME-Zarr CLI
$ErrorActionPreference = "Stop"

$rootDir = $PSScriptRoot
if (-not $rootDir) { $rootDir = Get-Location }

$runtimeDir = Join-Path $rootDir "ome-zarr-runtime"
$zipUrl = "https://www.python.org/ftp/python/3.12.8/python-3.12.8-embed-amd64.zip"
$zipFile = Join-Path $rootDir "python-3.12.8-embed-amd64.zip"

Write-Host "Setting up bundled Python runtime at: $runtimeDir"

if (-not (Test-Path $zipFile)) {
    Write-Host "Downloading Python 3.12.8 embeddable zip..."
    Invoke-WebRequest -Uri $zipUrl -OutFile $zipFile -UseBasicParsing
}

if (Test-Path $runtimeDir) {
    Write-Host "Cleaning existing runtime directory..."
    Remove-Item -Path $runtimeDir -Recurse -Force
}

Write-Host "Extracting Python 3.12.8 embeddable zip..."
Expand-Archive -Path $zipFile -DestinationPath $runtimeDir -Force

# Configure python312._pth for isolated site-packages
$pthFile = Join-Path $runtimeDir "python312._pth"
if (Test-Path $pthFile) {
    Write-Host "Configuring $pthFile for isolated site-packages..."
    $pthContent = Get-Content $pthFile
    $newContent = @()
    foreach ($line in $pthContent) {
        if ($line -eq "#import site") {
            $newContent += "import site"
        } else {
            $newContent += $line
        }
    }
    if (-not ($newContent -contains "Lib\site-packages")) {
        $newContent += "Lib\site-packages"
    }
    if (-not ($newContent -contains ".")) {
        $newContent += "."
    }
    Set-Content -Path $pthFile -Value $newContent
}

# Create site-packages directory
$sitePackages = Join-Path $runtimeDir "Lib\site-packages"
if (-not (Test-Path $sitePackages)) {
    New-Item -ItemType Directory -Path $sitePackages -Force | Out-Null
}

$getPipFile = Join-Path $rootDir "get-pip.py"
if (-not (Test-Path $getPipFile)) {
    Write-Host "Downloading get-pip.py..."
    Invoke-WebRequest -Uri "https://bootstrap.pypa.io/get-pip.py" -OutFile $getPipFile -UseBasicParsing
}

$pythonExe = Join-Path $runtimeDir "python.exe"

Write-Host "Installing pip into embedded Python runtime..."
& $pythonExe $getPipFile --no-warn-script-location

Write-Host "Installing setuptools and wheel into embedded runtime..."
& $pythonExe -m pip install setuptools wheel --no-warn-script-location

Write-Host "Installing pinned ome-zarr dependencies into bundled runtime..."
& $pythonExe -m pip install "numcodecs==0.12.1" "zarr==2.18.2" "numpy==1.26.4" "ome-zarr==0.9.0" --no-warn-script-location

Write-Host "SUCCESS: Bundled Python 3.12 OME-Zarr runtime setup complete at: $runtimeDir"
