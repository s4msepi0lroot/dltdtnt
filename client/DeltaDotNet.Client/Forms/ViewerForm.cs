using System.Text.Json;
using DeltaDotNet.Client.Capture;
using DeltaDotNet.Client.Input;
using DeltaDotNet.Client.Net;
using DeltaDotNet.Client.Ui;

namespace DeltaDotNet.Client.Forms;

/// <summary>
/// Окно гостя (P2/P3/P4): показывает трансляцию хоста и отправляет ему свои
/// действия. Клавиши читаются по аппаратному scan code, поэтому раскладка
/// клавиатуры не влияет на игру.
///
/// F2 — открыть редактор своего управления.
/// </summary>
public sealed class ViewerForm : Form
{
    private const int WM_KEYDOWN = 0x0100;
    private const int WM_KEYUP = 0x0101;
    private const int WM_SYSKEYDOWN = 0x0104;
    private const int WM_SYSKEYUP = 0x0105;

    private readonly RelayClient _client;
    private readonly AppConfig _cfg;
    private readonly string _role;

    private readonly Panel _screen = new();
    private readonly Label _status = DeltaTheme.Caption("", DeltaTheme.TextDim, DeltaTheme.FontSmall);
    private readonly Label _keysLabel = DeltaTheme.Caption("", DeltaTheme.Accent, DeltaTheme.FontSmall);

    private Bindings _bindings;
    private readonly HashSet<string> _pressed = new(StringComparer.Ordinal);

    private Image _frame;
    private readonly object _frameLock = new();
    private long _framesReceived;
    private bool _running;
    private string _waitText = "ОЖИДАНИЕ ХОСТА...";

    public ViewerForm(RelayClient client, AppConfig cfg, string role, JsonElement lobby)
    {
        _client = client;
        _cfg = cfg;
        _role = role ?? "P2";
        _bindings = _cfg.GetMyBindings(_role);

        Text = $"DeltaDotNet — игрок {_role}";
        ClientSize = new Size(1000, 700);
        KeyPreview = true;
        DeltaTheme.ApplyForm(this, drawBorder: false);
        DeltaAssets.ApplyIcon(this);

        BuildLayout(lobby);

        _client.OnJson += HandleJson;
        _client.OnBinary += HandleBinary;
        _client.OnClosed += HandleClosed;

        Deactivate += (s, e) => ReleaseAll();

        FormClosed += (s, e) =>
        {
            ReleaseAll();
            _client.OnJson -= HandleJson;
            _client.OnBinary -= HandleBinary;
            _client.OnClosed -= HandleClosed;
            if (_client.IsConnected) _ = _client.SendJsonAsync(new { t = "leave_lobby" });
            lock (_frameLock) { _frame?.Dispose(); _frame = null; }
        };
    }

    // ------------------------------------------------------------------ вёрстка
    private void BuildLayout(JsonElement lobby)
    {
        var header = new Panel { Dock = DockStyle.Top, Height = 56, BackColor = DeltaTheme.Background };
        Controls.Add(header);

        var roleLabel = DeltaTheme.Caption("ВЫ — ИГРОК " + _role, DeltaTheme.Text, DeltaTheme.FontBig);
        roleLabel.Location = new Point(20, 8);
        header.Controls.Add(roleLabel);

        var lobbyLabel = DeltaTheme.Caption(
            "Лобби " + lobby.GetProperty("code").GetString() + " · хост " + lobby.GetProperty("host").GetString(),
            DeltaTheme.TextDim, DeltaTheme.FontSmall);
        lobbyLabel.Location = new Point(22, 34);
        header.Controls.Add(lobbyLabel);

        var btnKeys = new DeltaButton { Text = "УПРАВЛЕНИЕ (F2)", Location = new Point(760, 8), Size = new Size(210, 40) };
        btnKeys.Click += (s, e) => EditBindings();
        header.Controls.Add(btnKeys);

        var footer = new Panel { Dock = DockStyle.Bottom, Height = 46, BackColor = DeltaTheme.Background };
        Controls.Add(footer);
        _status.Location = new Point(20, 6);
        _keysLabel.Location = new Point(20, 26);
        footer.Controls.Add(_status);
        footer.Controls.Add(_keysLabel);

        _screen.Dock = DockStyle.Fill;
        _screen.BackColor = DeltaTheme.Background;
        typeof(Panel).GetProperty("DoubleBuffered", System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic)
            ?.SetValue(_screen, true, null);
        _screen.Paint += DrawScreen;
        Controls.Add(_screen);
        _screen.BringToFront();

        UpdateKeysLabel();
        UpdateStatus("Ожидаем старт игры от хоста");
    }

