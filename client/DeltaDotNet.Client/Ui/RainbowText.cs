namespace DeltaDotNet.Client.Ui;

/// <summary>
/// Украшения ника, которые выдаёт админ: переливающийся цвет, свой цвет, тег.
/// Приходят с сервера вместе со списком игроков.
/// </summary>
public sealed class Cosmetic
{
    /// <summary>Ник переливается всеми цветами радуги.</summary>
    public bool Rainbow { get; set; }

    /// <summary>Постоянный цвет в виде "#RRGGBB" (если задан и нет радуги).</summary>
    public string Color { get; set; }

    /// <summary>Короткая приставка перед ником, например "VIP".</summary>
    public string Tag { get; set; }

    public bool IsEmpty => !Rainbow && string.IsNullOrEmpty(Color) && string.IsNullOrEmpty(Tag);

    /// <summary>Разбирает Color в System.Drawing.Color; если не вышло - возвращает fallback.</summary>
    public Color Resolve(Color fallback)
    {
        if (string.IsNullOrEmpty(Color)) return fallback;
        try
        {
            return ColorTranslator.FromHtml(Color);
        }
        catch
        {
            return fallback;
        }
    }
}

/// <summary>
/// Отрисовка ников с украшениями.
///
/// Радуга считается от общего таймера: все переливающиеся ники в окне
/// меняют цвет синхронно, а соседние буквы сдвинуты по фазе - получается
/// бегущая волна, как в игровых меню.
/// </summary>
public static class RainbowText
{
    /// <summary>Полный цикл радуги в миллисекундах.</summary>
    public const int CycleMs = 2600;

    /// <summary>На сколько сдвигается фаза каждого следующего символа (доля цикла).</summary>
    private const double PerCharShift = 0.055;

    /// <summary>Текущая фаза радуги 0..1, общая для всего приложения.</summary>
    public static double Phase => (Environment.TickCount64 % CycleMs) / (double)CycleMs;

    /// <summary>Цвет радуги для заданной фазы (0..1). Насыщенный, чтобы читался на чёрном.</summary>
    public static Color ColorAt(double phase)
    {
        double h = (phase % 1.0 + 1.0) % 1.0 * 6.0;
        int i = (int)Math.Floor(h);
        double f = h - i;
        int v = 255;
        int p = 60;
        int q = (int)(255 * (1 - 0.76 * f) + 60 * (0.76 * f));
        int t = (int)(60 * (1 - f) + 255 * f);

        return i switch
        {
            0 => Color.FromArgb(v, t, p),
            1 => Color.FromArgb(q, v, p),
            2 => Color.FromArgb(p, v, t),
            3 => Color.FromArgb(p, q, v),
            4 => Color.FromArgb(t, p, v),
            _ => Color.FromArgb(v, p, q),
        };
    }

    /// <summary>
    /// Рисует ник с учётом украшений и возвращает ширину нарисованного текста.
    /// Обычный ник рисуется одним вызовом, радужный - посимвольно.
    /// </summary>
    public static int Draw(Graphics g, string text, Font font, Point at, Color baseColor, Cosmetic cosmetic)
    {
        if (string.IsNullOrEmpty(text)) return 0;

        int x = at.X;

        // Тег рисуется всегда жёлтым перед ником.
        if (!string.IsNullOrEmpty(cosmetic?.Tag))
        {
            string tag = "[" + cosmetic.Tag + "] ";
            TextRenderer.DrawText(g, tag, font, new Point(x, at.Y), DeltaTheme.Accent,
                TextFormatFlags.NoPadding);
            x += TextRenderer.MeasureText(g, tag, font, Size.Empty, TextFormatFlags.NoPadding).Width;
        }

        if (cosmetic != null && cosmetic.Rainbow)
        {
            double phase = Phase;
            for (int i = 0; i < text.Length; i++)
            {
                var color = ColorAt(phase + i * PerCharShift);
                string ch = text[i].ToString();
                TextRenderer.DrawText(g, ch, font, new Point(x, at.Y), color, TextFormatFlags.NoPadding);
                x += TextRenderer.MeasureText(g, ch, font, Size.Empty, TextFormatFlags.NoPadding).Width;
            }
        }
        else
        {
            var color = cosmetic?.Resolve(baseColor) ?? baseColor;
            TextRenderer.DrawText(g, text, font, new Point(x, at.Y), color, TextFormatFlags.NoPadding);
            x += TextRenderer.MeasureText(g, text, font, Size.Empty, TextFormatFlags.NoPadding).Width;
        }

        return x - at.X;
    }

    /// <summary>Нужно ли перерисовывать список по таймеру (есть ли хотя бы один радужный).</summary>
    public static bool NeedsAnimation(IEnumerable<Cosmetic> cosmetics)
        => cosmetics != null && cosmetics.Any(c => c != null && c.Rainbow);
}

/// <summary>
/// Надпись с поддержкой переливающегося текста. Используется для собственного
/// ника в шапке главного окна. Таймер работает только при включённой радуге.
/// </summary>
public sealed class CosmeticLabel : Control
{
    private readonly System.Windows.Forms.Timer _timer;
    private Cosmetic _cosmetic;

    public CosmeticLabel()
    {
        SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer, true);
        BackColor = DeltaTheme.Background;
        ForeColor = DeltaTheme.Text;
        Font = DeltaTheme.FontBody;
        Height = 22;
        _timer = new System.Windows.Forms.Timer { Interval = 60 };
        _timer.Tick += (s, e) => Invalidate();
    }

    public Cosmetic Cosmetic
    {
        get => _cosmetic;
        set
        {
            _cosmetic = value;
            _timer.Enabled = value != null && value.Rainbow;
            Invalidate();
        }
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.Clear(BackColor);
        RainbowText.Draw(e.Graphics, Text, Font, new Point(0, (Height - Font.Height) / 2), ForeColor, _cosmetic);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing) _timer.Dispose();
        base.Dispose(disposing);
    }
}
