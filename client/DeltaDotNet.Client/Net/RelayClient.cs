using System.Net;
using System.Net.Http;
using System.Net.Security;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace DeltaDotNet.Client.Net;

/// <summary>
/// Клиент relay-сервера: оборачивает ClientWebSocket, разбирает JSON-сообщения
/// и бинарные кадры, сериализует отправку (все отправки через один SemaphoreSlim).
/// События вызываются из фонового потока — в UI используйте Control.BeginInvoke.
/// </summary>
public sealed class RelayClient : IDisposable
{
    private const string WsGuid = "258EAFA5-E914-47DA-95CA-5AB0DC85B11F";

    private WebSocket _ws;
    private Stream _rawStream;
    private TcpClient _tcp;
    private CancellationTokenSource _cts;
    private readonly SemaphoreSlim _sendLock = new(1, 1);
    private readonly SemaphoreSlim _connectLock = new(1, 1);

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
        try { ws.Options.SetRequestHeader("User-Agent", "DeltaDotNet/1.0"); } catch { }
        return ws;
    }

    /// <summary>
    /// Подключается к relay-серверу. Пробует три способа по очереди:
    ///   1) обычный ClientWebSocket в обход системного прокси;
    ///   2) собственное рукопожатие на «голом» TCP-сокете — минует весь стек
    ///      HttpClient, который и выдаёт ошибку "The 'Sec-WebSocket-Accept'
    ///      header value ... is invalid", если рукопожатие кто-то трогает
    ///      (фильтр антивируса, LSP-провайдер, повторное использование
    ///      соединения после сворачивания окна);
    ///   3) ClientWebSocket через системный прокси — если прямой выход закрыт.
    /// Вызовы сериализованы: параллельные попытки подключения запрещены.
    /// </summary>
    public async Task ConnectAsync(string url, CancellationToken ct = default)
    {
        await _connectLock.WaitAsync(ct);
        try
        {
            Close("reconnect");
            var uri = NormalizeUrl(url);
            Exception firstError = null;
            ConnectNote = null;

            for (var attempt = 1; attempt <= 3; attempt++)
            {
                try
                {
                    _cts = new CancellationTokenSource();
                    if (attempt == 2)
                    {
                        _ws = await ConnectRawAsync(uri, ct);
                        ConnectNote = "использовано резервное подключение (прямой сокет)";
                    }
                    else
                    {
                        var ws = CreateSocket(bypassProxy: attempt == 1);
                        await ws.ConnectAsync(uri, ct);
                        _ws = ws;
                        ConnectNote = attempt == 3 ? "подключение прошло через системный прокси" : null;
                    }

                    var token = _cts.Token;
                    _ = Task.Run(() => ReceiveLoopAsync(token));
                    return;
                }
                catch (Exception ex)
                {
                    firstError ??= ex;
                    CleanupSocket();
                    if (ct.IsCancellationRequested) throw;
                }
            }

            throw new IOException(await DescribeErrorAsync(firstError, uri), firstError);
        }
        finally
        {
            _connectLock.Release();
        }
    }

    /// <summary>
    /// Резервный путь подключения: TCP-соединение, рукопожатие вручную и
    /// WebSocket поверх готового потока. Заголовок Sec-WebSocket-Accept
    /// проверяем сами, поэтому точно знаем, подменил ли его кто-то по дороге.
    /// </summary>
    private async Task<WebSocket> ConnectRawAsync(Uri uri, CancellationToken ct)
    {
        var secure = string.Equals(uri.Scheme, "wss", StringComparison.OrdinalIgnoreCase);
        var port = uri.IsDefaultPort ? (secure ? 443 : 80) : uri.Port;

        var tcp = new TcpClient { NoDelay = true };
        await tcp.ConnectAsync(uri.Host, port, ct);

        Stream stream = tcp.GetStream();
        if (secure)
        {
            var ssl = new SslStream(stream, leaveInnerStreamOpen: false);
            await ssl.AuthenticateAsClientAsync(
                new SslClientAuthenticationOptions { TargetHost = uri.Host }, ct);
            stream = ssl;
        }

        var key = Convert.ToBase64String(RandomNumberGenerator.GetBytes(16));
        var hostHeader = uri.IsDefaultPort ? uri.Host : uri.Host + ":" + port;
        var request =
            "GET " + uri.PathAndQuery + " HTTP/1.1\r\n" +
            "Host: " + hostHeader + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: " + key + "\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "User-Agent: DeltaDotNet/1.0\r\n\r\n";

        await stream.WriteAsync(Encoding.ASCII.GetBytes(request), ct);
        await stream.FlushAsync(ct);

        var head = await ReadHandshakeHeadAsync(stream, ct);
        if (!head.StartsWith("HTTP/1.1 101", StringComparison.Ordinal))
        {
            tcp.Dispose();
            throw new IOException("сервер не принял рукопожатие: " + head.Split('\r')[0]);
        }

        var expected = Convert.ToBase64String(SHA1.HashData(Encoding.ASCII.GetBytes(key + WsGuid)));
        if (head.IndexOf(expected, StringComparison.Ordinal) < 0)
        {
            tcp.Dispose();
            throw new IOException("ответ на рукопожатие изменён по пути (Sec-WebSocket-Accept не совпал)");
        }

        _rawStream = stream;
        _tcp = tcp;
        return WebSocket.CreateFromStream(stream, new WebSocketCreationOptions
        {
            IsServer = false,
            KeepAliveInterval = TimeSpan.FromSeconds(15),
        });
    }

    /// <summary>
    /// Читает заголовки ответа побайтно — так мы гарантированно не проглотим
    /// первые байты WebSocket-кадров, идущие сразу за пустой строкой.
    /// </summary>
    private static async Task<string> ReadHandshakeHeadAsync(Stream stream, CancellationToken ct)
    {
        var sb = new StringBuilder();
        var one = new byte[1];
        while (sb.Length < 8192)
        {
            var read = await stream.ReadAsync(one, ct);
            if (read == 0) throw new IOException("соединение закрыто во время рукопожатия");
            sb.Append((char)one[0]);
            if (sb.Length >= 4 && sb[^1] == '\n' && sb[^2] == '\r' && sb[^3] == '\n' && sb[^4] == '\r')
                return sb.ToString();
        }
        throw new IOException("слишком длинный ответ на рукопожатие");
    }

    /// <summary>Закрывает и обнуляет всё, что связано с текущим соединением.</summary>
    private void CleanupSocket()
    {
        try { _ws?.Dispose(); } catch { }
        try { _rawStream?.Dispose(); } catch { }
        try { _tcp?.Dispose(); } catch { }
        _ws = null;
        _rawStream = null;
        _tcp = null;
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
            sb.AppendLine("  4. Убедитесь, что по адресу отвечает именно deltadotnet-relay, а не");
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
        CleanupSocket();
    }

    public void Dispose() => Close("dispose");
}