    private void UpdateStatus(string text, Color? color = null)
    {
        _status.Text = "* " + text;
        _status.ForeColor = color ?? DeltaTheme.TextDim;
    }

    private void UpdateKeysLabel()
        => _keysLabel.Text = "Ваши клавиши: " + _bindings.Describe();

    /// <summary>Отрисовка кадра с сохранением пропорций и чёрными полями.</summary>
    private void DrawScreen(object sender, PaintEventArgs e)
    {
        var g = e.Graphics;
        g.Clear(DeltaTheme.Background);

        Image image;
        lock (_frameLock) image = _frame;

        if (image == null)
        {
            var size = TextRenderer.MeasureText(_waitText, DeltaTheme.FontBig);
            TextRenderer.DrawText(g, _waitText, DeltaTheme.FontBig,
                new Point((_screen.Width - size.Width) / 2, (_screen.Height - size.Height) / 2), DeltaTheme.TextDim);
            return;
        }

        double scale = Math.Min(_screen.Width / (double)image.Width, _screen.Height / (double)image.Height);
        int w = Math.Max(1, (int)(image.Width * scale));
        int h = Math.Max(1, (int)(image.Height * scale));
        g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
        g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
        lock (_frameLock)
        {
            if (_frame != null)
                g.DrawImage(_frame, (_screen.Width - w) / 2, (_screen.Height - h) / 2, w, h);
        }
    }

    // --------------------------------------------------------------- клавиатура
    protected override bool ProcessKeyPreview(ref Message m)
    {
        if (m.Msg == WM_KEYDOWN || m.Msg == WM_SYSKEYDOWN || m.Msg == WM_KEYUP || m.Msg == WM_SYSKEYUP)
        {
            long lParam = m.LParam.ToInt64();
            uint scan = (uint)((lParam >> 16) & 0xFF);
            bool extended = ((lParam >> 24) & 1) != 0;
            bool down = m.Msg == WM_KEYDOWN || m.Msg == WM_SYSKEYDOWN;

            var keyName = KeyMap.FromScan(scan, extended);

            // F2 — редактор управления (не уходит в игру).
            if (keyName == "F2")
            {
                if (down) EditBindings();
                return true;
            }

            var action = _bindings.ActionFor(keyName);
            if (action == null) return base.ProcessKeyPreview(ref m);

            // Автоповтор Windows не шлём — игре нужны только фронты нажатия/отпускания.
            if (down && !_pressed.Add(action)) return true;
            if (!down && !_pressed.Remove(action)) return true;

            _ = SendActionAsync(action, down);
            return true;
        }
        return base.ProcessKeyPreview(ref m);
    }

    private async Task SendActionAsync(string action, bool down)
    {
        try
        {
            if (_client.IsConnected)
                await _client.SendJsonAsync(new { t = "input", action, down });
        }
        catch
        {
            // Обрыв связи обрабатывается в OnClosed.
        }
    }

    private void ReleaseAll()
    {
        if (_pressed.Count == 0) return;
        _pressed.Clear();
        if (_client.IsConnected) _ = _client.SendJsonAsync(new { t = "release_all" });
    }

