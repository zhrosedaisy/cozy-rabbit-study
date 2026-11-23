# Creates "Cozy Rabbit Study" shortcuts (with the rabbit icon) on the Desktop and in the Start menu.
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$shell = New-Object -ComObject WScript.Shell

function New-AppShortcut([string]$folder) {
    $path = Join-Path $folder "Cozy Rabbit Study.lnk"
    $lnk = $shell.CreateShortcut($path)
    $lnk.TargetPath = Join-Path $here "start.bat"
    $lnk.WorkingDirectory = $here
    $lnk.IconLocation = (Join-Path $here "icon.ico") + ",0"
    $lnk.WindowStyle = 7   # start minimized, so no black console window flashes
    $lnk.Description = "A cozy study timer with a fishing rabbit"
    $lnk.Save()
    Write-Host "Created $path"
}

New-AppShortcut ([Environment]::GetFolderPath("Desktop"))
New-AppShortcut ([Environment]::GetFolderPath("Programs"))
Write-Host ""
Write-Host "Done. Open Cozy Rabbit Study from the Desktop or from the Start menu."
