using System.Text.Json;
using System.Text.Json.Serialization;

namespace CoopStream.Client;

/// <summary>
/// Настройки клиента. Сохраняются в %APPDATA%\CoopStream\config.json.
/// Пароль никогда не сохраняется — только токен автовхода.
/// </summary>
public sealed class AppConfig
{
    /// <summary>Адрес relay-сервера, например wss://coop.example.com/ws</summary>
    public string ServerUrl { get; set; } = "ws://127.0.0.1:8080/ws";

    public string Login { get; set; } = "";

    public string Token { get; set; } = "";

    /// <summary>Частота отправки кадров хостом (кадров в секунду).</summary>
    public int Fps { get; set; } = 20;

    /// <summary>Качество JPEG, 20..95.</summary>
    public int JpegQuality { get; set; } = 55;

    /// <summary>Максимальная ширина кадра в пикселях (кадр масштабируется перед отправкой).</summary>
    public int MaxWidth { get; set; } = 1280;

    /// <summary>Роль хоста при создании лобби: "P1" или "P2".</summary>
    public string HostRole { get; set; } = "P1";

    [JsonIgnore]
    public static string ConfigPath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "CoopStream", "config.json");

    public static AppConfig Load()
    {
        try
        {
            if (File.Exists(ConfigPath))
            {
                var json = File.ReadAllText(ConfigPath);
                return JsonSerializer.Deserialize<AppConfig>(json) ?? new AppConfig();
            }
        }
        catch
        {
            // Повреждённый конфиг — просто начинаем с настроек по умолчанию.
        }
        return new AppConfig();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(ConfigPath)!);
            File.WriteAllText(ConfigPath, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch
        {
            // Настройки не критичны — молча игнорируем ошибку записи.
        }
    }
}
