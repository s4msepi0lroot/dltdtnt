using System.Text.Json;
using CoopStream.Client.Input;
using CoopStream.Client.Net;

namespace CoopStream.Client.Forms;

/// <summary>
/// Главное окно: подключение к серверу, авторизация, список/создание/вход в лобби,
/// запуск игровой сессии (HostForm для хоста, ViewerForm для гостя).
/// </summary>
public sealed class MainForm : Form
{
    private readonly AppConfig _config;
    private readonly RelayClient _client = new();

    // --- элементы интерфейса ---
    private readonly TextBox _txtServer = new() { Width = 300 };
    private readonly TextBox _txtLogin = new() { Width = 140 };
    private readonly TextBox _txtPassword = new() { Width = 140, UseSystemPasswordChar = true };
    private readonly Button _btnLogin = new() { Text = "Войти", Width = 90 };
    private readonly Button _btnRegister = new() { Text = "Регистрация", Width = 100 };
    private readonly Label _lblAuth = new() { Text = "Не авторизован", AutoSize = true, ForeColor = Color.Firebrick };

    private readonly TextBox _txtLobbyName = new() { Width = 180 };
    private readonly ComboBox _cmbRole = new() { Width = 190, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly Button _btnCreate = new() { Text = "Создать лобби", Width = 130 };
    private readonly TextBox _txtCode = new() { Width = 100, CharacterCasing = CharacterCasing.Upper };
    private readonly Button _btnJoin = new() { Text = "Войти по коду", Width = 130 };
    private readonly Button _btnRefresh = new() { Text = "Обновить список", Width = 130 };
    private readonly ListBox _lstLobbies = new() { Width = 460, Height = 120 };
    private readonly Button _btnStart = new() { Text = "Начать игру", Width = 130, Enabled = false };
    private readonly Button _btnLeave = new() { Text = "Покинуть лобби", Width = 130, Enabled = false };
    private readonly Label _lblLobby = new() { Text = "Вы не в лобби", AutoSize = true };
    private readonly Label _lblKeys = new() { AutoSize = true, ForeColor = Color.DimGray };
    private readonly TextBox _txtLog = new() { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Width = 460, Height = 110 };

    // --- состояние сессии ---
    private string _role = "P1";
    private bool _isHost;
    private string _lobbyCode;
    private Form _sessionForm;

    public MainForm(AppConfig config)
    {
        _config = config;
        Text = "CoopStream — совместная игра по сети";
        Width = 520;
        Height = 700;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;

        _txtServer.Text = _config.ServerUrl;
        _txtLogin.Text = _config.Login;
        _cmbRole.Items.AddRange(new object[] { "Я — игрок 1 (WASD/Z/X/P/C)", "Я — игрок 2 (стрелки/Enter/C)" });
        _cmbRole.SelectedIndex = _config.HostRole == "P2" ? 1 : 0;

        BuildLayout();

        _btnLogin.Click += async (_, _) => await AuthAsync("login");
        _btnRegister.Click += async (_, _) => await AuthAsync("register");
        _btnCreate.Click += async (_, _) => await CreateLobbyAsync();
        _btnJoin.Click += async (_, _) => await JoinLobbyAsync(_txtCode.Text.Trim());
        _btnRefresh.Click += async (_, _) => await SendAsync(new { t = "list_lobbies" });
        _btnStart.Click += async (_, _) => await SendAsync(new { t = "start" });
        _btnLeave.Click += async (_, _) => await SendAsync(new { t = "leave_lobby" });
        _lstLobbies.DoubleClick += async (_, _) =>
        {
            if (_lstLobbies.SelectedItem is LobbyItem item) await JoinLobbyAsync(item.Code);
        };

        _client.OnJson += HandleJsonFromBackground;
        _client.OnClosed += reason => BeginInvoke(() =>
        {
            Log($"Соединение закрыто: {reason}");
            SetAuthState(false);
        });

        FormClosing += (_, _) =>
        {
            _config.ServerUrl = _txtServer.Text.Trim();
            _config.Login = _txtLogin.Text.Trim();
            _config.HostRole = _cmbRole.SelectedIndex == 1 ? "P2" : "P1";
            _config.Save();
            _client.Close("exit");
        };
    }

    private sealed record LobbyItem(string Code, string Text)
    {
        public override string ToString() => Text;
    }

    private void BuildLayout()
    {
        var root = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoScroll = true, Padding = new Padding(12) };

        root.Controls.Add(Header("1. Сервер"));
        root.Controls.Add(Row(new Label { Text = "Адрес:", AutoSize = true, Padding = new Padding(0, 6, 0, 0) }, _txtServer));

        root.Controls.Add(Header("2. Авторизация"));
        root.Controls.Add(Row(new Label { Text = "Логин:", AutoSize = true, Padding = new Padding(0, 6, 0, 0) }, _txtLogin,
            new Label { Text = "Пароль:", AutoSize = true, Padding = new Padding(8, 6, 0, 0) }, _txtPassword));
        root.Controls.Add(Row(_btnLogin, _btnRegister, _lblAuth));

        root.Controls.Add(Header("3. Лобби"));
        root.Controls.Add(Row(new Label { Text = "Название:", AutoSize = true, Padding = new Padding(0, 6, 0, 0) }, _txtLobbyName, _btnCreate));
        root.Controls.Add(Row(new Label { Text = "Роль хоста:", AutoSize = true, Padding = new Padding(0, 6, 0, 0) }, _cmbRole));
        root.Controls.Add(Row(new Label { Text = "Код:", AutoSize = true, Padding = new Padding(0, 6, 0, 0) }, _txtCode, _btnJoin, _btnRefresh));
        root.Controls.Add(_lstLobbies);
        root.Controls.Add(Row(_btnStart, _btnLeave));
        root.Controls.Add(_lblLobby);
        root.Controls.Add(_lblKeys);

        root.Controls.Add(Header("Журнал"));
        root.Controls.Add(_txtLog);

        Controls.Add(root);
    }

