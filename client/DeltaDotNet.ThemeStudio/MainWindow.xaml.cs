using System;
using System.Collections.Generic;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Microsoft.Win32;

namespace DeltaDotNet.ThemeStudio
{
    /// <summary>
    /// The theme editor. Left side = properties, right side = a live mock of the
    /// real client. "COMPILE" packs everything into a single .ddntheme file.
    /// </summary>
    public partial class MainWindow : Window
    {
        private readonly Dictionary<string, TextBox> _colorBoxes = new Dictionary<string, TextBox>();
        private readonly Dictionary<string, string> _files = new Dictionary<string, string>();
        private MediaPlayer _player;
        private bool _loading = true;

        private static readonly string[] ColorKeys =
        {
            "background", "panel", "border", "text", "muted", "accent", "accent2", "danger", "success"
        };

        private static readonly Dictionary<string, string> ColorTitles = new Dictionary<string, string>
        {
            { "background", "Background" },
            { "panel", "Panels and the top bar" },
            { "border", "Borders" },
            { "text", "Main text" },
            { "muted", "Secondary text" },
            { "accent", "Accent (hover, highlights)" },
            { "accent2", "Second accent" },
            { "danger", "Danger (ban, close)" },
            { "success", "Success" }
        };

        public MainWindow()
        {
            InitializeComponent();
            BuildColorEditors();
            LoadFonts();
            LoadBranding();
            LoadManifest(new ThemeManifest());
            _loading = false;
            RefreshPreview();
        }

