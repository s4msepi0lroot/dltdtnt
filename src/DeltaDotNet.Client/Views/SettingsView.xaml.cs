using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using DeltaDotNet.Client.Services;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Views;

/// <summary>Quality, per-player key bindings, capture source, themes and account settings.</summary>
public partial class SettingsView : UserControl
{
    private class ThemeEntry
    {
        public string Path { get; set; }
        public string Label { get; set; }
    }

    private bool _loading = true;
    private Button _waitingForKey;
    private string _waitingAction;

    public SettingsView()
    {
        InitializeComponent();

        var s = App.Settings;

        // quality
        var presetIndex = Array.IndexOf(QualitySettings.PresetNames, s.QualityPreset);
        PresetBox.SelectedIndex = presetIndex < 0 ? 2 : presetIndex;
        FpsSlider.Value = s.Quality.Fps;
        ScaleSlider.Value = s.Quality.Scale;
        JpegSlider.Value = s.Quality.JpegQuality;
        StatsBox.IsChecked = s.ShowStats;

        // capture
        CaptureModeBox.SelectedIndex = s.CaptureMode == "screen" ? 1 : 0;
        WindowTitleBox.Text = s.CaptureWindowTitle;

        // themes
        MusicBox.IsChecked = s.MusicEnabled;
        VolumeSlider.Value = s.MusicVolume * 100;
        ReloadThemes();

        // account
        ServerBox.Text = s.ServerUrl;
        RememberBox.IsChecked = s.RememberMe;
        AccountText.Text = App.User == null
            ? "Not signed in."
            : $"Signed in as {App.User.Username} (role: {App.User.Role}). Settings file: {AppSettings.SettingsPath}";

        _loading = false;
        UpdateQualityLabels();
        BuildBindingButtons();
        RefreshWindows_Click(null, null);
    }

