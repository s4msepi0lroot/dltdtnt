using System.Text.Json;
using DeltaDotNet.Client.Net;
using DeltaDotNet.Client.Ui;

namespace DeltaDotNet.Client.Forms;

/// <summary>
/// Админ-панель. Открывается только если сервер прислал isAdmin = true
/// (роль admin автоматически получает учётка, указанная в ADMIN_LOGIN, по умолчанию s4msepi0l).
/// Каждая команда ещё раз проверяется на сервере, так что спрятанная кнопка -
/// это удобство, а не защита.
///
/// Возможности: переливающиеся ники, цвет ника, тег перед ником, бан/разбан,
/// выдача роли админа, сброс пароля, удаление учётки, список и закрытие любых
/// лобби, объявление всем онлайн и статистика сервера.
/// </summary>
public sealed class AdminForm : Form
{
    private readonly RelayClient _client;

    private readonly UserListBox _users = new();
    private readonly DeltaListBox _lobbies = new();
    private readonly DeltaListBox _log = new();
    private readonly Label _stats = DeltaTheme.Caption("", DeltaTheme.TextDim, DeltaTheme.FontSmall);

    private readonly List<string> _lobbyCodes = new();

    public AdminForm(RelayClient client)
    {
        _client = client;

        Text = "DeltaDotNet - админка";
        ClientSize = new Size(980, 700);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterParent;
        DeltaTheme.ApplyForm(this);
        DeltaAssets.ApplyIcon(this);

        Build();

        _client.OnJson += HandleJson;
        FormClosed += (s, e) => _client.OnJson -= HandleJson;

        Refresh_();
    }

    // ------------------------------------------------------------------ вёрстка
    private void Build()
    {
        var title = DeltaTheme.Title("АДМИНКА");
        title.Location = new Point(28, 20);
        Controls.Add(title);

        _stats.Location = new Point(30, 664);
        _stats.Size = new Size(920, 18);
        Controls.Add(_stats);

        // ---- пользователи
        var usersPanel = new DeltaPanel("ПОЛЬЗОВАТЕЛИ") { Location = new Point(26, 64), Size = new Size(560, 380) };
        Controls.Add(usersPanel);

        _users.Location = new Point(16, 34);
        _users.Size = new Size(528, 250);
        usersPanel.Controls.Add(_users);

        AddButton(usersPanel, "РАДУГА ВКЛ/ВЫКЛ", 16, 296, 170, ToggleRainbow);
        AddButton(usersPanel, "ЦВЕТ НИКА", 196, 296, 170, SetColor);
        AddButton(usersPanel, "ТЕГ", 376, 296, 168, SetTag);
        AddButton(usersPanel, "БАН", 16, 336, 170, () => BanUser(true));
        AddButton(usersPanel, "РАЗБАН", 196, 336, 170, () => BanUser(false));
        AddButton(usersPanel, "ЕЩЁ...", 376, 336, 168, MoreActions);

        // ---- лобби
        var lobbyPanel = new DeltaPanel("ЛОББИ НА СЕРВЕРЕ") { Location = new Point(600, 64), Size = new Size(354, 380) };
        Controls.Add(lobbyPanel);

        _lobbies.Location = new Point(16, 34);
        _lobbies.Size = new Size(322, 250);
        lobbyPanel.Controls.Add(_lobbies);

        AddButton(lobbyPanel, "ЗАКРЫТЬ ЛОББИ", 16, 296, 322, CloseLobby);
        AddButton(lobbyPanel, "ОБЪЯВЛЕНИЕ ВСЕМ", 16, 336, 322, Broadcast);

        // ---- журнал
        var logPanel = new DeltaPanel("ЖУРНАЛ ДЕЙСТВИЙ") { Location = new Point(26, 460), Size = new Size(928, 190) };
        Controls.Add(logPanel);

        _log.Location = new Point(16, 34);
        _log.Size = new Size(896, 100);
        logPanel.Controls.Add(_log);

        AddButton(logPanel, "ОБНОВИТЬ", 16, 142, 200, Refresh_);
        AddButton(logPanel, "ЗАКРЫТЬ", 232, 142, 200, Close);
    }

    private static void AddButton(Control parent, string text, int x, int y, int w, Action onClick)
    {
        var button = new DeltaButton { Text = text, Location = new Point(x, y), Size = new Size(w, 36) };
        button.Click += (s, e) => onClick();
        parent.Controls.Add(button);
    }

    // ------------------------------------------------------------------- действия
    private void Refresh_()
    {
        Send(new { t = "admin_users" });
        Send(new { t = "admin_lobbies" });
        Send(new { t = "admin_stats" });
    }

