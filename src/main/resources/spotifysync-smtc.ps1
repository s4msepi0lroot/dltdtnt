# Spotify Sync - Windows System Media Transport Controls bridge (v2).
#
# Reads (and controls) the media session of the local Spotify desktop client.
# Works with a free Spotify account because it never touches the Web API.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File spotifysync-smtc.ps1 -Command status
#   ... -Command watch -IntervalMs 250      (streams one JSON line per update)
#   ... -Command playpause | next | previous
#   ... -Command seek -Arg <milliseconds>
#   ... -Command volume -Arg <0-100>
#
# All output is pure ASCII JSON: every character above 127 is written as a
# \uXXXX escape. That keeps Cyrillic (and any other) track titles intact no
# matter which code page the Windows console happens to use.

param(
    [string]$Command = "status",
    [string]$Arg = "",
    [string]$Filter = "spotify",
    [string]$OutDir = ".",
    [int]$IntervalMs = 250
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
}

function Escape-Json([string]$value) {
    if ($null -eq $value) { return "" }
    $builder = New-Object System.Text.StringBuilder
    foreach ($ch in $value.ToCharArray()) {
        $code = [int][char]$ch
        switch ($ch) {
            '"' { [void]$builder.Append('\"'); continue }
            '\' { [void]$builder.Append('\\'); continue }
            "`n" { [void]$builder.Append('\n'); continue }
            "`r" { [void]$builder.Append('\r'); continue }
            "`t" { [void]$builder.Append('\t'); continue }
        }
        if ($code -lt 32 -or $code -gt 126) {
            [void]$builder.Append(('\u{0:x4}' -f $code))
        } else {
            [void]$builder.Append($ch)
        }
    }
    return $builder.ToString()
}