    // ---------------- quality ----------------
    private void Preset_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading) return;
        var name = ((ComboBoxItem)PresetBox.SelectedItem).Content.ToString();
        if (name == "Custom") return;
        var preset = QualitySettings.Preset(name);
        _loading = true;
        FpsSlider.Value = preset.Fps;
        ScaleSlider.Value = preset.Scale;
        JpegSlider.Value = preset.JpegQuality;
        _loading = false;
        UpdateQualityLabels();
    }

    private void Quality_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_loading) return;
        PresetBox.SelectedIndex = 5; // Custom
        UpdateQualityLabels();
    }

    private void UpdateQualityLabels()
    {
        if (FpsLabel == null) return;
        FpsLabel.Text = $"Frame rate: {(int)FpsSlider.Value} fps";
        ScaleLabel.Text = $"Resolution scale: {(int)ScaleSlider.Value}%";
        JpegLabel.Text = $"Image quality: {(int)JpegSlider.Value}";
    }

    // ---------------- key bindings ----------------
    private KeyBindings CurrentBindings()
    {
        int slot = Math.Max(0, SlotBox.SelectedIndex);
        return LayerBox.SelectedIndex == 1 ? App.Settings.OutputFor(slot) : App.Settings.BindingsFor(slot);
    }

    private void Slot_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading) return;
        BuildBindingButtons();
    }

    private void BuildBindingButtons()
    {
        if (BindingItems == null) return;
        BindingItems.Items.Clear();
        var bindings = CurrentBindings();

        foreach (var action in GameAction.All)
        {
            var panel = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 14, 6) };
            panel.Children.Add(new TextBlock
            {
                Text = GameAction.Title(action),
                Width = 90,
                VerticalAlignment = VerticalAlignment.Center
            });

            var button = new Button
            {
                Content = KeyBindings.KeyName(bindings.Get(action)),
                Width = 110,
                Tag = action
            };
            button.Click += BindingButton_Click;
            panel.Children.Add(button);
            BindingItems.Items.Add(panel);
        }
    }

    private void BindingButton_Click(object sender, RoutedEventArgs e)
    {
        var button = (Button)sender;
        _waitingForKey = button;
        _waitingAction = (string)button.Tag;
        button.Content = "press a key...";
        Focus();
        Keyboard.Focus(this);
        PreviewKeyDown -= CaptureBindingKey;
        PreviewKeyDown += CaptureBindingKey;
    }

    private void CaptureBindingKey(object sender, KeyEventArgs e)
    {
        if (_waitingForKey == null) return;
        e.Handled = true;
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        int vk = key == Key.Escape ? 0 : KeyInterop.VirtualKeyFromKey(key);
        CurrentBindings().Set(_waitingAction, vk);
        _waitingForKey.Content = KeyBindings.KeyName(vk);
        _waitingForKey = null;
        _waitingAction = null;
        PreviewKeyDown -= CaptureBindingKey;
        MessageText.Text = "* Binding updated (remember to press SAVE)";
    }

    private void Defaults_Click(object sender, RoutedEventArgs e)
    {
        int slot = Math.Max(0, SlotBox.SelectedIndex);
        var defaults = KeyBindings.DefaultForSlot(slot);
        if (LayerBox.SelectedIndex == 1) App.Settings.OutputBindings[slot] = defaults;
        else App.Settings.Bindings[slot] = defaults;
        BuildBindingButtons();
    }

    // ---------------- capture ----------------
    private void RefreshWindows_Click(object sender, RoutedEventArgs e)
    {
        try { WindowListBox.ItemsSource = ScreenCapture.ListWindowTitles(); }
        catch { }
    }

    private void UseWindow_Click(object sender, RoutedEventArgs e)
    {
        if (WindowListBox.SelectedItem is string title) WindowTitleBox.Text = title;
    }

    // ---------------- themes ----------------
    private void ReloadThemes()
    {
        var entries = ThemeManager.AvailableThemes().Select(path =>
        {
            var manifest = ThemePackage.PeekManifest(path);
            return new ThemeEntry
            {
                Path = path,
                Label = manifest == null
                    ? Path.GetFileName(path) + "  (invalid)"
                    : $"{manifest.Name}  v{manifest.Version}  by {manifest.Author}"
            };
        }).ToList();
        ThemeList.ItemsSource = entries;
    }

    private void ApplyTheme_Click(object sender, RoutedEventArgs e)
    {
        if (ThemeList.SelectedItem is not ThemeEntry entry) { MessageText.Text = "* Select a theme"; return; }
        if (App.Theme.TryApplyFile(entry.Path, out var error)) MessageText.Text = "* Theme applied";
        else MessageText.Text = "* " + error;
    }

    private void ImportTheme_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new Microsoft.Win32.OpenFileDialog
        {
            Filter = "DeltaDotNet theme (*.ddntheme)|*.ddntheme|All files (*.*)|*.*"
        };
        if (dialog.ShowDialog() != true) return;
        try
        {
            var imported = App.Theme.ImportTheme(dialog.FileName);
            ReloadThemes();
            if (App.Theme.TryApplyFile(imported, out var error)) MessageText.Text = "* Theme imported and applied";
            else MessageText.Text = "* " + error;
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private void OpenThemesFolder_Click(object sender, RoutedEventArgs e)
    {
        try { Process.Start(new ProcessStartInfo(AppSettings.ThemesFolder) { UseShellExecute = true }); }
        catch { }
    }

    private void DefaultTheme_Click(object sender, RoutedEventArgs e)
    {
        App.Theme.ApplyDefault();
        MessageText.Text = "* Built-in theme restored";
    }

    private void Volume_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_loading) return;
        VolumeLabel.Text = $"Music volume: {(int)VolumeSlider.Value}%";
        App.Theme.SetVolume(VolumeSlider.Value / 100.0);
    }

    // ---------------- save ----------------
    private void Save_Click(object sender, RoutedEventArgs e)
    {
        var s = App.Settings;
        s.QualityPreset = ((ComboBoxItem)PresetBox.SelectedItem).Content.ToString();
        s.Quality = new QualitySettings
        {
            Fps = (int)FpsSlider.Value,
            Scale = (int)ScaleSlider.Value,
            JpegQuality = (int)JpegSlider.Value
        };
        s.ShowStats = StatsBox.IsChecked == true;
        s.CaptureMode = CaptureModeBox.SelectedIndex == 1 ? "screen" : "window";
        s.CaptureWindowTitle = WindowTitleBox.Text.Trim();
        s.MusicEnabled = MusicBox.IsChecked == true;
        s.MusicVolume = VolumeSlider.Value / 100.0;
        s.ServerUrl = ServerBox.Text.Trim().TrimEnd('/');
        s.RememberMe = RememberBox.IsChecked == true;
        if (!s.RememberMe) s.Token = "";
        s.Save();

        App.Theme.RefreshMusicState();
        MessageText.Text = "* Saved";
        MainWindow.Instance.SetStatus("Settings saved.");
    }

    private void Back_Click(object sender, RoutedEventArgs e)
    {
        MainWindow.Instance.Navigate(App.User == null ? new LoginView() : (UserControl)new LobbyBrowserView());
    }
}
