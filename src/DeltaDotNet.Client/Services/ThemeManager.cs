using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Services;

public class ThemeManager
{
    public ThemePackage Current { get; private set; }
    public ThemeManifest Manifest => Current?.Manifest ?? ThemeManifest.Default();

    public BitmapImage LogoImage { get; private set; }
    public BitmapImage BackgroundImage { get; private set; }

    private readonly MediaPlayer _music = new();
    private bool _musicReady;

    public event Action ThemeChanged;

    public static IEnumerable<string> AvailableThemes()
    {
        try { return Directory.GetFiles(AppSettings.ThemesFolder, "*" + ThemePackage.Extension); }
        catch { return Array.Empty<string>(); }
    }

    public void ApplyStartupTheme()
    {
        var name = App.Settings.ActiveTheme;
        if (!string.IsNullOrEmpty(name))
        {
            var path = Path.Combine(AppSettings.ThemesFolder, name);
            if (File.Exists(path) && TryApplyFile(path, out _)) return;
        }
        ApplyDefault();
    }

    public void ApplyDefault()
    {
        Current = null;
        LogoImage = LoadBuiltInLogo();
        BackgroundImage = null;
        StopMusic();
        ApplyManifest(ThemeManifest.Default());
        App.Settings.ActiveTheme = "";
        App.Settings.Save();
        ThemeChanged?.Invoke();
    }

    public bool TryApplyFile(string path, out string error)
    {
        error = null;
        try
        {
            var package = ThemePackage.Load(path);
            Current = package;
            LogoImage = ToImage(package.Logo) ?? LoadBuiltInLogo();
            BackgroundImage = ToImage(package.Background);
            ApplyManifest(package.Manifest);
            PlayMusic(package);
            App.Settings.ActiveTheme = Path.GetFileName(path);
            App.Settings.Save();
            ThemeChanged?.Invoke();
            return true;
        }
        catch (Exception ex)
        {
            error = ex.Message;
            return false;
        }
    }

    public string ImportTheme(string sourcePath)
    {
        var target = Path.Combine(AppSettings.ThemesFolder, Path.GetFileName(sourcePath));
        if (!string.Equals(Path.GetFullPath(sourcePath), Path.GetFullPath(target), StringComparison.OrdinalIgnoreCase))
            File.Copy(sourcePath, target, true);
        return target;
    }

    private void ApplyManifest(ThemeManifest m)
    {
        var r = Application.Current.Resources;
        SetBrush(r, "BackgroundBrush", m.Background, "#000000");
        SetBrush(r, "PanelBrush", m.Panel, "#0B0B10");
        SetBrush(r, "BorderBrush2", m.Border, "#FFFFFF");
        SetBrush(r, "TextBrush", m.Text, "#FFFFFF");
        SetBrush(r, "AccentBrush", m.Accent, "#FFD800");
        SetBrush(r, "Accent2Brush", m.Accent2, "#00A2E8");
        SetBrush(r, "DangerBrush", m.Danger, "#FF3B3B");
        SetBrush(r, "MutedBrush", m.Muted, "#8A8AA0");

        try { r["UiFont"] = new FontFamily(string.IsNullOrWhiteSpace(m.FontFamily) ? "Consolas" : m.FontFamily); }
        catch { r["UiFont"] = new FontFamily("Consolas"); }
        r["UiFontSize"] = m.FontSize <= 6 ? 15d : m.FontSize;
    }

    private static void SetBrush(ResourceDictionary r, string key, string hex, string fallback)
    {
        r[key] = new SolidColorBrush(ParseColor(hex, fallback));
    }

    public static Color ParseColor(string hex, string fallback = "#FFFFFF")
    {
        try { return (Color)ColorConverter.ConvertFromString(string.IsNullOrWhiteSpace(hex) ? fallback : hex); }
        catch { return (Color)ColorConverter.ConvertFromString(fallback); }
    }

    public static BitmapImage ToImage(byte[] data)
    {
        if (data == null || data.Length == 0) return null;
        try
        {
            var image = new BitmapImage();
            using var stream = new MemoryStream(data);
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.StreamSource = stream;
            image.EndInit();
            image.Freeze();
            return image;
        }
        catch { return null; }
    }

    public static BitmapImage LoadBuiltInLogo()
    {
        try
        {
            var uri = new Uri("pack://application:,,,/Assets/logo.png", UriKind.Absolute);
            var image = new BitmapImage(uri);
            image.Freeze();
            return image;
        }
        catch { return null; }
    }

    public void PlayMusic(ThemePackage package)
    {
        StopMusic();
        if (package == null || !App.Settings.MusicEnabled) return;
        var file = package.ExtractMusicToCache();
        if (file == null) return;
        try
        {
            _music.Open(new Uri(file));
            _music.Volume = Math.Clamp(App.Settings.MusicVolume, 0, 1);
            _music.MediaEnded -= LoopMusic;
            if (package.Manifest.MusicLoop) _music.MediaEnded += LoopMusic;
            _music.Play();
            _musicReady = true;
        }
        catch { }
    }

    private void LoopMusic(object sender, EventArgs e)
    {
        try { _music.Position = TimeSpan.Zero; _music.Play(); } catch { }
    }

    public void StopMusic()
    {
        if (!_musicReady) return;
        try { _music.Stop(); _music.Close(); } catch { }
        _musicReady = false;
    }

    public void SetVolume(double volume)
    {
        App.Settings.MusicVolume = Math.Clamp(volume, 0, 1);
        try { _music.Volume = App.Settings.MusicVolume; } catch { }
    }

    public void RefreshMusicState()
    {
        if (App.Settings.MusicEnabled) PlayMusic(Current);
        else StopMusic();
    }
}
