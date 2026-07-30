using System.Drawing.Drawing2D;
using System.Drawing.Text;

namespace DeltaDotNet.Client.Ui;

/// <summary>
/// Визуальный стиль в духе Deltarune: чёрный фон, белая рамка в несколько
/// пикселей, моноширинный шрифт, жёлтые акценты и красное сердечко-курсор.
/// Всё рисуется вручную, без сторонних ресурсов, чтобы сборка оставалась
/// одним файлом exe.
/// </summary>
public static class DeltaTheme
{
    // ------------------------------------------------------------------ цвета
    public static readonly Color Background = Color.FromArgb(0, 0, 0);
    public static readonly Color Panel = Color.FromArgb(12, 12, 12);
    public static readonly Color Border = Color.FromArgb(255, 255, 255);
    public static readonly Color Text = Color.FromArgb(255, 255, 255);
    public static readonly Color TextDim = Color.FromArgb(150, 150, 150);
    public static readonly Color Accent = Color.FromArgb(255, 213, 0);   // жёлтый выбор
    public static readonly Color Heart = Color.FromArgb(255, 0, 0);      // красная душа
    public static readonly Color Good = Color.FromArgb(0, 255, 60);
    public static readonly Color Bad = Color.FromArgb(255, 60, 60);

    /// <summary>Предпочтительные семейства шрифтов от самого "игрового" к запасному.</summary>
    private static readonly string[] FontCandidates =
    {
        "Determination Mono", "Determination Sans", "8bitoperator JVE",
        "Pixel Operator", "Press Start 2P", "Consolas", "Courier New",
    };

    private static string _family;

    /// <summary>Первый шрифт из списка, реально установленный в системе.</summary>
    public static string FamilyName
    {
        get
        {
            if (_family != null) return _family;
            var installed = new InstalledFontCollection().Families.Select(f => f.Name).ToHashSet(StringComparer.OrdinalIgnoreCase);
            _family = FontCandidates.FirstOrDefault(installed.Contains) ?? FontFamily.GenericMonospace.Name;
            return _family;
        }
    }

    public static Font Font(float size, FontStyle style = FontStyle.Regular)
        => new(FamilyName, size, style, GraphicsUnit.Point);

    public static readonly Font FontTitle = Font(22f, FontStyle.Bold);
    public static readonly Font FontBig = Font(14f, FontStyle.Bold);
    public static readonly Font FontBody = Font(11f);
    public static readonly Font FontSmall = Font(9f);

    // ------------------------------------------------------------------ формы

    /// <summary>Общие настройки окна: чёрный фон, белый текст, белая рамка по краю.</summary>
    public static void ApplyForm(Form form, bool drawBorder = true)
    {
        form.BackColor = Background;
        form.ForeColor = Text;
        form.Font = FontBody;
        form.StartPosition = FormStartPosition.CenterScreen;
        form.DoubleBuffered(true);
        if (drawBorder)
        {
            form.Paint += (s, e) => DrawFrame(e.Graphics, new Rectangle(0, 0, form.ClientSize.Width, form.ClientSize.Height));
            form.Resize += (s, e) => form.Invalidate();
        }
    }

    /// <summary>Двойная белая рамка — узнаваемый элемент меню из игры.</summary>
    public static void DrawFrame(Graphics g, Rectangle bounds, Color? color = null, int thickness = 4)
    {
        using var pen = new Pen(color ?? Border, thickness) { Alignment = PenAlignment.Inset };
        var r = new Rectangle(bounds.X + thickness / 2, bounds.Y + thickness / 2,
                              Math.Max(1, bounds.Width - thickness), Math.Max(1, bounds.Height - thickness));
        g.DrawRectangle(pen, r);
    }

    /// <summary>Красное сердечко-курсор (душа) как указатель выбранного пункта.</summary>
    public static void DrawHeart(Graphics g, Rectangle box, Color? color = null)
    {
        // Пиксельное сердце 8x7 — рисуется квадратиками, без сглаживания.
        string[] rows =
        {
            "01100110",
            "11111111",
            "11111111",
            "11111111",
            "01111110",
            "00111100",
            "00011000",
        };
        int px = Math.Max(1, Math.Min(box.Width / 8, box.Height / 7));
        int offsetX = box.X + (box.Width - px * 8) / 2;
        int offsetY = box.Y + (box.Height - px * 7) / 2;
        using var brush = new SolidBrush(color ?? Heart);
        for (int y = 0; y < rows.Length; y++)
            for (int x = 0; x < rows[y].Length; x++)
                if (rows[y][x] == '1')
                    g.FillRectangle(brush, offsetX + x * px, offsetY + y * px, px, px);
    }

    private static void DoubleBuffered(this Control control, bool enabled)
    {
        var prop = typeof(Control).GetProperty("DoubleBuffered",
            System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic);
        prop?.SetValue(control, enabled, null);
    }

    // ------------------------------------------------------------- готовые виджеты

    public static Label Title(string text)
        => new()
        {
            Text = text,
            Font = FontTitle,
            ForeColor = Text,
            BackColor = Color.Transparent,
            AutoSize = true,
        };

    public static Label Caption(string text, Color? color = null, Font font = null)
        => new()
        {
            Text = text,
            Font = font ?? FontBody,
            ForeColor = color ?? Text,
            BackColor = Color.Transparent,
            AutoSize = true,
        };
}

