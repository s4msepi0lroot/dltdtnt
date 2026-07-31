using System.IO;
using System.IO.Compression;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace DeltaDotNet.Core;

/// <summary>
/// Description of a DeltaDotNet client theme. Serialized as "theme.json"
/// inside a .ddntheme package (which is a ZIP archive).
/// </summary>
public class ThemeManifest
{
    [JsonPropertyName("formatVersion")] public int FormatVersion { get; set; } = 1;
    [JsonPropertyName("name")] public string Name { get; set; } = "Untitled theme";
    [JsonPropertyName("author")] public string Author { get; set; } = "";
    [JsonPropertyName("version")] public string Version { get; set; } = "1.0.0";
    [JsonPropertyName("description")] public string Description { get; set; } = "";

    // --- colors (hex strings like #RRGGBB) ---
    [JsonPropertyName("background")] public string Background { get; set; } = "#000000";
    [JsonPropertyName("panel")] public string Panel { get; set; } = "#0B0B10";
    [JsonPropertyName("border")] public string Border { get; set; } = "#FFFFFF";
    [JsonPropertyName("text")] public string Text { get; set; } = "#FFFFFF";
    [JsonPropertyName("accent")] public string Accent { get; set; } = "#FFD800";
    [JsonPropertyName("accent2")] public string Accent2 { get; set; } = "#00A2E8";
    [JsonPropertyName("danger")] public string Danger { get; set; } = "#FF3B3B";
    [JsonPropertyName("muted")] public string Muted { get; set; } = "#8A8AA0";

    // --- typography ---
    [JsonPropertyName("fontFamily")] public string FontFamily { get; set; } = "Consolas";
    [JsonPropertyName("fontSize")] public double FontSize { get; set; } = 15;

    // --- assets (file names inside the package, may be null) ---
    [JsonPropertyName("logoFile")] public string LogoFile { get; set; }
    [JsonPropertyName("backgroundFile")] public string BackgroundFile { get; set; }
    [JsonPropertyName("musicFile")] public string MusicFile { get; set; }
    [JsonPropertyName("clickSoundFile")] public string ClickSoundFile { get; set; }

    // --- behaviour ---
    [JsonPropertyName("backgroundOpacity")] public double BackgroundOpacity { get; set; } = 0.35;
    [JsonPropertyName("musicVolume")] public double MusicVolume { get; set; } = 0.4;
    [JsonPropertyName("musicLoop")] public bool MusicLoop { get; set; } = true;
    [JsonPropertyName("cornerRadius")] public double CornerRadius { get; set; } = 0;

    public ThemeManifest Clone() =>
        JsonSerializer.Deserialize<ThemeManifest>(JsonSerializer.Serialize(this));

    /// <summary>The built-in Deltarune-like dark theme.</summary>
    public static ThemeManifest Default() => new()
    {
        Name = "DeltaDotNet Classic",
        Author = "DeltaDotNet",
        Background = "#000000",
        Panel = "#0B0B10",
        Border = "#FFFFFF",
        Text = "#FFFFFF",
        Accent = "#FFD800",
        Accent2 = "#00A2E8",
        Danger = "#FF3B3B",
        Muted = "#8A8AA0",
        FontFamily = "Consolas",
        FontSize = 15
    };
}

/// <summary>A loaded theme: manifest plus the raw bytes of its assets.</summary>
public class ThemePackage
{
    public ThemeManifest Manifest { get; set; } = ThemeManifest.Default();
    public byte[] Logo { get; set; }
    public byte[] Background { get; set; }
    public byte[] Music { get; set; }
    public byte[] ClickSound { get; set; }
    public string SourcePath { get; set; }

    public const string Extension = ".ddntheme";
    private const string ManifestEntry = "theme.json";

