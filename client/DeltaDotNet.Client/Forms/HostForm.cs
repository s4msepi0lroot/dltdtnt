using System.Text.Json;
using DeltaDotNet.Client.Capture;
using DeltaDotNet.Client.Input;
using DeltaDotNet.Client.Net;
using DeltaDotNet.Client.Ui;

namespace DeltaDotNet.Client.Forms;

/// <summary>
/// Окно хоста (игрок P1). Здесь запущена сама игра: мы шлём картинку
/// остальным и превращаем их действия в нажатия клавиш через SendInput.
/// Сам хост играет обычным образом, его ввод никуда не пересылается.
/// </summary>
public sealed class HostForm : Form
{
    private readonly RelayClient _client;
    private readonly AppConfig _cfg;
    private readonly ScreenCapturer _capturer = new();
    private readonly System.Windows.Forms.Timer _timer = new();

    /// <summary>Отдельный инжектор на каждую роль — чтобы удержание клавиш не путалось.</summary>
    private readonly Dictionary<string, InputInjector> _injectors = new(StringComparer.Ordinal);

    private readonly ComboBox _windows = new();
    private readonly DeltaListBox _players = new();
    private readonly DeltaListBox _log = new();
    private readonly Label _status = DeltaTheme.Caption("* Ожидание игроков", DeltaTheme.TextDim, DeltaTheme.FontSmall);
    private readonly Label _codeLabel = DeltaTheme.Caption("", DeltaTheme.Accent, DeltaTheme.FontTitle);

    private DeltaButton _btnStart;
    private DeltaButton _btnStop;
    private readonly List<DeltaButton> _keyButtons = new();

    private string _code;
    private int _maxPlayers = 2;
    private bool _running;
    private long _frames;
    private long _bytes;
    private DateTime _startedAt = DateTime.UtcNow;

    public HostForm(RelayClient client, AppConfig cfg, JsonElement lobby)
    {
        _client = client;
        _cfg = cfg;

        Text = "DeltaDotNet — хост";
        ClientSize = new Size(940, 660);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        DeltaTheme.ApplyForm(this);

        Build();
        ApplyLobby(lobby);

        _capturer.MaxWidth = _cfg.MaxWidth;
        _capturer.Quality = _cfg.JpegQuality;
        _timer.Interval = Math.Max(16, 1000 / Math.Max(5, _cfg.Fps));
        _timer.Tick += async (s, e) => await CaptureTickAsync();

        _client.OnJson += HandleJson;
        _client.OnClosed += HandleClosed;

        FormClosed += (s, e) =>
        {
            _timer.Stop();
            foreach (var injector in _injectors.Values) injector.ReleaseAll();
            _client.OnJson -= HandleJson;
            _client.OnClosed -= HandleClosed;
            if (_client.IsConnected) _ = _client.SendJsonAsync(new { t = "leave_lobby" });
            _capturer.Dispose();
        };
    }

