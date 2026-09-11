# Spotify Sync - Windows System Media Transport Controls bridge.
#
# Reads (and optionally controls) the media session of the local Spotify
# desktop client. Works with a free Spotify account because it never touches
# the Spotify Web API.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File spotifysync-smtc.ps1 status
#   ... playpause | next | previous | seek <milliseconds>

param(
    [string]$Command = "status",
    [string]$Arg = "",
    [string]$Filter = "spotify",
    [string]$OutDir = "."
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Write-Result($map) {
    [Console]::Out.Write((New-Object psobject -Property $map | ConvertTo-Json -Compress))
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null

    $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

    function Await($operation, $resultType) {
        $asTask = $asTaskGeneric.MakeGenericMethod($resultType)
        $task = $asTask.Invoke($null, @($operation))
        $null = $task.Wait(8000)
        return $task.Result
    }

    # Load the WinRT projections.
    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType = WindowsRuntime] | Out-Null
    [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null

    $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])

    $session = $null
    if ($Filter -and $Filter.Trim().Length -gt 0) {
        foreach ($candidate in $manager.GetSessions()) {
            if ($candidate.SourceAppUserModelId -like "*$Filter*") {
                $session = $candidate
                break
            }
        }
    }
    if ($null -eq $session) {
        $session = $manager.GetCurrentSession()
    }

    if ($null -eq $session) {
        Write-Result @{ ok = $true; playing = $false; hasTrack = $false; error = "" }
        exit 0
    }

    switch ($Command.ToLowerInvariant()) {
        "playpause" {
            $null = Await ($session.TryTogglePlayPauseAsync()) ([bool])
            Write-Result @{ ok = $true; hasTrack = $true; error = "" }
            exit 0
        }
        "play" {
            $null = Await ($session.TryPlayAsync()) ([bool])
            Write-Result @{ ok = $true; hasTrack = $true; error = "" }
            exit 0
        }
        "pause" {
            $null = Await ($session.TryPauseAsync()) ([bool])
            Write-Result @{ ok = $true; hasTrack = $true; error = "" }
            exit 0
        }
        "next" {
            $null = Await ($session.TrySkipNextAsync()) ([bool])
            Write-Result @{ ok = $true; hasTrack = $true; error = "" }
            exit 0
        }
        "previous" {
            $null = Await ($session.TrySkipPreviousAsync()) ([bool])
            Write-Result @{ ok = $true; hasTrack = $true; error = "" }
            exit 0
        }
        "seek" {
            $ms = 0
            [void][int64]::TryParse($Arg, [ref]$ms)
            $null = Await ($session.TryChangePlaybackPositionAsync([int64]$ms * 10000)) ([bool])
            Write-Result @{ ok = $true; hasTrack = $true; error = "" }
            exit 0
        }
    }

    # ---------------------------------------------------------------- status
    $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $timeline = $session.GetTimelineProperties()
    $playback = $session.GetPlaybackInfo()

    $title = ""
    $artist = ""
    $album = ""
    if ($null -ne $props) {
        $title = [string]$props.Title
        $artist = [string]$props.Artist
        $album = [string]$props.AlbumTitle
    }

    $positionMs = 0
    $durationMs = 0
    if ($null -ne $timeline) {
        $positionMs = [int64]($timeline.Position.TotalMilliseconds)
        $durationMs = [int64](($timeline.EndTime - $timeline.StartTime).TotalMilliseconds)
    }

    $playing = $false
    if ($null -ne $playback -and $null -ne $playback.PlaybackStatus) {
        $playing = ([string]$playback.PlaybackStatus -eq "Playing")
    }

    # Cover art: cached on disk, keyed by a hash of the track identity so the
    # Java side can detect changes cheaply.
    $coverPath = ""
    $trackKey = ""
    if ($title.Length -gt 0) {
        $identity = "$title|$artist|$album"
        $md5 = [System.Security.Cryptography.MD5]::Create()
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($identity)
        $trackKey = ([System.BitConverter]::ToString($md5.ComputeHash($bytes))).Replace("-", "").Substring(0, 16).ToLowerInvariant()

        if (-not (Test-Path -LiteralPath $OutDir)) {
            $null = New-Item -ItemType Directory -Path $OutDir -Force
        }
        $candidatePath = Join-Path $OutDir ("cover-" + $trackKey + ".png")
        if (Test-Path -LiteralPath $candidatePath) {
            $coverPath = $candidatePath
        } elseif ($null -ne $props -and $null -ne $props.Thumbnail) {
            try {
                $stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                $size = [uint32]$stream.Size
                if ($size -gt 0) {
                    $reader = New-Object Windows.Storage.Streams.DataReader($stream)
                    $null = Await ($reader.LoadAsync($size)) ([uint32])
                    $buffer = New-Object byte[] $size
                    $reader.ReadBytes($buffer)
                    [System.IO.File]::WriteAllBytes($candidatePath, $buffer)
                    $coverPath = $candidatePath
                    $reader.Dispose()
                }
                $stream.Dispose()
            } catch {
                $coverPath = ""
            }
        }
    }

    Write-Result @{
        ok = $true
        error = ""
        hasTrack = ($title.Length -gt 0)
        title = $title
        artist = $artist
        album = $album
        playing = $playing
        positionMs = $positionMs
        durationMs = $durationMs
        app = [string]$session.SourceAppUserModelId
        cover = $coverPath
        trackKey = $trackKey
    }
    exit 0
} catch {
    Write-Result @{ ok = $false; hasTrack = $false; error = [string]$_.Exception.Message }
    exit 1
}
