using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace CoopStream.Client.Net;

/// <summary>
/// Клиент relay-сервера: оборачивает ClientWebSocket, разбирает JSON-сообщения
/// и бинарные кадры, сериализует отправку (все отправки через один SemaphoreSlim).
/// События вызываются из фонового потока — в UI используйте Control.BeginInvoke.
/// </summary>
public sealed class RelayClient : IDisposable
{
    private ClientWebSocket _ws;
    private CancellationTokenSource _cts;
    private readonly SemaphoreSlim _sendLock = new(1, 1);

    /// <summary>Пришло управляющее JSON-сообщение.</summary>
    public event Action<JsonElement> OnJson;

    /// <summary>Пришёл бинарный пакет (видеокадр).</summary>
    public event Action<byte[]> OnBinary;

    /// <summary>Соединение закрыто (аргумент — причина).</summary>
    public event Action<string> OnClosed;

    public bool IsConnected => _ws != null && _ws.State == WebSocketState.Open;

    /// <summary>Счётчики трафика для статистики в UI.</summary>
    public long BytesSent;
    public long BytesReceived;

    public async Task ConnectAsync(string url, CancellationToken ct = default)
    {
        Close("reconnect");
        _ws = new ClientWebSocket();
        _ws.Options.KeepAliveInterval = TimeSpan.FromSeconds(15);
        _cts = new CancellationTokenSource();
        await _ws.ConnectAsync(new Uri(url), ct);
        _ = Task.Run(() => ReceiveLoopAsync(_cts.Token));
    }

    private async Task ReceiveLoopAsync(CancellationToken ct)
    {
        var buffer = new byte[64 * 1024];
        var acc = new MemoryStream();
        try
        {
            while (!ct.IsCancellationRequested && _ws.State == WebSocketState.Open)
            {
                acc.SetLength(0);
                WebSocketReceiveResult result;
                do
                {
                    result = await _ws.ReceiveAsync(new ArraySegment<byte>(buffer), ct);
                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        OnClosed?.Invoke("сервер закрыл соединение");
                        return;
                    }
                    acc.Write(buffer, 0, result.Count);
                    BytesReceived += result.Count;
                } while (!result.EndOfMessage);

                var payload = acc.ToArray();
                if (result.MessageType == WebSocketMessageType.Text)
                {
                    try
                    {
                        using var doc = JsonDocument.Parse(Encoding.UTF8.GetString(payload));
                        OnJson?.Invoke(doc.RootElement.Clone());
                    }
                    catch (JsonException)
                    {
                        // Мусор от сервера — игнорируем.
                    }
                }
                else
                {
                    OnBinary?.Invoke(payload);
                }
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            OnClosed?.Invoke(ex.Message);
            return;
        }
        OnClosed?.Invoke("соединение завершено");
    }

    public Task SendJsonAsync(object message) =>
        SendRawAsync(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(message)), WebSocketMessageType.Text);

    public Task SendBinaryAsync(byte[] data) => SendRawAsync(data, WebSocketMessageType.Binary);

    private async Task SendRawAsync(byte[] data, WebSocketMessageType type)
    {
        if (_ws == null || _ws.State != WebSocketState.Open) return;
        await _sendLock.WaitAsync();
        try
        {
            await _ws.SendAsync(new ArraySegment<byte>(data), type, true, _cts.Token);
            BytesSent += data.Length;
        }
        catch (Exception ex)
        {
            OnClosed?.Invoke(ex.Message);
        }
        finally
        {
            _sendLock.Release();
        }
    }

    public void Close(string reason)
    {
        try { _cts?.Cancel(); } catch { }
        try
        {
            if (_ws != null && _ws.State == WebSocketState.Open)
                _ws.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, reason, CancellationToken.None).Wait(500);
        }
        catch { }
        _ws?.Dispose();
        _ws = null;
    }

    public void Dispose() => Close("dispose");
}