    private void Send(object message)
    {
        try
        {
            _ = _client.SendJsonAsync(message);
        }
        catch (Exception ex)
        {
            Log("Ошибка отправки: " + ex.Message);
        }
    }

    /// <summary>Выбранный в списке пользователь или null с показом подсказки.</summary>
    private AdminUser Selected()
    {
        var user = _users.SelectedItem as AdminUser;
        if (user == null) Log("Сначала выберите пользователя в списке");
        return user;
    }

    private void ToggleRainbow()
    {
        var user = Selected();
        if (user == null) return;
        Send(new
        {
            t = "admin_set_cosmetic",
            login = user.Login,
            rainbow = !user.Cosmetic.Rainbow,
            color = user.Cosmetic.Color,
            tag = user.Cosmetic.Tag,
        });
        Log((user.Cosmetic.Rainbow ? "Снимаем" : "Выдаём") + " переливающийся ник: " + user.Login);
    }

    private void SetColor()
    {
        var user = Selected();
        if (user == null) return;
        var value = PromptForm.Ask(this, "Цвет ника", "В виде #RRGGBB, пусто — убрать цвет", user.Cosmetic.Color ?? "");
        if (value == null) return;
        Send(new
        {
            t = "admin_set_cosmetic",
            login = user.Login,
            rainbow = user.Cosmetic.Rainbow,
            color = string.IsNullOrWhiteSpace(value) ? null : value.Trim(),
            tag = user.Cosmetic.Tag,
        });
    }

    private void SetTag()
    {
        var user = Selected();
        if (user == null) return;
        var value = PromptForm.Ask(this, "Тег перед ником", "Например VIP, до 16 символов, пусто — убрать", user.Cosmetic.Tag ?? "");
        if (value == null) return;
        Send(new
        {
            t = "admin_set_cosmetic",
            login = user.Login,
            rainbow = user.Cosmetic.Rainbow,
            color = user.Cosmetic.Color,
            tag = string.IsNullOrWhiteSpace(value) ? null : value.Trim(),
        });
    }

    private void BanUser(bool banned)
    {
        var user = Selected();
        if (user == null) return;

        if (banned)
        {
            var reason = PromptForm.Ask(this, "Бан " + user.Login, "Причина (можно оставить пустым)", "");
            if (reason == null) return;
            Send(new { t = "admin_ban", login = user.Login, reason });
        }
        else
        {
            Send(new { t = "admin_unban", login = user.Login });
        }
    }