    // ------------------------------------------------------------------ вёрстка
    private void Build()
    {
        var title = DeltaTheme.Title("ВЫ — ХОСТ (P1)");
        title.Location = new Point(30, 22);
        Controls.Add(title);

        _codeLabel.Location = new Point(30, 60);
        Controls.Add(_codeLabel);

        _status.Location = new Point(32, 628);
        Controls.Add(_status);

        // ---- трансляция
        var capturePanel = new DeltaPanel("ТРАНСЛЯЦИЯ") { Location = new Point(30, 110), Size = new Size(560, 200) };
        Controls.Add(capturePanel);

        var hint = DeltaTheme.Caption("Источник картинки (окно игры или весь экран)", DeltaTheme.TextDim, DeltaTheme.FontSmall);
        hint.Location = new Point(18, 36);
        capturePanel.Controls.Add(hint);

        _windows.Location = new Point(18, 58);
        _windows.Size = new Size(400, 28);
        _windows.DropDownStyle = ComboBoxStyle.DropDownList;
        _windows.FlatStyle = FlatStyle.Flat;
        _windows.BackColor = DeltaTheme.Background;
        _windows.ForeColor = DeltaTheme.Text;
        _windows.Font = DeltaTheme.FontBody;
        _windows.DrawMode = DrawMode.OwnerDrawFixed;
        _windows.ItemHeight = 22;
        _windows.DrawItem += (s, e) =>
        {
            if (e.Index < 0) return;
            bool selected = (e.State & DrawItemState.Selected) != 0;
            e.Graphics.FillRectangle(new SolidBrush(DeltaTheme.Background), e.Bounds);
            TextRenderer.DrawText(e.Graphics, _windows.Items[e.Index].ToString(), DeltaTheme.FontBody,
                e.Bounds, selected ? DeltaTheme.Accent : DeltaTheme.Text,
                TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
        };
        capturePanel.Controls.Add(_windows);

        var btnRefreshWindows = new DeltaButton { Text = "ОБНОВИТЬ", Location = new Point(430, 54), Size = new Size(110, 34) };
        btnRefreshWindows.Click += (s, e) => ReloadWindows();
        capturePanel.Controls.Add(btnRefreshWindows);

        _btnStart = new DeltaButton { Text = "НАЧАТЬ ИГРУ", Location = new Point(18, 106), Size = new Size(255, 44) };
        _btnStart.Click += async (s, e) => await _client.SendJsonAsync(new { t = "start" });
        capturePanel.Controls.Add(_btnStart);

        _btnStop = new DeltaButton { Text = "ОСТАНОВИТЬ", Location = new Point(285, 106), Size = new Size(255, 44), Enabled = false };
        _btnStop.Click += async (s, e) => await _client.SendJsonAsync(new { t = "stop" });
        capturePanel.Controls.Add(_btnStop);

        var qualityHint = DeltaTheme.Caption($"{_cfg.Fps} кадр/с · ширина {_cfg.MaxWidth}px · качество {_cfg.JpegQuality}", DeltaTheme.TextDim, DeltaTheme.FontSmall);
        qualityHint.Location = new Point(18, 160);
        capturePanel.Controls.Add(qualityHint);

        // ---- игроки
        var playersPanel = new DeltaPanel("ИГРОКИ") { Location = new Point(610, 110), Size = new Size(300, 200) };
        Controls.Add(playersPanel);
        _players.Location = new Point(16, 34);
        _players.Size = new Size(268, 150);
        playersPanel.Controls.Add(_players);

        // ---- клавиши игры для гостей
        var keysPanel = new DeltaPanel("КЛАВИШИ ИГРЫ ДЛЯ ГОСТЕЙ") { Location = new Point(30, 330), Size = new Size(880, 120) };
        Controls.Add(keysPanel);

        var keysHint = DeltaTheme.Caption(
            "Какие клавиши ждёт сам мод для каждого игрока. Гость жмёт свои удобные кнопки — сюда приходит действие, а мы жмём эту клавишу в игре.",
            DeltaTheme.TextDim, DeltaTheme.FontSmall);
        keysHint.Location = new Point(18, 34);
        keysPanel.Controls.Add(keysHint);

        int x = 18;
        foreach (var role in new[] { "P2", "P3", "P4" })
        {
            var button = new DeltaButton { Text = "КЛАВИШИ " + role, Location = new Point(x, 60), Size = new Size(270, 40), Tag = role };
            button.Click += (s, e) => EditGameKeys((string)((Control)s).Tag);
            keysPanel.Controls.Add(button);
            _keyButtons.Add(button);
            x += 285;
        }

        // ---- журнал
        var logPanel = new DeltaPanel("ЖУРНАЛ") { Location = new Point(30, 466), Size = new Size(880, 150) };
        Controls.Add(logPanel);
        _log.Location = new Point(16, 34);
        _log.Size = new Size(848, 100);
        logPanel.Controls.Add(_log);

        ReloadWindows();
    }

    private void ReloadWindows()
    {
        var current = _windows.SelectedItem as WindowList.WindowInfo;
        _windows.Items.Clear();
        foreach (var window in WindowList.Enumerate()) _windows.Items.Add(window);
        _windows.SelectedIndex = 0;
        if (current != null)
        {
            for (int i = 0; i < _windows.Items.Count; i++)
                if (((WindowList.WindowInfo)_windows.Items[i]).Handle == current.Handle) { _windows.SelectedIndex = i; break; }
        }
    }

    private void Log(string text)
    {
        _log.Items.Insert(0, DateTime.Now.ToString("HH:mm:ss") + "  " + text);
        while (_log.Items.Count > 200) _log.Items.RemoveAt(_log.Items.Count - 1);
    }

    private void ApplyLobby(JsonElement lobby)
    {
        _code = lobby.GetProperty("code").GetString();
        _maxPlayers = lobby.GetProperty("maxPlayers").GetInt32();
        _codeLabel.Text = "КОД: " + _code;
        Text = $"DeltaDotNet — хост — {_code}";

        _players.Items.Clear();
        foreach (var player in lobby.GetProperty("players").EnumerateArray())
        {
            var role = player.GetProperty("role").GetString();
            var login = player.GetProperty("login").GetString();
            _players.Items.Add($"{role}   {login}" + (player.GetProperty("host").GetBoolean() ? "   (хост)" : ""));
        }
        int free = _maxPlayers - lobby.GetProperty("playerCount").GetInt32();
        for (int i = 0; i < free; i++) _players.Items.Add("—   свободное место");

        // Кнопки настройки клавиш активны только для ролей, которые есть в этом лобби.
        for (int i = 0; i < _keyButtons.Count; i++)
            _keyButtons[i].Enabled = i + 2 <= _maxPlayers;
    }

    private void EditGameKeys(string role)
    {
        using var dialog = new BindingsForm(
            _cfg.GetGameKeys(role), role,
            "КЛАВИШИ ИГРЫ — " + role,
            "Что ждёт мод на этой машине для игрока " + role);
        if (dialog.ShowDialog(this) != DialogResult.OK) return;

        _cfg.SetGameKeys(role, dialog.Result);
        _cfg.Save();
        Log($"Клавиши игры для {role} обновлены: {dialog.Result.Describe()}");
    }

    // ------------------------------------------------------------------- сеть
    private void HandleClosed(string reason)
        => BeginInvoke(new Action(() =>
        {
            _timer.Stop();
            _status.Text = "* Соединение закрыто: " + reason;
            _status.ForeColor = DeltaTheme.Bad;
            foreach (var injector in _injectors.Values) injector.ReleaseAll();
        }));

    private void HandleJson(JsonElement m)
        => BeginInvoke(new Action(() => OnJson(m)));

    private void OnJson(JsonElement m)
    {
        if (IsDisposed || !m.TryGetProperty("t", out var typeElement)) return;

        switch (typeElement.GetString())
        {
            case "peer_joined":
                ApplyLobby(m.GetProperty("lobby"));
                Log($"{m.GetProperty("login").GetString()} подключился как {m.GetProperty("role").GetString()}");
                break;

            case "peer_left":
            {
                var role = m.GetProperty("role").GetString();
                ApplyLobby(m.GetProperty("lobby"));
                if (_injectors.TryGetValue(role, out var injector)) injector.ReleaseAll();
                Log($"{m.GetProperty("login").GetString()} ({role}) вышел");
                break;
            }

            case "started":
                _running = true;
                _frames = 0;
                _bytes = 0;
                _startedAt = DateTime.UtcNow;
                _capturer.TargetWindow = (_windows.SelectedItem as WindowList.WindowInfo)?.Handle ?? IntPtr.Zero;
                _timer.Start();
                _btnStart.Enabled = false;
                _btnStop.Enabled = true;
                Log("Игра началась — идёт трансляция");
                break;

            case "stopped":
                StopStreaming("Трансляция остановлена");
                break;

            case "input":
            {
                var role = m.GetProperty("role").GetString();
                var action = m.GetProperty("action").GetString();
                bool down = m.GetProperty("down").GetBoolean();

                var keyName = _cfg.GetGameKeys(role)[action];
                if (keyName == null) break;

                if (!_injectors.TryGetValue(role, out var injector))
                    _injectors[role] = injector = new InputInjector();
                injector.Send(keyName, down);
                break;
            }

            case "release_all":
            {
                var role = m.GetProperty("role").GetString();
                if (_injectors.TryGetValue(role, out var injector)) injector.ReleaseAll();
                break;
            }

            case "chat":
                Log($"[чат] {m.GetProperty("from").GetString()}: {m.GetProperty("text").GetString()}");
                break;

            case "error":
                Log("Ошибка: " + m.GetProperty("message").GetString());
                break;
        }
    }

    private void StopStreaming(string reason)
    {
        _running = false;
        _timer.Stop();
        _btnStart.Enabled = true;
        _btnStop.Enabled = false;
        foreach (var injector in _injectors.Values) injector.ReleaseAll();
        Log(reason);
    }

    /// <summary>Один тик захвата: снимок → JPEG → бинарный кадр на сервер.</summary>
    private async Task CaptureTickAsync()
    {
        if (!_running || !_client.IsConnected) return;
        var packet = _capturer.CaptureFrame();
        if (packet == null) return;
        try
        {
            await _client.SendBinaryAsync(packet);
            _frames++;
            _bytes += packet.Length;
            var seconds = Math.Max(1, (DateTime.UtcNow - _startedAt).TotalSeconds);
            _status.Text = $"* Кадров: {_frames} · {_frames / seconds:F1} кадр/с · {_bytes / 1024.0 / 1024.0:F1} МБ · удерживается клавиш: {_injectors.Values.Sum(i => i.HeldCount)}";
            _status.ForeColor = DeltaTheme.Good;
        }
        catch (Exception ex)
        {
            StopStreaming("Ошибка отправки кадра: " + ex.Message);
        }
    }
}
