using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace DeltaDotNet.Client.Core
{
    /// <summary>
    /// GDI based screen grabber. Produces JPEG bytes ready to be pushed through
    /// the WebSocket. Uses only in-box WPF imaging, so the client has zero
    /// NuGet dependencies (important for a clean GitHub Actions build).
    /// </summary>
    public static class ScreenCapture
    {
        /// <summary>Finds the first visible window whose title contains <paramref name="part"/>.</summary>
        public static IntPtr FindWindowByTitlePart(string part)
        {
            if (string.IsNullOrWhiteSpace(part)) return IntPtr.Zero;
            IntPtr found = IntPtr.Zero;
            var needle = part.ToLowerInvariant();
            Native.EnumWindows((h, l) =>
            {
                if (!Native.IsWindowVisible(h)) return true;
                var sb = new StringBuilder(512);
                Native.GetWindowTextW(h, sb, sb.Capacity);
                var title = sb.ToString();
                if (title.Length > 0 && title.ToLowerInvariant().Contains(needle))
                {
                    found = h;
                    return false;
                }
                return true;
            }, IntPtr.Zero);
            return found;
        }

        /// <summary>Lists visible top level windows (for the capture picker in Settings).</summary>
        public static System.Collections.Generic.List<string> ListWindowTitles()
        {
            var list = new System.Collections.Generic.List<string>();
            Native.EnumWindows((h, l) =>
            {
                if (!Native.IsWindowVisible(h)) return true;
                var sb = new StringBuilder(512);
                Native.GetWindowTextW(h, sb, sb.Capacity);
                var t = sb.ToString().Trim();
                if (t.Length > 1 && !list.Contains(t)) list.Add(t);
                return true;
            }, IntPtr.Zero);
            list.Sort(StringComparer.OrdinalIgnoreCase);
            return list;
        }

        /// <summary>Resolves the rectangle that should be captured for the given settings.</summary>
        public static bool TryGetSourceRect(QualitySettings q, out int x, out int y, out int w, out int h)
        {
            x = y = w = h = 0;
            if (q.CaptureMode == "Region")
            {
                x = q.RegionX; y = q.RegionY; w = q.RegionW; h = q.RegionH;
            }
            else if (q.CaptureMode == "Window")
            {
                var hwnd = FindWindowByTitlePart(q.WindowTitle);
                if (hwnd == IntPtr.Zero) return false;
                Native.RECT cr;
                if (!Native.GetClientRect(hwnd, out cr)) return false;
                var origin = new Native.POINT { X = 0, Y = 0 };
                Native.ClientToScreen(hwnd, ref origin);
                x = origin.X; y = origin.Y;
                w = cr.Right - cr.Left;
                h = cr.Bottom - cr.Top;
            }
            else // Screen
            {
                x = Native.GetSystemMetrics(Native.SM_XVIRTUALSCREEN);
                y = Native.GetSystemMetrics(Native.SM_YVIRTUALSCREEN);
                w = Native.GetSystemMetrics(Native.SM_CXVIRTUALSCREEN);
                h = Native.GetSystemMetrics(Native.SM_CYVIRTUALSCREEN);
            }
            return w > 0 && h > 0;
        }

        /// <summary>
        /// Grabs one frame and encodes it as JPEG. Returns null when the source
        /// window is missing or the capture failed.
        /// </summary>
        public static byte[] CaptureJpeg(QualitySettings q, out int outW, out int outH)
        {
            outW = outH = 0;
            int x, y, w, h;
            if (!TryGetSourceRect(q, out x, out y, out w, out h)) return null;

            IntPtr screenDc = Native.GetDC(IntPtr.Zero);
            IntPtr memDc = IntPtr.Zero;
            IntPtr bmp = IntPtr.Zero;
            IntPtr old = IntPtr.Zero;
            try
            {
                if (screenDc == IntPtr.Zero) return null;
                memDc = Native.CreateCompatibleDC(screenDc);
                bmp = Native.CreateCompatibleBitmap(screenDc, w, h);
                if (memDc == IntPtr.Zero || bmp == IntPtr.Zero) return null;
                old = Native.SelectObject(memDc, bmp);

                if (!Native.BitBlt(memDc, 0, 0, w, h, screenDc, x, y, Native.SRCCOPY | Native.CAPTUREBLT))
                    return null;

                if (q.CaptureCursor)
                {
                    var ci = new Native.CURSORINFO();
                    ci.cbSize = Marshal.SizeOf(typeof(Native.CURSORINFO));
                    if (Native.GetCursorInfo(ref ci) && (ci.flags & Native.CURSOR_SHOWING) != 0)
                        Native.DrawIcon(memDc, ci.ptScreenPos.X - x, ci.ptScreenPos.Y - y, ci.hCursor);
                }

                BitmapSource src = Imaging.CreateBitmapSourceFromHBitmap(
                    bmp, IntPtr.Zero, Int32Rect.Empty, BitmapSizeOptions.FromEmptyOptions());

                double scale = Math.Max(10, Math.Min(100, q.Scale)) / 100.0;
                BitmapSource final = src;
                if (scale < 0.999)
                {
                    var t = new TransformedBitmap(src, new ScaleTransform(scale, scale));
                    final = t;
                }
                final.Freeze();

                outW = final.PixelWidth;
                outH = final.PixelHeight;

                var encoder = new JpegBitmapEncoder();
                encoder.QualityLevel = Math.Max(10, Math.Min(95, q.JpegQuality));
                encoder.Frames.Add(BitmapFrame.Create(final));
                using (var ms = new MemoryStream())
                {
                    encoder.Save(ms);
                    return ms.ToArray();
                }
            }
            catch
            {
                return null;
            }
            finally
            {
                if (memDc != IntPtr.Zero && old != IntPtr.Zero) Native.SelectObject(memDc, old);
                if (bmp != IntPtr.Zero) Native.DeleteObject(bmp);
                if (memDc != IntPtr.Zero) Native.DeleteDC(memDc);
                if (screenDc != IntPtr.Zero) Native.ReleaseDC(IntPtr.Zero, screenDc);
            }
        }
    }
}
