using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace DeltaDotNet.ThemeStudio
{
    /// <summary>
    /// Everything a .ddntheme file can contain. This class is the single source
    /// of truth for the format; the client has an identical reader in
    /// Core/ThemeEngine.cs.
    ///
    /// A .ddntheme is just a ZIP archive:
    ///     theme.json          (this object, UTF-8, no BOM)
    ///     assets/background.*  optional
    ///     assets/logo.*        optional
    ///     assets/music.*       optional
    ///     assets/font.ttf      optional
    /// </summary>
    public class ThemeManifest
    {
        [JsonPropertyName("format")] public int Format { get; set; } = 1;
        [JsonPropertyName("name")] public string Name { get; set; } = "My theme";
        [JsonPropertyName("author")] public string Author { get; set; } = "";
        [JsonPropertyName("version")] public string Version { get; set; } = "1.0";
        [JsonPropertyName("description")] public string Description { get; set; } = "";

        [JsonPropertyName("background")] public string Background { get; set; } = "#FF0B0B12";
        [JsonPropertyName("panel")] public string Panel { get; set; } = "#FF14141F";
        [JsonPropertyName("border")] public string Border { get; set; } = "#FFFFFFFF";
        [JsonPropertyName("text")] public string Text { get; set; } = "#FFFFFFFF";
        [JsonPropertyName("muted")] public string Muted { get; set; } = "#FF9A9AB5";
        [JsonPropertyName("accent")] public string Accent { get; set; } = "#FFFFD400";
        [JsonPropertyName("accent2")] public string Accent2 { get; set; } = "#FF00E1FF";
        [JsonPropertyName("danger")] public string Danger { get; set; } = "#FFFF3B3B";
        [JsonPropertyName("success")] public string Success { get; set; } = "#FF4CFF7A";

        [JsonPropertyName("fontFamily")] public string FontFamily { get; set; } = "Consolas";
        [JsonPropertyName("fontFile")] public string FontFile { get; set; } = "";

        [JsonPropertyName("backgroundImage")] public string BackgroundImage { get; set; } = "";
        [JsonPropertyName("backgroundOpacity")] public double BackgroundOpacity { get; set; } = 0.35;
        [JsonPropertyName("backgroundStretch")] public string BackgroundStretch { get; set; } = "UniformToFill";

        [JsonPropertyName("music")] public string Music { get; set; } = "";
        [JsonPropertyName("musicVolume")] public double MusicVolume { get; set; } = 0.4;
        [JsonPropertyName("musicLoop")] public bool MusicLoop { get; set; } = true;

        [JsonPropertyName("logo")] public string Logo { get; set; } = "";
    }

    /// <summary>Reads and writes .ddntheme archives.</summary>
    public static class ThemePackage
    {
        public const string Extension = ".ddntheme";

        private static readonly JsonSerializerOptions JsonOpts = new JsonSerializerOptions
        {
            WriteIndented = true,
            Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping
        };

        /// <summary>
        /// Writes the theme to <paramref name="outputPath"/>.
        /// <paramref name="files"/> maps the entry name inside the archive
        /// ("assets/logo.png") to a real file on disk.
        /// </summary>
        public static void Compile(ThemeManifest manifest, Dictionary<string, string> files, string outputPath)
        {
            if (manifest == null) throw new ArgumentNullException("manifest");
            if (string.IsNullOrWhiteSpace(manifest.Name)) manifest.Name = "My theme";
            manifest.Format = 1;

            var dir = Path.GetDirectoryName(outputPath);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            if (File.Exists(outputPath)) File.Delete(outputPath);

            using (var zip = ZipFile.Open(outputPath, ZipArchiveMode.Create))
            {
                var entry = zip.CreateEntry("theme.json", CompressionLevel.Optimal);
                using (var stream = entry.Open())
                using (var writer = new StreamWriter(stream, new UTF8Encoding(false)))
                    writer.Write(JsonSerializer.Serialize(manifest, JsonOpts));

                if (files != null)
                {
                    foreach (var pair in files)
                    {
                        if (string.IsNullOrEmpty(pair.Value) || !File.Exists(pair.Value)) continue;
                        zip.CreateEntryFromFile(pair.Value, pair.Key, CompressionLevel.Optimal);
                    }
                }
            }
        }

        /// <summary>Reads only the manifest of an existing package.</summary>
        public static ThemeManifest ReadManifest(string path)
        {
            using (var zip = ZipFile.OpenRead(path))
            {
                var entry = zip.GetEntry("theme.json");
                if (entry == null) throw new InvalidDataException("theme.json is missing inside the package");
                using (var reader = new StreamReader(entry.Open(), Encoding.UTF8))
                    return JsonSerializer.Deserialize<ThemeManifest>(reader.ReadToEnd());
            }
        }

        /// <summary>Unpacks a package into a folder so it can be edited again.</summary>
        public static ThemeManifest Extract(string path, string targetDir)
        {
            Directory.CreateDirectory(targetDir);
            foreach (var f in Directory.GetFiles(targetDir, "*", SearchOption.AllDirectories))
            {
                try { File.Delete(f); } catch { }
            }
            ZipFile.ExtractToDirectory(path, targetDir, true);
            var json = Path.Combine(targetDir, "theme.json");
            if (!File.Exists(json)) throw new InvalidDataException("theme.json is missing inside the package");
            return JsonSerializer.Deserialize<ThemeManifest>(File.ReadAllText(json, Encoding.UTF8));
        }

        /// <summary>Working folder used by "Open" before the theme is compiled again.</summary>
        public static string WorkDir
        {
            get
            {
                var dir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                    "DeltaDotNet", "themestudio");
                Directory.CreateDirectory(dir);
                return dir;
            }
        }

        /// <summary>Where the client looks for installed themes.</summary>
        public static string ClientThemesDir
        {
            get
            {
                var dir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                    "DeltaDotNet", "themes");
                Directory.CreateDirectory(dir);
                return dir;
            }
        }
    }
}