    /// <summary>Редкие действия: роль, сброс пароля, удаление учётки.</summary>
    private void MoreActions()
    {
        var user = Selected();
        if (user == null) return;

        using var menu = new ContextMenuStrip();
        menu.Items.Add(user.Role == "admin" ? "Снять роль админа" : "Сделать админом", null, (s, e) =>
            Send(new { t = "admin_set_role", login = user.Login, role = user.Role == "admin" ? "user" : "admin" }));

        menu.Items.Add("Сбросить пароль", null, (s, e) =>
        {
            var password = PromptForm.Ask(this, "Новый пароль для " + user.Login, "Минимум 6 символов", "");
            if (!string.IsNullOrEmpty(password)) Send(new { t = "admin_set_password", login = user.Login, password });
        });

        menu.Items.Add("Удалить учётную запись", null, (s, e) =>
        {
            var answer = MessageBox.Show(this, "Удалить " + user.Login + " безвозвратно?", "Подтверждение",
                MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
            if (answer == DialogResult.Yes) Send(new { t = "admin_delete_user", login = user.Login });
        });

        menu.Show(Cursor.Position);
        // Ждём закрытия меню, иначе using уничтожит его сразу.
        while (menu.Visible) Application.DoEvents();
    }

    private void CloseLobby()
    {
        int index = _lobbies.SelectedIndex;
        if (index < 0 || index >= _lobbyCodes.Count)
        {
            Log("Сначала выберите лобби");
            return;
        }
        Send(new { t = "admin_close_lobby", code = _lobbyCodes[index] });
    }

    private void Broadcast()
    {
        var text = PromptForm.Ask(this, "Объявление", "Сообщение увидят все, кто сейчас онлайн", "");
        if (string.IsNullOrWhiteSpace(text)) return;
        Send(new { t = "admin_broadcast", text });
    }

    private void Log(string text)
    {
        _log.Items.Insert(0, DateTime.Now.ToString("HH:mm:ss") + "  " + text);
        while (_log.Items.Count > 200) _log.Items.RemoveAt(_log.Items.Count - 1);
    }

    // -------------------------------------------------------- сообщения сервера
    private void HandleJson(JsonElement m)
    {
        if (IsDisposed) return;
        try { BeginInvoke(new Action(() => OnJson(m))); } catch { /* окно закрывается */ }
    }

    private void OnJson(JsonElement m)
    {
        if (IsDisposed || !m.TryGetProperty("t", out var typeElement)) return;

        switch (typeElement.GetString())
        {
            case "admin_users":
            {
                var selected = (_users.SelectedItem as AdminUser)?.Login;
                _users.Items.Clear();
                foreach (var element in m.GetProperty("users").EnumerateArray())
                    _users.Items.Add(AdminUser.Parse(element));

                if (selected != null)
                {
                    for (int i = 0; i < _users.Items.Count; i++)
                        if (((AdminUser)_users.Items[i]).Login == selected) { _users.SelectedIndex = i; break; }
                }
                break;
            }

            case "admin_user":
            {
                var user = AdminUser.Parse(m.GetProperty("user"));
                Log("Обновлён " + user.Login + ": " + user.Describe());
                Send(new { t = "admin_users" });
                break;
            }

            case "admin_user_deleted":
                Log("Удалён " + m.GetProperty("login").GetString());
                Send(new { t = "admin_users" });
                break;

            case "admin_lobbies":
            {
                _lobbies.Items.Clear();
                _lobbyCodes.Clear();
                foreach (var lobby in m.GetProperty("lobbies").EnumerateArray())
                {
                    _lobbyCodes.Add(lobby.GetProperty("code").GetString());
                    _lobbies.Items.Add(string.Format("[{0}] {1} — {2}/{3} — {4}{5}",
                        lobby.GetProperty("code").GetString(),
                        lobby.GetProperty("name").GetString(),
                        lobby.GetProperty("playerCount").GetInt32(),
                        lobby.GetProperty("maxPlayers").GetInt32(),
                        lobby.GetProperty("visibility").GetString() == "private" ? "закрытое" : "открытое",
                        lobby.GetProperty("running").GetBoolean() ? ", идёт игра" : ""));
                }
                if (_lobbies.Items.Count == 0) _lobbies.Items.Add("Лобби нет");
                break;
            }

            case "admin_lobby_closed":
                Log("Лобби " + m.GetProperty("code").GetString() + " закрыто");
                Send(new { t = "admin_lobbies" });
                break;

            case "admin_broadcast_ok":
                Log("Объявление доставлено: " + m.GetProperty("sent").GetInt32() + " чел.");
                break;

            case "admin_stats":
            {
                var s = m.GetProperty("stats");
                _stats.Text = string.Format(
                    "* Аптайм {0} мин · пользователей {1} · онлайн {2} · лобби {3} · кадров {4} · трафик {5:F1} МБ · регистрация {6}",
                    s.GetProperty("uptimeSec").GetInt32() / 60,
                    s.GetProperty("users").GetInt32(),
                    s.GetProperty("online").GetInt32(),
                    s.GetProperty("lobbies").GetInt32(),
                    s.GetProperty("frames").GetInt64(),
                    s.GetProperty("bytes").GetInt64() / 1024.0 / 1024.0,
                    s.GetProperty("allowRegister").GetBoolean() ? "открыта" : "закрыта");
                break;
            }

            case "error":
                Log("Ошибка: " + m.GetProperty("message").GetString());
                break;
        }
    }
}

/// <summary>Строка списка пользователей в админке.</summary>
public sealed class AdminUser
{
    public string Login { get; init; }
    public string Role { get; init; }
    public bool Banned { get; init; }
    public string BanReason { get; init; }
    public bool Online { get; init; }
    public Cosmetic Cosmetic { get; init; } = new();

    public static AdminUser Parse(JsonElement e)
    {
        var cosmetic = new Cosmetic();
        if (e.TryGetProperty("cosmetic", out var c) && c.ValueKind == JsonValueKind.Object)
        {
            cosmetic.Rainbow = c.TryGetProperty("rainbow", out var r) && r.ValueKind == JsonValueKind.True;
            cosmetic.Color = c.TryGetProperty("color", out var col) && col.ValueKind == JsonValueKind.String ? col.GetString() : null;
            cosmetic.Tag = c.TryGetProperty("tag", out var tag) && tag.ValueKind == JsonValueKind.String ? tag.GetString() : null;
        }

        return new AdminUser
        {
            Login = e.GetProperty("login").GetString(),
            Role = e.TryGetProperty("role", out var role) ? role.GetString() : "user",
            Banned = e.TryGetProperty("banned", out var b) && b.ValueKind == JsonValueKind.True,
            BanReason = e.TryGetProperty("banReason", out var br) && br.ValueKind == JsonValueKind.String ? br.GetString() : null,
            Online = e.TryGetProperty("online", out var on) && on.ValueKind == JsonValueKind.True,
            Cosmetic = cosmetic,
        };
    }

