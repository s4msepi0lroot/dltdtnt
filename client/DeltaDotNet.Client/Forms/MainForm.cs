using System.Text.Json;
using DeltaDotNet.Client.Input;
using DeltaDotNet.Client.Net;
using DeltaDotNet.Client.Ui;

namespace DeltaDotNet.Client.Forms;

/// <summary>
/// Главное меню DeltaDotNet: подключение, авторизация, создание и поиск лобби,
/// выбор количества игроков, типа доступа, качества трансляции и своего управления.
/// Администратору дополнительно показывается кнопка админ-панели.
/// </summary>
public sealed class MainForm : Form
{
    private readonly AppConfig _cfg;
    private readonly RelayClient _client = new();

    private readonly DeltaTextBox _server = new();
    private readonly DeltaTextBox _login = new();
    private readonly DeltaTextBox _password = new(password: true);
    private readonly DeltaTextBox _lobbyName = new();
    private readonly DeltaTextBox _code = new();
    private readonly DeltaTextBox _joinPassword = new(password: true);
    private readonly DeltaListBox _lobbies = new();
    private readonly Label _status = DeltaTheme.Caption("* Не подключено", DeltaTheme.TextDim, DeltaTheme.FontSmall);
    private readonly CosmeticLabel _who = new();

    private DeltaButton _btnPlayers;
    private DeltaButton _btnAccess;
    private DeltaButton _btnCreate;
    private DeltaButton _btnJoin;
    private DeltaButton _btnRefresh;
    private DeltaButton _btnAdmin;
    private readonly List<string> _lobbyCodes = new();

    private bool _childOpen;
    private bool _authorized;
    private bool _isAdmin;

    public MainForm(AppConfig cfg)
    {
        _cfg = cfg;

        Text = "DeltaDotNet";
        ClientSize = new Size(920, 720);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        DeltaTheme.ApplyForm(this);

        // Папки для картинок создаём сразу, чтобы их было куда положить.
        DeltaAssets.EnsureFolders();
        DeltaAssets.ApplyIcon(this);

        BuildHeader();
        BuildAuthPanel();
        BuildLobbyPanel();

        _server.Text = _cfg.ServerUrl;
        _login.Text = _cfg.Login;

        _client.OnJson += json => BeginInvoke(new Action(() => OnJson(json)));
        _client.OnClosed += reason => BeginInvoke(new Action(() =>
        {
            _authorized = false;
            _isAdmin = false;
            SetStatus("Соединение закрыто: " + reason, DeltaTheme.Bad);
            UpdateEnabled();
        }));

        FormClosed += (s, e) =>
        {
            _cfg.ServerUrl = _server.Text.Trim();
            _cfg.Save();
            _client.Dispose();
        };
    }

    // ------------------------------------------------------------------ вёрстка
    private void BuildHeader()
    {
        // Вместо текстового заголовка — картинка assets/logo.png.
        // Если файла нет, баннер сам нарисует название текстом.
        var banner = new LogoBanner
        {
            Location = new Point(30, 18),
            Size = new Size(560, 84),
            FallbackText = "Delta.Dot.Net",
            Subtitle = "* Совместная игра через сеть для локального мультиплеера Deltarune",
        };
        Controls.Add(banner);

        _who.Location = new Point(620, 34);
        _who.Size = new Size(270, 22);
        _who.ForeColor = DeltaTheme.Accent;
        Controls.Add(_who);

        _btnAdmin = new DeltaButton { Text = "АДМИНКА", Location = new Point(620, 60), Size = new Size(270, 36), Visible = false };
        _btnAdmin.Click += (s, e) =>
        {
            using var admin = new AdminForm(_client);
            admin.ShowDialog(this);
        };
        Controls.Add(_btnAdmin);

        _status.Location = new Point(36, 690);
        _status.Size = new Size(860, 18);
        Controls.Add(_status);
    }

