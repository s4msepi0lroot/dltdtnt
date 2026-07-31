using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using DeltaDotNet.Core;

namespace DeltaDotNet.ThemeStudio;

/// <summary>
/// Theme editor: edit colors/fonts/assets, preview live, compile to a .ddntheme package
/// and optionally install it straight into the client's theme folder.
/// </summary>
public partial class MainWindow : Window
{
    private byte[] _logo, _background, _music, _clickSound;
    private string _logoName, _backgroundName, _musicName, _clickName;
    private readonly MediaPlayer _player = new();
    private bool _loading;

    public MainWindow()
    {
        InitializeComponent();
        FontBox.ItemsSource = Fonts.SystemFontFamilies
            .Select(f => f.Source).OrderBy(s => s).ToList();
        UpdatePreview();
    }

    // ---------------- manifest <-> UI ----------------
    private ThemeManifest BuildManifest() => new()
    {
        Name = NameBox.Text.Trim(),
        Author = AuthorBox.Text.Trim(),
        Version = VersionBox.Text.Trim(),
        Description = DescriptionBox.Text.Trim(),
        Background = BackgroundColorBox.Text.Trim(),
        Panel = PanelColorBox.Text.Trim(),
        Border = BorderColorBox.Text.Trim(),
        Text = TextColorBox.Text.Trim(),
        Accent = AccentColorBox.Text.Trim(),
        Accent2 = Accent2ColorBox.Text.Trim(),
        Danger = DangerColorBox.Text.Trim(),
        Muted = MutedColorBox.Text.Trim(),
        FontFamily = string.IsNullOrWhiteSpace(FontBox.Text) ? "Consolas" : FontBox.Text.Trim(),
        FontSize = FontSizeSlider.Value,
        BackgroundOpacity = OpacitySlider.Value / 100.0,
        MusicVolume = MusicVolumeSlider.Value / 100.0,
        MusicLoop = LoopBox.IsChecked == true,
        LogoFile = _logoName,
        BackgroundFile = _backgroundName,
        MusicFile = _musicName,
        ClickSoundFile = _clickName
    };

    private void LoadManifest(ThemeManifest m)
    {
        _loading = true;
        NameBox.Text = m.Name;
        AuthorBox.Text = m.Author;
        VersionBox.Text = m.Version;
        DescriptionBox.Text = m.Description;
        BackgroundColorBox.Text = m.Background;
        PanelColorBox.Text = m.Panel;
        BorderColorBox.Text = m.Border;
        TextColorBox.Text = m.Text;
        AccentColorBox.Text = m.Accent;
        Accent2ColorBox.Text = m.Accent2;
        DangerColorBox.Text = m.Danger;
        MutedColorBox.Text = m.Muted;
        FontBox.Text = m.FontFamily;
        FontSizeSlider.Value = m.FontSize;
        OpacitySlider.Value = m.BackgroundOpacity * 100;
        MusicVolumeSlider.Value = m.MusicVolume * 100;
        LoopBox.IsChecked = m.MusicLoop;
        _loading = false;
        UpdatePreview();
    }

    // ---------------- live preview ----------------
    private void AnyChanged(object sender, RoutedEventArgs e) => UpdatePreview();
    private void AnyChangedSelection(object sender, RoutedEventArgs e) => UpdatePreview();
    private void SliderChanged(object sender, RoutedPropertyChangedEventArgs<double> e) => UpdatePreview();

    private static SolidColorBrush Brush(string hex, string fallback)
    {
        try { return new SolidColorBrush((Color)ColorConverter.ConvertFromString(string.IsNullOrWhiteSpace(hex) ? fallback : hex)); }
        catch { return new SolidColorBrush((Color)ColorConverter.ConvertFromString(fallback)); }
    }

