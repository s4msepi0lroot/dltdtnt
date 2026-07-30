using System.Text.Json;
using DeltaDotNet.Client.Input;
using DeltaDotNet.Client.Net;
using DeltaDotNet.Client.Ui;

namespace DeltaDotNet.Client.Forms;

/// <summary>
/// Главное меню DeltaDotNet: подключение, авторизация, создание и поиск лобби,
/// выбор количества игроков и настройка своего управления.
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
    private readonly DeltaListBox _lobbies = new();
    private readonly Label _status = DeltaTheme.Caption("* Не подключено", DeltaTheme.TextDim, DeltaTheme.FontSmall);
    private readonly Label _who = DeltaTheme.Caption("", DeltaTheme.Accent, DeltaTheme.FontSmall);

    private DeltaButton _btnPlayers;
    private DeltaButton _btnCreate;
    private DeltaButton _btnJoin;
    private DeltaButton _btnRefresh;
    private readonly List<string> _lobbyCodes = new();

    private bool _childOpen;
    private bool _authorized;

    public MainForm(AppConfig cfg)
    {
        _cfg = cfg;

        Text = "DeltaDotNet";
        ClientSize = new Size(900, 640);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        DeltaTheme.ApplyForm(this);

        BuildHeader();
        BuildAuthPanel();
        BuildLobbyPanel();

        _server.Text = _cfg.ServerUrl;
        _login.Text = _cfg.Login;

        _client.OnJson += json => BeginInvoke(new Action(() => OnJson(json)));
        _client.OnClosed += reason => BeginInvoke(new Action(() =>
        {
            _authorized = false;
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
        var title = DeltaTheme.Title("DELTA . DOT . NET");
        title.Location = new Point(34, 26);
        Controls.Add(title);

        var sub = DeltaTheme.Caption("* Совместная игра через сеть для локального мультиплеера Deltarune", DeltaTheme.TextDim, DeltaTheme.FontSmall);
        sub.Location = new Point(36, 66);
        Controls.Add(sub);

        _status.Location = new Point(36, 606);
        Controls.Add(_status);

        _who.Location = new Point(700, 34);
        Controls.Add(_who);
    }

    private void BuildAuthPanel()
    {
        var panel = new DeltaPanel("СЕРВЕР И ВХОД") { Location = new Point(30, 96), Size = new Size(400, 300) };
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

        var btnBindings = new DeltaButton { Text = "МОЁ УПРАВЛЕНИЕ" };
        btnBindings.Click += (s, e) => EditMyBindings();
        Add(btnBindings, 18, 220, 360, 40);

        var note = DeltaTheme.Caption("Токен сохраняется, повторный вход пройдёт автоматически.", DeltaTheme.TextDim, DeltaTheme.FontSmall);
        Add(note, 18, 268, 360, 16);
    }

    private void BuildLobbyPanel()
    {
        var panel = new DeltaPanel("ЛОББИ") { Location = new Point(452, 96), Size = new Size(418, 300) };
        Controls.Add(panel);

        void Add(Control c, int x, int y, int w, int h)
        {
            c.Location = new Point(x, y);
            c.Size = new Size(w, h);
            panel.Controls.Add(c);
        }

        Add(DeltaTheme.Caption("Название игры", DeltaTheme.TextDim, DeltaTheme.FontSmall), 18, 36, 200, 16);
        Add(_lobbyName, 18, 56, 378, 34);

        // Выбор количества игроков: кнопка-переключатель 2 → 3 → 4 → 2.
        _btnPlayers = new DeltaButton { Text = PlayersText() };
        _btnPlayers.Click += (s, e) =>
        {
            _cfg.PlayerCount = _cfg.PlayerCount >= 4 ? 2 : _cfg.PlayerCount + 1;
            _cfg.Save();
            _btnPlayers.Text = PlayersText();
        };
        Add(_btnPlayers, 18, 100, 185, 40);

        _btnCreate = new DeltaButton { Text = "СОЗДАТЬ" };
        _btnCreate.Click += async (s, e) => await SendAsync(new
        {
            t = "create_lobby",
            name = string.IsNullOrWhiteSpace(_lobbyName.Text) ? null : _lobbyName.Text.Trim(),
            maxPlayers = _cfg.PlayerCount,
        });
        Add(_btnCreate, 211, 100, 185, 40);

        Add(DeltaTheme.Caption("Код лобби", DeltaTheme.TextDim, DeltaTheme.FontSmall), 18, 152, 200, 16);
        Add(_code, 18, 172, 185, 34);

        _btnJoin = new DeltaButton { Text = "ПОДКЛЮЧИТЬСЯ" };
        _btnJoin.Click += async (s, e) => await JoinAsync(_code.Text.Trim().ToUpperInvariant());
        Add(_btnJoin, 211, 168, 185, 40);

        _btnRefresh = new DeltaButton { Text = "ОБНОВИТЬ СПИСОК" };
        _btnRefresh.Click += async (s, e) => await SendAsync(new { t = "list_lobbies" });
        Add(_btnRefresh, 18, 218, 378, 38);

        // Список активных лобби внизу окна.
        var listPanel = new DeltaPanel("АКТИВНЫЕ ИГРЫ") { Location = new Point(30, 412), Size = new Size(840, 180) };
        Controls.Add(listPanel);

        _lobbies.Location = new Point(16, 34);
        _lobbies.Size = new Size(806, 130);
        _lobbies.DoubleClick += async (s, e) =>
        {
            if (_lobbies.SelectedIndex >= 0 && _lobbies.SelectedIndex < _lobbyCodes.Count)
                await JoinAsync(_lobbyCodes[_lobbies.SelectedIndex]);
        };
        listPanel.Controls.Add(_lobbies);

        UpdateEnabled();
    }

    private string PlayersText() => $"ИГРОКОВ: {_cfg.PlayerCount}";

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

    private async Task JoinAsync(string code)
    {
        if (string.IsNullOrWhiteSpace(code))
        {
            SetStatus("Укажите код лобби", DeltaTheme.Bad);
            return;
        }
        await SendAsync(new { t = "join_lobby", code });
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
            "МОЁ УПРАВЛЕНИЕ — " + role,
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
                _cfg.Login = m.GetProperty("login").GetString() ?? "";
                _cfg.Token = m.TryGetProperty("token", out var tk) ? tk.GetString() ?? "" : "";
                _cfg.Save();
                _who.Text = "Вы вошли как " + _cfg.Login;
                SetStatus("Авторизация успешна", DeltaTheme.Good);
                UpdateEnabled();
                _ = _client.SendJsonAsync(new { t = "list_lobbies" });
                break;

            case "lobby_list":
                _lobbies.Items.Clear();
                _lobbyCodes.Clear();
                foreach (var lobby in m.GetProperty("lobbies").EnumerateArray())
                {
                    var code = lobby.GetProperty("code").GetString();
                    _lobbyCodes.Add(code);
                    _lobbies.Items.Add(string.Format("[{0}]  {1}   — хост {2}   — {3}/{4} игроков{5}",
                        code,
                        lobby.GetProperty("name").GetString(),
                        lobby.GetProperty("host").GetString(),
                        lobby.GetProperty("playerCount").GetInt32(),
                        lobby.GetProperty("maxPlayers").GetInt32(),
                        lobby.GetProperty("running").GetBoolean() ? "   — идёт игра" : ""));
                }
                if (_lobbies.Items.Count == 0) _lobbies.Items.Add("Пока никто не создал лобби");
                break;

            case "lobby_created":
                OpenChild(new HostForm(_client, _cfg, m.GetProperty("lobby")));
                break;

            case "lobby_joined":
                OpenChild(new ViewerForm(_client, _cfg, m.GetProperty("role").GetString(), m.GetProperty("lobby")));
                break;

            case "error":
                SetStatus(m.GetProperty("message").GetString(), DeltaTheme.Bad);
                break;
        }
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