    private void BuildAuthPanel()
    {
        var panel = new DeltaPanel("СЕРВЕР И ВХОД") { Location = new Point(30, 116), Size = new Size(400, 330) };
        Controls.Add(panel);

        void Add(Control c, int x, int y, int w, int h)
        {
            c.Location = new Point(x, y);
            c.Size = new Size(w, h);
            panel.Controls.Add(c);
        }

        Add(DeltaTheme.Caption("Адрес сервера", DeltaTheme.TextDim, DeltaTheme.FontSmall), 18, 36, 200, 16);
        Add(_server, 18, 56, 360, 34);

        Add(DeltaTheme.Caption("Логин", DeltaTheme.TextDim, DeltaTheme.FontSmall), 18, 98, 200, 16);
        Add(_login, 18, 118, 174, 34);

        Add(DeltaTheme.Caption("Пароль", DeltaTheme.TextDim, DeltaTheme.FontSmall), 204, 98, 200, 16);
        Add(_password, 204, 118, 174, 34);

        var btnLogin = new DeltaButton { Text = "ВОЙТИ" };
        btnLogin.Click += async (s, e) => await AuthAsync("login");
        Add(btnLogin, 18, 168, 174, 40);

        var btnRegister = new DeltaButton { Text = "РЕГИСТРАЦИЯ" };
        btnRegister.Click += async (s, e) => await AuthAsync("register");
        Add(btnRegister, 204, 168, 174, 40);

        var btnBindings = new DeltaButton { Text = "УПРАВЛЕНИЕ" };
        btnBindings.Click += (s, e) => EditMyBindings();
        Add(btnBindings, 18, 220, 174, 40);

        // Вернувшиеся настройки качества трансляции.
        var btnQuality = new DeltaButton { Text = "КАЧЕСТВО" };
        btnQuality.Click += (s, e) =>
        {
            using var dialog = new SettingsForm(_cfg);
            if (dialog.ShowDialog(this) == DialogResult.OK)
                SetStatus($"Качество: {_cfg.Fps} кадр/с, ширина {_cfg.MaxWidth}px, сжатие {_cfg.JpegQuality}", DeltaTheme.Good);
        };
        Add(btnQuality, 204, 220, 174, 40);

        var note = DeltaTheme.Caption("Повторный вход автоматический.", DeltaTheme.TextDim, DeltaTheme.FontSmall);
        Add(note, 18, 272, 360, 16);
    }

    private void BuildLobbyPanel()
    {
        var panel = new DeltaPanel("ЛОББИ") { Location = new Point(452, 116), Size = new Size(438, 330) };
        Controls.Add(panel);

        void Add(Control c, int x, int y, int w, int h)
        {
            c.Location = new Point(x, y);
            c.Size = new Size(w, h);
            panel.Controls.Add(c);
        }

        Add(DeltaTheme.Caption("Название игры", DeltaTheme.TextDim, DeltaTheme.FontSmall), 18, 36, 200, 16);
        Add(_lobbyName, 18, 56, 398, 34);

        // Количество игроков: кнопка-переключатель 2 → 3 → 4 → 2.
        _btnPlayers = new DeltaButton { Text = PlayersText() };
        _btnPlayers.Click += (s, e) =>
        {
            _cfg.PlayerCount = _cfg.PlayerCount >= 4 ? 2 : _cfg.PlayerCount + 1;
            _cfg.Save();
            _btnPlayers.Text = PlayersText();
        };
        Add(_btnPlayers, 18, 100, 195, 40);

        // Тип доступа: открытое → по паролю → по списку логинов → открытое.
        _btnAccess = new DeltaButton { Text = AccessText() };
        _btnAccess.Click += (s, e) => CycleAccess();
        Add(_btnAccess, 221, 100, 195, 40);

        _btnCreate = new DeltaButton { Text = "СОЗДАТЬ ЛОББИ" };
        _btnCreate.Click += async (s, e) => await CreateLobbyAsync();
        Add(_btnCreate, 18, 148, 398, 40);

        Add(DeltaTheme.Caption("Код лобби", DeltaTheme.TextDim, DeltaTheme.FontSmall), 18, 198, 190, 16);
        Add(_code, 18, 218, 195, 34);

        Add(DeltaTheme.Caption("Пароль (если закрытое лобби)", DeltaTheme.TextDim, DeltaTheme.FontSmall), 221, 198, 200, 16);
        Add(_joinPassword, 221, 218, 195, 34);

        _btnJoin = new DeltaButton { Text = "ПОДКЛЮЧИТЬСЯ" };
        _btnJoin.Click += async (s, e) => await JoinAsync(_code.Text.Trim().ToUpperInvariant());
        Add(_btnJoin, 18, 262, 195, 40);

        _btnRefresh = new DeltaButton { Text = "ОБНОВИТЬ СПИСОК" };
        _btnRefresh.Click += async (s, e) => await SendAsync(new { t = "list_lobbies" });
        Add(_btnRefresh, 221, 262, 195, 40);

        // Список активных лобби внизу окна (закрытые в списке не показываются).
        var listPanel = new DeltaPanel("АКТИВНЫЕ ИГРЫ") { Location = new Point(30, 462), Size = new Size(860, 210) };
        Controls.Add(listPanel);

        _lobbies.Location = new Point(16, 34);
        _lobbies.Size = new Size(826, 160);
        _lobbies.DoubleClick += async (s, e) =>
        {
            if (_lobbies.SelectedIndex >= 0 && _lobbies.SelectedIndex < _lobbyCodes.Count)
                await JoinAsync(_lobbyCodes[_lobbies.SelectedIndex]);
        };
        listPanel.Controls.Add(_lobbies);

        UpdateEnabled();
    }

