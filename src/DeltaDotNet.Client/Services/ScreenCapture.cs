using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Services;

/// <summary>
/// Captures the game window (or the whole screen) with GDI and encodes JPEG frames.
/// Used by the host to stream the game to every guest through the server.
/// </summary>
public class ScreenCapture : IDisposable
{
    [DllImport("user32.dll")] private static extern IntPtr GetDesktopWindow();
    [DllImport("user32.dll")] private static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] private static extern bool GetClientRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] private static extern bool ClientToScreen(IntPtr hWnd, ref POINT point);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumWindowsProc callback, IntPtr param);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr hWnd, System.Text.StringBuilder text, int count);
    [DllImport("user32.dll")] private static extern int GetWindowTextLength(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern int GetSystemMetrics(int index);

    private const int SM_XVIRTUALSCREEN = 76, SM_YVIRTUALSCREEN = 77,
                      SM_CXVIRTUALSCREEN = 78, SM_CYVIRTUALSCREEN = 79;

    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr param);

    [StructLayout(LayoutKind.Sequential)]
    private struct RECT { public int Left, Top, Right, Bottom; }

    [StructLayout(LayoutKind.Sequential)]
    private struct POINT { public int X, Y; }

    private Bitmap _buffer;
    private Graphics _graphics;
    private readonly ImageCodecInfo _jpegCodec = GetJpegCodec();

    /// <summary>Finds a visible top-level window whose title contains <paramref name="titlePart"/>.</summary>
    public static IntPtr FindWindowByTitle(string titlePart)
    {
        if (string.IsNullOrWhiteSpace(titlePart)) return IntPtr.Zero;
        IntPtr found = IntPtr.Zero;
        EnumWindows((hWnd, _) =>
        {
            if (!IsWindowVisible(hWnd)) return true;
            int length = GetWindowTextLength(hWnd);
            if (length == 0) return true;
            var sb = new System.Text.StringBuilder(length + 1);
            GetWindowText(hWnd, sb, sb.Capacity);
            if (sb.ToString().IndexOf(titlePart, StringComparison.OrdinalIgnoreCase) >= 0)
            {
                found = hWnd;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }

    /// <summary>All visible window titles – shown in the Settings capture picker.</summary>
    public static List<string> ListWindowTitles()
    {
        var titles = new List<string>();
        EnumWindows((hWnd, _) =>
        {
            if (!IsWindowVisible(hWnd)) return true;
            int length = GetWindowTextLength(hWnd);
            if (length == 0) return true;
            var sb = new System.Text.StringBuilder(length + 1);
            GetWindowText(hWnd, sb, sb.Capacity);
            var title = sb.ToString().Trim();
            if (title.Length > 1 && !titles.Contains(title)) titles.Add(title);
            return true;
        }, IntPtr.Zero);
        titles.Sort(StringComparer.OrdinalIgnoreCase);
        return titles;
    }

    /// <summary>
    /// Grabs one frame and returns JPEG bytes, or null when the target window is gone.
    /// </summary>
    public byte[] CaptureJpeg(AppSettings settings, QualitySettings quality, out int width, out int height)
    {
        width = height = 0;

        int x, y, w, h;
        if (settings.CaptureMode == "window")
        {
            var hWnd = FindWindowByTitle(settings.CaptureWindowTitle);
            if (hWnd == IntPtr.Zero) return null;
            if (!GetClientRect(hWnd, out var client)) return null;
            var origin = new POINT { X = 0, Y = 0 };
            ClientToScreen(hWnd, ref origin);
            x = origin.X; y = origin.Y;
            w = client.Right - client.Left;
            h = client.Bottom - client.Top;
            if (w <= 0 || h <= 0)
            {
                if (!GetWindowRect(hWnd, out var window)) return null;
                x = window.Left; y = window.Top;
                w = window.Right - window.Left;
                h = window.Bottom - window.Top;
            }
        }
        else
        {
            x = GetSystemMetrics(SM_XVIRTUALSCREEN);
            y = GetSystemMetrics(SM_YVIRTUALSCREEN);
            w = GetSystemMetrics(SM_CXVIRTUALSCREEN);
            h = GetSystemMetrics(SM_CYVIRTUALSCREEN);
        }

        if (w <= 0 || h <= 0) return null;

        EnsureBuffer(w, h);
        try { _graphics.CopyFromScreen(x, y, 0, 0, new Size(w, h), CopyPixelOperation.SourceCopy); }
        catch { return null; }

        int scaledW = Math.Max(16, w * Math.Clamp(quality.Scale, 25, 100) / 100);
        int scaledH = Math.Max(16, h * Math.Clamp(quality.Scale, 25, 100) / 100);
        width = scaledW; height = scaledH;

        using var scaled = new Bitmap(scaledW, scaledH, PixelFormat.Format24bppRgb);
        using (var g = Graphics.FromImage(scaled))
        {
            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.Bilinear;
            g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
            g.DrawImage(_buffer, new Rectangle(0, 0, scaledW, scaledH), new Rectangle(0, 0, w, h), GraphicsUnit.Pixel);
        }

        using var output = new MemoryStream();
        using var parameters = new EncoderParameters(1);
        parameters.Param[0] = new EncoderParameter(Encoder.Quality, (long)Math.Clamp(quality.JpegQuality, 20, 95));
        scaled.Save(output, _jpegCodec, parameters);
        return output.ToArray();
    }

    private void EnsureBuffer(int w, int h)
    {
        if (_buffer != null && _buffer.Width == w && _buffer.Height == h) return;
        _graphics?.Dispose();
        _buffer?.Dispose();
        _buffer = new Bitmap(w, h, PixelFormat.Format24bppRgb);
        _graphics = Graphics.FromImage(_buffer);
    }

    private static ImageCodecInfo GetJpegCodec()
        => ImageCodecInfo.GetImageEncoders().First(c => c.FormatID == ImageFormat.Jpeg.Guid);

    public void Dispose()
    {
        _graphics?.Dispose();
        _buffer?.Dispose();
        _graphics = null;
        _buffer = null;
    }
}
