$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Remove-Item (Join-Path $root 'Libraries\libraries.zip') -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $root 'ci\libraries.zip') -Force -ErrorAction SilentlyContinue

# Important: zip the Libraries folder itself, not Libraries\*
Compress-Archive -Path (Join-Path $root 'Libraries') -DestinationPath (Join-Path $root 'ci\libraries.zip')

$zip = Get-Item (Join-Path $root 'ci\libraries.zip')
Write-Host "Created ci/libraries.zip ($([math]::Round($zip.Length / 1MB, 2)) MB)"
Write-Host "Upload this file to GitLab Secure Files as libraries.zip"