    private void UpdatePreview()
    {
        if (_loading || PreviewRoot == null) return;

        var background = Brush(BackgroundColorBox.Text, "#000000");
        var panel = Brush(PanelColorBox.Text, "#0B0B10");
        var border = Brush(BorderColorBox.Text, "#FFFFFF");
        var text = Brush(TextColorBox.Text, "#FFFFFF");
        var accent = Brush(AccentColorBox.Text, "#FFD800");
        var accent2 = Brush(Accent2ColorBox.Text, "#00A2E8");
        var danger = Brush(DangerColorBox.Text, "#FF3B3B");
        var muted = Brush(MutedColorBox.Text, "#8A8AA0");

        BackgroundSwatch.Background = background;
        PanelSwatch.Background = panel;
        BorderSwatch.Background = border;
        TextSwatch.Background = text;
        AccentSwatch.Background = accent;
        Accent2Swatch.Background = accent2;
        DangerSwatch.Background = danger;
        MutedSwatch.Background = muted;

        PreviewRoot.Background = background;
        PreviewHeader.Background = PreviewBody.Background = panel;
        PreviewHeader.BorderBrush = PreviewBody.BorderBrush = PreviewButton.BorderBrush = border;

        var family = new FontFamily(string.IsNullOrWhiteSpace(FontBox.Text) ? "Consolas" : FontBox.Text);
        foreach (var block in new[] { PreviewTitle, PreviewText1, PreviewText2, PreviewMuted, PreviewButtonText, PreviewDanger })
        {
            block.FontFamily = family;
            block.FontSize = FontSizeSlider.Value;
            block.Foreground = text;
        }
        PreviewTitle.Foreground = accent;
        PreviewTitle.FontSize = FontSizeSlider.Value + 9;
        PreviewText2.Foreground = accent2;
        PreviewMuted.Foreground = muted;
        PreviewDanger.Foreground = danger;

        PreviewBackground.Opacity = OpacitySlider.Value / 100.0;
        OpacityLabel.Text = $"Background image opacity: {(int)OpacitySlider.Value}%";
        MusicVolumeLabel.Text = $"Music volume: {(int)MusicVolumeSlider.Value}%";
        FontSizeLabel.Text = $"Font size: {(int)FontSizeSlider.Value}";
    }

    // ---------------- asset pickers ----------------
    private byte[] PickFile(string filter, out string fileName)
    {
        fileName = null;
        var dialog = new Microsoft.Win32.OpenFileDialog { Filter = filter };
        if (dialog.ShowDialog() != true) return null;
        fileName = Path.GetFileName(dialog.FileName);
        return File.ReadAllBytes(dialog.FileName);
    }

    private const string ImageFilter = "Images (*.png;*.jpg;*.jpeg;*.gif;*.bmp)|*.png;*.jpg;*.jpeg;*.gif;*.bmp";
    private const string AudioFilter = "Audio (*.mp3;*.wav;*.ogg)|*.mp3;*.wav;*.ogg";

    private void PickLogo_Click(object sender, RoutedEventArgs e)
    {
        var data = PickFile(ImageFilter, out var name);
        if (data == null) return;
        _logo = data; _logoName = name;
        LogoText.Text = name;
        PreviewLogo.Source = ToImage(data);
        PreviewTitle.Visibility = Visibility.Collapsed;
    }

    private void PickBackground_Click(object sender, RoutedEventArgs e)
    {
        var data = PickFile(ImageFilter, out var name);
        if (data == null) return;
        _background = data; _backgroundName = name;
        BackgroundText.Text = name;
        PreviewBackground.Source = ToImage(data);
    }

    private void PickMusic_Click(object sender, RoutedEventArgs e)
    {
        var data = PickFile(AudioFilter, out var name);
        if (data == null) return;
        _music = data; _musicName = name;
        MusicText.Text = name;
    }

    private void PickClick_Click(object sender, RoutedEventArgs e)
    {
        var data = PickFile(AudioFilter, out var name);
        if (data == null) return;
        _clickSound = data; _clickName = name;
        ClickText.Text = name;
    }