function Write-Result($map) {
    $parts = @()
    foreach ($key in $map.Keys) {
        $value = $map[$key]
        if ($value -is [bool]) {
            $parts += ('"{0}":{1}' -f $key, $(if ($value) { "true" } else { "false" }))
        } elseif ($value -is [int] -or $value -is [int64] -or $value -is [double]) {
            $parts += ('"{0}":{1}' -f $key, $value)
        } else {
            $parts += ('"{0}":"{1}"' -f $key, (Escape-Json ([string]$value)))
        }
    }
    $json = "{" + ($parts -join ",") + "}"
    $bytes = [System.Text.Encoding]::ASCII.GetBytes($json + "`n")
    $stdout = [Console]::OpenStandardOutput()
    $stdout.Write($bytes, 0, $bytes.Length)
    $stdout.Flush()
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

    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType = WindowsRuntime] | Out-Null
    [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null

    $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])

    function Get-Session() {
        $found = $null
        if ($Filter -and $Filter.Trim().Length -gt 0) {
            foreach ($candidate in $manager.GetSessions()) {
                if ($candidate.SourceAppUserModelId -like "*$Filter*") {
                    $found = $candidate
                    break
                }
            }
        }
        if ($null -eq $found) {
            $found = $manager.GetCurrentSession()
        }
        return $found
    }

    # ------------------------------------------------------------- app volume
    # Per application volume through the Core Audio session API, so the volume
    # slider in the mod controls the Spotify desktop client itself.
    function Set-AppVolume([int]$percent) {
        $code = @'
using System;
using System.Runtime.InteropServices;

public class SpotifySyncVolume {
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    internal class MMDeviceEnumerator { }

    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IMMDeviceEnumerator {
        int NotImpl1();
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
    }

    [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IMMDevice {
        int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object o);
    }

    [Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IAudioSessionManager2 {
        int NotImpl1();
        int NotImpl2();
        int GetSessionEnumerator(out IAudioSessionEnumerator sessionEnum);
    }

    [Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IAudioSessionEnumerator {
        int GetCount(out int count);
        int GetSession(int index, out IAudioSessionControl session);
    }

    [Guid("F4B1A599-7266-4319-A8CA-E70ACB11E8CD"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IAudioSessionControl { }

    [Guid("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IAudioSessionControl2 {
        int NotImpl0();
        int NotImpl1();
        int NotImpl2();
        int NotImpl3();
        int NotImpl4();
        int NotImpl5();
        int NotImpl6();
        int NotImpl7();
        int NotImpl8();
        int GetSessionIdentifier(out IntPtr id);
        int GetSessionInstanceIdentifier(out IntPtr id);
        int GetProcessId(out int pid);
    }

    [Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface ISimpleAudioVolume {
        int SetMasterVolume(float level, ref Guid eventContext);
        int GetMasterVolume(out float level);
        int SetMute(bool mute, ref Guid eventContext);
        int GetMute(out bool mute);
    }

    public static bool Set(string processName, float level) {
        bool applied = false;
        Guid iidManager = new Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F");
        Guid empty = Guid.Empty;
        IMMDeviceEnumerator enumerator = (IMMDeviceEnumerator)(new MMDeviceEnumerator());
        IMMDevice device;
        enumerator.GetDefaultAudioEndpoint(0, 1, out device);
        object managerObject;
        device.Activate(ref iidManager, 0, IntPtr.Zero, out managerObject);
        IAudioSessionManager2 manager = (IAudioSessionManager2)managerObject;
        IAudioSessionEnumerator sessions;
        manager.GetSessionEnumerator(out sessions);
        int count;
        sessions.GetCount(out count);
        for (int i = 0; i < count; i++) {
            IAudioSessionControl control;
            sessions.GetSession(i, out control);
            IAudioSessionControl2 control2 = control as IAudioSessionControl2;
            if (control2 == null) { continue; }
            int pid;
            if (control2.GetProcessId(out pid) != 0 || pid == 0) { continue; }
            try {
                var process = System.Diagnostics.Process.GetProcessById(pid);
                if (!process.ProcessName.ToLowerInvariant().Contains(processName.ToLowerInvariant())) { continue; }
            } catch { continue; }
            ISimpleAudioVolume volume = control as ISimpleAudioVolume;
            if (volume == null) { continue; }
            volume.SetMasterVolume(level, ref empty);
            applied = true;
        }
        return applied;
    }
}
'@
        if (-not ("SpotifySyncVolume" -as [type])) {
            Add-Type -TypeDefinition $code -Language CSharp | Out-Null
        }
        $level = [math]::Max(0.0, [math]::Min(1.0, $percent / 100.0))
        return [SpotifySyncVolume]::Set("spotify", [float]$level)
    }

    function Read-Status($session) {
        if ($null -eq $session) {
            return [ordered]@{ ok = $true; error = ""; hasTrack = $false; playing = $false }
        }
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

        return [ordered]@{
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
    }

    $session = Get-Session

    switch ($Command.ToLowerInvariant()) {
        "playpause" {
            if ($null -ne $session) { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
            Write-Result ([ordered]@{ ok = $true; hasTrack = $true; error = "" })
            exit 0
        }
        "play" {
            if ($null -ne $session) { $null = Await ($session.TryPlayAsync()) ([bool]) }
            Write-Result ([ordered]@{ ok = $true; hasTrack = $true; error = "" })
            exit 0
        }
        "pause" {
            if ($null -ne $session) { $null = Await ($session.TryPauseAsync()) ([bool]) }
            Write-Result ([ordered]@{ ok = $true; hasTrack = $true; error = "" })
            exit 0
        }
        "next" {
            if ($null -ne $session) { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
            Write-Result ([ordered]@{ ok = $true; hasTrack = $true; error = "" })
            exit 0
        }
        "previous" {
            if ($null -ne $session) { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
            Write-Result ([ordered]@{ ok = $true; hasTrack = $true; error = "" })
            exit 0
        }
        "seek" {
            $ms = 0
            [void][int64]::TryParse($Arg, [ref]$ms)
            if ($null -ne $session) { $null = Await ($session.TryChangePlaybackPositionAsync([int64]$ms * 10000)) ([bool]) }
            Write-Result ([ordered]@{ ok = $true; hasTrack = $true; error = "" })
            exit 0
        }
        "volume" {
            $percent = 100
            [void][int]::TryParse($Arg, [ref]$percent)
            $applied = $false
            try {
                $applied = Set-AppVolume $percent
            } catch {
                $applied = $false
            }
            Write-Result ([ordered]@{ ok = $applied; hasTrack = $true; error = $(if ($applied) { "" } else { "per-app volume unavailable" }) })
            exit 0
        }
        "watch" {
            # Streaming mode: one compact JSON line per sample. Keeping a single
            # long-lived process alive removes the PowerShell startup cost, which
            # is what made playback tracking feel laggy before.
            $sleep = [math]::Max(80, $IntervalMs)
            while ($true) {
                try {
                    $session = Get-Session
                    Write-Result (Read-Status $session)
                } catch {
                    Write-Result ([ordered]@{ ok = $false; hasTrack = $false; error = [string]$_.Exception.Message })
                }
                Start-Sleep -Milliseconds $sleep
            }
        }
    }

    Write-Result (Read-Status $session)
    exit 0
} catch {
    Write-Result ([ordered]@{ ok = $false; hasTrack = $false; error = [string]$_.Exception.Message })
    exit 1
}