    private string PlayersText() => $"ИГРОКОВ: {_cfg.PlayerCount}";

    private string AccessText() => _cfg.LobbyJoinMode switch
    {
        "password" => "ДОСТУП: ПАРОЛЬ",
        "whitelist" => "ДОСТУП: ЛОГИН",
        _ => "ДОСТУП: ОТКРЫТОЕ",
    };

    /// <summary>Переключает тип доступа и спрашивает пароль или список логинов.</summary>
    private void CycleAccess()
    {
        switch (_cfg.LobbyJoinMode)
        {
            case "open":
            {
                var password = PromptForm.Ask(this, "Пароль лобби",
                    "Закрытое лобби: войти можно только по коду и паролю", _cfg.LobbyPassword);
                if (string.IsNullOrWhiteSpace(password)) return;
                _cfg.LobbyJoinMode = "password";
                _cfg.LobbyVisibility = "private";
                _cfg.LobbyPassword = password.Trim();
                break;
            }

            case "password":
            {
                var list = PromptForm.Ask(this, "Кого пускать",
                    "Логины через запятую - только они смогут зайти",
                    string.Join(", ", _cfg.LobbyAllowList));
                if (string.IsNullOrWhiteSpace(list)) return;
                _cfg.LobbyJoinMode = "whitelist";
                _cfg.LobbyVisibility = "private";
                _cfg.LobbyAllowList = list
                    .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
                    .ToList();
                break;
            }

            default:
                _cfg.LobbyJoinMode = "open";
                _cfg.LobbyVisibility = "public";
                break;
        }

        _cfg.Save();
        _btnAccess.Text = AccessText();
    }

    // ------------------------------------------------------------------- логика
    private void SetStatus(string text, Color? color = null)
    {
        _status.Text = "* " + text;
        _status.ForeColor = color ?? DeltaTheme.TextDim;
    }

    private void UpdateEnabled()
    {
        _btnCreate.Enabled = _authorized;
        _btnJoin.Enabled = _authorized;
        _btnRefresh.Enabled = _authorized;
        _btnAdmin.Visible = _authorized && _isAdmin;
    }

    /// <summary>Автовход при запуске: подключаемся и, если сохранён токен, входим без пароля.</summary>
    public async Task TryAutoLoginAsync()
    {
        if (string.IsNullOrWhiteSpace(_server.Text)) return;
        try
        {
            await EnsureConnectedAsync();
        }
        catch (Exception ex)
        {
            SetStatus("Не удалось подключиться: " + ex.Message, DeltaTheme.Bad);
        }
    }

