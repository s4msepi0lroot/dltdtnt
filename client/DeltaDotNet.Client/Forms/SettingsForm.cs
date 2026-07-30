using DeltaDotNet.Client.Ui;

namespace DeltaDotNet.Client.Forms;

/// <summary>
/// Настройки качества трансляции: частота кадров, сжатие JPEG и ширина картинки.
///
/// Все три значения влияют только на хоста (он сжимает и отправляет картинку),
/// но хранятся у каждого локально - любой может стать хостом в следующей игре.
///
/// Ориентиры:
///   медленный канал   - 12 кадр/с, качество 40, ширина 960
///   обычный            - 20 кадр/с, качество 55, ширина 1280
///   хороший канал     - 30 кадр/с, качество 75, ширина 1600
/// </summary>
public sealed class SettingsForm : Form
{
    private readonly AppConfig _cfg;

    private readonly TrackBar _fps = new() { Minimum = 5, Maximum = 60, TickFrequency = 5 };
    private readonly TrackBar _quality = new() { Minimum = 20, Maximum = 95, TickFrequency = 5 };
    private readonly TrackBar _width = new() { Minimum = 480, Maximum = 1920, TickFrequency = 120, SmallChange = 20, LargeChange = 160 };

    private readonly Label _fpsValue = DeltaTheme.Caption("", DeltaTheme.Accent, DeltaTheme.FontBig);
    private readonly Label _qualityValue = DeltaTheme.Caption("", DeltaTheme.Accent, DeltaTheme.FontBig);
    private readonly Label _widthValue = DeltaTheme.Caption("", DeltaTheme.Accent, DeltaTheme.FontBig);
    private readonly Label _estimate = DeltaTheme.Caption("", DeltaTheme.TextDim, DeltaTheme.FontSmall);

    public SettingsForm(AppConfig cfg)
    {
        _cfg = cfg;

        Text = "Delta.Dot.Net - качество";
        ClientSize = new Size(560, 520);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterParent;
        DeltaTheme.ApplyForm(this);
        DeltaAssets.ApplyIcon(this);

        var title = DeltaTheme.Caption("НАСТРОЙКИ КАЧЕСТВА", DeltaTheme.Text, DeltaTheme.FontBig);
        title.Location = new Point(28, 24);
        Controls.Add(title);

        var panel = new DeltaPanel("ТРАНСЛЯЦИЯ") { Location = new Point(24, 60), Size = new Size(512, 330) };
        Controls.Add(panel);

        BuildSlider(panel, "Кадров в секунду", "больше - плавнее, но тяжелее для сети", _fps, _fpsValue, 36);
        BuildSlider(panel, "Качество JPEG", "больше - чётче картинка, но толще кадр", _quality, _qualityValue, 132);
        BuildSlider(panel, "Максимальная ширина, пикселей", "картинка уменьшается до этой ширины", _width, _widthValue, 228);

        _estimate.Location = new Point(28, 400);
        _estimate.Size = new Size(500, 18);
        Controls.Add(_estimate);

        // Готовые пресеты - чтобы не крутить три ползунка вручную.
        AddPreset("ЭКОНОМ", 24, 424, 12, 40, 960);
        AddPreset("ОБЫЧНО", 196, 424, 20, 55, 1280);
        AddPreset("КАЧЕСТВО", 368, 424, 30, 75, 1600);

        var save = new DeltaButton { Text = "СОХРАНИТЬ", Location = new Point(24, 470), Size = new Size(340, 40) };
        save.Click += (s, e) =>
        {
            _cfg.Fps = _fps.Value;
            _cfg.JpegQuality = _quality.Value;
            _cfg.MaxWidth = _width.Value;
            _cfg.Save();
            DialogResult = DialogResult.OK;
            Close();
        };
        Controls.Add(save);

        var cancel = new DeltaButton { Text = "ОТМЕНА", Location = new Point(376, 470), Size = new Size(160, 40) };
        cancel.Click += (s, e) => { DialogResult = DialogResult.Cancel; Close(); };
        Controls.Add(cancel);

        // Загружаем текущие значения (с подстраховкой от выхода за границы).
        _fps.Value = Math.Clamp(_cfg.Fps, _fps.Minimum, _fps.Maximum);
        _quality.Value = Math.Clamp(_cfg.JpegQuality, _quality.Minimum, _quality.Maximum);
        _width.Value = Math.Clamp(_cfg.MaxWidth, _width.Minimum, _width.Maximum);
        UpdateLabels();
    }

    /// <summary>Одна строка настройки: заголовок, подсказка, ползунок и текущее число.</summary>
    private void BuildSlider(Control parent, string caption, string hint, TrackBar bar, Label value, int y)
    {
        var label = DeltaTheme.Caption(caption, DeltaTheme.Text, DeltaTheme.FontBody);
        label.Location = new Point(18, y);
        label.Size = new Size(340, 18);
        parent.Controls.Add(label);

        value.Location = new Point(400, y - 4);
        value.Size = new Size(96, 26);
        value.TextAlign = ContentAlignment.MiddleRight;
        parent.Controls.Add(value);

        var hintLabel = DeltaTheme.Caption(hint, DeltaTheme.TextDim, DeltaTheme.FontSmall);
        hintLabel.Location = new Point(18, y + 20);
        hintLabel.Size = new Size(470, 16);
        parent.Controls.Add(hintLabel);

        bar.Location = new Point(14, y + 40);
        bar.Size = new Size(478, 40);
        bar.BackColor = DeltaTheme.Background;
        bar.ValueChanged += (s, e) => UpdateLabels();
        parent.Controls.Add(bar);
    }

    private void AddPreset(string text, int x, int y, int fps, int quality, int width)
    {
        var button = new DeltaButton { Text = text, Location = new Point(x, y), Size = new Size(160, 36) };
        button.Click += (s, e) =>
        {
            _fps.Value = fps;
            _quality.Value = quality;
            _width.Value = width;
            UpdateLabels();
        };
        Controls.Add(button);
    }

    /// <summary>Обновляет цифры справа и грубую оценку трафика.</summary>
    private void UpdateLabels()
    {
        _fpsValue.Text = _fps.Value + " к/с";
        _qualityValue.Text = _quality.Value.ToString();
        _widthValue.Text = _width.Value + "px";

        // Очень приблизительная оценка: размер кадра растёт с площадью и качеством.
        double pixels = _width.Value * (_width.Value * 9.0 / 16.0);
        double frameKb = pixels / 1000.0 * (_quality.Value / 100.0) * 0.16;
        double mbits = frameKb * 1024 * 8 * _fps.Value / 1_000_000.0;
        _estimate.Text = $"Примерный расход канала у хоста: ~{mbits:0.0} Мбит/с на каждого зрителя";
    }
}