    private void EditBindings()
    {
        ReleaseAll();
        using var dialog = new BindingsForm(
            _bindings, _role,
            "МОЁ УПРАВЛЕНИЕ — " + _role,
            "Какие клавиши вы жмёте у себя. Остальных это не касается.");
        if (dialog.ShowDialog(this) != DialogResult.OK) return;

        _bindings = dialog.Result;
        _cfg.SetMyBindings(_role, _bindings);
        _cfg.Save();
        UpdateKeysLabel();
    }

    // ------------------------------------------------------------------- сеть
    private void HandleBinary(byte[] packet)
    {
        if (!ScreenCapturer.TryParse(packet, out var image, out _, out var timestampMs)) return;

        lock (_frameLock)
        {
            _frame?.Dispose();
            _frame = image;
        }
        _framesReceived++;

        if (IsHandleCreated)
        {
            BeginInvoke(new Action(() =>
            {
                if (IsDisposed) return;
                _screen.Invalidate();
                long lag = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - timestampMs;
                UpdateStatus($"Кадров: {_framesReceived} · задержка ~{Math.Max(0, lag)} мс", DeltaTheme.Good);
            }));
        }
    }

    private void HandleClosed(string reason)
        => BeginInvoke(new Action(() =>
        {
            if (IsDisposed) return;
            _running = false;
            _waitText = "СОЕДИНЕНИЕ ПОТЕРЯНО";
            UpdateStatus("Соединение закрыто: " + reason, DeltaTheme.Bad);
            _screen.Invalidate();
        }));

    private void HandleJson(JsonElement m)
        => BeginInvoke(new Action(() =>
        {
            if (IsDisposed || !m.TryGetProperty("t", out var typeElement)) return;

            switch (typeElement.GetString())
            {
                case "started":
                    _running = true;
                    _waitText = "ПОЛУЧАЕМ КАРТИНКУ...";
                    UpdateStatus("Игра началась", DeltaTheme.Good);
                    _screen.Invalidate();
                    break;

                case "stopped":
                    _running = false;
                    ReleaseAll();
                    _waitText = "ХОСТ ОСТАНОВИЛ ИГРУ";
                    UpdateStatus("Игра остановлена");
                    _screen.Invalidate();
                    break;

                case "peer_joined":
                case "peer_left":
                    UpdateStatus("Состав игроков изменился: " +
                        m.GetProperty("lobby").GetProperty("playerCount").GetInt32() + "/" +
                        m.GetProperty("lobby").GetProperty("maxPlayers").GetInt32());
                    break;

                case "lobby_closed":
                    ReleaseAll();
                    MessageBox.Show(this,
                        m.TryGetProperty("reason", out var closeReason) && closeReason.ValueKind == JsonValueKind.String
                            ? "Лобби закрыто: " + closeReason.GetString()
                            : "Хост закрыл лобби.",
                        "DeltaDotNet", MessageBoxButtons.OK, MessageBoxIcon.Information);
                    Close();
                    break;

                // Хост выгнал или забанил нас — возвращаемся в главное меню.
                case "kicked":
                {
                    ReleaseAll();
                    bool banned = m.TryGetProperty("banned", out var b) && b.ValueKind == JsonValueKind.True;
                    string reason = m.TryGetProperty("reason", out var r) && r.ValueKind == JsonValueKind.String
                        ? r.GetString()
                        : "без указания причины";
                    MessageBox.Show(this,
                        (banned ? "Вас забанили в этом лобби." : "Вас выгнали из лобби.") + "\nПричина: " + reason,
                        "DeltaDotNet", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    Close();
                    break;
                }

                // Объявление от администратора сервера.
                case "announce":
                    UpdateStatus("Объявление: " + m.GetProperty("text").GetString(), DeltaTheme.Accent);
                    break;

                case "error":
                    UpdateStatus("Ошибка: " + m.GetProperty("message").GetString(), DeltaTheme.Bad);
                    break;
            }
        }));
}
