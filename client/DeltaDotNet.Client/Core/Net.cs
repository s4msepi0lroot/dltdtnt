using System;
using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace DeltaDotNet.Client.Core
{
    /// <summary>
    /// Persistent WebSocket connection to the DeltaDotNet server.
    ///
    /// Two kinds of traffic go through it:
    ///   * JSON text messages  - lobby control, chat, input events, admin commands
    ///   * binary messages     - video frames (0x01 + uint64 timestamp + JPEG bytes)
    ///
    /// Events are raised on a background thread; UI code must marshal them with
    /// Dispatcher.Invoke (the views already do this).
    /// </summary>
    public class Net
    {
        private ClientWebSocket _ws;
        private CancellationTokenSource _cts;
        private readonly SemaphoreSlim _sendLock = new SemaphoreSlim(1, 1);

        public event Action<JsonElement> Message;
        public event Action<byte[], long> Frame;
        public event Action<string> Disconnected;
        public event Action Connected;

        public bool IsConnected
        {
            get { return _ws != null && _ws.State == WebSocketState.Open; }
        }

        /// <summary>Turns http(s)://host:port into ws(s)://host:port/ws?token=...</summary>
        public static string BuildWsUrl(string baseUrl, string token)
        {
            // rewrite wildcard hosts (0.0.0.0 / ::) to loopback so a local server works
            var b = Endpoint.Normalize(baseUrl);
            if (b.StartsWith("https://", StringComparison.OrdinalIgnoreCase)) b = "wss://" + b.Substring(8);
            else if (b.StartsWith("http://", StringComparison.OrdinalIgnoreCase)) b = "ws://" + b.Substring(7);
            else if (!b.StartsWith("ws", StringComparison.OrdinalIgnoreCase)) b = "ws://" + b;
            return b + "/ws?token=" + Uri.EscapeDataString(token ?? "");
        }

        public async Task<string> ConnectAsync(string baseUrl, string token)
        {
            await DisconnectAsync().ConfigureAwait(false);
            _cts = new CancellationTokenSource();
            _ws = new ClientWebSocket();
            _ws.Options.KeepAliveInterval = TimeSpan.FromSeconds(20);
            try
            {
                await _ws.ConnectAsync(new Uri(BuildWsUrl(baseUrl, token)), _cts.Token).ConfigureAwait(false);
            }
            catch (Exception ex)
            {
                return "Connection failed: " + ex.Message;
            }

            var handler = Connected;
            if (handler != null) handler();

            _ = Task.Run(ReceiveLoopAsync);
            return null;
        }

        private async Task ReceiveLoopAsync()
        {
            var buffer = new byte[64 * 1024];
            var ms = new MemoryStream();
            string reason = "Connection closed";
            try
            {
                while (_ws != null && _ws.State == WebSocketState.Open)
                {
                    ms.SetLength(0);
                    WebSocketReceiveResult res;
                    do
                    {
                        res = await _ws.ReceiveAsync(new ArraySegment<byte>(buffer), _cts.Token).ConfigureAwait(false);
                        if (res.MessageType == WebSocketMessageType.Close)
                        {
                            reason = res.CloseStatusDescription ?? "Server closed the connection";
                            goto done;
                        }
                        ms.Write(buffer, 0, res.Count);
                    }
                    while (!res.EndOfMessage);

                    if (res.MessageType == WebSocketMessageType.Binary)
                    {
                        var data = ms.ToArray();
                        if (data.Length > 9 && data[0] == 0x01)
                        {
                            long ts = BitConverter.ToInt64(data, 1);
                            var jpeg = new byte[data.Length - 9];
                            Buffer.BlockCopy(data, 9, jpeg, 0, jpeg.Length);
                            var fh = Frame;
                            if (fh != null) fh(jpeg, ts);
                        }
                    }
                    else
                    {
                        var text = Encoding.UTF8.GetString(ms.ToArray());
                        try
                        {
                            var doc = JsonDocument.Parse(text);
                            var mh = Message;
                            if (mh != null) mh(doc.RootElement.Clone());
                        }
                        catch { /* ignore malformed frames */ }
                    }
                }
            }
            catch (OperationCanceledException) { reason = "Disconnected"; }
            catch (Exception ex) { reason = ex.Message; }

        done:
            var dh = Disconnected;
            if (dh != null) dh(reason);
        }

        public async Task SendAsync(object payload)
        {
            if (!IsConnected) return;
            var bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(payload));
            await _sendLock.WaitAsync().ConfigureAwait(false);
            try
            {
                await _ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, CancellationToken.None)
                         .ConfigureAwait(false);
            }
            catch { }
            finally { _sendLock.Release(); }
        }

        /// <summary>Sends one JPEG video frame (host only).</summary>
        public async Task SendFrameAsync(byte[] jpeg)
        {
            if (!IsConnected || jpeg == null || jpeg.Length == 0) return;
            var packet = new byte[jpeg.Length + 9];
            packet[0] = 0x01;
            Buffer.BlockCopy(BitConverter.GetBytes(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()), 0, packet, 1, 8);
            Buffer.BlockCopy(jpeg, 0, packet, 9, jpeg.Length);

            await _sendLock.WaitAsync().ConfigureAwait(false);
            try
            {
                await _ws.SendAsync(new ArraySegment<byte>(packet), WebSocketMessageType.Binary, true, CancellationToken.None)
                         .ConfigureAwait(false);
            }
            catch { }
            finally { _sendLock.Release(); }
        }

        public async Task DisconnectAsync()
        {
            try
            {
                if (_cts != null) _cts.Cancel();
                if (_ws != null && _ws.State == WebSocketState.Open)
                    await _ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "bye", CancellationToken.None).ConfigureAwait(false);
            }
            catch { }
            finally
            {
                if (_ws != null) { _ws.Dispose(); _ws = null; }
                if (_cts != null) { _cts.Dispose(); _cts = null; }
            }
        }
    }
}
