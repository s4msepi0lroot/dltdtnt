using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace DeltaDotNet.Core;

/// <summary>
/// All client settings. Stored as JSON in %AppData%\DeltaDotNet\settings.json
/// </summary>
public class AppSettings
{
    [JsonPropertyName("serverUrl")] public string ServerUrl { get; set; } = "http://localhost:8080";
    [JsonPropertyName("username")] public string Username { get; set; } = "";
    [JsonPropertyName("token")] public string Token { get; set; } = "";
    [JsonPropertyName("rememberMe")] public bool RememberMe { get; set; } = true;

    /// <summary>Preset name shown in Settings ("Potato".."Ultra" or "Custom").</summary>
    [JsonPropertyName("qualityPreset")] public string QualityPreset { get; set; } = "Medium";
    [JsonPropertyName("quality")] public QualitySettings Quality { get; set; } = QualitySettings.Preset("Medium");

    /// <summary>Per player-slot key bindings (slot 0 = player 1).</summary>
    [JsonPropertyName("bindings")] public Dictionary<int, KeyBindings> Bindings { get; set; } = new()
    {
        [0] = KeyBindings.DefaultPlayer1(),
        [1] = KeyBindings.DefaultPlayer2(),
        [2] = KeyBindings.DefaultPlayer3(),
        [3] = KeyBindings.DefaultPlayer4()
    };

    /// <summary>Keys the HOST injects into the game for each slot (what the co-op mod reads).</summary>
    [JsonPropertyName("outputBindings")] public Dictionary<int, KeyBindings> OutputBindings { get; set; } = new()
    {
        [0] = KeyBindings.DefaultPlayer1(),
        [1] = KeyBindings.DefaultPlayer2(),
        [2] = KeyBindings.DefaultPlayer3(),
        [3] = KeyBindings.DefaultPlayer4()
    };

    /// <summary>File name (inside the themes folder) of the active .ddntheme, or empty for the built-in theme.</summary>
    [JsonPropertyName("activeTheme")] public string ActiveTheme { get; set; } = "";
    [JsonPropertyName("musicEnabled")] public bool MusicEnabled { get; set; } = true;
    [JsonPropertyName("musicVolume")] public double MusicVolume { get; set; } = 0.4;
    [JsonPropertyName("sfxEnabled")] public bool SfxEnabled { get; set; } = true;

    /// <summary>Capture source for the host: "screen" or "window".</summary>
    [JsonPropertyName("captureMode")] public string CaptureMode { get; set; } = "window";
    /// <summary>Part of the window title of the game to capture, e.g. "DELTARUNE".</summary>
    [JsonPropertyName("captureWindowTitle")] public string CaptureWindowTitle { get; set; } = "DELTARUNE";
    /// <summary>Process id of the target picked in the Cheat-Engine-style process list.</summary>
    [JsonPropertyName("captureProcessId")] public int CaptureProcessId { get; set; }
    /// <summary>Process name of that target (used to find it again after a restart).</summary>
    [JsonPropertyName("captureProcessName")] public string CaptureProcessName { get; set; } = "";
    /// <summary>Window handle of that target (valid until the game is restarted).</summary>
    [JsonPropertyName("captureHandle")] public long CaptureHandle { get; set; }
    /// <summary>Human readable description of the picked target, shown in Settings.</summary>
    [JsonPropertyName("captureLabel")] public string CaptureLabel { get; set; } = "";

    /// <summary>Interface language: "en" (default) or "ru".</summary>
    [JsonPropertyName("language")] public string Language { get; set; } = "en";
    /// <summary>Show the stream stats overlay while playing.</summary>
    [JsonPropertyName("showStats")] public bool ShowStats { get; set; } = true;

    public KeyBindings BindingsFor(int slot)
    {
        if (!Bindings.TryGetValue(slot, out var b) || b == null)
        {
            b = KeyBindings.DefaultForSlot(slot);
            Bindings[slot] = b;
        }
        return b;
    }

    public KeyBindings OutputFor(int slot)
    {
        if (!OutputBindings.TryGetValue(slot, out var b) || b == null)
        {
            b = KeyBindings.DefaultForSlot(slot);
            OutputBindings[slot] = b;
        }
        return b;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------
    private static readonly JsonSerializerOptions JsonOpts = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    public static string AppFolder
    {
        get
        {
            var dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "DeltaDotNet");
            Directory.CreateDirectory(dir);
            return dir;
        }
    }

    public static string ThemesFolder
    {
        get
        {
            var dir = Path.Combine(AppFolder, "themes");
            Directory.CreateDirectory(dir);
            return dir;
        }
    }

    public static string SettingsPath => Path.Combine(AppFolder, "settings.json");

    public static AppSettings Load()
    {
        try
        {
            if (File.Exists(SettingsPath))
            {
                var s = JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(SettingsPath), JsonOpts);
                if (s != null) return s;
            }
        }
        catch { /* corrupt settings fall back to defaults */ }
        return new AppSettings();
    }

    public void Save()
    {
        try { File.WriteAllText(SettingsPath, JsonSerializer.Serialize(this, JsonOpts)); }
        catch { /* ignore disk errors, settings are not critical */ }
    }
}