    private async Task EnsureConnectedAsync()
    {
        if (_client.IsConnected) return;
        var url = _server.Text.Trim();
        SetStatus("Подключаемся к " + url + " ...");
        await _client.ConnectAsync(url, CancellationToken.None);
        SetStatus("Соединение установлено" +
                  (string.IsNullOrEmpty(_client.ConnectNote) ? "" : " (" + _client.ConnectNote + ")"), DeltaTheme.Good);
    }

    private async Task SendAsync(object message)
    {
        try
        {
            await EnsureConnectedAsync();
            await _client.SendJsonAsync(message);
        }
        catch (Exception ex)
        {
            SetStatus("Ошибка: " + ex.Message, DeltaTheme.Bad);
        }
    }

    private async Task AuthAsync(string type)
    {
        var login = _login.Text.Trim();
        var password = _password.Text;
        if (login.Length == 0 || password.Length == 0)
        {
            SetStatus("Введите логин и пароль", DeltaTheme.Bad);
            return;
        }
        await SendAsync(new { t = type, login, password });
    }

    /// <summary>Создание лобби с учётом выбранного типа доступа.</summary>
    private async Task CreateLobbyAsync()
    {
        await SendAsync(new
        {
            t = "create_lobby",
            name = string.IsNullOrWhiteSpace(_lobbyName.Text) ? null : _lobbyName.Text.Trim(),
            maxPlayers = _cfg.PlayerCount,
            visibility = _cfg.LobbyVisibility,
            joinMode = _cfg.LobbyJoinMode,
            password = _cfg.LobbyJoinMode == "password" ? _cfg.LobbyPassword : "",
            allowList = _cfg.LobbyJoinMode == "whitelist" ? _cfg.LobbyAllowList : new List<string>(),
        });
    }

    private async Task JoinAsync(string code)
    {
        if (string.IsNullOrWhiteSpace(code))
        {
            SetStatus("Укажите код лобби", DeltaTheme.Bad);
            return;
        }
        await SendAsync(new { t = "join_lobby", code, password = _joinPassword.Text });
    }

    /// <summary>Настройка своих клавиш для любой роли ещё до входа в лобби.</summary>
    private void EditMyBindings()
    {
        var roles = new[] { "P1", "P2", "P3", "P4" };
        using var picker = new RolePickerForm(roles);
        if (picker.ShowDialog(this) != DialogResult.OK) return;

        var role = picker.SelectedRole;
        using var dialog = new BindingsForm(
            _cfg.GetMyBindings(role), role,
            "УПРАВЛЕНИЕ: " + role,
            "Какие клавиши вы жмёте у себя, играя за " + role);
        if (dialog.ShowDialog(this) != DialogResult.OK) return;

        _cfg.SetMyBindings(role, dialog.Result);
        _cfg.Save();
        SetStatus("Управление для " + role + " сохранено", DeltaTheme.Good);
    }

