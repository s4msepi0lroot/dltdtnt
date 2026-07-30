using System.Diagnostics;
using System.Text.Json;
using CoopStream.Client.Capture;
using CoopStream.Client.Input;
using CoopStream.Client.Net;

namespace CoopStream.Client.Forms;

/// <summary>
/// Окно хоста: захватывает экран/окно игры, шлёт кадры на сервер и инжектирует
/// клавиши второго игрока в активное окно через SendInput.
/// Своими клавишами хост играет напрямую — перехват ему не нужен.
/// </summary>
public sealed class HostForm : Form
{
    private readonly RelayClient _client;
    private readonly AppConfig _config;
    private readonly string _hostRole;
    private readonly string _guestRole;
    private readonly ScreenCapturer _capturer = new();
    private readonly InputInjector _injector = new();

    private readonly ComboBox _cmbWindow = new() { Width = 320, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly Button _btnRefreshWindows = new() { Text = "↻", Width = 34 };
    private readonly NumericUpDown _numFps = new() { Minimum = 5, Maximum = 60, Width = 60 };
    private readonly NumericUpDown _numQuality = new() { Minimum = 20, Maximum = 95, Width = 60 };
    private readonly NumericUpDown _numWidth = new() { Minimum = 320, Maximum = 1920, Increment = 160, Width = 70 };
    private readonly CheckBox _chkPause = new() { Text = "Пауза ввода гостя (F8)", AutoSize = true };
    private readonly Label _lblStats = new() { AutoSize = true, Text = "—" };
    private readonly Label _lblInfo = new() { AutoSize = true, ForeColor = Color.DimGray };

    private CancellationTokenSource _cts;
    private long _framesSent;
    private long _bytesSent;
    private readonly Stopwatch _uptime = Stopwatch.StartNew();
    private volatile bool _sending;

    public HostForm(RelayClient client, AppConfig config, string hostRole)
    {
        _client = client;
        _config = config;
        _hostRole = hostRole;
        _guestRole = hostRole == "P1" ? "P2" : "P1";

        Text = "CoopStream — хост (трансляция)";
        Width = 470;
        Height = 290;
        TopMost = true;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.Manual;
        Location = new Point(20, 20);
        KeyPreview = true;

        _numFps.Value = Math.Clamp(config.Fps, 5, 60);
        _numQuality.Value = Math.Clamp(config.JpegQuality, 20, 95);
        _numWidth.Value = Math.Clamp(config.MaxWidth, 320, 1920);
        _lblInfo.Text = $"Вы играете как {_hostRole} напрямую. Гость ({_guestRole}): {KeyPolicy.Describe(_guestRole)}";

        BuildLayout();
        RefreshWindows();

        _btnRefreshWindows.Click += (_, _) => RefreshWindows();
        _cmbWindow.SelectedIndexChanged += (_, _) =>
        {
            if (_cmbWindow.SelectedItem is WindowList.WindowInfo w) _capturer.TargetWindow = w.Handle;
        };
        _chkPause.CheckedChanged += (_, _) => { if (_chkPause.Checked) _injector.ReleaseAll(); };
        KeyDown += (_, e) => { if (e.KeyCode == Keys.F8) _chkPause.Checked = !_chkPause.Checked; };

        _client.OnJson += HandleJsonFromBackground;

        var uiTimer = new System.Windows.Forms.Timer { Interval = 1000 };
        uiTimer.Tick += (_, _) => UpdateStats();
        uiTimer.Start();

        FormClosing += (_, _) =>
        {
            _cts?.Cancel();
            _client.OnJson -= HandleJsonFromBackground;
            _injector.ReleaseAll();
            uiTimer.Stop();
            _config.Fps = (int)_numFps.Value;
            _config.JpegQuality = (int)_numQuality.Value;
            _config.MaxWidth = (int)_numWidth.Value;
            _config.Save();
            _ = _client.SendJsonAsync(new { t = "stop" });
            _capturer.Dispose();
        };

        Shown += (_, _) => StartCaptureLoop();
    }

    private void BuildLayout()
    {
        var root = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false, Padding = new Padding(10) };
        root.Controls.Add(Row(Lbl("Источник:"), _cmbWindow, _btnRefreshWindows));
        root.Controls.Add(Row(Lbl("FPS:"), _numFps, Lbl("Качество:"), _numQuality, Lbl("Ширина:"), _numWidth));
        root.Controls.Add(_chkPause);
        root.Controls.Add(_lblStats);
        root.Controls.Add(_lblInfo);
        root.Controls.Add(new Label
        {
            AutoSize = false,
            Width = 430,
            Height = 46,
            ForeColor = Color.DimGray,
            Text = "Важно: клавиши гостя попадают в АКТИВНОЕ окно Windows. Держите игру в фокусе, а это окно — в стороне. Лучше всего работает оконный (borderless) режим игры.",
        });
        Controls.Add(root);
    }

