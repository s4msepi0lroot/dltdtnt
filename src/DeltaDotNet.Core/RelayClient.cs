using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace DeltaDotNet.Core;

/// <summary>
/// WebSocket client for the DeltaDotNet relay: lobby events, chat, input events
/// and the binary video stream. All traffic goes through the server (no P2P).
/// </summary>
public class RelayClient : IDisposable
{
    public const byte PayloadVideo = 1;

    private ClientWebSocket _ws;
    private CancellationTokenSource _cts;
    private readonly SemaphoreSlim _sendLock = new(1, 1);

    public bool IsConnected => _ws != null && _ws.State == WebSocketState.Open;

    // ---- events (raised on a background thread, marshal to the UI yourself) ----
    public event Action<UserInfo, string> Authenticated;          // user, motd
    public event Action<LobbyInfo, int, bool> Joined;             // lobby, mySlot, amIHost
    public event Action<LobbyInfo> LobbyUpdated;
    public event Action<QualitySettings, LobbyInfo> GameStarted;
    public event Action GameStopped;
    public event Action<int, string, bool> InputReceived;         // slot, action, isDown (host only)
    public event Action<byte[], int, int> VideoFrame;             // jpeg bytes, width, height
    public event Action<string, string, bool> Chat;               // from, text, rainbow
    public event Action<string> Announce;
    public event Action<bool, string> Kicked;                     // banned?, reason
    public event Action<string> LobbyClosed;
    public event Action<string> ErrorReceived;
    public event Action Disconnected;

    public async Task ConnectAsync(string wsUrl, string token)
    {
        Dispose();
        _ws = new ClientWebSocket();
        _cts = new CancellationTokenSource();
        await _ws.ConnectAsync(new Uri(wsUrl), _cts.Token).ConfigureAwait(false);
        _ = Task.Run(ReceiveLoopAsync);
        await SendJsonAsync(new { t = "auth", token }).ConfigureAwait(false);
    }

    // ---------------- outgoing ----------------
    public Task JoinAsync(string lobbyId, string password = null)
        => SendJsonAsync(new { t = "join", lobbyId, password });

    public Task LeaveAsync() => SendJsonAsync(new { t = "leave" });
    public Task ReadyAsync(bool ready) => SendJsonAsync(new { t = "ready", ready });
    public Task StartAsync() => SendJsonAsync(new { t = "start" });
    public Task StopAsync() => SendJsonAsync(new { t = "stop" });
    public Task CloseLobbyAsync() => SendJsonAsync(new { t = "close" });
    public Task KickAsync(string userId) => SendJsonAsync(new { t = "kick", userId });
    public Task BanAsync(string userId, string reason) => SendJsonAsync(new { t = "ban", userId, reason });
    public Task UnbanAsync(string userId) => SendJsonAsync(new { t = "unban", userId });
    public Task ChatAsync(string text) => SendJsonAsync(new { t = "chat", text });
    public Task SendInputAsync(string action, bool down) => SendJsonAsync(new { t = "input", action, down });

    public Task SetQualityAsync(QualitySettings q)
        => SendJsonAsync(new { t = "quality", fps = q.Fps, scale = q.Scale, jpegQuality = q.JpegQuality });

    /// <summary>Host only: push one encoded JPEG frame to every guest through the server.</summary>
    public async Task SendFrameAsync(byte[] jpeg, uint sequence, int width, int height)
    {
        if (!IsConnected) return;
        var buffer = new byte[13 + jpeg.Length];
        buffer[0] = PayloadVideo;
        WriteUInt32BE(buffer, 1, sequence);
        WriteUInt32BE(buffer, 5, (uint)width);
        WriteUInt32BE(buffer, 9, (uint)height);
        Buffer.BlockCopy(jpeg, 0, buffer, 13, jpeg.Length);
        await SendRawAsync(buffer, WebSocketMessageType.Binary).ConfigureAwait(false);
    }

    private static void WriteUInt32BE(byte[] buffer, int offset, uint value)
    {
        buffer[offset] = (byte)(value >> 24);
        buffer[offset + 1] = (byte)(value >> 16);
        buffer[offset + 2] = (byte)(value >> 8);
        buffer[offset + 3] = (byte)value;
    }

    private static uint ReadUInt32BE(byte[] buffer, int offset)
        => ((uint)buffer[offset] << 24) | ((uint)buffer[offset + 1] << 16) |
           ((uint)buffer[offset + 2] << 8) | buffer[offset + 3];