    // -------------------------------------------------------- сообщения сервера
    private void OnJson(JsonElement m)
    {
        if (!m.TryGetProperty("t", out var typeElement)) return;
        var type = typeElement.GetString();

        switch (type)
        {
            case "hello":
                // Есть сохранённый токен — пробуем войти без пароля.
                if (!_authorized && !string.IsNullOrEmpty(_cfg.Token))
                    _ = _client.SendJsonAsync(new { t = "auth_token", token = _cfg.Token });
                break;

            case "auth_ok":
                _authorized = true;
                _isAdmin = m.TryGetProperty("isAdmin", out var admin) && admin.ValueKind == JsonValueKind.True;
                _cfg.Login = m.GetProperty("login").GetString() ?? "";
                _cfg.Token = m.TryGetProperty("token", out var tk) ? tk.GetString() ?? "" : "";
                _cfg.Save();
                ApplyProfile(m);
                SetStatus(_isAdmin ? "Вход выполнен, у вас права администратора" : "Авторизация успешна", DeltaTheme.Good);
                UpdateEnabled();
                _ = _client.SendJsonAsync(new { t = "list_lobbies" });
                break;

            // Пришли новые украшения ника (например, админ выдал радугу).
            case "profile":
                ApplyProfile(m.GetProperty("user"));
                break;

            case "lobby_list":
                _lobbies.Items.Clear();
                _lobbyCodes.Clear();
                foreach (var lobby in m.GetProperty("lobbies").EnumerateArray())
                {
                    var code = lobby.GetProperty("code").GetString();
                    _lobbyCodes.Add(code);

                    string access = lobby.TryGetProperty("joinMode", out var jm) ? jm.GetString() switch
                    {
                        "password" => "   — по паролю",
                        "whitelist" => "   — по списку логинов",
                        _ => "",
                    } : "";

                    _lobbies.Items.Add(string.Format("[{0}]  {1}   — хост {2}   — {3}/{4} игроков{5}{6}",
                        code,
                        lobby.GetProperty("name").GetString(),
                        lobby.GetProperty("host").GetString(),
                        lobby.GetProperty("playerCount").GetInt32(),
                        lobby.GetProperty("maxPlayers").GetInt32(),
                        access,
                        lobby.GetProperty("running").GetBoolean() ? "   — идёт игра" : ""));
                }
                if (_lobbies.Items.Count == 0) _lobbies.Items.Add("Пока никто не создал открытое лобби");
                break;

            case "lobby_created":
                OpenChild(new HostForm(_client, _cfg, m.GetProperty("lobby")));
                break;

            case "lobby_joined":
                OpenChild(new ViewerForm(_client, _cfg, m.GetProperty("role").GetString(), m.GetProperty("lobby")));
                break;

            case "announce":
                SetStatus("Объявление: " + m.GetProperty("text").GetString(), DeltaTheme.Accent);
                break;

            case "error":
                SetStatus(m.GetProperty("message").GetString(), DeltaTheme.Bad);
                break;
        }
    }

    /// <summary>Показывает ник в шапке с учётом выданных украшений.</summary>
    private void ApplyProfile(JsonElement source)
    {
        var cosmetic = new Cosmetic();
        if (source.TryGetProperty("cosmetic", out var c) && c.ValueKind == JsonValueKind.Object)
        {
            cosmetic.Rainbow = c.TryGetProperty("rainbow", out var r) && r.ValueKind == JsonValueKind.True;
            cosmetic.Color = c.TryGetProperty("color", out var col) && col.ValueKind == JsonValueKind.String ? col.GetString() : null;
            cosmetic.Tag = c.TryGetProperty("tag", out var tag) && tag.ValueKind == JsonValueKind.String ? tag.GetString() : null;
        }

        if (source.TryGetProperty("role", out var role) && role.ValueKind == JsonValueKind.String)
            _isAdmin = role.GetString() == "admin";

        _who.Text = _cfg.Login;
        _who.Cosmetic = cosmetic;
        UpdateEnabled();
    }

    /// <summary>Открывает окно игры и прячет меню, пока игра идёт.</summary>
    private void OpenChild(Form child)
    {
        if (_childOpen) return;
        _childOpen = true;
        Hide();
        try
        {
            child.ShowDialog(this);
        }
        finally
        {
            child.Dispose();
            _childOpen = false;
            Show();
            SetStatus("Вы вернулись в меню");
            if (_client.IsConnected) _ = _client.SendJsonAsync(new { t = "list_lobbies" });
        }
    }
}

/// <summary>Маленькое окно выбора роли для редактора управления.</summary>
public sealed class RolePickerForm : Form
{
    public string SelectedRole { get; private set; } = "P1";

    public RolePickerForm(IEnumerable<string> roles)
    {
        Text = "Выбор игрока";
        ClientSize = new Size(320, 300);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        DeltaTheme.ApplyForm(this);

        var title = DeltaTheme.Caption("ДЛЯ КАКОГО ИГРОКА?", DeltaTheme.Text, DeltaTheme.FontBig);
        title.Location = new Point(26, 24);
        Controls.Add(title);

        int y = 70;
        foreach (var role in roles)
        {
            var button = new DeltaButton { Text = role, Location = new Point(26, y), Size = new Size(268, 42), Tag = role };
            button.Click += (s, e) =>
            {
                SelectedRole = (string)((Control)s).Tag;
                DialogResult = DialogResult.OK;
                Close();
            };
            Controls.Add(button);
            y += 52;
        }
    }
}
