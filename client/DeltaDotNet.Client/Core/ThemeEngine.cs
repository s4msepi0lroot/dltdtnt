using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text.Json;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace DeltaDotNet.Client.Core
{
    /// <summary>Manifest stored inside a .ddntheme package as theme.json.</summary>
    public class ThemeManifest
    {
        public int Format { get; set; } = 1;
        public string Name { get; set; } = "Untitled theme";
        public string Author { get; set; } = "";
        public string Version { get; set; } = "1.0.0";
        public string Description { get; set; } = "";

        // #AARRGGBB or #RRGGBB
        public string Background { get; set; } = "#FF000000";
        public string Panel { get; set; } = "#FF0B0B12";
        public string Border { get; set; } = "#FFFFFFFF";
        public string Text { get; set; } = "#FFFFFFFF";
        public string Muted { get; set; } = "#FF9A9AB0";
        public string Accent { get; set; } = "#FFFFD23F";
        public string Accent2 { get; set; } = "#FF7A5CFF";
        public string Danger { get; set; } = "#FFFF4D5E";
        public string Success { get; set; } = "#FF49E36B";

        public string FontFamily { get; set; } = "Consolas";
        /// <summary>Relative path of a .ttf/.otf inside the package (optional).</summary>
        public string FontFile { get; set; } = "";

        /// <summary>Relative path of the background image inside the package (optional).</summary>
        public string BackgroundImage { get; set; } = "";
        public double BackgroundOpacity { get; set; } = 1.0;
        /// <summary>Uniform, UniformToFill, Fill or None.</summary>
        public string BackgroundStretch { get; set; } = "UniformToFill";

        /// <summary>Relative path of a looping music track inside the package (optional).</summary>
        public string Music { get; set; } = "";
        public double MusicVolume { get; set; } = 0.35;
        public bool MusicLoop { get; set; } = true;

        /// <summary>Relative path of a custom logo image shown instead of Assets\logo.png.</summary>
        public string Logo { get; set; } = "";
    }

    /// <summary>
    /// Loads .ddntheme packages (a ZIP archive with theme.json + assets) and
    /// pushes their values into Application.Current.Resources. All views use
    /// DynamicResource, so a theme change is applied instantly, without restart.
    /// </summary>
    public static class ThemeEngine
    {
        public static ThemeManifest CurrentManifest { get; private set; }
        public static string CurrentThemeFile { get; private set; }
        public static string CurrentExtractDir { get; private set; }

        private static MediaPlayer _player;
        private static string _musicPath;

        public static event Action ThemeChanged;

        public const string Extension = ".ddntheme";

        // ------------------------------------------------------------ loading
        /// <summary>Reads the manifest of a package without applying it.</summary>
        public static ThemeManifest ReadManifest(string ddnthemePath)
        {
            using (var zip = ZipFile.OpenRead(ddnthemePath))
            {
                var entry = zip.GetEntry("theme.json");
                if (entry == null) throw new InvalidDataException("theme.json is missing: this is not a DeltaDotNet theme.");
                using (var sr = new StreamReader(entry.Open()))
                {
                    var json = sr.ReadToEnd();
                    return JsonSerializer.Deserialize<ThemeManifest>(json,
                        new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
                }
            }
        }

        /// <summary>Extracts and applies a .ddntheme file.</summary>
        public static void Apply(string ddnthemePath)
        {
            var manifest = ReadManifest(ddnthemePath);

            var target = Path.Combine(AppConfig.CacheDir,
                "theme_" + Math.Abs(ddnthemePath.GetHashCode()).ToString());
            try { if (Directory.Exists(target)) Directory.Delete(target, true); } catch { }
            Directory.CreateDirectory(target);
            ZipFile.ExtractToDirectory(ddnthemePath, target, true);

            ApplyManifest(manifest, target);

            CurrentThemeFile = ddnthemePath;
            CurrentExtractDir = target;
            AppConfig.Current.ThemePath = ddnthemePath;
            AppConfig.Save();
        }

        /// <summary>Applies a manifest whose assets live in <paramref name="assetDir"/> (used by Theme Studio for live preview).</summary>
        public static void ApplyManifest(ThemeManifest m, string assetDir)
        {
            if (m == null) return;
            var res = Application.Current.Resources;

            SetBrush(res, "DdnBackgroundBrush", m.Background);
            SetBrush(res, "DdnPanelBrush", m.Panel);
            SetBrush(res, "DdnBorderBrush", m.Border);
            SetBrush(res, "DdnTextBrush", m.Text);
            SetBrush(res, "DdnMutedBrush", m.Muted);
            SetBrush(res, "DdnAccentBrush", m.Accent);
            SetBrush(res, "DdnAccent2Brush", m.Accent2);
            SetBrush(res, "DdnDangerBrush", m.Danger);
            SetBrush(res, "DdnSuccessBrush", m.Success);

            // font: either a family name, or a font file shipped inside the package
            try
            {
                if (!string.IsNullOrWhiteSpace(m.FontFile) && assetDir != null)
                {
                    var fontPath = Path.Combine(assetDir, m.FontFile);
                    if (File.Exists(fontPath))
                    {
                        var dir = Path.GetDirectoryName(fontPath);
                        var family = new FontFamily(new Uri(dir + Path.DirectorySeparatorChar), "./#" + (m.FontFamily ?? ""));
                        res["DdnFont"] = family;
                    }
                    else if (!string.IsNullOrWhiteSpace(m.FontFamily))
                    {
                        res["DdnFont"] = new FontFamily(m.FontFamily);
                    }
                }
                else if (!string.IsNullOrWhiteSpace(m.FontFamily))
                {
                    res["DdnFont"] = new FontFamily(m.FontFamily);
                }
            }
            catch { }

            // background image
            try
            {
                var brush = new ImageBrush();
                if (!string.IsNullOrWhiteSpace(m.BackgroundImage) && assetDir != null)
                {
                    var p = Path.Combine(assetDir, m.BackgroundImage);
                    if (File.Exists(p))
                    {
                        brush.ImageSource = LoadImage(p);
                        brush.Opacity = Math.Max(0, Math.Min(1, m.BackgroundOpacity));
                        Stretch st;
                        brush.Stretch = Enum.TryParse<Stretch>(m.BackgroundStretch, true, out st) ? st : Stretch.UniformToFill;
                    }
                }
                brush.Freeze();
                res["DdnBackgroundImageBrush"] = brush;
            }
            catch { }

            // logo override
            try
            {
                if (!string.IsNullOrWhiteSpace(m.Logo) && assetDir != null)
                {
                    var p = Path.Combine(assetDir, m.Logo);
                    res["DdnLogoImage"] = File.Exists(p) ? LoadImage(p) : null;
                }
                else
                {
                    res["DdnLogoImage"] = null;
                }
            }
            catch { }

            // music
            try
            {
                if (!string.IsNullOrWhiteSpace(m.Music) && assetDir != null)
                {
                    var p = Path.Combine(assetDir, m.Music);
                    if (File.Exists(p)) PlayMusic(p, m.MusicVolume, m.MusicLoop);
                    else StopMusic();
                }
                else StopMusic();
            }
            catch { }

            CurrentManifest = m;
            var h = ThemeChanged;
            if (h != null) h();
        }

        /// <summary>Restores the built in "Dark World" look.</summary>
        public static void ResetToDefault()
        {
            StopMusic();
            var dict = new ResourceDictionary
            {
                Source = new Uri("pack://application:,,,/DeltaDotNet;component/Themes/Deltarune.xaml", UriKind.Absolute)
            };
            foreach (var key in new List<object>(dict.Keys))
            {
                Application.Current.Resources[key] = dict[key];
            }
            Application.Current.Resources["DdnLogoImage"] = null;
            CurrentManifest = null;
            CurrentThemeFile = null;
            AppConfig.Current.ThemePath = "";
            AppConfig.Save();
            var h = ThemeChanged;
            if (h != null) h();
        }

        // ------------------------------------------------------------ music
        public static void PlayMusic(string path, double volume, bool loop)
        {
            if (!AppConfig.Current.MusicEnabled) { StopMusic(); return; }
            try
            {
                if (_player == null)
                {
                    _player = new MediaPlayer();
                    _player.MediaEnded += (s, e) =>
                    {
                        if (_player != null && _musicPath != null)
                        {
                            _player.Position = TimeSpan.Zero;
                            _player.Play();
                        }
                    };
                }
                _musicPath = loop ? path : null;
                _player.Open(new Uri(path, UriKind.Absolute));
                _player.Volume = Math.Max(0, Math.Min(1, volume * AppConfig.Current.MusicVolume / 0.35));
                if (_player.Volume > 1) _player.Volume = 1;
                _player.Play();
            }
            catch { }
        }

        public static void SetVolume(double v)
        {
            AppConfig.Current.MusicVolume = Math.Max(0, Math.Min(1, v));
            if (_player != null) _player.Volume = AppConfig.Current.MusicVolume;
        }

        public static void StopMusic()
        {
            try
            {
                if (_player != null)
                {
                    _player.Stop();
                    _player.Close();
                }
            }
            catch { }
            _musicPath = null;
        }

        // ------------------------------------------------------------ helpers
        public static BitmapImage LoadImage(string path)
        {
            var bi = new BitmapImage();
            bi.BeginInit();
            bi.CacheOption = BitmapCacheOption.OnLoad;
            bi.CreateOptions = BitmapCreateOptions.IgnoreImageCache;
            bi.UriSource = new Uri(path, UriKind.Absolute);
            bi.EndInit();
            bi.Freeze();
            return bi;
        }

        public static Color ParseColor(string s, Color fallback)
        {
            try
            {
                if (string.IsNullOrWhiteSpace(s)) return fallback;
                return (Color)ColorConverter.ConvertFromString(s);
            }
            catch { return fallback; }
        }

        private static void SetBrush(ResourceDictionary res, string key, string colorString)
        {
            var color = ParseColor(colorString, Colors.Magenta);
            var brush = new SolidColorBrush(color);
            brush.Freeze();
            res[key] = brush;
        }
    }
}