    /// <summary>Короткое описание статуса для журнала и списка.</summary>
    public string Describe()
    {
        var parts = new List<string>();
        if (Role == "admin") parts.Add("админ");
        if (Banned) parts.Add("бан" + (string.IsNullOrEmpty(BanReason) ? "" : ": " + BanReason));
        if (Cosmetic.Rainbow) parts.Add("радуга");
        if (!string.IsNullOrEmpty(Cosmetic.Color)) parts.Add("цвет " + Cosmetic.Color);
        if (!string.IsNullOrEmpty(Cosmetic.Tag)) parts.Add("тег " + Cosmetic.Tag);
        if (Online) parts.Add("онлайн");
        return parts.Count == 0 ? "обычный игрок" : string.Join(", ", parts);
    }

    public override string ToString() => Login + "  —  " + Describe();
}

/// <summary>
/// Список пользователей, в котором ники рисуются с учётом украшений:
/// видно, как именно ник выглядит у остальных игроков.
/// </summary>
public sealed class UserListBox : DeltaListBox
{
    private readonly System.Windows.Forms.Timer _timer = new() { Interval = 60 };

    public UserListBox()
    {
        ItemHeight = 26;
        _timer.Tick += (s, e) => Invalidate();
        _timer.Start();
    }

    protected override void OnDrawItem(DrawItemEventArgs e)
    {
        if (e.Index < 0) return;
        if (Items[e.Index] is not AdminUser user)
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
        var baseColor = user.Banned ? DeltaTheme.Bad : selected ? DeltaTheme.Accent : DeltaTheme.Text;
        int width = RainbowText.Draw(g, user.Login, Font, new Point(left, y), baseColor, user.Cosmetic);

        TextRenderer.DrawText(g, "  —  " + user.Describe(), Font,
            new Rectangle(left + width, e.Bounds.Top, e.Bounds.Right - left - width - 4, e.Bounds.Height),
            DeltaTheme.TextDim,
            TextFormatFlags.VerticalCenter | TextFormatFlags.Left | TextFormatFlags.EndEllipsis);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing) _timer.Dispose();
        base.Dispose(disposing);
    }
}

/// <summary>Маленькое окно ввода строки в стилистике игры (вместо InputBox).</summary>
public sealed class PromptForm : Form
{
    private readonly DeltaTextBox _input = new();

    private PromptForm(string title, string hint, string initial)
    {
        Text = title;
        ClientSize = new Size(440, 200);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterParent;
        DeltaTheme.ApplyForm(this);

        var caption = DeltaTheme.Caption(title, DeltaTheme.Text, DeltaTheme.FontBig);
        caption.Location = new Point(24, 22);
        caption.Size = new Size(392, 24);
        Controls.Add(caption);

        var hintLabel = DeltaTheme.Caption(hint, DeltaTheme.TextDim, DeltaTheme.FontSmall);
        hintLabel.Location = new Point(24, 52);
        hintLabel.Size = new Size(392, 18);
        Controls.Add(hintLabel);

        _input.Location = new Point(24, 80);
        _input.Size = new Size(392, 34);
        _input.Text = initial ?? "";
        Controls.Add(_input);

        var ok = new DeltaButton { Text = "ОК", Location = new Point(24, 132), Size = new Size(190, 40) };
        ok.Click += (s, e) => { DialogResult = DialogResult.OK; Close(); };
        Controls.Add(ok);

        var cancel = new DeltaButton { Text = "ОТМЕНА", Location = new Point(226, 132), Size = new Size(190, 40) };
        cancel.Click += (s, e) => { DialogResult = DialogResult.Cancel; Close(); };
        Controls.Add(cancel);

        AcceptButton = ok;
        CancelButton = cancel;
    }

    /// <summary>Показывает окно ввода. Возвращает null, если пользователь нажал Отмена.</summary>
    public static string Ask(IWin32Window owner, string title, string hint, string initial = "")
    {
        using var form = new PromptForm(title, hint, initial);
        return form.ShowDialog(owner) == DialogResult.OK ? form._input.Text : null;
    }
}