/// <summary>Кнопка в стиле игрового меню: рамка, жёлтый текст при наведении и сердечко-курсор.</summary>
public class DeltaButton : Button
{
    private bool _hover;

    public DeltaButton()
    {
        SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer, true);
        FlatStyle = FlatStyle.Flat;
        FlatAppearance.BorderSize = 0;
        BackColor = DeltaTheme.Background;
        ForeColor = DeltaTheme.Text;
        Font = DeltaTheme.FontBig;
        Height = 40;
        Cursor = Cursors.Hand;
        MouseEnter += (s, e) => { _hover = true; Invalidate(); };
        MouseLeave += (s, e) => { _hover = false; Invalidate(); };
        GotFocus += (s, e) => Invalidate();
        LostFocus += (s, e) => Invalidate();
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.None;
        g.Clear(DeltaTheme.Background);

        bool active = (_hover || Focused) && Enabled;
        var color = !Enabled ? DeltaTheme.TextDim : active ? DeltaTheme.Accent : DeltaTheme.Text;

        DeltaTheme.DrawFrame(g, ClientRectangle, color, 3);

        int textLeft = 14;
        if (active)
        {
            DeltaTheme.DrawHeart(g, new Rectangle(10, (Height - 14) / 2, 14, 14));
            textLeft = 32;
        }

        TextRenderer.DrawText(g, Text, Font,
            new Rectangle(textLeft, 0, Width - textLeft - 10, Height), color,
            TextFormatFlags.VerticalCenter | TextFormatFlags.Left | TextFormatFlags.EndEllipsis);
    }
}

/// <summary>Поле ввода с белой рамкой и чёрным фоном.</summary>
public class DeltaTextBox : Panel
{
    public TextBox Inner { get; }

    public DeltaTextBox(bool password = false)
    {
        BackColor = DeltaTheme.Background;
        Padding = new Padding(8, 6, 8, 6);
        Height = 34;
        Inner = new TextBox
        {
            BorderStyle = BorderStyle.None,
            BackColor = DeltaTheme.Background,
            ForeColor = DeltaTheme.Text,
            Font = DeltaTheme.FontBody,
            Dock = DockStyle.Fill,
            UseSystemPasswordChar = password,
        };
        Controls.Add(Inner);
        Inner.GotFocus += (s, e) => Invalidate();
        Inner.LostFocus += (s, e) => Invalidate();
    }

    public override string Text
    {
        get => Inner.Text;
        set => Inner.Text = value;
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        DeltaTheme.DrawFrame(e.Graphics, ClientRectangle,
            Inner.Focused ? DeltaTheme.Accent : DeltaTheme.Border, 2);
    }
}

/// <summary>Список с собственной отрисовкой: выбранная строка жёлтая и с сердечком.</summary>
public class DeltaListBox : ListBox
{
    public DeltaListBox()
    {
        DrawMode = DrawMode.OwnerDrawFixed;
        BorderStyle = BorderStyle.None;
        BackColor = DeltaTheme.Background;
        ForeColor = DeltaTheme.Text;
        Font = DeltaTheme.FontBody;
        ItemHeight = 24;
    }

    protected override void OnDrawItem(DrawItemEventArgs e)
    {
        if (e.Index < 0) return;
        var g = e.Graphics;
        g.FillRectangle(new SolidBrush(DeltaTheme.Background), e.Bounds);

        bool selected = (e.State & DrawItemState.Selected) == DrawItemState.Selected;
        var color = selected ? DeltaTheme.Accent : DeltaTheme.Text;
        int left = e.Bounds.Left + 6;

        if (selected)
        {
            DeltaTheme.DrawHeart(g, new Rectangle(e.Bounds.Left + 4, e.Bounds.Top + (e.Bounds.Height - 12) / 2, 12, 12));
            left = e.Bounds.Left + 24;
        }

        TextRenderer.DrawText(g, Items[e.Index]?.ToString() ?? "", Font,
            new Rectangle(left, e.Bounds.Top, e.Bounds.Width - left, e.Bounds.Height), color,
            TextFormatFlags.VerticalCenter | TextFormatFlags.Left | TextFormatFlags.EndEllipsis);
    }
}

/// <summary>Панель с рамкой и необязательным заголовком.</summary>
public class DeltaPanel : Panel
{
    public string Caption { get; set; }

    public DeltaPanel(string caption = null)
    {
        Caption = caption;
        BackColor = DeltaTheme.Background;
        ForeColor = DeltaTheme.Text;
        Padding = new Padding(14, caption == null ? 14 : 28, 14, 14);
        SetStyle(ControlStyles.OptimizedDoubleBuffer | ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint, true);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.Clear(DeltaTheme.Background);
        DeltaTheme.DrawFrame(g, ClientRectangle, DeltaTheme.Border, 3);
        if (!string.IsNullOrEmpty(Caption))
        {
            var size = TextRenderer.MeasureText(g, Caption, DeltaTheme.FontSmall);
            g.FillRectangle(new SolidBrush(DeltaTheme.Background), 14, -2, size.Width + 12, size.Height + 2);
            TextRenderer.DrawText(g, Caption, DeltaTheme.FontSmall, new Point(20, 0), DeltaTheme.Accent);
        }
    }
}