    private Task SendJsonAsync(object payload)
        => SendRawAsync(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(payload)), WebSocketMessageType.Text);

    private async Task SendRawAsync(byte[] data, WebSocketMessageType type)
    {
        if (!IsConnected) return;
        await _sendLock.WaitAsync().ConfigureAwait(false);
        try { await _ws.SendAsync(new ArraySegment<byte>(data), type, true, _cts.Token).ConfigureAwait(false); }
        catch { /* connection dropped; ReceiveLoop reports it */ }
        finally { _sendLock.Release(); }
    }

    // ---------------- incoming ----------------
    private async Task ReceiveLoopAsync()
    {
        var chunk = new byte[64 * 1024];
        var buffer = new MemoryStream();
        try
        {
            while (IsConnected)
            {
                buffer.SetLength(0);
                WebSocketReceiveResult result;
                do
                {
                    result = await _ws.ReceiveAsync(new ArraySegment<byte>(chunk), _cts.Token).ConfigureAwait(false);
                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        Disconnected?.Invoke();
                        return;
                    }
                    buffer.Write(chunk, 0, result.Count);
                } while (!result.EndOfMessage);

                var data = buffer.ToArray();
                if (result.MessageType == WebSocketMessageType.Binary) HandleBinary(data);
                else HandleText(Encoding.UTF8.GetString(data));
            }
        }
        catch (OperationCanceledException) { }
        catch { Disconnected?.Invoke(); }
    }

    private void HandleBinary(byte[] data)
    {
        if (data.Length < 13 || data[0] != PayloadVideo) return;
        int width = (int)ReadUInt32BE(data, 5);
        int height = (int)ReadUInt32BE(data, 9);
        var jpeg = new byte[data.Length - 13];
        Buffer.BlockCopy(data, 13, jpeg, 0, jpeg.Length);
        VideoFrame?.Invoke(jpeg, width, height);
    }

    private void HandleText(string text)
    {
        try
        {
            using var doc = JsonDocument.Parse(text);
            var root = doc.RootElement;
            var type = root.GetProperty("t").GetString();
            switch (type)
            {
                case "authed":
                    Authenticated?.Invoke(
                        Deserialize<UserInfo>(root, "user"),
                        root.TryGetProperty("motd", out var m) ? m.GetString() : "");
                    break;
                case "joined":
                    Joined?.Invoke(
                        Deserialize<LobbyInfo>(root, "lobby"),
                        root.GetProperty("slot").GetInt32(),
                        root.GetProperty("isHost").GetBoolean());
                    break;
                case "lobby":
                    LobbyUpdated?.Invoke(Deserialize<LobbyInfo>(root, "lobby"));
                    break;
                case "started":
                    GameStarted?.Invoke(Deserialize<QualitySettings>(root, "quality"), Deserialize<LobbyInfo>(root, "lobby"));
                    break;
                case "stopped":
                    GameStopped?.Invoke();
                    break;
                case "input":
                    InputReceived?.Invoke(
                        root.GetProperty("slot").GetInt32(),
                        root.GetProperty("action").GetString(),
                        root.GetProperty("down").GetBoolean());
                    break;
                case "chat":
                    Chat?.Invoke(
                        root.GetProperty("from").GetString(),
                        root.GetProperty("text").GetString(),
                        root.TryGetProperty("rainbow", out var r) && r.GetBoolean());
                    break;
                case "announce":
                    Announce?.Invoke(root.GetProperty("text").GetString());
                    break;
                case "kicked":
                    Kicked?.Invoke(
                        root.TryGetProperty("banned", out var b) && b.GetBoolean(),
                        root.TryGetProperty("reason", out var rs) ? rs.GetString() : null);
                    break;
                case "lobbyClosed":
                    LobbyClosed?.Invoke(root.TryGetProperty("message", out var msg) ? msg.GetString() : "Lobby closed");
                    break;
                case "forceLogout":
                    ErrorReceived?.Invoke(root.GetProperty("message").GetString());
                    break;
                case "error":
                    ErrorReceived?.Invoke(root.GetProperty("message").GetString());
                    break;
            }
        }
        catch { /* malformed frame, ignore */ }
    }

    private static T Deserialize<T>(JsonElement root, string property) where T : class
    {
        if (!root.TryGetProperty(property, out var element)) return null;
        return JsonSerializer.Deserialize<T>(element.GetRawText(),
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
    }

    public void Dispose()
    {
        try { _cts?.Cancel(); } catch { }
        try { _ws?.Dispose(); } catch { }
        _ws = null;
        _cts = null;
    }
}
