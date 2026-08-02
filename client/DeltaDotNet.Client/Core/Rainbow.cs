using System;
using System.Collections.Generic;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace DeltaDotNet.Client.Core
{
    /// <summary>
    /// Animated "rainbow nickname" effect. The admin grants the flag on the
    /// server (admin.setRainbow) and every client renders that player's name
    /// with a slowly cycling hue.
    /// </summary>
    public static class Rainbow
    {
        private static readonly List<TextBlock> Targets = new List<TextBlock>();
        private static DispatcherTimer _timer;
        private static double _phase;

        public static void Attach(TextBlock tb)
        {
            if (tb == null || Targets.Contains(tb)) return;
            Targets.Add(tb);
            EnsureTimer();
        }

        public static void Detach(TextBlock tb)
        {
            Targets.Remove(tb);
        }

        /// <summary>Forgets all currently animated labels (called when a view is rebuilt).</summary>
        public static void Clear()
        {
            Targets.Clear();
        }

        private static void EnsureTimer()
        {
            if (_timer != null) return;
            _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(50) };
            _timer.Tick += (s, e) =>
            {
                _phase += 0.012;
                if (_phase > 1) _phase -= 1;
                for (int i = Targets.Count - 1; i >= 0; i--)
                {
                    var tb = Targets[i];
                    if (tb == null) { Targets.RemoveAt(i); continue; }
                    double h = (_phase + i * 0.07) % 1.0;
                    tb.Foreground = new SolidColorBrush(FromHsv(h * 360.0, 0.85, 1.0));
                }
            };
            _timer.Start();
        }

        /// <summary>Static rainbow gradient - handy for logos and headers.</summary>
        public static LinearGradientBrush GradientBrush()
        {
            var b = new LinearGradientBrush { StartPoint = new System.Windows.Point(0, 0), EndPoint = new System.Windows.Point(1, 0) };
            for (int i = 0; i <= 6; i++)
                b.GradientStops.Add(new GradientStop(FromHsv(i * 60, 0.85, 1.0), i / 6.0));
            return b;
        }

        public static Color FromHsv(double h, double s, double v)
        {
            h = ((h % 360) + 360) % 360;
            double c = v * s;
            double x = c * (1 - Math.Abs((h / 60.0) % 2 - 1));
            double m = v - c;
            double r = 0, g = 0, b = 0;
            if (h < 60) { r = c; g = x; }
            else if (h < 120) { r = x; g = c; }
            else if (h < 180) { g = c; b = x; }
            else if (h < 240) { g = x; b = c; }
            else if (h < 300) { r = x; b = c; }
            else { r = c; b = x; }
            return Color.FromRgb((byte)((r + m) * 255), (byte)((g + m) * 255), (byte)((b + m) * 255));
        }
    }
}
