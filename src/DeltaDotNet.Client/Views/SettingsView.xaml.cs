using System;
using System.Linq;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using DeltaDotNet.Client.Localization;
using DeltaDotNet.Client.Services;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Views;

/// <summary>Quality, per-player key bindings, capture target, themes, language and account settings.</summary>
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
        UpdateTargetText();

        // themes
        MusicBox.IsChecked = s.MusicEnabled;
        VolumeSlider.Value = s.MusicVolume * 100;
        ReloadThemes();

        // general / account
        LanguageBox.SelectedIndex = s.Language == "ru" ? 1 : 0;
        ServerBox.Text = s.ServerUrl;
        RememberBox.IsChecked = s.RememberMe;
        AccountText.Text = App.User == null
            ? Loc.T("settings.account.notsigned")
            : Loc.F("settings.account.info", App.User.Username, App.User.Role, AppSettings.SettingsPath);

        _loading = false;
        UpdateQualityLabels();
        UpdateVolumeLabel();
        BuildBindingButtons();
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
        FpsLabel.Text = Loc.F("settings.fps", (int)FpsSlider.Value);
        ScaleLabel.Text = Loc.F("settings.scale", (int)ScaleSlider.Value);
        JpegLabel.Text = Loc.F("settings.jpeg", (int)JpegSlider.Value);
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
        button.Content = Loc.T("settings.bind.press");
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
        MessageText.Text = Loc.T("settings.bind.updated");
    }

    private void Defaults_Click(object sender, RoutedEventArgs e)
    {
        int slot = Math.Max(0, SlotBox.SelectedIndex);
        var defaults = KeyBindings.DefaultForSlot(slot);
        if (LayerBox.SelectedIndex == 1) App.Settings.OutputBindings[slot] = defaults;
        else App.Settings.Bindings[slot] = defaults;
        BuildBindingButtons();
    }

    // ---------------- capture target ----------------
    private void UpdateTargetText()
    {
        var label = App.Settings.CaptureLabel;
        TargetText.Text = string.IsNullOrWhiteSpace(label) ? Loc.T("settings.capture.nothing") : label;
    }

    /// <summary>Opens the Cheat Engine style process list.</summary>
    private void PickProcess_Click(object sender, RoutedEventArgs e)
    {
        var picker = new ProcessPickerWindow { Owner = Window.GetWindow(this) };
        if (picker.ShowDialog() != true || picker.Selected == null) return;

        var target = picker.Selected;
        var s = App.Settings;
        s.CaptureProcessId = target.ProcessId;
        s.CaptureProcessName = target.ProcessName;
        s.CaptureHandle = target.Handle.ToInt64();
        s.CaptureWindowTitle = target.Title;
        s.CaptureLabel = target.Display;
        UpdateTargetText();
    }

    private void ClearProcess_Click(object sender, RoutedEventArgs e)
    {
        var s = App.Settings;
        s.CaptureProcessId = 0;
        s.CaptureProcessName = "";
        s.CaptureHandle = 0;
        s.CaptureLabel = "";
        UpdateTargetText();
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
                    ? System.IO.Path.GetFileName(path) + "  " + Loc.T("settings.themes.invalid")
                    : $"{manifest.Name}  v{manifest.Version}  by {manifest.Author}"
            };
        }).ToList();
        ThemeList.ItemsSource = entries;
    }

    private void ApplyTheme_Click(object sender, RoutedEventArgs e)
    {
        if (ThemeList.SelectedItem is not ThemeEntry entry)
        {
            MessageText.Text = Loc.T("settings.themes.select");
            return;
        }
        MessageText.Text = App.Theme.TryApplyFile(entry.Path, out var error)
            ? Loc.T("settings.themes.applied")
            : "* " + error;
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
            MessageText.Text = App.Theme.TryApplyFile(imported, out var error)
                ? Loc.T("settings.themes.imported")
                : "* " + error;
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
        MessageText.Text = Loc.T("settings.themes.restored");
    }

    private void Volume_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_loading) return;
        UpdateVolumeLabel();
        App.Theme.SetVolume(VolumeSlider.Value / 100.0);
    }

    private void UpdateVolumeLabel()
    {
        if (VolumeLabel == null) return;
        VolumeLabel.Text = Loc.F("settings.themes.volume", (int)VolumeSlider.Value);
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
        s.MusicEnabled = MusicBox.IsChecked == true;
        s.MusicVolume = VolumeSlider.Value / 100.0;
        s.ServerUrl = ServerBox.Text.Trim().TrimEnd('/');
        s.RememberMe = RememberBox.IsChecked == true;
        if (!s.RememberMe) s.Token = "";

        var language = LanguageBox.SelectedIndex == 1 ? "ru" : "en";
        bool languageChanged = language != s.Language;
        s.Language = language;
        s.Save();

        App.Theme.RefreshMusicState();

        if (languageChanged)
        {
            // Re-create the whole view tree so every {loc:Tr} is re-evaluated.
            Loc.SetLanguage(language);
            MainWindow.Instance.RefreshHeader();
            MainWindow.Instance.Navigate(new SettingsView());
            MainWindow.Instance.SetStatus(Loc.T("settings.saved.status"));
            return;
        }

        MessageText.Text = Loc.T("settings.saved");
        MainWindow.Instance.SetStatus(Loc.T("settings.saved.status"));
    }

    private void Back_Click(object sender, RoutedEventArgs e)
    {
        MainWindow.Instance.Navigate(App.User == null ? new LoginView() : (UserControl)new LobbyBrowserView());
    }
}
