using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using DeltaDotNet.Client.Services;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Views;

/// <summary>
/// The in-game screen.
///  * Host: captures the game window, streams JPEG frames, injects guest keys into the game.
///  * Guest: shows the stream and sends its own key presses (using its personal bindings).
/// </summary>
public partial class GameView : UserControl
{
    private readonly LobbyInfo _lobby;
    private readonly int _slot;
    private readonly bool _isHost;
    private QualitySettings _quality;

    private readonly ScreenCapture _capture = new();
    private readonly DispatcherTimer _captureTimer = new();
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private readonly HashSet<string> _pressed = new();

    private uint _sequence;
    private int _framesSent, _framesReceived;
    private long _bytesReceived;
    private DateTime _lastStats = DateTime.UtcNow;
    private bool _streaming;

    public GameView(LobbyInfo lobby, int slot, bool isHost, QualitySettings quality)
    {
        InitializeComponent();
        _lobby = lobby;
        _slot = slot;
        _isHost = isHost;
        _quality = quality ?? lobby.Quality ?? App.Settings.Quality;

        HeaderText.Text = $"* {lobby.Name}   — you are P{slot + 1}" + (isHost ? " (host, streaming)" : " (guest)");
        StopButton.Visibility = isHost ? Visibility.Visible : Visibility.Collapsed;
        FocusButton.Visibility = isHost ? Visibility.Visible : Visibility.Collapsed;

        App.Relay.VideoFrame += OnVideoFrame;
        App.Relay.InputReceived += OnInputReceived;
        App.Relay.GameStopped += OnGameStopped;
        App.Relay.LobbyClosed += OnLobbyClosed;
        App.Relay.Kicked += OnKicked;

        Loaded += (_, _) => { Focus(); Keyboard.Focus(this); if (isHost) StartStreaming(); };
        Unloaded += (_, _) => Cleanup();

        PreviewKeyDown += OnKeyDownEvent;
        PreviewKeyUp += OnKeyUpEvent;
    }

    // ---------------- host: capture + stream ----------------
    private void StartStreaming()
    {
        _streaming = true;
        _captureTimer.Interval = TimeSpan.FromMilliseconds(1000.0 / Math.Clamp(_quality.Fps, 5, 60));
        _captureTimer.Tick += async (_, _) => await CaptureTickAsync();
        _captureTimer.Start();
        InputInjector.FocusGameWindow(App.Settings);
    }

    private bool _capturing;

    private async Task CaptureTickAsync()
    {
        if (_capturing || !_streaming) return;
        _capturing = true;
        try
        {
            byte[] jpeg = null;
            int width = 0, height = 0;
            await Task.Run(() => { jpeg = _capture.CaptureJpeg(App.Settings, _quality, out width, out height); });

            if (jpeg == null)
            {
                StatsText.Text = $"* Game window \"{App.Settings.CaptureWindowTitle}\" not found — " +
                                  "check Settings > Capture.";
                return;
            }

            await App.Relay.SendFrameAsync(jpeg, _sequence++, width, height);
            _framesSent++;
            UpdateStats($"streaming {width}x{height}");
        }
        catch { }
        finally { _capturing = false; }
    }

    // ---------------- guest: display ----------------
    private void OnVideoFrame(byte[] jpeg, int width, int height) => Dispatcher.BeginInvoke(() =>
    {
        try
        {
            var image = new BitmapImage();
            using var stream = new MemoryStream(jpeg);
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.StreamSource = stream;
            image.EndInit();
            image.Freeze();
            VideoImage.Source = image;
            WaitingText.Visibility = Visibility.Collapsed;
            _framesReceived++;
            _bytesReceived += jpeg.Length;
            UpdateStats($"receiving {width}x{height}");
        }
        catch { }
    });

