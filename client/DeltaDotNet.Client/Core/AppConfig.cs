using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace DeltaDotNet.Client.Core
{
    /// <summary>Quality / capture settings used by the host while streaming.</summary>
    public class QualitySettings
    {
        /// <summary>Frames per second the host tries to send (5..60).</summary>
        public int Fps { get; set; } = 30;

        /// <summary>JPEG quality of every frame (10..95). Lower = less traffic.</summary>
        public int JpegQuality { get; set; } = 62;

        /// <summary>Resolution scale in percent (25..100).</summary>
        public int Scale { get; set; } = 75;

        /// <summary>"Screen", "Window" or "Region".</summary>
        public string CaptureMode { get; set; } = "Window";

        /// <summary>Part of the window title to look for when CaptureMode == "Window".</summary>
        public string WindowTitle { get; set; } = "DELTARUNE";

        /// <summary>Region used when CaptureMode == "Region".</summary>
        public int RegionX { get; set; } = 0;
        public int RegionY { get; set; } = 0;
        public int RegionW { get; set; } = 1280;
        public int RegionH { get; set; } = 720;

        /// <summary>Draw the mouse cursor into the stream.</summary>
        public bool CaptureCursor { get; set; } = false;

        /// <summary>Skip sending a frame if the picture did not change (saves a lot of traffic).</summary>
        public bool SkipIdenticalFrames { get; set; } = true;

        /// <summary>Preset name for the UI: Potato / Low / Medium / High / Ultra / Custom.</summary>
        public string Preset { get; set; } = "Medium";

        public QualitySettings Clone()
        {
            return (QualitySettings)MemberwiseClone();
        }

        public static QualitySettings FromPreset(string preset)
        {
            var q = new QualitySettings { Preset = preset };
            switch (preset)
            {
                case "Potato": q.Fps = 10; q.JpegQuality = 35; q.Scale = 40; break;
                case "Low": q.Fps = 20; q.JpegQuality = 45; q.Scale = 55; break;
                case "Medium": q.Fps = 30; q.JpegQuality = 62; q.Scale = 75; break;
                case "High": q.Fps = 45; q.JpegQuality = 75; q.Scale = 90; break;
                case "Ultra": q.Fps = 60; q.JpegQuality = 88; q.Scale = 100; break;
            }
            return q;
        }
    }

    /// <summary>Everything that is persisted between runs.</summary>
    public class ConfigData
    {
        public string ServerUrl { get; set; } = "http://localhost:8080";
        public string Login { get; set; } = "";
        public string Token { get; set; } = "";
        public bool RememberMe { get; set; } = true;

        public QualitySettings Quality { get; set; } = new QualitySettings();

        /// <summary>My personal keyboard: action -> local key name (WPF Key enum name).</summary>
        public Dictionary<string, string> MyBinds { get; set; } = new Dictionary<string, string>();

        /// <summary>
        /// Keys the mod itself expects, per player slot.
        /// slot number -> (action -> key name). Only the host uses this table:
        /// it converts "slot 2 pressed Confirm" into a real Enter key press.
        /// </summary>
        public Dictionary<string, Dictionary<string, string>> SlotGameKeys { get; set; }
            = new Dictionary<string, Dictionary<string, string>>();

        public string ThemePath { get; set; } = "";
        public double MusicVolume { get; set; } = 0.35;
        public bool MusicEnabled { get; set; } = true;
        public bool ScaleUiToWindow { get; set; } = true;
        public double WindowWidth { get; set; } = 1280;
        public double WindowHeight { get; set; } = 760;
        public bool ShowStreamStats { get; set; } = true;
        public bool FocusGameOnStart { get; set; } = true;
    }

    /// <summary>Loads/saves <see cref="ConfigData"/> from %AppData%\DeltaDotNet\config.json.</summary>
    public static class AppConfig
    {
        public static ConfigData Current = new ConfigData();

        public static string Dir
        {
            get
            {
                return Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                    "DeltaDotNet");
            }
        }

        public static string ConfigPath { get { return Path.Combine(Dir, "config.json"); } }
        public static string CrashLogPath { get { return Path.Combine(Dir, "crash.log"); } }
        public static string ThemesDir { get { return Path.Combine(Dir, "themes"); } }
        public static string CacheDir { get { return Path.Combine(Dir, "cache"); } }

        private static readonly JsonSerializerOptions JsonOpts = new JsonSerializerOptions
        {
            WriteIndented = true,
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
        };

        public static void Load()
        {
            try
            {
                Directory.CreateDirectory(Dir);
                Directory.CreateDirectory(ThemesDir);
                Directory.CreateDirectory(CacheDir);
                if (File.Exists(ConfigPath))
                {
                    var json = File.ReadAllText(ConfigPath);
                    var data = JsonSerializer.Deserialize<ConfigData>(json, JsonOpts);
                    if (data != null) Current = data;
                }
            }
            catch { Current = new ConfigData(); }

            if (Current.Quality == null) Current.Quality = new QualitySettings();
            if (Current.MyBinds == null || Current.MyBinds.Count == 0)
                Current.MyBinds = Keybinds.DefaultLocalBinds();
            if (Current.SlotGameKeys == null || Current.SlotGameKeys.Count == 0)
                Current.SlotGameKeys = Keybinds.DefaultSlotGameKeys();
        }

        public static void Save()
        {
            try
            {
                Directory.CreateDirectory(Dir);
                File.WriteAllText(ConfigPath, JsonSerializer.Serialize(Current, JsonOpts));
            }
            catch { }
        }
    }
}