    private static Control Header(string text) => new Label
    {
        Text = text,
        AutoSize = true,
        Font = new Font(SystemFonts.DefaultFont, FontStyle.Bold),
        Padding = new Padding(0, 10, 0, 4),
    };

    private static Control Row(params Control[] controls)
    {
        var p = new FlowLayoutPanel { FlowDirection = FlowDirection.LeftToRight, AutoSize = true, WrapContents = false, Margin = new Padding(0, 2, 0, 2) };
        p.Controls.AddRange(controls);
        return p;
    }

    private void Log(string message)
    {
        _txtLog.AppendText($"[{DateTime.Now:HH:mm:ss}] {message}{Environment.NewLine}");
    }

    private async Task<bool> EnsureConnectedAsync()
    {
        if (_client.IsConnected) return true;
        var url = _txtServer.Text.Trim();
        try
        {
            Log($"Подключение к {url}...");
            await _client.ConnectAsync(url);
            Log("Соединение установлено");
            return true;
        }
        catch (Exception ex)
        {
            Log($"Ошибка подключения: {ex.Message}");
            return false;
        }
    }

    private async Task SendAsync(object message)
    {
        if (!await EnsureConnectedAsync()) return;
        await _client.SendJsonAsync(message);
    }

    private async Task AuthAsync(string type)
    {
        if (!await EnsureConnectedAsync()) return;
        await _client.SendJsonAsync(new { t = type, login = _txtLogin.Text.Trim(), password = _txtPassword.Text });
    }

    /// <summary>Автовход по сохранённому токену при старте.</summary>
    public async Task TryAutoLoginAsync()
    {
        if (string.IsNullOrEmpty(_config.Token)) return;
        if (!await EnsureConnectedAsync()) return;
        await _client.SendJsonAsync(new { t = "auth_token", token = _config.Token });
    }

    private async Task CreateLobbyAsync()
    {
        var role = _cmbRole.SelectedIndex == 1 ? "P2" : "P1";
        var name = string.IsNullOrWhiteSpace(_txtLobbyName.Text) ? $"{_txtLogin.Text} game" : _txtLobbyName.Text.Trim();
        await SendAsync(new { t = "create_lobby", name, hostRole = role });
    }

    private async Task JoinLobbyAsync(string code)
    {
        if (string.IsNullOrWhiteSpace(code)) { Log("Введите код лобби"); return; }
        await SendAsync(new { t = "join_lobby", code = code.ToUpperInvariant() });
    }

    private void HandleJsonFromBackground(JsonElement msg)
    {
        try { BeginInvoke(() => HandleJson(msg)); } catch (InvalidOperationException) { /* окно закрыто */ }
    }