    private static BitmapImage ToImage(byte[] data)
    {
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

    // ---------------- music preview ----------------
    private void PlayMusic_Click(object sender, RoutedEventArgs e)
    {
        if (_music == null) { StatusText.Text = "No music selected."; return; }
        try
        {
            var temp = Path.Combine(Path.GetTempPath(), "ddn-studio-preview" + Path.GetExtension(_musicName ?? ".mp3"));
            File.WriteAllBytes(temp, _music);
            _player.Open(new Uri(temp));
            _player.Volume = MusicVolumeSlider.Value / 100.0;
            _player.Play();
            StatusText.Text = "Playing preview.";
        }
        catch (Exception ex) { StatusText.Text = ex.Message; }
    }

    private void StopMusic_Click(object sender, RoutedEventArgs e)
    {
        try { _player.Stop(); } catch { }
        StatusText.Text = "Stopped.";
    }

    // ---------------- open / compile ----------------
    private void New_Click(object sender, RoutedEventArgs e)
    {
        _logo = _background = _music = _clickSound = null;
        _logoName = _backgroundName = _musicName = _clickName = null;
        LogoText.Text = BackgroundText.Text = MusicText.Text = ClickText.Text = "none";
        PreviewLogo.Source = null;
        PreviewBackground.Source = null;
        PreviewTitle.Visibility = Visibility.Visible;
        LoadManifest(ThemeManifest.Default());
        StatusText.Text = "New theme.";
    }

    private void Open_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new Microsoft.Win32.OpenFileDialog
        {
            Filter = "DeltaDotNet theme (*.ddntheme)|*.ddntheme"
        };
        if (dialog.ShowDialog() != true) return;
        try
        {
            var package = ThemePackage.Load(dialog.FileName);
            _logo = package.Logo; _logoName = package.Manifest.LogoFile;
            _background = package.Background; _backgroundName = package.Manifest.BackgroundFile;
            _music = package.Music; _musicName = package.Manifest.MusicFile;
            _clickSound = package.ClickSound; _clickName = package.Manifest.ClickSoundFile;

            LogoText.Text = _logoName ?? "none";
            BackgroundText.Text = _backgroundName ?? "none";
            MusicText.Text = _musicName ?? "none";
            ClickText.Text = _clickName ?? "none";
            PreviewLogo.Source = _logo != null ? ToImage(_logo) : null;
            PreviewBackground.Source = _background != null ? ToImage(_background) : null;
            PreviewTitle.Visibility = _logo != null ? Visibility.Collapsed : Visibility.Visible;

            LoadManifest(package.Manifest);
            StatusText.Text = "Opened " + Path.GetFileName(dialog.FileName);
        }
        catch (Exception ex) { StatusText.Text = "Error: " + ex.Message; }
    }

    private void Compile_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new Microsoft.Win32.SaveFileDialog
        {
            Filter = "DeltaDotNet theme (*.ddntheme)|*.ddntheme",
            FileName = Sanitize(NameBox.Text) + ".ddntheme"
        };
        if (dialog.ShowDialog() != true) return;
        Compile(dialog.FileName);
    }

    private void Install_Click(object sender, RoutedEventArgs e)
    {
        var target = Path.Combine(AppSettings.ThemesFolder, Sanitize(NameBox.Text) + ".ddntheme");
        Compile(target);
    }

    private void Compile(string path)
    {
        try
        {
            ThemePackage.Save(path, BuildManifest(), _logo, _background, _music, _clickSound);
            StatusText.Text = "Compiled: " + path;
        }
        catch (Exception ex) { StatusText.Text = "Error: " + ex.Message; }
    }

    private static string Sanitize(string name)
    {
        var clean = new string((name ?? "theme").Select(c => char.IsLetterOrDigit(c) || c == '-' || c == '_' ? c : '-').ToArray());
        return string.IsNullOrWhiteSpace(clean) ? "theme" : clean;
    }
}
