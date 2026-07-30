using System.Text.Json;
using CoopStream.Client.Capture;
using CoopStream.Client.Input;
using CoopStream.Client.Net;

namespace CoopStream.Client.Forms;

/// <summary>
/// Окно гостя (второго игрока): показывает трансляцию хоста и отправляет его нажатия.
/// Клавиатура читается напрямую из WM_KEYDOWN/WM_KEYUP: так можно различить левый/правый
/// Shift и Ctrl и отбросить автоповтор.
/// </summary>
public sealed class ViewerForm : Form
{
    private const int WM_KEYDOWN = 0x0100;
    private const int WM_KEYUP = 0x0101;
    private const int WM_SYSKEYDOWN = 0x0104;
    private const int WM_SYSKEYUP = 0x0105;

    private readonly RelayClient _client;
    private readonly string _role;
    private readonly PictureBox _picture = new() { Dock = DockStyle.Fill, SizeMode = PictureBoxSizeMode.Zoom, BackColor = Color.Black };
    private readonly StatusStrip _status = new();
    private readonly ToolStripStatusLabel _lblStatus = new() { Text = "Ожидание кадров..." };
    private readonly HashSet<string> _pressed = new(StringComparer.Ordinal);

    private Image _lastImage;
    private int _framesReceived;
    private long _bytesReceived;
    private long _lastFrameTimestamp;
    private int _latencyMs;

    public ViewerForm(RelayClient client, string role)
    {
        _client = client;
        _role = role;

        Text = $"CoopStream — игрок {role} | клавиши: {KeyPolicy.Describe(role)}";
        Width = 1280;
        Height = 760;
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.Black;
        KeyPreview = true;
        DoubleBuffered = true;

        _status.Items.Add(_lblStatus);
        Controls.Add(_picture);
        Controls.Add(_status);

        _client.OnBinary += HandleBinaryFromBackground;
        _client.OnJson += HandleJsonFromBackground;

        // Потеря фокуса — отпускаем все клавиши, иначе персонаж убежит в стену.
        Deactivate += (_, _) => ReleaseAll();

        var timer = new System.Windows.Forms.Timer { Interval = 1000 };
        timer.Tick += (_, _) => UpdateStatus();
        timer.Start();

        FormClosing += (_, _) =>
        {
            ReleaseAll();
            timer.Stop();
            _client.OnBinary -= HandleBinaryFromBackground;
            _client.OnJson -= HandleJsonFromBackground;
        };
    }

    /// <summary>Перехват сырых сообщений клавиатуры: scan-код и флаг extended берём из lParam.</summary>
    protected override bool ProcessKeyPreview(ref Message m)
    {
        if (m.Msg is WM_KEYDOWN or WM_KEYUP or WM_SYSKEYDOWN or WM_SYSKEYUP)
        {
            long lParam = m.LParam.ToInt64();
            var scan = (ushort)((lParam >> 16) & 0xFF);
            var extended = ((lParam >> 24) & 0x1) != 0;
            var wasDown = ((lParam >> 30) & 0x1) != 0;
            var isDown = m.Msg is WM_KEYDOWN or WM_SYSKEYDOWN;

            var key = KeyMap.FromScan(scan, extended);
            if (key != null && KeyPolicy.IsAllowed(_role, key))
            {
                if (isDown && wasDown) return true;              // автоповтор — не шлём
                if (isDown) { if (!_pressed.Add(key)) return true; }
                else _pressed.Remove(key);

                _ = _client.SendJsonAsync(new { t = "input", key, down = isDown, ts = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() });
                return true; // гасим событие, чтобы WinForms не дёргал фокус кнопками
            }
        }
        return base.ProcessKeyPreview(ref m);
    }

    /// <summary>Стрелки и Enter должны приходить как обычные клавиши, а не как навигация по форме.</summary>
    protected override bool IsInputKey(Keys keyData) => true;

    private void ReleaseAll()
    {
        foreach (var key in _pressed.ToArray())
            _ = _client.SendJsonAsync(new { t = "input", key, down = false });
        _pressed.Clear();
        _ = _client.SendJsonAsync(new { t = "release_all" });
    }

    private void HandleBinaryFromBackground(byte[] packet)
    {
        if (!ScreenCapturer.TryParse(packet, out var image, out _, out var ts)) return;
        _framesReceived++;
        _bytesReceived += packet.Length;
        _lastFrameTimestamp = ts;
        try
        {
            BeginInvoke(() =>
            {
                var old = _lastImage;
                _lastImage = image;
                _picture.Image = image;
                old?.Dispose();
            });
        }
        catch (InvalidOperationException)
        {
            image.Dispose(); // окно уже закрыто
        }
    }

    private void HandleJsonFromBackground(JsonElement msg)
    {
        var type = msg.TryGetProperty("t", out var t) ? t.GetString() : null;
        if (type is "stopped" or "lobby_closed")
        {
            try { BeginInvoke(Close); } catch (InvalidOperationException) { }
        }
    }

    private void UpdateStatus()
    {
        if (_lastFrameTimestamp > 0)
            _latencyMs = (int)(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - _lastFrameTimestamp);
        _lblStatus.Text = $"Роль: {_role} | кадров: {_framesReceived} | трафик: {_bytesReceived / 1_048_576.0:F1} МБ | задержка кадра: ~{_latencyMs} мс | клавиши: {KeyPolicy.Describe(_role)}";
    }
}
