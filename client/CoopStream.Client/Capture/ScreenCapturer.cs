using System.Drawing.Imaging;
using System.Runtime.InteropServices;

namespace CoopStream.Client.Capture;

/// <summary>
/// Захват экрана или отдельного окна и упаковка кадра в JPEG.
/// Формат бинарного пакета (см. docs/PROTOCOL.md):
///   [0]      = 0x01 (тип: JPEG-кадр)
///   [1..4]   = uint32 LE номер кадра
///   [5..6]   = uint16 LE ширина
///   [7..8]   = uint16 LE высота
///   [9..16]  = int64 LE метка времени (Unix ms)
///   [17..]   = сам JPEG
/// </summary>
public sealed class ScreenCapturer : IDisposable
{
    public const int HeaderSize = 17;
    public const byte FrameTypeJpeg = 0x01;

    [DllImport("user32.dll")]
    private static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

    [DllImport("user32.dll")]
    private static extern bool IsWindow(IntPtr hWnd);

    [StructLayout(LayoutKind.Sequential)]
    private struct RECT { public int Left, Top, Right, Bottom; }

    private readonly ImageCodecInfo _jpegCodec = GetJpegCodec();
    private Bitmap _scaled;
    private uint _sequence;

    /// <summary>Окно-источник. IntPtr.Zero = весь экран.</summary>
    public IntPtr TargetWindow { get; set; } = IntPtr.Zero;

    public int MaxWidth { get; set; } = 1280;

    public int Quality { get; set; } = 55;

    private static ImageCodecInfo GetJpegCodec()
        => ImageCodecInfo.GetImageEncoders().First(c => c.FormatID == ImageFormat.Jpeg.Guid);

    /// <summary>Границы захватываемой области в координатах рабочего стола.</summary>
    public Rectangle GetSourceBounds()
    {
        if (TargetWindow != IntPtr.Zero && IsWindow(TargetWindow) && GetWindowRect(TargetWindow, out var r))
        {
            var rect = new Rectangle(r.Left, r.Top, Math.Max(1, r.Right - r.Left), Math.Max(1, r.Bottom - r.Top));
            if (rect.Width > 1 && rect.Height > 1) return rect;
        }
        return Screen.PrimaryScreen?.Bounds ?? new Rectangle(0, 0, 1920, 1080);
    }

    /// <summary>Делает снимок и возвращает готовый к отправке пакет или null при ошибке.</summary>
    public byte[] CaptureFrame()
    {
        var src = GetSourceBounds();
        int outW = Math.Min(MaxWidth, src.Width);
        if (outW < 160) outW = Math.Min(160, src.Width);
        int outH = Math.Max(1, (int)Math.Round(src.Height * (outW / (double)src.Width)));
        // Нечётные размеры не мешают JPEG, но ровные чуть быстрее.
        outW -= outW % 2; outH -= outH % 2;
        if (outW <= 0 || outH <= 0) return null;

        try
        {
            using var raw = new Bitmap(src.Width, src.Height, PixelFormat.Format32bppRgb);
            using (var g = Graphics.FromImage(raw))
            {
                g.CopyFromScreen(src.Left, src.Top, 0, 0, new Size(src.Width, src.Height), CopyPixelOperation.SourceCopy);
            }

            if (_scaled == null || _scaled.Width != outW || _scaled.Height != outH)
            {
                _scaled?.Dispose();
                _scaled = new Bitmap(outW, outH, PixelFormat.Format24bppRgb);
            }
            using (var g2 = Graphics.FromImage(_scaled))
            {
                g2.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.Bilinear;
                g2.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
                g2.DrawImage(raw, 0, 0, outW, outH);
            }

            using var ms = new MemoryStream(128 * 1024);
            using (var p = new EncoderParameters(1))
            {
                p.Param[0] = new EncoderParameter(Encoder.Quality, (long)Math.Clamp(Quality, 20, 95));
                _scaled.Save(ms, _jpegCodec, p);
            }

            var jpeg = ms.GetBuffer();
            int jpegLen = (int)ms.Length;
            var packet = new byte[HeaderSize + jpegLen];
            packet[0] = FrameTypeJpeg;
            BitConverter.TryWriteBytes(packet.AsSpan(1, 4), ++_sequence);
            BitConverter.TryWriteBytes(packet.AsSpan(5, 2), (ushort)outW);
            BitConverter.TryWriteBytes(packet.AsSpan(7, 2), (ushort)outH);
            BitConverter.TryWriteBytes(packet.AsSpan(9, 8), DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
            Buffer.BlockCopy(jpeg, 0, packet, HeaderSize, jpegLen);
            return packet;
        }
        catch
        {
            // Окно закрылось / сменился режим экрана — пропускаем кадр.
            return null;
        }
    }

    /// <summary>Разбор пакета на стороне зрителя.</summary>
    public static bool TryParse(byte[] packet, out Image image, out uint sequence, out long timestampMs)
    {
        image = null; sequence = 0; timestampMs = 0;
        if (packet == null || packet.Length <= HeaderSize || packet[0] != FrameTypeJpeg) return false;
        sequence = BitConverter.ToUInt32(packet, 1);
        timestampMs = BitConverter.ToInt64(packet, 9);
        try
        {
            var ms = new MemoryStream(packet, HeaderSize, packet.Length - HeaderSize, writable: false);
            image = Image.FromStream(ms);
            return true;
        }
        catch
        {
            return false;
        }
    }

    public void Dispose() => _scaled?.Dispose();
}

/// <summary>Перечисление видимых окон верхнего уровня для выбора источника захвата.</summary>
public static class WindowList
{
    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowTextLength(IntPtr hWnd);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr hWnd, System.Text.StringBuilder text, int count);

    public sealed record WindowInfo(IntPtr Handle, string Title)
    {
        public override string ToString() => Title;
    }

    public static List<WindowInfo> Enumerate()
    {
        var list = new List<WindowInfo> { new(IntPtr.Zero, "— Весь экран —") };
        EnumWindows((h, _) =>
        {
            if (!IsWindowVisible(h)) return true;
            int len = GetWindowTextLength(h);
            if (len == 0) return true;
            var sb = new System.Text.StringBuilder(len + 1);
            GetWindowText(h, sb, sb.Capacity);
            var title = sb.ToString().Trim();
            if (title.Length == 0) return true;
            list.Add(new WindowInfo(h, title));
            return true;
        }, IntPtr.Zero);
        return list;
    }
}
