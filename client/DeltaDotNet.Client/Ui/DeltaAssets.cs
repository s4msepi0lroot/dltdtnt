using System.Drawing.Imaging;

namespace DeltaDotNet.Client.Ui;

/// <summary>
/// Загрузчик картинок (логотип и иконки).
///
/// Картинки НЕ вшиты в exe: программа ищет их в папке assets рядом
/// с файлом программы, а если там пусто - в %APPDATA%\DeltaDotNet\assets.
/// Так картинки можно менять без пересборки проекта.
///
/// Ожидаемые имена файлов (все необязательные):
///   logo.png   - большой логотип в главном окне
///   icon.ico   - иконка окон и панели задач
///   heart.png  - своё сердечко-курсор вместо нарисованного кодом
///
/// Если файла нет, используется запасной вариант, нарисованный кодом,
/// так что программа работает и без картинок.
/// </summary>
public static class DeltaAssets
{
    private static readonly Dictionary<string, Image> Cache = new(StringComparer.OrdinalIgnoreCase);
    private static Icon _appIcon;
    private static bool _appIconLoaded;

    /// <summary>Папка assets рядом с исполняемым файлом.</summary>
    public static string LocalFolder =>
        Path.Combine(AppContext.BaseDirectory, "assets");

    /// <summary>Запасная папка в профиле пользователя (удобно для single-file сборки).</summary>
    public static string UserFolder => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "DeltaDotNet", "assets");

    /// <summary>Создаёт обе папки, чтобы было куда положить картинки.</summary>
    public static void EnsureFolders()
    {
        foreach (var dir in new[] { LocalFolder, UserFolder })
        {
            try { Directory.CreateDirectory(dir); } catch { /* нет прав - не страшно */ }
        }
    }

    /// <summary>Ищет файл сначала рядом с exe, потом в профиле. Возвращает null, если нету.</summary>
    public static string FindFile(string fileName)
    {
        foreach (var dir in new[] { LocalFolder, UserFolder })
        {
            try
            {
                var full = Path.Combine(dir, fileName);
                if (File.Exists(full)) return full;
            }
            catch { /* битый путь - просто пропускаем */ }
        }
        return null;
    }

    /// <summary>
    /// Загружает картинку по имени файла (с кэшем). Возвращает null, если файла нет
    /// или он битый. Файл читается в память целиком, чтобы не держать его занятым.
    /// </summary>
    public static Image Load(string fileName)
    {
        if (Cache.TryGetValue(fileName, out var cached)) return cached;

        Image image = null;
        var path = FindFile(fileName);
        if (path != null)
        {
            try
            {
                var bytes = File.ReadAllBytes(path);
                using var ms = new MemoryStream(bytes);
                using var loaded = Image.FromStream(ms);
                image = new Bitmap(loaded);
            }
            catch
            {
                image = null;
            }
        }

        Cache[fileName] = image;
        return image;
    }

    /// <summary>Логотип главного окна (assets/logo.png) или null.</summary>
    public static Image Logo => Load("logo.png");

    /// <summary>Своё сердечко-курсор (assets/heart.png) или null.</summary>
    public static Image Heart => Load("heart.png");

    /// <summary>
    /// Иконка приложения: сначала assets/icon.ico, потом assets/icon.png,
    /// потом иконка, вшитая в exe при сборке. Может вернуть null.
    /// </summary>
    public static Icon AppIcon
    {
        get
        {
            if (_appIconLoaded) return _appIcon;
            _appIconLoaded = true;

            var icoPath = FindFile("icon.ico");
            if (icoPath != null)
            {
                try { _appIcon = new Icon(icoPath); return _appIcon; }
                catch { /* битый ico - пробуем дальше */ }
            }

            var png = Load("icon.png");
            if (png != null)
            {
                try
                {
                    using var bmp = new Bitmap(png);
                    _appIcon = Icon.FromHandle(bmp.GetHicon());
                    return _appIcon;
                }
                catch { /* не вышло - берём вшитую */ }
            }

            try
            {
                _appIcon = Icon.ExtractAssociatedIcon(Environment.ProcessPath ?? "");
            }
            catch
            {
                _appIcon = null;
            }
            return _appIcon;
        }
    }

    /// <summary>Ставит иконку приложения окну, если она есть.</summary>
    public static void ApplyIcon(Form form)
    {
        var icon = AppIcon;
        if (icon != null) form.Icon = icon;
    }

    /// <summary>
    /// Рисует картинку вписанной в прямоугольник с сохранением пропорций.
    /// Сглаживание отключено - пиксель-арт должен оставаться чётким.
    /// </summary>
    public static void DrawFitted(Graphics g, Image image, Rectangle box)
    {
        if (image == null || box.Width <= 0 || box.Height <= 0) return;

        double scale = Math.Min((double)box.Width / image.Width, (double)box.Height / image.Height);
        int w = Math.Max(1, (int)(image.Width * scale));
        int h = Math.Max(1, (int)(image.Height * scale));
        int x = box.X + (box.Width - w) / 2;
        int y = box.Y + (box.Height - h) / 2;

        var oldMode = g.InterpolationMode;
        var oldPixel = g.PixelOffsetMode;
        g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
        g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
        g.DrawImage(image, new Rectangle(x, y, w, h));
        g.InterpolationMode = oldMode;
        g.PixelOffsetMode = oldPixel;
    }
}

/// <summary>
/// Заголовок главного окна. Если в assets лежит logo.png - показывает его,
/// иначе рисует текстовое название тем же шрифтом, что и раньше.
/// </summary>
public sealed class LogoBanner : Control
{
    public string FallbackText { get; set; } = "DELTA .NET";
    public string Subtitle { get; set; }

    public LogoBanner()
    {
        SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer, true);
        BackColor = DeltaTheme.Background;
        Height = 90;
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.Clear(DeltaTheme.Background);

        int subtitleHeight = string.IsNullOrEmpty(Subtitle) ? 0 : 20;
        var imageBox = new Rectangle(0, 0, Width, Height - subtitleHeight);

        var logo = DeltaAssets.Logo;
        if (logo != null)
        {
            DeltaAssets.DrawFitted(g, logo, imageBox);
        }
        else
        {
            TextRenderer.DrawText(g, FallbackText, DeltaTheme.FontTitle, imageBox, DeltaTheme.Text,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
        }

        if (subtitleHeight > 0)
        {
            TextRenderer.DrawText(g, Subtitle, DeltaTheme.FontSmall,
                new Rectangle(0, Height - subtitleHeight, Width, subtitleHeight), DeltaTheme.TextDim,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
        }
    }
}