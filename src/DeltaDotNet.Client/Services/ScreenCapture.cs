using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Services;

/// <summary>One entry of the Cheat Engine style process picker.</summary>
public class CaptureTarget
{
    public int ProcessId { get; set; }
    public string ProcessName { get; set; } = "";
    public IntPtr Handle { get; set; }
    public string Title { get; set; } = "";

    /// <summary>"000012AC-DELTARUNE" - same shape Cheat Engine uses (hex pid + name).</summary>
    public string Display => $"{ProcessId:X8}-{(string.IsNullOrWhiteSpace(Title) ? ProcessName : Title)}";

    public string Short => string.IsNullOrWhiteSpace(Title) ? ProcessName : Title;
}

/// <summary>
/// Captures the selected game window (or the whole screen) with GDI and encodes
/// JPEG frames. Used by the host to stream the game to every guest.
/// The target is chosen once in Settings -&gt; Capture -&gt; Select process...
/// </summary>
public class ScreenCapture : IDisposable
{
    [DllImport("user32.dll")] private static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] private static extern bool GetClientRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] private static extern bool ClientToScreen(IntPtr hWnd, ref POINT point);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern bool IsWindow(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumWindowsProc callback, IntPtr param);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
    [DllImport("user32.dll")] private static extern int GetWindowTextLength(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern int GetSystemMetrics(int index);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);

    private const int SM_XVIRTUALSCREEN = 76, SM_YVIRTUALSCREEN = 77,
                      SM_CXVIRTUALSCREEN = 78, SM_CYVIRTUALSCREEN = 79;

    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr param);

    [StructLayout(LayoutKind.Sequential)]
    private struct RECT { public int Left, Top, Right, Bottom; }

    [StructLayout(LayoutKind.Sequential)]
    private struct POINT { public int X, Y; }

    private Bitmap _buffer;
    private Graphics _graphics;
    private IntPtr _cachedHandle = IntPtr.Zero;
    private readonly ImageCodecInfo _jpegCodec = GetJpegCodec();

    // ------------------------------------------------------------------
    // Target discovery (used by ProcessPickerWindow)
    // ------------------------------------------------------------------

    /// <summary>All windows. <paramref name="includeHidden"/> also returns invisible/untitled ones.</summary>
    public static List<CaptureTarget> ListWindows(bool includeHidden)
    {
        var list = new List<CaptureTarget>();
        EnumWindows((hWnd, _) =>
        {
            if (!includeHidden && !IsWindowVisible(hWnd)) return true;
            int length = GetWindowTextLength(hWnd);
            if (length == 0 && !includeHidden) return true;

            var sb = new StringBuilder(length + 2);
            GetWindowText(hWnd, sb, sb.Capacity);
            var title = sb.ToString().Trim();
            if (title.Length == 0 && !includeHidden) return true;

            GetWindowThreadProcessId(hWnd, out uint pid);
            string name = SafeProcessName((int)pid);

            list.Add(new CaptureTarget
            {
                ProcessId = (int)pid,
                ProcessName = name,
                Handle = hWnd,
                Title = title
            });
            return true;
        }, IntPtr.Zero);

        return list.OrderBy(t => t.Short, StringComparer.OrdinalIgnoreCase).ToList();
    }

    /// <summary>Every running process that owns a main window.</summary>
    public static List<CaptureTarget> ListProcesses()
    {
        var list = new List<CaptureTarget>();
        foreach (var process in Process.GetProcesses())
        {
            try
            {
                if (process.MainWindowHandle == IntPtr.Zero) continue;
                list.Add(new CaptureTarget
                {
                    ProcessId = process.Id,
                    ProcessName = process.ProcessName,
                    Handle = process.MainWindowHandle,
                    Title = process.MainWindowTitle
                });
            }
            catch { /* the process died while we were enumerating */ }
        }
        return list.OrderBy(t => t.ProcessName, StringComparer.OrdinalIgnoreCase).ToList();
    }

    private static string SafeProcessName(int pid)
    {
        try { return Process.GetProcessById(pid).ProcessName; }
        catch { return "?"; }
    }

    /// <summary>
    /// Resolves the window handle stored in the settings. The saved handle is
    /// checked first; if the game was restarted we look the process up by pid,
    /// then by process name, then by window title.
    /// </summary>
    public static IntPtr ResolveTarget(AppSettings settings)
    {
        if (settings.CaptureHandle != 0)
        {
            var handle = new IntPtr(settings.CaptureHandle);
            if (IsWindow(handle)) return handle;
        }

        if (settings.CaptureProcessId != 0)
        {
            try
            {
                var process = Process.GetProcessById(settings.CaptureProcessId);
                if (process.MainWindowHandle != IntPtr.Zero) return process.MainWindowHandle;
            }
            catch { }
        }

        if (!string.IsNullOrWhiteSpace(settings.CaptureProcessName))
        {
            var process = Process.GetProcessesByName(settings.CaptureProcessName)
                                 .FirstOrDefault(p => p.MainWindowHandle != IntPtr.Zero);
            if (process != null) return process.MainWindowHandle;
        }

        if (!string.IsNullOrWhiteSpace(settings.CaptureWindowTitle))
        {
            var match = ListWindows(false).FirstOrDefault(t =>
                t.Title.IndexOf(settings.CaptureWindowTitle, StringComparison.OrdinalIgnoreCase) >= 0);
            if (match != null) return match.Handle;
        }

        return IntPtr.Zero;
    }

    // ------------------------------------------------------------------
    // Frame grabbing
    // ------------------------------------------------------------------

    /// <summary>Grabs one frame and returns JPEG bytes, or null when the target is gone.</summary>
    public byte[] CaptureJpeg(AppSettings settings, QualitySettings quality, out int width, out int height)
    {
        width = height = 0;

        int x, y, w, h;
        if (settings.CaptureMode == "window")
        {
            var hWnd = ResolveTarget(settings);
            _cachedHandle = hWnd;
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

        int scale = Math.Clamp(quality.Scale, 25, 100);
        int scaledW = Math.Max(16, w * scale / 100);
        int scaledH = Math.Max(16, h * scale / 100);
        width = scaledW; height = scaledH;

        using var scaled = new Bitmap(scaledW, scaledH, PixelFormat.Format24bppRgb);
        using (var g = Graphics.FromImage(scaled))
        {
            // NearestNeighbor keeps the pixel-art look of the game crisp.
            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
            g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
            g.DrawImage(_buffer, new Rectangle(0, 0, scaledW, scaledH), new Rectangle(0, 0, w, h), GraphicsUnit.Pixel);
        }

        using var output = new MemoryStream();
        using var parameters = new EncoderParameters(1);
        parameters.Param[0] = new EncoderParameter(Encoder.Quality, (long)Math.Clamp(quality.JpegQuality, 20, 95));
        scaled.Save(output, _jpegCodec, parameters);
        return output.ToArray();
    }

    /// <summary>Handle used by the last successful capture (for input injection focus).</summary>
    public IntPtr LastHandle => _cachedHandle;

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
