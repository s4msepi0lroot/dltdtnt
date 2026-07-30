using System.Text.Json;
using System.Text.Json.Serialization;
using DeltaDotNet.Client.Input;

namespace DeltaDotNet.Client;

/// <summary>
/// Настройки клиента. Лежат в %APPDATA%\DeltaDotNet\config.json и читаются при старте.
/// Файл можно редактировать руками — это обычный JSON.
/// </summary>
public sealed class AppConfig
{
    // ------------------------------------------------------------- подключение
    public string ServerUrl { get; set; } = "ws://127.0.0.1:8080/ws";
    public string Login { get; set; } = "";
    public string Token { get; set; } = "";

    // ------------------------------------------------------------------ видео
    public int Fps { get; set; } = 20;
    public int JpegQuality { get; set; } = 55;
    public int MaxWidth { get; set; } = 1280;

    /// <summary>Сколько игроков создавать в лобби (2-4), включая хоста.</summary>
    public int PlayerCount { get; set; } = 2;

    /// <summary>
    /// Мои личные клавиши для каждой роли: роль -&gt; (действие -&gt; клавиша).
    /// Это то, что человек жмёт у себя на клавиатуре.
    /// </summary>
    public Dictionary<string, Dictionary<string, string>> MyBindings { get; set; } = new();

    /// <summary>
    /// Клавиши, которые ждёт сама игра для игроков P2/P3/P4 на машине хоста.
    /// Используется только хостом при вводе чужих нажатий в игру.
    /// </summary>
    public Dictionary<string, Dictionary<string, string>> GameKeys { get; set; } = new();

    // -------------------------------------------------------------- служебное
    [JsonIgnore]
    public static string ConfigPath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "DeltaDotNet", "config.json");

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };

    public static AppConfig Load()
    {
        try
        {
            if (File.Exists(ConfigPath))
            {
                var json = File.ReadAllText(ConfigPath);
                var cfg = JsonSerializer.Deserialize<AppConfig>(json);
                if (cfg != null) return cfg.Normalized();
            }
        }
        catch
        {
            // Битый конфиг не должен мешать запуску — просто берём умолчания.
        }
        return new AppConfig().Normalized();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(ConfigPath)!);
            File.WriteAllText(ConfigPath, JsonSerializer.Serialize(this, JsonOptions));
        }
        catch
        {
            // Нет прав на запись — работаем без сохранения.
        }
    }

    /// <summary>Подрезает значения к допустимым диапазонам.</summary>
    private AppConfig Normalized()
    {
        Fps = Math.Clamp(Fps, 5, 60);
        JpegQuality = Math.Clamp(JpegQuality, 20, 95);
        MaxWidth = Math.Clamp(MaxWidth, 480, 1920);
        PlayerCount = Math.Clamp(PlayerCount, 2, 4);
        MyBindings ??= new();
        GameKeys ??= new();
        return this;
    }

    // ------------------------------------------------------- работа с привязками

    /// <summary>Мои клавиши для роли (если не настроены — умолчания роли).</summary>
    public Bindings GetMyBindings(string role)
    {
        MyBindings.TryGetValue(role ?? "P1", out var stored);
        return Bindings.FromDictionary(stored, role);
    }

    public void SetMyBindings(string role, Bindings bindings)
    {
        MyBindings[role ?? "P1"] = bindings.ToDictionary();
    }

    /// <summary>Клавиши игры для чужого игрока на стороне хоста.</summary>
    public Bindings GetGameKeys(string role)
    {
        GameKeys.TryGetValue(role ?? "P2", out var stored);
        return Bindings.FromDictionary(stored, role);
    }

    public void SetGameKeys(string role, Bindings bindings)
    {
        GameKeys[role ?? "P2"] = bindings.ToDictionary();
    }
}
