using System;
using System.Threading;
using System.Threading.Tasks;

namespace DeltaDotNet.Client.Core
{
    /// <summary>
    /// Host side capture loop. Grabs the game window, encodes JPEG and pushes
    /// frames to the server, which fans them out to every guest in the lobby.
    /// </summary>
    public class Streamer
    {
        private CancellationTokenSource _cts;
        private Task _task;

        public bool IsRunning { get { return _task != null && !_task.IsCompleted; } }

        /// <summary>Frames actually sent during the last second.</summary>
        public int Fps { get; private set; }
        /// <summary>Kilobytes sent during the last second.</summary>
        public int KbPerSec { get; private set; }
        public int LastWidth { get; private set; }
        public int LastHeight { get; private set; }
        public string LastError { get; private set; }

        public void Start(Net net)
        {
            Stop();
            _cts = new CancellationTokenSource();
            var token = _cts.Token;
            _task = Task.Run(() => Loop(net, token), token);
        }

        public void Stop()
        {
            try { if (_cts != null) _cts.Cancel(); } catch { }
            _cts = null;
            _task = null;
            Fps = 0;
            KbPerSec = 0;
        }

        private void Loop(Net net, CancellationToken token)
        {
            int frames = 0;
            long bytes = 0;
            var second = DateTime.UtcNow;
            int lastHash = 0;

            while (!token.IsCancellationRequested)
            {
                var q = AppConfig.Current.Quality;
                int targetFps = Math.Max(1, Math.Min(60, q.Fps));
                var frameStart = DateTime.UtcNow;

                try
                {
                    int w, h;
                    var jpeg = ScreenCapture.CaptureJpeg(q, out w, out h);
                    if (jpeg == null)
                    {
                        LastError = q.CaptureMode == "Window"
                            ? "Window \"" + q.WindowTitle + "\" not found - is the game running?"
                            : "Capture failed";
                        Thread.Sleep(400);
                        continue;
                    }
                    LastError = null;
                    LastWidth = w; LastHeight = h;

                    bool send = true;
                    if (q.SkipIdenticalFrames)
                    {
                        int hash = CheapHash(jpeg);
                        if (hash == lastHash) send = false;
                        lastHash = hash;
                    }

                    if (send)
                    {
                        net.SendFrameAsync(jpeg).GetAwaiter().GetResult();
                        frames++;
                        bytes += jpeg.Length;
                    }
                }
                catch (Exception ex)
                {
                    LastError = ex.Message;
                }

                if ((DateTime.UtcNow - second).TotalMilliseconds >= 1000)
                {
                    Fps = frames;
                    KbPerSec = (int)(bytes / 1024);
                    frames = 0; bytes = 0;
                    second = DateTime.UtcNow;
                }

                var spent = (DateTime.UtcNow - frameStart).TotalMilliseconds;
                var wait = (1000.0 / targetFps) - spent;
                if (wait > 1) Thread.Sleep((int)wait);
            }
        }

        /// <summary>Very cheap change detector: samples the JPEG payload.</summary>
        private static int CheapHash(byte[] data)
        {
            unchecked
            {
                int h = 17 ^ data.Length;
                int step = Math.Max(1, data.Length / 512);
                for (int i = 0; i < data.Length; i += step) h = h * 31 + data[i];
                return h;
            }
        }
    }
}
