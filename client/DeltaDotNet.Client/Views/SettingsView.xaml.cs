using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using DeltaDotNet.Client.Core;
using Microsoft.Win32;

namespace DeltaDotNet.Client.Views
{
    /// <summary>Client settings: quality, key bindings, themes and account.</summary>
    public partial class SettingsView : UserControl
    {
        private bool _loading = true;
        private int _slotShown = 1;

        public SettingsView()
        {
            InitializeComponent();

            var q = AppConfig.Current.Quality;
            SelectTag(PresetBox, q.Preset);
            FpsSlider.Value = q.Fps;
            ScaleSlider.Value = q.Scale;
            JpegSlider.Value = q.JpegQuality;
            CursorCheck.IsChecked = q.CaptureCursor;
            SkipCheck.IsChecked = q.SkipIdenticalFrames;
            StatsCheck.IsChecked = AppConfig.Current.ShowStreamStats;
            FocusCheck.IsChecked = AppConfig.Current.FocusGameOnStart;
            SelectTag(ModeBox, q.CaptureMode);
            TitleBox.Text = q.WindowTitle;
            RegXBox.Text = q.RegionX.ToString();
            RegYBox.Text = q.RegionY.ToString();
            RegWBox.Text = q.RegionW.ToString();
            RegHBox.Text = q.RegionH.ToString();

            for (int i = 1; i <= 8; i++)
                SlotBox.Items.Add(new ComboBoxItem { Content = Lang.F("set.slot", i), Tag = i.ToString() });
            SlotBox.SelectedIndex = 0;

            // language selector (English first, saved choice preselected)
            foreach (var l in Lang.Available)
                LanguageBox.Items.Add(new ComboBoxItem { Content = l.Title, Tag = l.Code });
            SelectTag(LanguageBox, Lang.Current);

            VolumeSlider.Value = AppConfig.Current.MusicVolume * 100;
            MusicCheck.IsChecked = AppConfig.Current.MusicEnabled;
            ScaleUiCheck.IsChecked = AppConfig.Current.ScaleUiToWindow;

            _loading = false;
            ApplyLang();
            Lang.Changed += ApplyLang;
            Unloaded += (s, e) => { Lang.Changed -= ApplyLang; };

            RefreshLabels();
            BuildMyBinds();
            BuildSlotBinds();
            RefreshThemeInfo();
        }

        // ------------------------------------------------------------ language
        private void Language_Changed(object sender, SelectionChangedEventArgs e)
        {
            if (_loading) return;
            var code = TagOf(LanguageBox);
            if (string.IsNullOrEmpty(code) || code == Lang.Current) return;
            Lang.Current = code;                       // fires Lang.Changed -> every open view re-localizes
            AppConfig.Current.Language = code;
            AppConfig.Save();
            MainWindow.Instance.ApplyLang();
        }

        /// <summary>Localizes every static caption on the settings screen.</summary>
        private void ApplyLang()
        {
            TabQuality.Header = Lang.T("set.tabQuality");
            TabMyKeys.Header = Lang.T("set.tabMyKeys");
            TabModKeys.Header = Lang.T("set.tabModKeys");
            TabThemes.Header = Lang.T("set.tabTheme");
            TabAccount.Header = Lang.T("set.tabAccount");

            QualityTitle.Text = Lang.T("set.quality.title");
            PresetLabel.Text = Lang.T("set.preset");
            CursorCheck.Content = Lang.T("set.drawCursor");
            SkipCheck.Content = Lang.T("set.skipIdentical");
            StatsCheck.Content = Lang.T("set.showStats");
            FocusCheck.Content = Lang.T("set.focusGame");
            CaptureTitle.Text = Lang.T("set.capture.title");
            ModeWindowItem.Content = Lang.T("set.capWindow");
            ModeScreenItem.Content = Lang.T("set.capScreen");
            ModeRegionItem.Content = Lang.T("set.capRegion");
            PickWindowBtn.Content = Lang.T("set.pickWindow");
            TestCaptureBtn.Content = Lang.T("set.testCapture");

            MyKeysTitle.Text = Lang.T("set.myKeys.title");
            MyKeysNote.Text = Lang.T("set.myKeys.note");
            ResetMyBindsBtn.Content = Lang.T("set.resetDefaults");

            ModKeysTitle.Text = Lang.T("set.modKeys.title");
            ResetSlotsBtn.Content = Lang.T("set.resetDefaults");

            LanguageLabel.Text = Lang.T("set.language");
            ThemeTitle.Text = Lang.T("set.theme.title");
            LoadThemeBtn.Content = Lang.T("set.theme.load");
            ResetThemeBtn.Content = Lang.T("set.theme.reset");
            MusicCheck.Content = Lang.T("set.music.enabled");
            ScaleUiCheck.Content = Lang.T("set.scaleUi");

            AccountTitle.Text = Lang.T("set.account.title");
            OldPassLabel.Text = Lang.T("login.password");
            NewPassLabel.Text = Lang.T("login.password");
            ChangePassBtn.Content = Lang.T("common.save");
            AccountInfo.Text = Lang.F("set.account.info", Session.Display, Session.Rank) +
                               (Session.IsAdmin ? "  " + Lang.T("set.account.admin") : "");

            RefreshLabels();
        }

