using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace DeltaDotNet.Core;

/// <summary>Thin REST client for the DeltaDotNet server (auth, lobbies, admin).</summary>
public class ApiClient
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(15) };

    public string BaseUrl { get; set; }
    public string Token { get; set; }
    public UserInfo CurrentUser { get; private set; }

    private static readonly JsonSerializerOptions Json = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public ApiClient(string baseUrl) => BaseUrl = (baseUrl ?? "").TrimEnd('/');

    /// <summary>ws:// or wss:// URL of the realtime endpoint, derived from BaseUrl.</summary>
    public string WebSocketUrl
    {
        get
        {
            var url = BaseUrl;
            if (url.StartsWith("https://", StringComparison.OrdinalIgnoreCase)) return "wss://" + url.Substring(8) + "/ws";
            if (url.StartsWith("http://", StringComparison.OrdinalIgnoreCase)) return "ws://" + url.Substring(7) + "/ws";
            return url + "/ws";
        }
    }

    private HttpRequestMessage Build(HttpMethod method, string path, object body)
    {
        var req = new HttpRequestMessage(method, BaseUrl + path);
        if (!string.IsNullOrEmpty(Token))
            req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", Token);
        if (body != null)
            req.Content = new StringContent(JsonSerializer.Serialize(body, Json), Encoding.UTF8, "application/json");
        return req;
    }

    private async Task<T> SendAsync<T>(HttpMethod method, string path, object body = null) where T : class
    {
        using var res = await _http.SendAsync(Build(method, path, body)).ConfigureAwait(false);
        var text = await res.Content.ReadAsStringAsync().ConfigureAwait(false);
        if (string.IsNullOrWhiteSpace(text)) text = "{}";
        var parsed = JsonSerializer.Deserialize<T>(text, Json);
        if (!res.IsSuccessStatusCode)
        {
            string message;
            try { message = JsonDocument.Parse(text).RootElement.GetProperty("error").GetString(); }
            catch { message = "HTTP " + (int)res.StatusCode; }
            throw new ApiException(message ?? ("HTTP " + (int)res.StatusCode));
        }
        return parsed;
    }

    // ---------------- auth ----------------
    public async Task<AuthResponse> RegisterAsync(string username, string password)
    {
        var res = await SendAsync<AuthResponse>(HttpMethod.Post, "/api/auth/register",
            new { username, password });
        Token = res.Token; CurrentUser = res.User;
        return res;
    }

    public async Task<AuthResponse> LoginAsync(string username, string password)
    {
        var res = await SendAsync<AuthResponse>(HttpMethod.Post, "/api/auth/login",
            new { username, password });
        Token = res.Token; CurrentUser = res.User;
        return res;
    }

    /// <summary>Validates a stored token; returns null when the token expired.</summary>
    public async Task<UserInfo> MeAsync()
    {
        try
        {
            var res = await SendAsync<AuthResponse>(HttpMethod.Get, "/api/me");
            CurrentUser = res.User;
            return res.User;
        }
        catch { return null; }
    }

    // ---------------- lobbies ----------------
    public async Task<List<LobbyInfo>> ListLobbiesAsync()
        => (await SendAsync<LobbyListResponse>(HttpMethod.Get, "/api/lobbies")).Lobbies;

    public async Task<LobbyInfo> CreateLobbyAsync(CreateLobbyRequest request)
        => (await SendAsync<LobbyResponse>(HttpMethod.Post, "/api/lobbies", request)).Lobby;

    public async Task<LobbyInfo> GetLobbyAsync(string id)
        => (await SendAsync<LobbyResponse>(HttpMethod.Get, "/api/lobbies/" + id)).Lobby;

    public Task DeleteLobbyAsync(string id)
        => SendAsync<Dictionary<string, object>>(HttpMethod.Delete, "/api/lobbies/" + id);

    // ---------------- admin (owner account only) ----------------
    public Task<AdminStatsResponse> AdminStatsAsync()
        => SendAsync<AdminStatsResponse>(HttpMethod.Get, "/api/admin/stats");

    public async Task<List<UserInfo>> AdminUsersAsync(string query = "")
        => (await SendAsync<AdminUsersResponse>(HttpMethod.Get,
            "/api/admin/users" + (string.IsNullOrEmpty(query) ? "" : "?q=" + Uri.EscapeDataString(query)))).Users;

    public Task AdminPatchUserAsync(string userId, AdminUserPatch patch)
        => SendAsync<Dictionary<string, object>>(new HttpMethod("PATCH"), "/api/admin/users/" + userId, patch);

    public Task AdminDeleteUserAsync(string userId)
        => SendAsync<Dictionary<string, object>>(HttpMethod.Delete, "/api/admin/users/" + userId);

    public async Task<List<LobbyInfo>> AdminLobbiesAsync()
        => (await SendAsync<LobbyListResponse>(HttpMethod.Get, "/api/admin/lobbies")).Lobbies;

    public Task AdminDeleteLobbyAsync(string id)
        => SendAsync<Dictionary<string, object>>(HttpMethod.Delete, "/api/admin/lobbies/" + id);

    public Task AdminBroadcastAsync(string text)
        => SendAsync<Dictionary<string, object>>(HttpMethod.Post, "/api/admin/broadcast", new { text });

    public Task AdminSetMotdAsync(string motd)
        => SendAsync<Dictionary<string, object>>(HttpMethod.Post, "/api/admin/motd", new { motd });
}

public class ApiException : Exception
{
    public ApiException(string message) : base(message) { }
}