    /// <summary>Compiles a theme into a single .ddntheme file (ZIP container).</summary>
    public static void Save(string path, ThemeManifest manifest,
        byte[] logo = null, byte[] background = null, byte[] music = null, byte[] clickSound = null)
    {
        if (!path.EndsWith(Extension, StringComparison.OrdinalIgnoreCase)) path += Extension;

        manifest.LogoFile = logo != null ? (manifest.LogoFile ?? "logo.png") : null;
        manifest.BackgroundFile = background != null ? (manifest.BackgroundFile ?? "background.png") : null;
        manifest.MusicFile = music != null ? (manifest.MusicFile ?? "music.mp3") : null;
        manifest.ClickSoundFile = clickSound != null ? (manifest.ClickSoundFile ?? "click.wav") : null;

        using var stream = File.Create(path);
        using var zip = new ZipArchive(stream, ZipArchiveMode.Create);

        WriteEntry(zip, ManifestEntry, Encoding.UTF8.GetBytes(
            JsonSerializer.Serialize(manifest, new JsonSerializerOptions { WriteIndented = true })));

        if (logo != null) WriteEntry(zip, manifest.LogoFile, logo);
        if (background != null) WriteEntry(zip, manifest.BackgroundFile, background);
        if (music != null) WriteEntry(zip, manifest.MusicFile, music);
        if (clickSound != null) WriteEntry(zip, manifest.ClickSoundFile, clickSound);
    }

    private static void WriteEntry(ZipArchive zip, string name, byte[] data)
    {
        var entry = zip.CreateEntry(name, CompressionLevel.Optimal);
        using var target = entry.Open();
        target.Write(data, 0, data.Length);
    }

    /// <summary>Loads a compiled .ddntheme file.</summary>
    public static ThemePackage Load(string path)
    {
        using var zip = ZipFile.OpenRead(path);
        var manifestEntry = zip.GetEntry(ManifestEntry)
            ?? throw new InvalidDataException("theme.json is missing — this is not a valid .ddntheme file");

        ThemeManifest manifest;
        using (var reader = new StreamReader(manifestEntry.Open(), Encoding.UTF8))
            manifest = JsonSerializer.Deserialize<ThemeManifest>(reader.ReadToEnd()) ?? ThemeManifest.Default();

        return new ThemePackage
        {
            Manifest = manifest,
            SourcePath = path,
            Logo = ReadEntry(zip, manifest.LogoFile),
            Background = ReadEntry(zip, manifest.BackgroundFile),
            Music = ReadEntry(zip, manifest.MusicFile),
            ClickSound = ReadEntry(zip, manifest.ClickSoundFile)
        };
    }

    private static byte[] ReadEntry(ZipArchive zip, string name)
    {
        if (string.IsNullOrEmpty(name)) return null;
        var entry = zip.GetEntry(name);
        if (entry == null) return null;
        using var source = entry.Open();
        using var buffer = new MemoryStream();
        source.CopyTo(buffer);
        return buffer.ToArray();
    }

    /// <summary>Reads only the manifest (used for theme lists).</summary>
    public static ThemeManifest PeekManifest(string path)
    {
        try
        {
            using var zip = ZipFile.OpenRead(path);
            var entry = zip.GetEntry(ManifestEntry);
            if (entry == null) return null;
            using var reader = new StreamReader(entry.Open(), Encoding.UTF8);
            return JsonSerializer.Deserialize<ThemeManifest>(reader.ReadToEnd());
        }
        catch { return null; }
    }

    /// <summary>Writes theme assets that need a real file path (music playback) to a cache folder.</summary>
    public string ExtractMusicToCache()
    {
        if (Music == null || Music.Length == 0) return null;
        var dir = Path.Combine(AppSettings.AppFolder, "cache");
        Directory.CreateDirectory(dir);
        var ext = Path.GetExtension(Manifest.MusicFile);
        if (string.IsNullOrEmpty(ext)) ext = ".mp3";
        var file = Path.Combine(dir, "theme-music" + ext);
        try { File.WriteAllBytes(file, Music); } catch { return null; }
        return file;
    }
}