        // ------------------------------------------------------------ setup
        private void BuildColorEditors()
        {
            foreach (var key in ColorKeys)
            {
                var row = new Grid { Margin = new Thickness(0, 0, 0, 4) };
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(150) });
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(34) });

                var label = new TextBlock { Text = ColorTitles[key], VerticalAlignment = VerticalAlignment.Center };
                Grid.SetColumn(label, 0);
                row.Children.Add(label);

                var box = new TextBox { Margin = new Thickness(0, 0, 6, 4) };
                box.TextChanged += Any_Changed;
                Grid.SetColumn(box, 1);
                row.Children.Add(box);
                _colorBoxes[key] = box;

                var swatch = new Border
                {
                    BorderBrush = Brushes.White,
                    BorderThickness = new Thickness(1),
                    Margin = new Thickness(0, 0, 0, 4)
                };
                Grid.SetColumn(swatch, 2);
                row.Children.Add(swatch);
                box.Tag = swatch;

                ColorPanel.Children.Add(row);
            }
        }

        private void LoadFonts()
        {
            var names = new List<string>();
            foreach (var f in Fonts.SystemFontFamilies) names.Add(f.Source);
            names.Sort(StringComparer.OrdinalIgnoreCase);
            foreach (var n in names) FontBox.Items.Add(n);
        }

        private void LoadBranding()
        {
            var logo = Path.Combine(AppContext.BaseDirectory, "Assets", "logo.png");
            if (!File.Exists(logo)) return;
            try
            {
                var bmp = new BitmapImage();
                bmp.BeginInit();
                bmp.CacheOption = BitmapCacheOption.OnLoad;
                bmp.UriSource = new Uri(logo);
                bmp.EndInit();
                bmp.Freeze();
                LogoImage.Source = bmp;
                LogoImage.Visibility = Visibility.Visible;
                LogoText.Visibility = Visibility.Collapsed;
            }
            catch { }
        }

        // ------------------------------------------------------------ manifest <-> ui
        private void LoadManifest(ThemeManifest m)
        {
            _loading = true;
            NameBox.Text = m.Name;
            AuthorBox.Text = m.Author;
            VersionBox.Text = m.Version;
            DescBox.Text = m.Description;

            _colorBoxes["background"].Text = m.Background;
            _colorBoxes["panel"].Text = m.Panel;
            _colorBoxes["border"].Text = m.Border;
            _colorBoxes["text"].Text = m.Text;
            _colorBoxes["muted"].Text = m.Muted;
            _colorBoxes["accent"].Text = m.Accent;
            _colorBoxes["accent2"].Text = m.Accent2;
            _colorBoxes["danger"].Text = m.Danger;
            _colorBoxes["success"].Text = m.Success;

            FontBox.Text = m.FontFamily;
            BgOpacity.Value = m.BackgroundOpacity * 100.0;
            MusicVolume.Value = m.MusicVolume * 100.0;
            LoopCheck.IsChecked = m.MusicLoop;
            SelectContent(StretchBox, string.IsNullOrEmpty(m.BackgroundStretch) ? "UniformToFill" : m.BackgroundStretch);
            _loading = false;
        }

        private ThemeManifest BuildManifest()
        {
            return new ThemeManifest
            {
                Format = 1,
                Name = NameBox.Text,
                Author = AuthorBox.Text,
                Version = string.IsNullOrWhiteSpace(VersionBox.Text) ? "1.0" : VersionBox.Text,
                Description = DescBox.Text,
                Background = _colorBoxes["background"].Text,
                Panel = _colorBoxes["panel"].Text,
                Border = _colorBoxes["border"].Text,
                Text = _colorBoxes["text"].Text,
                Muted = _colorBoxes["muted"].Text,
                Accent = _colorBoxes["accent"].Text,
                Accent2 = _colorBoxes["accent2"].Text,
                Danger = _colorBoxes["danger"].Text,
                Success = _colorBoxes["success"].Text,
                FontFamily = FontBox.Text,
                FontFile = _files.ContainsKey("assets/font.ttf") ? "assets/font.ttf" : "",
                BackgroundImage = FindEntry("assets/background"),
                BackgroundOpacity = BgOpacity.Value / 100.0,
                BackgroundStretch = ContentOf(StretchBox),
                Music = FindEntry("assets/music"),
                MusicVolume = MusicVolume.Value / 100.0,
                MusicLoop = LoopCheck.IsChecked == true,
                Logo = FindEntry("assets/logo")
            };
        }

        private string FindEntry(string prefix)
        {
            foreach (var key in _files.Keys)
                if (key.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return key;
            return "";
        }

        private void RemoveEntries(string prefix)
        {
            var toRemove = new List<string>();
            foreach (var key in _files.Keys)
                if (key.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) toRemove.Add(key);
            foreach (var key in toRemove) _files.Remove(key);
        }

        // ------------------------------------------------------------ preview
        private void Any_Changed(object sender, RoutedEventArgs e) { RefreshPreview(); }
        private void Any_Changed(object sender, TextChangedEventArgs e) { RefreshPreview(); }
        private void Any_Changed(object sender, SelectionChangedEventArgs e) { RefreshPreview(); }
        private void Any_Changed(object sender, RoutedPropertyChangedEventArgs<double> e) { RefreshPreview(); }

        private void Volume_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            MusicVolumeText.Text = "Volume: " + (int)MusicVolume.Value + "%";
            if (_player != null) _player.Volume = MusicVolume.Value / 100.0;
        }

        private void RefreshPreview()
        {
            if (_loading) return;

            var bg = Color("background", "#FF0B0B12");
            var panel = Color("panel", "#FF14141F");
            var border = Color("border", "#FFFFFFFF");
            var text = Color("text", "#FFFFFFFF");
            var muted = Color("muted", "#FF9A9AB5");
            var accent = Color("accent", "#FFFFD400");
            var danger = Color("danger", "#FFFF3B3B");
            var success = Color("success", "#FF4CFF7A");

            foreach (var pair in _colorBoxes)
            {
                var swatch = pair.Value.Tag as Border;
                if (swatch != null) swatch.Background = new SolidColorBrush(Color(pair.Key, "#FF000000"));
            }

            PreviewRoot.Background = new SolidColorBrush(bg);
            PvTopBar.Background = new SolidColorBrush(panel);
            PvTopBar.BorderBrush = new SolidColorBrush(border);
            PvBox.Background = new SolidColorBrush(panel);
            PvBox.BorderBrush = new SolidColorBrush(border);
            PvButton.BorderBrush = new SolidColorBrush(border);
            PvButton.Background = new SolidColorBrush(panel);
            PvDanger.BorderBrush = new SolidColorBrush(danger);
            PvDanger.Background = new SolidColorBrush(panel);

            PvLogo.Foreground = new SolidColorBrush(accent);
            PvTitle.Foreground = new SolidColorBrush(accent);
            PvText.Foreground = new SolidColorBrush(text);
            PvMuted.Foreground = new SolidColorBrush(muted);
            PvButtonText.Foreground = new SolidColorBrush(text);
            PvDangerText.Foreground = new SolidColorBrush(danger);
            PvSuccess.Foreground = new SolidColorBrush(success);

            var family = string.IsNullOrWhiteSpace(FontBox.Text) ? "Consolas" : FontBox.Text;
            try
            {
                var ff = new FontFamily(family);
                PvLogo.FontFamily = ff; PvTitle.FontFamily = ff; PvText.FontFamily = ff;
                PvMuted.FontFamily = ff; PvButtonText.FontFamily = ff;
                PvDangerText.FontFamily = ff; PvSuccess.FontFamily = ff;
            }
            catch { }

            BgOpacityText.Text = "Opacity: " + (int)BgOpacity.Value + "%";
            MusicVolumeText.Text = "Volume: " + (int)MusicVolume.Value + "%";

            var bgFile = FindEntry("assets/background");
            if (bgFile.Length > 0 && _files.ContainsKey(bgFile) && File.Exists(_files[bgFile]))
            {
                try
                {
                    var bmp = LoadBitmap(_files[bgFile]);
                    var brush = new ImageBrush(bmp)
                    {
                        Opacity = BgOpacity.Value / 100.0,
                        Stretch = ParseStretch(ContentOf(StretchBox))
                    };
                    PreviewBgImage.Fill = brush;
                }
                catch { PreviewBgImage.Fill = null; }
            }
            else PreviewBgImage.Fill = null;

            var logoFile = FindEntry("assets/logo");
            if (logoFile.Length > 0 && _files.ContainsKey(logoFile) && File.Exists(_files[logoFile]))
            {
                try
                {
                    PvLogoImage.Source = LoadBitmap(_files[logoFile]);
                    PvLogoImage.Visibility = Visibility.Visible;
                    PvLogo.Visibility = Visibility.Collapsed;
                }
                catch { }
            }
            else
            {
                PvLogoImage.Visibility = Visibility.Collapsed;
                PvLogo.Visibility = Visibility.Visible;
            }
        }

        private static BitmapImage LoadBitmap(string path)
        {
            var bmp = new BitmapImage();
            bmp.BeginInit();
            bmp.CacheOption = BitmapCacheOption.OnLoad;
            bmp.UriSource = new Uri(path);
            bmp.EndInit();
            bmp.Freeze();
            return bmp;
        }

        private static Stretch ParseStretch(string value)
        {
            switch ((value ?? "").ToLowerInvariant())
            {
                case "uniform": return Stretch.Uniform;
                case "fill": return Stretch.Fill;
                case "none": return Stretch.None;
                default: return Stretch.UniformToFill;
            }
        }

        private System.Windows.Media.Color Color(string key, string fallback)
        {
            var raw = _colorBoxes.ContainsKey(key) ? _colorBoxes[key].Text : fallback;
            try { return (System.Windows.Media.Color)ColorConverter.ConvertFromString(raw); }
            catch { return (System.Windows.Media.Color)ColorConverter.ConvertFromString(fallback); }
        }

        // ------------------------------------------------------------ file pickers
        private void PickFont_Click(object sender, RoutedEventArgs e)
        {
            var path = Pick("Font (*.ttf;*.otf)|*.ttf;*.otf");
            if (path == null) return;
            _files["assets/font.ttf"] = path;
            FontFileText.Text = "attached font: " + Path.GetFileName(path);
            RefreshPreview();
        }

        private void ClearFont_Click(object sender, RoutedEventArgs e)
        {
            _files.Remove("assets/font.ttf");
            FontFileText.Text = "no attached font file";
        }

        private void PickBg_Click(object sender, RoutedEventArgs e)
        {
            var path = Pick("Images (*.png;*.jpg;*.jpeg;*.bmp;*.gif)|*.png;*.jpg;*.jpeg;*.bmp;*.gif");
            if (path == null) return;
            RemoveEntries("assets/background");
            _files["assets/background" + Path.GetExtension(path).ToLowerInvariant()] = path;
            BgFileText.Text = "background: " + Path.GetFileName(path);
            RefreshPreview();
        }

        private void ClearBg_Click(object sender, RoutedEventArgs e)
        {
            RemoveEntries("assets/background");
            BgFileText.Text = "no background image";
            RefreshPreview();
        }

        private void PickLogo_Click(object sender, RoutedEventArgs e)
        {
            var path = Pick("Images (*.png;*.jpg;*.jpeg)|*.png;*.jpg;*.jpeg");
            if (path == null) return;
            RemoveEntries("assets/logo");
            _files["assets/logo" + Path.GetExtension(path).ToLowerInvariant()] = path;
            LogoFileText.Text = "logo: " + Path.GetFileName(path);
            RefreshPreview();
        }

        private void ClearLogo_Click(object sender, RoutedEventArgs e)
        {
            RemoveEntries("assets/logo");
            LogoFileText.Text = "no logo, the client will show the default one";
            RefreshPreview();
        }

        private void PickMusic_Click(object sender, RoutedEventArgs e)
        {
            var path = Pick("Audio (*.mp3;*.wav;*.wma)|*.mp3;*.wav;*.wma");
            if (path == null) return;
            RemoveEntries("assets/music");
            _files["assets/music" + Path.GetExtension(path).ToLowerInvariant()] = path;
            MusicFileText.Text = "music: " + Path.GetFileName(path);
        }

        private void ClearMusic_Click(object sender, RoutedEventArgs e)
        {
            StopMusic_Click(sender, e);
            RemoveEntries("assets/music");
            MusicFileText.Text = "no music";
        }

        private void PlayMusic_Click(object sender, RoutedEventArgs e)
        {
            var key = FindEntry("assets/music");
            if (key.Length == 0) return;
            StopMusic_Click(sender, e);
            _player = new MediaPlayer();
            _player.Open(new Uri(_files[key]));
            _player.Volume = MusicVolume.Value / 100.0;
            if (LoopCheck.IsChecked == true)
                _player.MediaEnded += (s, ev) => { _player.Position = TimeSpan.Zero; _player.Play(); };
            _player.Play();
        }

        private void StopMusic_Click(object sender, RoutedEventArgs e)
        {
            if (_player == null) return;
            try { _player.Stop(); _player.Close(); } catch { }
            _player = null;
        }

        private static string Pick(string filter)
        {
            var dlg = new OpenFileDialog { Filter = filter + "|All files (*.*)|*.*" };
            return dlg.ShowDialog() == true ? dlg.FileName : null;
        }

        // ------------------------------------------------------------ commands
        private void New_Click(object sender, RoutedEventArgs e)
        {
            _files.Clear();
            BgFileText.Text = "no background image";
            LogoFileText.Text = "no logo, the client will show the default one";
            MusicFileText.Text = "no music";
            FontFileText.Text = "no attached font file";
            LoadManifest(new ThemeManifest());
            RefreshPreview();
            StatusText.Text = "new theme";
        }

        private void Open_Click(object sender, RoutedEventArgs e)
        {
            var dlg = new OpenFileDialog
            {
                Filter = "DeltaDotNet theme (*.ddntheme)|*.ddntheme|All files (*.*)|*.*",
                InitialDirectory = ThemePackage.ClientThemesDir
            };
            if (dlg.ShowDialog() != true) return;
            try
            {
                var work = Path.Combine(ThemePackage.WorkDir, "open");
                var manifest = ThemePackage.Extract(dlg.FileName, work);
                _files.Clear();

                Adopt(work, manifest.BackgroundImage, BgFileText, "background: ");
                Adopt(work, manifest.Logo, LogoFileText, "logo: ");
                Adopt(work, manifest.Music, MusicFileText, "music: ");
                Adopt(work, manifest.FontFile, FontFileText, "attached font: ");

                LoadManifest(manifest);
                RefreshPreview();
                StatusText.Text = "opened: " + Path.GetFileName(dlg.FileName);
            }
            catch (Exception ex)
            {
                MessageBox.Show("Could not open the theme: " + ex.Message, "DeltaDotNet Theme Studio");
            }
        }

        private void Adopt(string workDir, string entry, TextBlock label, string prefix)
        {
            if (string.IsNullOrEmpty(entry)) return;
            var real = Path.Combine(workDir, entry.Replace('/', Path.DirectorySeparatorChar));
            if (!File.Exists(real)) return;
            _files[entry] = real;
            label.Text = prefix + Path.GetFileName(real);
        }

        private void Compile_Click(object sender, RoutedEventArgs e)
        {
            var dlg = new SaveFileDialog
            {
                Filter = "DeltaDotNet theme (*.ddntheme)|*.ddntheme",
                FileName = Safe(NameBox.Text) + ThemePackage.Extension
            };
            if (dlg.ShowDialog() != true) return;
            try
            {
                ThemePackage.Compile(BuildManifest(), _files, dlg.FileName);
                StatusText.Text = "compiled: " + dlg.FileName;
                MessageBox.Show("The theme is ready:\n" + dlg.FileName, "DeltaDotNet Theme Studio");
            }
            catch (Exception ex)
            {
                MessageBox.Show("Compilation failed: " + ex.Message, "DeltaDotNet Theme Studio");
            }
        }

        private void Install_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                var target = Path.Combine(ThemePackage.ClientThemesDir, Safe(NameBox.Text) + ThemePackage.Extension);
                ThemePackage.Compile(BuildManifest(), _files, target);
                StatusText.Text = "installed: " + target;
                MessageBox.Show("The theme was placed into the client folder:\n" + target +
                                "\n\nOpen DeltaDotNet -> SETTINGS -> THEMES -> LOAD A .ddntheme",
                    "DeltaDotNet Theme Studio");
            }
            catch (Exception ex)
            {
                MessageBox.Show("Install failed: " + ex.Message, "DeltaDotNet Theme Studio");
            }
        }

        private static string Safe(string name)
        {
            if (string.IsNullOrWhiteSpace(name)) return "theme";
            foreach (var c in Path.GetInvalidFileNameChars()) name = name.Replace(c, '_');
            return name.Trim();
        }

        private static string ContentOf(ComboBox box)
        {
            var item = box.SelectedItem as ComboBoxItem;
            return item == null ? "UniformToFill" : Convert.ToString(item.Content);
        }

        private static void SelectContent(ComboBox box, string content)
        {
            foreach (var o in box.Items)
            {
                var item = o as ComboBoxItem;
                if (item != null && string.Equals(Convert.ToString(item.Content), content, StringComparison.OrdinalIgnoreCase))
                {
                    box.SelectedItem = item;
                    return;
                }
            }
            if (box.Items.Count > 0) box.SelectedIndex = 0;
        }

        protected override void OnClosed(EventArgs e)
        {
            StopMusic_Click(null, null);
            base.OnClosed(e);
        }
    }
}
