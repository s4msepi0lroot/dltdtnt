using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using DeltaDotNet.Client.Core;

namespace DeltaDotNet.Client.Views
{
    /// <summary>
    /// The play screen.
    ///
    /// HOST:  captures the game window, streams JPEG frames to the server and
    ///        turns every "input" message coming back from the guests into a
    ///        real key press inside the game window.
    /// GUEST: shows the incoming frames and forwards its own keyboard to the
    ///        host as logical actions (Up / Confirm / Cancel / ...).
    /// </summary>
    public partial class GameView : UserControl
    {
        private readonly Streamer _streamer = new Streamer();
        private readonly DispatcherTimer _hud = new DispatcherTimer();
        private readonly HashSet<string> _pressed = new HashSet<string>();

        private Window _window;
        private bool _grabKeyboard = true;
        private int _seq;

        // incoming stream counters (guest side)
        private int _inFrames;
        private long _inBytes;
        private int _inFps;
        private int _inKb;
        private DateTime _second = DateTime.UtcNow;
        private int _lastW;
        private int _lastH;

        public GameView()
        {
            InitializeComponent();
            Loaded += OnLoaded;
            Unloaded += OnUnloaded;
        }

        // ------------------------------------------------------------ lifetime
        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            Session.Net.Message += OnMessage;
            Session.Net.Frame += OnFrame;

            _window = Window.GetWindow(this);
            if (_window != null)
            {
                _window.PreviewKeyDown += OnWindowKeyDown;
                _window.PreviewKeyUp += OnWindowKeyUp;
            }

            StatsBox.Visibility = AppConfig.Current.ShowStreamStats ? Visibility.Visible : Visibility.Collapsed;
            FocusBtn.Visibility = Session.IsHost ? Visibility.Visible : Visibility.Collapsed;
            StopBtn.Visibility = Session.IsHost ? Visibility.Visible : Visibility.Collapsed;
            GrabBtn.Visibility = Session.IsHost ? Visibility.Collapsed : Visibility.Visible;

            var lobby = Session.Lobby;
            TitleText.Text = "* " + (lobby == null ? "THE GAME" : lobby.Name.ToUpperInvariant());
            SubText.Text = Session.IsHost
                ? "You are the host. Keep the game window focused - the keys of the guests are injected into it."
                : "You are player " + Session.MySlot + ". Keep this window focused and play on your own keyboard.";

            if (Session.IsHost)
            {
                if (AppConfig.Current.FocusGameOnStart) FocusGame(false);
                _streamer.Start(Session.Net);
            }

            _hud.Interval = TimeSpan.FromMilliseconds(500);
            _hud.Tick += OnHudTick;
            _hud.Start();

            Focusable = true;
            Focus();
            Keyboard.Focus(this);
        }

        private void OnUnloaded(object sender, RoutedEventArgs e)
        {
            Session.Net.Message -= OnMessage;
            Session.Net.Frame -= OnFrame;

            if (_window != null)
            {
                _window.PreviewKeyDown -= OnWindowKeyDown;
                _window.PreviewKeyUp -= OnWindowKeyUp;
                _window = null;
            }

            _hud.Stop();
            _hud.Tick -= OnHudTick;

            _streamer.Stop();
            ReleaseEverything();
            try { InputInjector.ReleaseAll(); } catch { }
        }

        // ------------------------------------------------------------ network
        private void OnMessage(JsonElement msg)
        {
            var t = Json.Str(msg, "t");
            switch (t)
            {
                // host only: a guest pressed or released one of its keys
                case "input":
                    if (!Session.IsHost) break;
                    try
                    {
                        InputInjector.Send(Json.Int(msg, "slot", 0),
                                           Json.Str(msg, "action"),
                                           Json.Bool(msg, "down"));
                    }
                    catch { }
                    break;

                case "lobby.state":
                    Session.Lobby = LobbyInfo.Parse(Json.Obj(msg, "lobby"));
                    foreach (var m in Session.Lobby.Members)
                        if (string.Equals(m.Login, Session.Login, StringComparison.OrdinalIgnoreCase))
                        {
                            Session.MySlot = m.Slot;
                            Session.IsHost = m.IsHost;
                        }
                    break;

                case "game.stopped":
                    Dispatcher.Invoke(() =>
                    {
                        _streamer.Stop();
                        MainWindow.Instance.SetStatus("the game was stopped by the host");
                        MainWindow.Instance.Navigate(new LobbyRoomView());
                    });
                    break;
            }
        }

        private void OnFrame(byte[] jpeg, long timestamp)
        {
            _inFrames++;
            _inBytes += jpeg.Length;

            try
            {
                Dispatcher.Invoke(() =>
                {
                    var bmp = Decode(jpeg);
                    if (bmp == null) return;
                    _lastW = bmp.PixelWidth;
                    _lastH = bmp.PixelHeight;
                    Screen.Source = bmp;
                    if (WaitText.Visibility == Visibility.Visible)
                        WaitText.Visibility = Visibility.Collapsed;
                }, DispatcherPriority.Render);
            }
            catch { }
        }

