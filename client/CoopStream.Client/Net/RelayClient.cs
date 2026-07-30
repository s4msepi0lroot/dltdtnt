using System.Net;
using System.Net.Http;
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

    /// <summary>Заметка о последнем подключении (например, что пришлось идти через прокси).</summary>
    public string ConnectNote { get; private set; }

    /// <summary>
    /// Создаёт сокет. Системный прокси по умолчанию отключаем: локальные
    /// прокси-клиенты, VPN и антивирусы с проверкой трафика часто подменяют
    /// заголовки рукопожатия, из-за чего .NET ругается на
    /// "The 'Sec-WebSocket-Accept' header value ... is invalid".
    /// </summary>
    private static ClientWebSocket CreateSocket(bool bypassProxy)
    {
        var ws = new ClientWebSocket();
        ws.Options.KeepAliveInterval = TimeSpan.FromSeconds(15);
        ws.Options.UseDefaultCredentials = false;
        if (bypassProxy) ws.Options.Proxy = null;
        try { ws.Options.SetRequestHeader("User-Agent", "CoopStream/1.0"); } catch { }
        return ws;
    }

    public async Task ConnectAsync(string url, CancellationToken ct = default)
    {
        Close("reconnect");
        var uri = NormalizeUrl(url);
        Exception firstError = null;

        // Сначала напрямую, затем (если не вышло) через системный прокси.
        foreach (var bypassProxy in new[] { true, false })
        {
            try
            {
                _ws = CreateSocket(bypassProxy);
                _cts = new CancellationTokenSource();
                await _ws.ConnectAsync(uri, ct);
                ConnectNote = bypassProxy ? null : "подключение прошло через системный прокси";
                _ = Task.Run(() => ReceiveLoopAsync(_cts.Token));
                return;
            }
            catch (Exception ex)
            {
                firstError ??= ex;
                try { _ws?.Dispose(); } catch { }
                _ws = null;
                if (ct.IsCancellationRequested) throw;
            }
        }

        throw new IOException(await DescribeErrorAsync(firstError, uri), firstError);
    }

    /// <summary>Приводит адрес к виду ws(s)://host[:port]/ws.</summary>
    public static Uri NormalizeUrl(string url)
    {
        var text = (url ?? string.Empty).Trim();
        if (text.Length == 0) throw new ArgumentException("Адрес сервера не указан");
        if (text.StartsWith("http://", StringComparison.OrdinalIgnoreCase))
            text = "ws://" + text.Substring(7);
        else if (text.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            text = "wss://" + text.Substring(8);
        else if (!text.Contains("://", StringComparison.Ordinal))
            text = "ws://" + text;

        var uri = new Uri(text);
        if (uri.AbsolutePath == "/" || uri.AbsolutePath.Length == 0)
            uri = new UriBuilder(uri) { Path = "/ws" }.Uri;
        return uri;
    }

    /// <summary>
    /// Проверяет HTTP-эндпоинт /health, чтобы понять, наш ли сервер отвечает по адресу.
    /// Возвращает текст ответа либо описание ошибки.
    /// </summary>
    public static async Task<string> ProbeHealthAsync(Uri wsUri)
    {
        var builder = new UriBuilder(wsUri)
        {
            Scheme = wsUri.Scheme == "wss" ? "https" : "http",
            Path = "/health",
        };
        try
        {
            using var handler = new HttpClientHandler { UseProxy = false };
            using var http = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(5) };
            var body = await http.GetStringAsync(builder.Uri);
            return body.Length > 300 ? body.Substring(0, 300) : body;
        }
        catch (Exception ex)
        {
            return "недоступен (" + ex.Message + ")";
        }
    }

    private static async Task<string> DescribeErrorAsync(Exception ex, Uri uri)
    {
        var message = ex?.Message ?? "неизвестная ошибка";
        var sb = new StringBuilder();
        sb.Append("Не удалось подключиться к ").Append(uri).Append(": ").Append(message);

        if (message.Contains("Sec-WebSocket-Accept", StringComparison.OrdinalIgnoreCase))
        {
            sb.AppendLine();
            sb.AppendLine();
            sb.AppendLine("Сервер вернул неверный ответ на WebSocket-рукопожатие. Обычно это значит,");
            sb.AppendLine("что трафик идёт не напрямую. Что проверить:");
            sb.AppendLine("  1. Выключите VPN / прокси-клиент (Clash, Shadowsocks, WARP и подобные)");
            sb.AppendLine("     либо добавьте адрес сервера в список исключений.");
            sb.AppendLine("  2. Отключите проверку трафика в антивирусе (Kaspersky, ESET, Avast).");
            sb.AppendLine("  3. Windows: Параметры → Сеть → Прокси-сервер → выключить.");
            sb.AppendLine("  4. Убедитесь, что по адресу отвечает именно coopstream-relay, а не");
            sb.AppendLine("     другой сервис на том же порту.");
            sb.Append("Ответ /health: ").Append(await ProbeHealthAsync(uri));
        }

        return sb.ToString();
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