    private void HandleJson(JsonElement msg)
    {
        var type = msg.TryGetProperty("t", out var t) ? t.GetString() : null;
        switch (type)
        {
            case "hello":
                Log("Сервер готов");
                break;

            case "auth_ok":
                _config.Login = msg.GetProperty("login").GetString() ?? "";
                _config.Token = msg.TryGetProperty("token", out var tok) ? tok.GetString() ?? "" : "";
                _config.Save();
                SetAuthState(true);
                Log($"Вход выполнен: {_config.Login}");
                _ = SendAsync(new { t = "list_lobbies" });
                break;

            case "lobby_list":
                _lstLobbies.Items.Clear();
                foreach (var l in msg.GetProperty("lobbies").EnumerateArray())
                {
                    var code = l.GetProperty("code").GetString();
                    var name = l.GetProperty("name").GetString();
                    var host = l.GetProperty("host").GetString();
                    var players = l.GetProperty("players").GetInt32();
                    _lstLobbies.Items.Add(new LobbyItem(code, $"{code} — {name} (хост: {host}, игроков: {players}/2)"));
                }
                break;

            case "lobby_created":
                _isHost = true;
                _role = msg.GetProperty("role").GetString() ?? "P1";
                _lobbyCode = msg.GetProperty("lobby").GetProperty("code").GetString();
                _lblLobby.Text = $"Лобби {_lobbyCode} создано. Вы — хост ({_role}). Ожидание второго игрока...";
                _lblKeys.Text = $"Ваши клавиши: {KeyPolicy.Describe(_role)}";
                _btnLeave.Enabled = true;
                Log($"Лобби создано. Код для друга: {_lobbyCode}");
                break;

            case "lobby_joined":
                _isHost = false;
                _role = msg.GetProperty("role").GetString() ?? "P2";
                _lobbyCode = msg.GetProperty("lobby").GetProperty("code").GetString();
                _lblLobby.Text = $"Вы в лобби {_lobbyCode} как гость ({_role}). Ждём старта от хоста.";
                _lblKeys.Text = $"Ваши клавиши: {KeyPolicy.Describe(_role)}";
                _btnLeave.Enabled = true;
                break;

            case "peer_joined":
                _btnStart.Enabled = _isHost;
                _lblLobby.Text = $"Лобби {_lobbyCode}: подключился {msg.GetProperty("login").GetString()}. Можно начинать.";
                Log("Второй игрок подключился");
                break;

            case "peer_left":
                _btnStart.Enabled = false;
                Log("Второй игрок отключился");
                break;

            case "lobby_left":
                ResetLobbyState("Вы покинули лобби");
                break;

            case "lobby_closed":
                ResetLobbyState("Хост закрыл лобби");
                break;

            case "started":
                StartSession();
                break;

            case "stopped":
                Log("Сессия остановлена");
                break;

            case "chat":
                Log($"{msg.GetProperty("from").GetString()}: {msg.GetProperty("text").GetString()}");
                break;

            case "error":
                Log($"Ошибка: {msg.GetProperty("message").GetString()}");
                break;
        }
    }

    private void ResetLobbyState(string reason)
    {
        _lobbyCode = null;
        _btnStart.Enabled = false;
        _btnLeave.Enabled = false;
        _lblLobby.Text = "Вы не в лобби";
        Log(reason);
        if (_sessionForm is { IsDisposed: false }) _sessionForm.Close();
    }

    private void SetAuthState(bool ok)
    {
        _lblAuth.Text = ok ? $"Авторизован: {_config.Login}" : "Не авторизован";
        _lblAuth.ForeColor = ok ? Color.SeaGreen : Color.Firebrick;
        _btnCreate.Enabled = ok;
        _btnJoin.Enabled = ok;
        _btnRefresh.Enabled = ok;
    }

    /// <summary>Открывает окно сессии соответственно роли.</summary>
    private void StartSession()
    {
        if (_sessionForm is { IsDisposed: false }) return;
        if (_isHost)
        {
            var form = new HostForm(_client, _config, _role);
            _sessionForm = form;
            form.FormClosed += (_, _) => Log("Трансляция завершена");
            form.Show(this);
            Log("Игра началась: вы транслируете экран и принимаете ввод второго игрока");
        }
        else
        {
            var form = new ViewerForm(_client, _role);
            _sessionForm = form;
            form.FormClosed += (_, _) => Log("Окно трансляции закрыто");
            form.Show(this);
            Log("Игра началась: смотрите трансляцию и играйте своими клавишами");
        }
    }
}