        private static BitmapSource Decode(byte[] jpeg)
        {
            try
            {
                var bmp = new BitmapImage();
                bmp.BeginInit();
                bmp.CacheOption = BitmapCacheOption.OnLoad;
                bmp.StreamSource = new MemoryStream(jpeg);
                bmp.EndInit();
                bmp.Freeze();
                return bmp;
            }
            catch { return null; }
        }

        // ------------------------------------------------------------ keyboard
        private void OnWindowKeyDown(object sender, KeyEventArgs e)
        {
            if (Session.IsHost) return;          // the host plays on the game window itself
            if (!_grabKeyboard) return;
            if (e.IsRepeat) { e.Handled = true; return; }

            var key = e.Key == Key.System ? e.SystemKey : e.Key;
            var action = Keybinds.ActionForKey(AppConfig.Current.MyBinds, key);
            if (action == null) return;

            e.Handled = true;
            if (_pressed.Contains(action)) return;
            _pressed.Add(action);
            SendInput(action, true);
        }

        private void OnWindowKeyUp(object sender, KeyEventArgs e)
        {
            if (Session.IsHost) return;
            if (!_grabKeyboard) return;

            var key = e.Key == Key.System ? e.SystemKey : e.Key;
            var action = Keybinds.ActionForKey(AppConfig.Current.MyBinds, key);
            if (action == null) return;

            e.Handled = true;
            if (!_pressed.Remove(action)) return;
            SendInput(action, false);
        }

        private void SendInput(string action, bool down)
        {
            _seq++;
            _ = Session.Net.SendAsync(new { t = "input", action, down, seq = _seq });
        }

        /// <summary>Releases every action we still hold, so nothing gets stuck.</summary>
        private void ReleaseEverything()
        {
            if (_pressed.Count == 0) return;
            var copy = new List<string>(_pressed);
            _pressed.Clear();
            foreach (var a in copy) SendInput(a, false);
        }

        // ------------------------------------------------------------ hud
        private void OnHudTick(object sender, EventArgs e)
        {
            if ((DateTime.UtcNow - _second).TotalMilliseconds >= 1000)
            {
                _inFps = _inFrames;
                _inKb = (int)(_inBytes / 1024);
                _inFrames = 0;
                _inBytes = 0;
                _second = DateTime.UtcNow;
            }

            if (StatsBox.Visibility == Visibility.Visible)
            {
                StatsText.Text = Session.IsHost
                    ? "out " + _streamer.Fps + " fps   " + _streamer.KbPerSec + " KB/s   " +
                      _streamer.LastWidth + "x" + _streamer.LastHeight +
                      "   preset: " + AppConfig.Current.Quality.Preset
                    : "in " + _inFps + " fps   " + _inKb + " KB/s   " + _lastW + "x" + _lastH +
                      "   slot: P" + Session.MySlot;
            }

            var err = Session.IsHost ? _streamer.LastError : null;
            if (!string.IsNullOrEmpty(err))
            {
                ErrText.Text = "! " + err;
                ErrText.Visibility = Visibility.Visible;
            }
            else if (ErrText.Visibility == Visibility.Visible)
            {
                ErrText.Visibility = Visibility.Collapsed;
            }
        }

        // ------------------------------------------------------------ buttons
        private void Grab_Click(object sender, RoutedEventArgs e)
        {
            _grabKeyboard = !_grabKeyboard;
            if (!_grabKeyboard) ReleaseEverything();
            GrabBtn.Content = _grabKeyboard ? "RELEASE THE KEYBOARD" : "GRAB THE KEYBOARD";
            MainWindow.Instance.SetStatus(_grabKeyboard
                ? "your keys are sent to the game"
                : "the keyboard is free, the game does not receive your keys");
            if (_grabKeyboard) { Focus(); Keyboard.Focus(this); }
        }

        private void Focus_Click(object sender, RoutedEventArgs e)
        {
            FocusGame(true);
        }

        private void FocusGame(bool report)
        {
            var title = AppConfig.Current.Quality.WindowTitle;
            bool ok = false;
            try { ok = InputInjector.FocusGameWindow(title); } catch { }
            if (!report) return;
            MainWindow.Instance.SetStatus(ok
                ? "the game window is in front now"
                : "window \"" + title + "\" was not found - check Settings > Quality");
        }

        private void Stop_Click(object sender, RoutedEventArgs e)
        {
            _streamer.Stop();
            try { InputInjector.ReleaseAll(); } catch { }
            _ = Session.Net.SendAsync(new { t = "lobby.stop" });
            MainWindow.Instance.Navigate(new LobbyRoomView());
        }

        private void Back_Click(object sender, RoutedEventArgs e)
        {
            ReleaseEverything();
            MainWindow.Instance.Navigate(new LobbyRoomView());
        }
    }
}