    private static Label Lbl(string text) => new() { Text = text, AutoSize = true, Padding = new Padding(0, 6, 4, 0) };

    private static Control Row(params Control[] controls)
    {
        var p = new FlowLayoutPanel { FlowDirection = FlowDirection.LeftToRight, AutoSize = true, WrapContents = false };
        p.Controls.AddRange(controls);
        return p;
    }

    private void RefreshWindows()
    {
        var current = (_cmbWindow.SelectedItem as WindowList.WindowInfo)?.Handle ?? IntPtr.Zero;
        _cmbWindow.Items.Clear();
        foreach (var w in WindowList.Enumerate()) _cmbWindow.Items.Add(w);
        var index = 0;
        for (var i = 0; i < _cmbWindow.Items.Count; i++)
            if (((WindowList.WindowInfo)_cmbWindow.Items[i]).Handle == current) index = i;
        _cmbWindow.SelectedIndex = index;
    }

    /// <summary>Цикл захвата в фоновом потоке, чтобы не блокировать UI.</summary>
    private void StartCaptureLoop()
    {
        _cts = new CancellationTokenSource();
        var token = _cts.Token;
        var fps = (int)_numFps.Value;
        _numFps.ValueChanged += (_, _) => fps = (int)_numFps.Value;
        _numQuality.ValueChanged += (_, _) => _capturer.Quality = (int)_numQuality.Value;
        _numWidth.ValueChanged += (_, _) => _capturer.MaxWidth = (int)_numWidth.Value;
        _capturer.Quality = (int)_numQuality.Value;
        _capturer.MaxWidth = (int)_numWidth.Value;

        Task.Run(async () =>
        {
            while (!token.IsCancellationRequested)
            {
                var started = Environment.TickCount64;
                if (!_sending)
                {
                    var packet = _capturer.CaptureFrame();
                    if (packet != null)
                    {
                        _sending = true;
                        try
                        {
                            await _client.SendBinaryAsync(packet);
                            Interlocked.Increment(ref _framesSent);
                            Interlocked.Add(ref _bytesSent, packet.Length);
                        }
                        finally { _sending = false; }
                    }
                }
                var elapsed = (int)(Environment.TickCount64 - started);
                var delay = Math.Max(1, 1000 / Math.Max(1, fps) - elapsed);
                try { await Task.Delay(delay, token); } catch (TaskCanceledException) { break; }
            }
        }, token);
    }

    private void UpdateStats()
    {
        var seconds = Math.Max(1, _uptime.Elapsed.TotalSeconds);
        var mbit = _bytesSent * 8 / seconds / 1_000_000;
        _lblStats.Text = $"Кадров: {_framesSent} | трафик: {_bytesSent / 1_048_576.0:F1} МБ (~{mbit:F1} Мбит/с) | зажато клавиш гостя: {_injector.HeldCount}";
    }

    /// <summary>Ввод гостя обрабатывается сразу в фоновом потоке — так меньше задержка.</summary>
    private void HandleJsonFromBackground(JsonElement msg)
    {
        var type = msg.TryGetProperty("t", out var t) ? t.GetString() : null;
        switch (type)
        {
            case "input":
            {
                if (_chkPause.Checked) return;
                var key = msg.GetProperty("key").GetString();
                var down = msg.GetProperty("down").GetBoolean();
                if (key == null || !KeyPolicy.IsAllowed(_guestRole, key)) return;
                _injector.Send(key, down);
                return;
            }
            case "release_all":
            case "peer_left":
            case "lobby_closed":
            case "stopped":
                _injector.ReleaseAll();
                return;
        }
    }
}
