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
///
/// Здесь же хост управляет лобби: кикает и банит игроков, меняет тип доступа
/// (открытое / по паролю / по списку логинов) и может закрыть лобби совсем.
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
    private readonly PlayerListBox _players = new();
    private readonly DeltaListBox _log = new();
    private readonly Label _status = DeltaTheme.Caption("* Ожидание игроков", DeltaTheme.TextDim, DeltaTheme.FontSmall);
    private readonly Label _codeLabel = DeltaTheme.Caption("", DeltaTheme.Accent, DeltaTheme.FontTitle);
    private readonly Label _accessLabel = DeltaTheme.Caption("", DeltaTheme.TextDim, DeltaTheme.FontSmall);
    private readonly Label _qualityHint = DeltaTheme.Caption("", DeltaTheme.TextDim, DeltaTheme.FontSmall);

    private DeltaButton _btnStart;
    private DeltaButton _btnStop;
    private readonly List<DeltaButton> _keyButtons = new();

    private string _code;
    private int _maxPlayers = 2;
    private string _visibility = "public";
    private string _joinMode = "open";
    private readonly List<string> _bans = new();
    private bool _running;
    private bool _lobbyGone;
    private long _frames;
    private long _bytes;
    private DateTime _startedAt = DateTime.UtcNow;

    public HostForm(RelayClient client, AppConfig cfg, JsonElement lobby)
    {
        _client = client;
        _cfg = cfg;

        Text = "DeltaDotNet — хост";
        ClientSize = new Size(980, 720);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        DeltaTheme.ApplyForm(this);
        DeltaAssets.ApplyIcon(this);

        Build();
        ApplyLobby(lobby);
        ApplyQuality();

        _timer.Tick += async (s, e) => await CaptureTickAsync();

        _client.OnJson += HandleJson;
        _client.OnClosed += HandleClosed;

        FormClosed += (s, e) =>
        {
            _timer.Stop();
            foreach (var injector in _injectors.Values) injector.ReleaseAll();
            _client.OnJson -= HandleJson;
            _client.OnClosed -= HandleClosed;
            // Если лобби уже закрыто командой, второй раз выходить не надо.
            if (!_lobbyGone && _client.IsConnected) _ = _client.SendJsonAsync(new { t = "leave_lobby" });
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
        _codeLabel.Size = new Size(400, 30);
        Controls.Add(_codeLabel);

        _accessLabel.Location = new Point(32, 92);
        _accessLabel.Size = new Size(500, 18);
        Controls.Add(_accessLabel);

        _status.Location = new Point(32, 690);
        _status.Size = new Size(900, 18);
        Controls.Add(_status);

        // ---- трансляция
        var capturePanel = new DeltaPanel("ТРАНСЛЯЦИЯ") { Location = new Point(30, 120), Size = new Size(560, 230) };
        Controls.Add(capturePanel);

        var hint = DeltaTheme.Caption("Источник картинки (окно игры или весь экран)", DeltaTheme.TextDim, DeltaTheme.FontSmall);
        hint.Location = new Point(18, 36);
        hint.Size = new Size(500, 16);
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

        // Настройки качества можно менять прямо во время игры.
        var btnQuality = new DeltaButton { Text = "НАСТРОЙКИ КАЧЕСТВА", Location = new Point(18, 158), Size = new Size(522, 38) };
        btnQuality.Click += (s, e) =>
        {
            using var dialog = new SettingsForm(_cfg);
            if (dialog.ShowDialog(this) != DialogResult.OK) return;
            ApplyQuality();
            Log($"Качество обновлено: {_cfg.Fps} кадр/с, ширина {_cfg.MaxWidth}px, сжатие {_cfg.JpegQuality}");
        };
        capturePanel.Controls.Add(btnQuality);

        _qualityHint.Location = new Point(18, 202);
        _qualityHint.Size = new Size(522, 16);
        capturePanel.Controls.Add(_qualityHint);

        // ---- игроки и модерация
        var playersPanel = new DeltaPanel("ИГРОКИ") { Location = new Point(610, 120), Size = new Size(340, 230) };
        Controls.Add(playersPanel);

        _players.Location = new Point(16, 34);
        _players.Size = new Size(308, 110);
        playersPanel.Controls.Add(_players);

        var btnKick = new DeltaButton { Text = "КИКНУТЬ", Location = new Point(16, 152), Size = new Size(150, 36) };
        btnKick.Click += (s, e) => KickSelected(false);
        playersPanel.Controls.Add(btnKick);

        var btnBan = new DeltaButton { Text = "ЗАБАНИТЬ", Location = new Point(174, 152), Size = new Size(150, 36) };
        btnBan.Click += (s, e) => KickSelected(true);
        playersPanel.Controls.Add(btnBan);

        var btnBans = new DeltaButton { Text = "БАН-ЛИСТ ЛОББИ", Location = new Point(16, 194), Size = new Size(308, 36) };
        btnBans.Click += (s, e) => ShowBans();
        playersPanel.Controls.Add(btnBans);

        // ---- доступ в лобби
        var accessPanel = new DeltaPanel("ДОСТУП В ЛОББИ") { Location = new Point(30, 366), Size = new Size(920, 110) };
        Controls.Add(accessPanel);

        var accessHint = DeltaTheme.Caption(
            "Открытое лобби видно всем в списке. Закрытое в списке не показывается: зайти можно по коду с паролем или по списку логинов.",
            DeltaTheme.TextDim, DeltaTheme.FontSmall);
        accessHint.Location = new Point(18, 34);
        accessHint.Size = new Size(880, 16);
        accessPanel.Controls.Add(accessHint);

        var btnOpen = new DeltaButton { Text = "ОТКРЫТОЕ", Location = new Point(18, 58), Size = new Size(210, 38) };
        btnOpen.Click += (s, e) => Send(new { t = "lobby_settings", visibility = "public", joinMode = "open" });
        accessPanel.Controls.Add(btnOpen);

        var btnPassword = new DeltaButton { Text = "ПО ПАРОЛЮ", Location = new Point(240, 58), Size = new Size(210, 38) };
        btnPassword.Click += (s, e) =>
        {
            var password = PromptForm.Ask(this, "Пароль лобби", "Скажите его тем, кого хотите пустить", "");
            if (string.IsNullOrWhiteSpace(password)) return;
            Send(new { t = "lobby_settings", visibility = "private", joinMode = "password", password });
        };
        accessPanel.Controls.Add(btnPassword);

        var btnWhitelist = new DeltaButton { Text = "ПО СПИСКУ ЛОГИНОВ", Location = new Point(462, 58), Size = new Size(230, 38) };
        btnWhitelist.Click += (s, e) =>
        {
            var list = PromptForm.Ask(this, "Кого пускать", "Логины через запятую", "");
            if (list == null) return;
            var allowList = list.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
            Send(new { t = "lobby_settings", visibility = "private", joinMode = "whitelist", allowList });
        };
        accessPanel.Controls.Add(btnWhitelist);

        // Главное: кнопка удаления лобби и возврата в меню.
        var btnClose = new DeltaButton { Text = "ЗАКРЫТЬ ЛОББИ", Location = new Point(704, 58), Size = new Size(196, 38) };
        btnClose.Click += (s, e) => CloseLobby();
        accessPanel.Controls.Add(btnClose);

        // ---- клавиши игры для гостей
        var keysPanel = new DeltaPanel("КЛАВИШИ ИГРЫ ДЛЯ ГОСТЕЙ") { Location = new Point(30, 492), Size = new Size(920, 120) };
        Controls.Add(keysPanel);

        var keysHint = DeltaTheme.Caption(
            "Какие клавиши ждёт сам мод для каждого игрока. Гость жмёт свои удобные кнопки — сюда приходит действие, а мы жмём эту клавишу в игре.",
            DeltaTheme.TextDim, DeltaTheme.FontSmall);
        keysHint.Location = new Point(18, 34);
        keysHint.Size = new Size(880, 16);
        keysPanel.Controls.Add(keysHint);

        int x = 18;
        foreach (var role in new[] { "P2", "P3", "P4" })
        {
            var button = new DeltaButton { Text = "КЛАВИШИ " + role, Location = new Point(x, 60), Size = new Size(285, 40), Tag = role };
            button.Click += (s, e) => EditGameKeys((string)((Control)s).Tag);
            keysPanel.Controls.Add(button);
            _keyButtons.Add(button);
            x += 300;
        }

        // ---- журнал
        var logPanel = new DeltaPanel("ЖУРНАЛ") { Location = new Point(30, 628), Size = new Size(920, 56) };
        Controls.Add(logPanel);
        _log.Location = new Point(16, 12);
        _log.Size = new Size(888, 36);
        logPanel.Controls.Add(_log);

        ReloadWindows();
    }

    /// <summary>Переносит настройки качества в захватчик и таймер (работает и на лету).</summary>
    private void ApplyQuality()
    {
        _capturer.MaxWidth = _cfg.MaxWidth;
        _capturer.Quality = _cfg.JpegQuality;
        _timer.Interval = Math.Max(16, 1000 / Math.Max(5, _cfg.Fps));
        _qualityHint.Text = $"{_cfg.Fps} кадр/с · ширина {_cfg.MaxWidth}px · качество {_cfg.JpegQuality}";
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

    private void Send(object message)
    {
        try { _ = _client.SendJsonAsync(message); }
        catch (Exception ex) { Log("Ошибка отправки: " + ex.Message); }
    }

    // ---------------------------------------------------------------- модерация
    /// <summary>Кик или бан выбранного в списке игрока (хоста выбрать нельзя).</summary>
    private void KickSelected(bool ban)
    {
        if (_players.SelectedItem is not LobbyPlayer player || player.IsHost || string.IsNullOrEmpty(player.Login))
        {
            Log("Выберите игрока в списке (себя выбрать нельзя)");
            return;
        }

        if (ban)
        {
            var reason = PromptForm.Ask(this, "Бан " + player.Login, "Причина (можно пусто). Игрок больше не зайдёт в это лобби.", "");
            if (reason == null) return;
            Send(new { t = "ban", login = player.Login, reason });
            Log("Забанен: " + player.Login);
        }
        else
        {
            Send(new { t = "kick", login = player.Login });
            Log("Кикнут: " + player.Login);
        }
    }

    /// <summary>Показывает бан-лист лобби и позволяет снять бан по логину.</summary>
    private void ShowBans()
    {
        if (_bans.Count == 0)
        {
            Log("Бан-лист пуст");
            return;
        }

        var login = PromptForm.Ask(this, "Бан-лист лобби",
            "В бане: " + string.Join(", ", _bans) + ". Введите логин, чтобы разбанить", "");
        if (string.IsNullOrWhiteSpace(login)) return;
        Send(new { t = "unban", login = login.Trim() });
        Log("Снят бан: " + login.Trim());
    }

    /// <summary>Удаляет лобби целиком и закрывает окно (возврат в главное меню).</summary>
    private void CloseLobby()
    {
        var answer = MessageBox.Show(this,
            "Закрыть лобби " + _code + "? Все игроки вернутся в меню.",
            "Закрытие лобби", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
        if (answer != DialogResult.Yes) return;

        _lobbyGone = true;
        Send(new { t = "close_lobby" });
        _timer.Stop();
        Close();
    }

    private void ApplyLobby(JsonElement lobby)
    {
        _code = lobby.GetProperty("code").GetString();
        _maxPlayers = lobby.GetProperty("maxPlayers").GetInt32();
        _codeLabel.Text = "КОД: " + _code;
        Text = $"DeltaDotNet — хост — {_code}";

        if (lobby.TryGetProperty("visibility", out var vis)) _visibility = vis.GetString();
        if (lobby.TryGetProperty("joinMode", out var jm)) _joinMode = jm.GetString();

        _accessLabel.Text = "Доступ: " + (_visibility == "private" ? "закрытое лобби" : "открытое лобби") + " · " + _joinMode switch
        {
            "password" => "вход по паролю",
            "whitelist" => "вход только по списку логинов",
            _ => "вход свободный",
        };

        // Список игроков с украшениями ников.
        _players.Items.Clear();
        foreach (var player in lobby.GetProperty("players").EnumerateArray())
            _players.Items.Add(LobbyPlayer.Parse(player));

        int free = _maxPlayers - lobby.GetProperty("playerCount").GetInt32();
        for (int i = 0; i < free; i++) _players.Items.Add(LobbyPlayer.Empty());

        _bans.Clear();
        if (lobby.TryGetProperty("bans", out var bans) && bans.ValueKind == JsonValueKind.Array)
        {
            foreach (var ban in bans.EnumerateArray())
                _bans.Add(ban.GetProperty("login").GetString());
        }

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

            // Полное состояние лобби — приходит после кика, бана и смены настроек.
            case "lobby_state":
                ApplyLobby(m.GetProperty("lobby"));
                break;

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

            // Лобби закрыл администратор или сам сервер.
            case "lobby_closed":
                _lobbyGone = true;
                StopStreaming("Лобби закрыто");
                Close();
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

            case "announce":
                Log("[объявление] " + m.GetProperty("text").GetString());
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

/// <summary>Игрок в списке лобби (с украшениями ника, если админ их выдал).</summary>
public sealed class LobbyPlayer
{
    public string Login { get; init; }
    public string Role { get; init; }
    public bool IsHost { get; init; }
    public bool IsAdmin { get; init; }
    public Cosmetic Cosmetic { get; init; } = new();

    public static LobbyPlayer Empty() => new() { Login = null, Role = "—" };

    public static LobbyPlayer Parse(JsonElement e)
    {
        var cosmetic = new Cosmetic();
        if (e.TryGetProperty("cosmetic", out var c) && c.ValueKind == JsonValueKind.Object)
        {
            cosmetic.Rainbow = c.TryGetProperty("rainbow", out var r) && r.ValueKind == JsonValueKind.True;
            cosmetic.Color = c.TryGetProperty("color", out var col) && col.ValueKind == JsonValueKind.String ? col.GetString() : null;
            cosmetic.Tag = c.TryGetProperty("tag", out var tag) && tag.ValueKind == JsonValueKind.String ? tag.GetString() : null;
        }

        return new LobbyPlayer
        {
            Login = e.GetProperty("login").GetString(),
            Role = e.GetProperty("role").GetString(),
            IsHost = e.TryGetProperty("host", out var h) && h.ValueKind == JsonValueKind.True,
            IsAdmin = e.TryGetProperty("admin", out var a) && a.ValueKind == JsonValueKind.True,
            Cosmetic = cosmetic,
        };
    }

    public override string ToString()
        => Login == null ? "—   свободное место" : $"{Role}   {Login}" + (IsHost ? "   (хост)" : "");
}

/// <summary>Список игроков лобби с поддержкой переливающихся ников.</summary>
public sealed class PlayerListBox : DeltaListBox
{
    private readonly System.Windows.Forms.Timer _timer = new() { Interval = 60 };

    public PlayerListBox()
    {
        ItemHeight = 26;
        _timer.Tick += (s, e) => Invalidate();
        _timer.Start();
    }

    protected override void OnDrawItem(DrawItemEventArgs e)
    {
        if (e.Index < 0) return;
        if (Items[e.Index] is not LobbyPlayer player || player.Login == null)
        {
            base.OnDrawItem(e);
            return;
        }

        var g = e.Graphics;
        g.FillRectangle(new SolidBrush(DeltaTheme.Background), e.Bounds);

        bool selected = (e.State & DrawItemState.Selected) == DrawItemState.Selected;
        int left = e.Bounds.Left + 6;
        if (selected)
        {
            DeltaTheme.DrawHeart(g, new Rectangle(e.Bounds.Left + 4, e.Bounds.Top + (e.Bounds.Height - 12) / 2, 12, 12));
            left = e.Bounds.Left + 24;
        }

        int y = e.Bounds.Top + (e.Bounds.Height - Font.Height) / 2;
        string prefix = player.Role + "   ";
        TextRenderer.DrawText(g, prefix, Font, new Point(left, y),
            selected ? DeltaTheme.Accent : DeltaTheme.TextDim, TextFormatFlags.NoPadding);
        left += TextRenderer.MeasureText(g, prefix, Font, Size.Empty, TextFormatFlags.NoPadding).Width;

        int width = RainbowText.Draw(g, player.Login, Font, new Point(left, y),
            selected ? DeltaTheme.Accent : DeltaTheme.Text, player.Cosmetic);

        string suffix = (player.IsHost ? "   (хост)" : "") + (player.IsAdmin ? "   ★" : "");
        if (suffix.Length > 0)
        {
            TextRenderer.DrawText(g, suffix, Font, new Point(left + width, y), DeltaTheme.TextDim,
                TextFormatFlags.NoPadding);
        }
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing) _timer.Dispose();
        base.Dispose(disposing);
    }
}