    private void UpdateStats(string prefix)
    {
        if (!App.Settings.ShowStats) { StatsText.Text = ""; return; }
        var elapsed = (DateTime.UtcNow - _lastStats).TotalSeconds;
        if (elapsed < 1) return;
        var fpsOut = _framesSent / elapsed;
        var fpsIn = _framesReceived / elapsed;
        var kbps = _bytesReceived / 1024.0 / elapsed;
        StatsText.Text = _isHost
            ? $"{prefix} · {fpsOut:0.0} fps out · target {_quality.Fps} fps · scale {_quality.Scale}% · q{_quality.JpegQuality}"
            : $"{prefix} · {fpsIn:0.0} fps in · {kbps:0} KB/s";
        _framesSent = _framesReceived = 0;
        _bytesReceived = 0;
        _lastStats = DateTime.UtcNow;
    }

    // ---------------- input ----------------
    /// <summary>Finds which logical action the pressed key is bound to for this player.</summary>
    private string ActionForKey(Key key)
    {
        int vk = KeyInterop.VirtualKeyFromKey(key);
        var bindings = App.Settings.BindingsFor(_slot);
        foreach (var action in GameAction.All)
        {
            int bound = bindings.Get(action);
            if (bound == 0) continue;
            if (bound == vk) return action;
            // Ctrl / Shift may arrive as the generic key code
            if (bound is KeyBindings.VK_LCONTROL or KeyBindings.VK_RCONTROL && vk == KeyBindings.VK_CONTROL) return action;
        }
        return null;
    }

    private async void OnKeyDownEvent(object sender, KeyEventArgs e)
    {
        if (CaptureInputBox.IsChecked != true) return;
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        var action = ActionForKey(key);
        if (action == null) return;
        e.Handled = true;
        if (!_pressed.Add(action)) return; // ignore auto-repeat
        await SendActionAsync(action, true);
    }

    private async void OnKeyUpEvent(object sender, KeyEventArgs e)
    {
        if (CaptureInputBox.IsChecked != true) return;
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        var action = ActionForKey(key);
        if (action == null) return;
        e.Handled = true;
        _pressed.Remove(action);
        await SendActionAsync(action, false);
    }

    private async Task SendActionAsync(string action, bool down)
    {
        if (_isHost)
        {
            // The host presses keys locally for its own slot.
            InputInjector.SendAction(App.Settings, _slot, action, down);
        }
        else
        {
            await App.Relay.SendInputAsync(action, down);
        }
    }

    /// <summary>Host side: a guest pressed a key, inject it for that guest's player slot.</summary>
    private void OnInputReceived(int slot, string action, bool down)
    {
        if (!_isHost) return;
        InputInjector.SendAction(App.Settings, slot, action, down);
    }

    // ---------------- lifecycle ----------------
    private void OnGameStopped() => Dispatcher.Invoke(() =>
        MainWindow.Instance.Navigate(new LobbyRoomView(_lobby, _slot, _isHost)));

    private void OnLobbyClosed(string message) => Dispatcher.Invoke(() =>
    {
        MainWindow.Instance.SetStatus(message);
        MainWindow.Instance.Navigate(new LobbyBrowserView());
    });

    private void OnKicked(bool banned, string reason) => Dispatcher.Invoke(() =>
        MainWindow.Instance.Navigate(new LobbyBrowserView()));

    private async void Stop_Click(object sender, RoutedEventArgs e) => await App.Relay.StopAsync();

    private void Back_Click(object sender, RoutedEventArgs e) =>
        MainWindow.Instance.Navigate(new LobbyRoomView(_lobby, _slot, _isHost));

    private void FocusGame_Click(object sender, RoutedEventArgs e) => InputInjector.FocusGameWindow(App.Settings);

    private void Cleanup()
    {
        _streaming = false;
        _captureTimer.Stop();
        _capture.Dispose();
        if (_isHost) InputInjector.ReleaseAll(App.Settings);

        App.Relay.VideoFrame -= OnVideoFrame;
        App.Relay.InputReceived -= OnInputReceived;
        App.Relay.GameStopped -= OnGameStopped;
        App.Relay.LobbyClosed -= OnLobbyClosed;
        App.Relay.Kicked -= OnKicked;
    }
}
