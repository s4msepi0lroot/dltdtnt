using System;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace DeltaDotNet.Client.Core
{
    /// <summary>Result of a REST call: either <see cref="Data"/> or <see cref="Error"/> is set.</summary>
    public class ApiResult
    {
        public bool Ok;
        public string Error;
        public JsonElement Data;
    }

    /// <summary>Thin wrapper around the server's HTTP API (register / login / me / health).</summary>
    public static class ApiClient
    {
        private static readonly HttpClient Http = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(15)
        };

        public static string BaseUrl
        {
            get { return (AppConfig.Current.ServerUrl ?? "").TrimEnd('/'); }
        }

        private static async Task<ApiResult> SendAsync(HttpRequestMessage req)
        {
            var result = new ApiResult();
            try
            {
                var resp = await Http.SendAsync(req).ConfigureAwait(false);
                var body = await resp.Content.ReadAsStringAsync().ConfigureAwait(false);
                JsonElement el = default(JsonElement);
                if (!string.IsNullOrWhiteSpace(body))
                {
                    try { el = JsonDocument.Parse(body).RootElement.Clone(); } catch { }
                }
                result.Data = el;
                if (resp.IsSuccessStatusCode)
                {
                    result.Ok = true;
                }
                else
                {
                    string msg = "HTTP " + (int)resp.StatusCode;
                    if (el.ValueKind == JsonValueKind.Object && el.TryGetProperty("error", out var e))
                        msg = e.GetString();
                    result.Error = msg;
                }
            }
            catch (Exception ex)
            {
                result.Error = "Cannot reach the server: " + ex.Message;
            }
            return result;
        }

        private static HttpRequestMessage Post(string path, object payload, string token = null)
        {
            var req = new HttpRequestMessage(HttpMethod.Post, BaseUrl + path);
            req.Content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
            if (!string.IsNullOrEmpty(token)) req.Headers.Add("Authorization", "Bearer " + token);
            return req;
        }

        public static Task<ApiResult> HealthAsync()
        {
            return SendAsync(new HttpRequestMessage(HttpMethod.Get, BaseUrl + "/api/health"));
        }

        public static Task<ApiResult> RegisterAsync(string login, string password)
        {
            return SendAsync(Post("/api/register", new { login, password }));
        }

        public static Task<ApiResult> LoginAsync(string login, string password)
        {
            return SendAsync(Post("/api/login", new { login, password }));
        }

        public static Task<ApiResult> MeAsync(string token)
        {
            var req = new HttpRequestMessage(HttpMethod.Get, BaseUrl + "/api/me");
            req.Headers.Add("Authorization", "Bearer " + token);
            return SendAsync(req);
        }

        public static Task<ApiResult> ChangePasswordAsync(string token, string oldPassword, string newPassword)
        {
            return SendAsync(Post("/api/password", new { oldPassword, newPassword }, token));
        }
    }
}