        // ------------------------------------------------------------ quality
        private void RefreshLabels()
        {
            FpsLabel.Text = Lang.F("set.fps", (int)FpsSlider.Value);
            ScaleLabel.Text = Lang.F("set.scale", (int)ScaleSlider.Value);
            JpegLabel.Text = Lang.F("set.jpeg", (int)JpegSlider.Value);
            VolumeLabel.Text = Lang.F("set.music.volume", (int)VolumeSlider.Value);
            RegionPanel.Visibility = TagOf(ModeBox) == "Region" ? Visibility.Visible : Visibility.Collapsed;
            TitleBox.Visibility = TagOf(ModeBox) == "Window" ? Visibility.Visible : Visibility.Collapsed;
        }

        private void Preset_Changed(object sender, SelectionChangedEventArgs e)
        {
            if (_loading) return;
            var tag = TagOf(PresetBox);
            if (tag == "Custom" || string.IsNullOrEmpty(tag)) { SaveQuality(); return; }
            var preset = QualitySettings.FromPreset(tag);
            _loading = true;
            FpsSlider.Value = preset.Fps;
            ScaleSlider.Value = preset.Scale;
            JpegSlider.Value = preset.JpegQuality;
            _loading = false;
            SaveQuality();
        }

        private void Quality_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading) return;
            // moving a slider by hand switches the preset to Custom
            if (sender == FpsSlider || sender == ScaleSlider || sender == JpegSlider)
                SelectTag(PresetBox, "Custom");
            SaveQuality();
        }

        private void SaveQuality()
        {
            var q = AppConfig.Current.Quality;
            q.Fps = (int)FpsSlider.Value;
            q.Scale = (int)ScaleSlider.Value;
            q.JpegQuality = (int)JpegSlider.Value;
            q.CaptureCursor = CursorCheck.IsChecked == true;
            q.SkipIdenticalFrames = SkipCheck.IsChecked == true;
            q.CaptureMode = TagOf(ModeBox);
            q.WindowTitle = TitleBox.Text;
            q.Preset = TagOf(PresetBox);
            int v;
            if (int.TryParse(RegXBox.Text, out v)) q.RegionX = v;
            if (int.TryParse(RegYBox.Text, out v)) q.RegionY = v;
            if (int.TryParse(RegWBox.Text, out v)) q.RegionW = v;
            if (int.TryParse(RegHBox.Text, out v)) q.RegionH = v;

            AppConfig.Current.ShowStreamStats = StatsCheck.IsChecked == true;
            AppConfig.Current.FocusGameOnStart = FocusCheck.IsChecked == true;
            AppConfig.Save();
            RefreshLabels();
        }

        private void PickWindow_Click(object sender, RoutedEventArgs e)
        {
            var titles = ScreenCapture.ListWindowTitles();
            var pick = new Window
            {
                Title = "Pick the game window",
                Width = 560,
                Height = 460,
                WindowStartupLocation = WindowStartupLocation.CenterScreen,
                Owner = Application.Current.MainWindow,
                Background = (Brush)Application.Current.Resources["DdnPanelBrush"]
            };
            var list = new ListBox { Style = (Style)Application.Current.Resources["DdnList"], Margin = new Thickness(10) };
            foreach (var t in titles) list.Items.Add(t);
            list.MouseDoubleClick += (s, ev) =>
            {
                if (list.SelectedItem != null)
                {
                    TitleBox.Text = Convert.ToString(list.SelectedItem);
                    SaveQuality();
                }
                pick.Close();
            };
            pick.Content = list;
            pick.ShowDialog();
        }

        private void TestCapture_Click(object sender, RoutedEventArgs e)
        {
            SaveQuality();
            int w, h;
            var jpeg = ScreenCapture.CaptureJpeg(AppConfig.Current.Quality, out w, out h);
            if (jpeg == null)
            {
                MessageBox.Show("Capture failed. If you picked \"Game window\", make sure the game is running and the title matches.",
                    "DeltaDotNet");
                return;
            }
            var bmp = new BitmapImage();
            bmp.BeginInit();
            bmp.CacheOption = BitmapCacheOption.OnLoad;
            bmp.StreamSource = new MemoryStream(jpeg);
            bmp.EndInit();
            bmp.Freeze();
            PreviewImage.Source = bmp;
            MainWindow.Instance.SetStatus("test frame: " + w + "x" + h + ", " + (jpeg.Length / 1024) + " KB");
        }

        // ------------------------------------------------------------ my keys
        private void BuildMyBinds()
        {
            MyBindsPanel.Children.Clear();
            foreach (var action in Keybinds.Actions)
            {
                string bound;
                if (!AppConfig.Current.MyBinds.TryGetValue(action, out bound)) bound = "";
                MyBindsPanel.Children.Add(BuildBindRow(Keybinds.ActionTitles[action], bound, key =>
                {
                    AppConfig.Current.MyBinds[action] = key;
                    AppConfig.Save();
                    BuildMyBinds();
                }));
            }
        }

        private void ResetMyBinds_Click(object sender, RoutedEventArgs e)
        {
            AppConfig.Current.MyBinds = Keybinds.DefaultLocalBinds();
            AppConfig.Save();
            BuildMyBinds();
        }

        // ------------------------------------------------------------ mod keys
        private void Slot_Changed(object sender, SelectionChangedEventArgs e)
        {
            int.TryParse(TagOf(SlotBox), out _slotShown);
            if (_slotShown < 1) _slotShown = 1;
            BuildSlotBinds();
        }

        private void BuildSlotBinds()
        {
            if (SlotBindsPanel == null) return;
            SlotBindsPanel.Children.Clear();

            Dictionary<string, string> map;
            var key = _slotShown.ToString();
            if (!AppConfig.Current.SlotGameKeys.TryGetValue(key, out map))
            {
                map = new Dictionary<string, string>();
                AppConfig.Current.SlotGameKeys[key] = map;
            }

            foreach (var action in Keybinds.Actions)
            {
                string bound;
                if (!map.TryGetValue(action, out bound)) bound = "";
                var localAction = action;
                SlotBindsPanel.Children.Add(BuildBindRow(Keybinds.ActionTitles[action], bound, k =>
                {
                    map[localAction] = k;
                    AppConfig.Save();
                    BuildSlotBinds();
                }));
            }
        }

        private void ResetSlots_Click(object sender, RoutedEventArgs e)
        {
            AppConfig.Current.SlotGameKeys = Keybinds.DefaultSlotGameKeys();
            AppConfig.Save();
            BuildSlotBinds();
        }

        /// <summary>One "Action .......... [KEY]" row with a "press a key" capture button.</summary>
        private UIElement BuildBindRow(string title, string currentKey, Action<string> onPicked)
        {
            var grid = new Grid { Margin = new Thickness(0, 0, 0, 6) };
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(260) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var label = new TextBlock
            {
                Text = title,
                Style = (Style)FindResource("DdnText"),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(label, 0);
            grid.Children.Add(label);

            var value = new TextBlock
            {
                Text = Keybinds.Pretty(currentKey),
                Style = (Style)FindResource("DdnText"),
                Foreground = (Brush)FindResource("DdnAccentBrush"),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(value, 1);
            grid.Children.Add(value);

            var tools = new StackPanel { Orientation = Orientation.Horizontal };
            var setBtn = new Button { Content = "SET", Style = (Style)FindResource("DdnButton") };
            setBtn.Click += (s, e) =>
            {
                var picked = KeyCaptureDialog.Capture(title);
                if (picked != null) onPicked(picked);
            };
            var clearBtn = new Button { Content = "CLEAR", Style = (Style)FindResource("DdnButton") };
            clearBtn.Click += (s, e) => onPicked("");
            tools.Children.Add(setBtn);
            tools.Children.Add(clearBtn);
            Grid.SetColumn(tools, 2);
            grid.Children.Add(tools);

            return grid;
        }

        // ------------------------------------------------------------ themes
        private void RefreshThemeInfo()
        {
            var m = ThemeEngine.CurrentManifest;
            ThemeInfo.Text = m == null
                ? "Current theme: built in \"Dark World\"."
                : "Current theme: " + m.Name + " v" + m.Version +
                  (string.IsNullOrEmpty(m.Author) ? "" : " by " + m.Author) +
                  "\n" + m.Description;
        }

        private void LoadTheme_Click(object sender, RoutedEventArgs e)
        {
            var dlg = new OpenFileDialog
            {
                Filter = "DeltaDotNet theme (*.ddntheme)|*.ddntheme|All files (*.*)|*.*",
                InitialDirectory = AppConfig.ThemesDir
            };
            if (dlg.ShowDialog() != true) return;
            try
            {
                ThemeEngine.Apply(dlg.FileName);
                RefreshThemeInfo();
                MainWindow.Instance.LoadBranding();
                MainWindow.Instance.SetStatus("theme applied");
            }
            catch (Exception ex)
            {
                MessageBox.Show("Could not load the theme: " + ex.Message, "DeltaDotNet");
            }
        }

        private void OpenThemes_Click(object sender, RoutedEventArgs e)
        {
            Directory.CreateDirectory(AppConfig.ThemesDir);
            Process.Start(new ProcessStartInfo("explorer.exe", AppConfig.ThemesDir));
        }

        private void ResetTheme_Click(object sender, RoutedEventArgs e)
        {
            ThemeEngine.ResetToDefault();
            RefreshThemeInfo();
            MainWindow.Instance.LoadBranding();
        }

        private void Volume_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (_loading) return;
            ThemeEngine.SetVolume(VolumeSlider.Value / 100.0);
            AppConfig.Save();
            RefreshLabels();
        }

        private void Music_Changed(object sender, RoutedEventArgs e)
        {
            AppConfig.Current.MusicEnabled = MusicCheck.IsChecked == true;
            AppConfig.Save();
            if (!AppConfig.Current.MusicEnabled) ThemeEngine.StopMusic();
            else if (!string.IsNullOrEmpty(AppConfig.Current.ThemePath) && File.Exists(AppConfig.Current.ThemePath))
                ThemeEngine.Apply(AppConfig.Current.ThemePath);
        }

        private void ScaleUi_Changed(object sender, RoutedEventArgs e)
        {
            AppConfig.Current.ScaleUiToWindow = ScaleUiCheck.IsChecked == true;
            AppConfig.Save();
            MainWindow.Instance.RootBox.Stretch = AppConfig.Current.ScaleUiToWindow ? Stretch.Uniform : Stretch.Fill;
        }

        // ------------------------------------------------------------ account
        private async void ChangePass_Click(object sender, RoutedEventArgs e)
        {
            AccountStatus.Text = "changing...";
            var r = await ApiClient.ChangePasswordAsync(Session.Token, OldPass.Password, NewPass.Password);
            AccountStatus.Text = r.Ok ? "password changed" : "error: " + r.Error;
            OldPass.Clear();
            NewPass.Clear();
        }

        // ------------------------------------------------------------ helpers
        private static string TagOf(ComboBox box)
        {
            var item = box.SelectedItem as ComboBoxItem;
            return item == null ? "" : Convert.ToString(item.Tag);
        }

        private static void SelectTag(ComboBox box, string tag)
        {
            foreach (var o in box.Items)
            {
                var item = o as ComboBoxItem;
                if (item != null && string.Equals(Convert.ToString(item.Tag), tag, StringComparison.OrdinalIgnoreCase))
                {
                    box.SelectedItem = item;
                    return;
                }
            }
            if (box.Items.Count > 0 && box.SelectedItem == null) box.SelectedIndex = 0;
        }
    }

    /// <summary>Modal "press any key" dialog used by the rebinding UI.</summary>
    public static class KeyCaptureDialog
    {
        public static string Capture(string what)
        {
            string result = null;
            var win = new Window
            {
                Title = "DeltaDotNet",
                Width = 460,
                Height = 180,
                WindowStartupLocation = WindowStartupLocation.CenterScreen,
                ResizeMode = ResizeMode.NoResize,
                Owner = Application.Current.MainWindow,
                Background = (Brush)Application.Current.Resources["DdnPanelBrush"]
            };
            var tb = new TextBlock
            {
                Text = "Press the key for:\n" + what + "\n\n(Esc = cancel)",
                TextAlignment = TextAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
                Style = (Style)Application.Current.Resources["DdnText"]
            };
            win.Content = tb;
            win.PreviewKeyDown += (s, e) =>
            {
                var key = e.Key == Key.System ? e.SystemKey : e.Key;
                e.Handled = true;
                if (key == Key.Escape) { win.Close(); return; }
                result = key.ToString();
                win.Close();
            };
            win.ShowDialog();
            return result;
        }
    }
}
