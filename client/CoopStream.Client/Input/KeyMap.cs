namespace CoopStream.Client.Input;

/// <summary>
/// Единый словарь клавиш: имя в протоколе &lt;-&gt; PS/2 set-1 scan code (+флаг extended).
/// Используются именно scan code, а не virtual key, потому что игры (DirectInput/RawInput)
/// часто читают именно scan code и игнорируют события без него.
/// </summary>
public static class KeyMap
{
    public readonly record struct ScanKey(ushort Scan, bool Extended);

    /// <summary>Имя клавиши в протоколе -&gt; scan code.</summary>
    public static readonly IReadOnlyDictionary<string, ScanKey> ByName = new Dictionary<string, ScanKey>(StringComparer.Ordinal)
    {
        // Игрок 1
        ["W"] = new(0x11, false),
        ["A"] = new(0x1E, false),
        ["S"] = new(0x1F, false),
        ["D"] = new(0x20, false),
        ["Z"] = new(0x2C, false),
        ["X"] = new(0x2D, false),
        ["P"] = new(0x19, false),
        // Общие
        ["C"] = new(0x2E, false),
        ["LShift"] = new(0x2A, false),
        ["RShift"] = new(0x36, false),
        ["LCtrl"] = new(0x1D, false),
        ["RCtrl"] = new(0x1D, true),
        // Игрок 2
        ["Up"] = new(0x48, true),
        ["Down"] = new(0x50, true),
        ["Left"] = new(0x4B, true),
        ["Right"] = new(0x4D, true),
        ["Enter"] = new(0x1C, false),
        ["NumEnter"] = new(0x1C, true),
    };

    /// <summary>Обратное соответствие: (scan, extended) -&gt; имя клавиши.</summary>
    private static readonly Dictionary<(ushort, bool), string> Reverse = BuildReverse();

    private static Dictionary<(ushort, bool), string> BuildReverse()
    {
        var map = new Dictionary<(ushort, bool), string>();
        foreach (var kv in ByName) map[(kv.Value.Scan, kv.Value.Extended)] = kv.Key;
        // NumEnter шлём как обычный Enter.
        map[(0x1C, true)] = "Enter";
        return map;
    }

    /// <summary>По аппаратному scan code возвращает имя клавиши или null.</summary>
    public static string FromScan(ushort scan, bool extended)
        => Reverse.TryGetValue((scan, extended), out var name) ? name : null;
}

/// <summary>
/// Разрешённые клавиши по ролям. Тот же список проверяется на сервере (двойная защита).
/// </summary>
public static class KeyPolicy
{
    /// <summary>Игрок 1: WASD, Z, X, P, C, оба Ctrl, оба Shift.</summary>
    public static readonly HashSet<string> P1 = new(StringComparer.Ordinal)
    { "W", "A", "S", "D", "Z", "X", "P", "C", "LCtrl", "RCtrl", "LShift", "RShift" };

    /// <summary>Игрок 2: стрелки, Enter, C, оба Ctrl, оба Shift.</summary>
    public static readonly HashSet<string> P2 = new(StringComparer.Ordinal)
    { "Up", "Down", "Left", "Right", "Enter", "C", "LCtrl", "RCtrl", "LShift", "RShift" };

    public static HashSet<string> For(string role) => role == "P2" ? P2 : P1;

    public static bool IsAllowed(string role, string key) => For(role).Contains(key);

    /// <summary>Человекочитаемое описание раскладки для UI.</summary>
    public static string Describe(string role) => role == "P2"
        ? "↑ ↓ ← →, Enter, C, Ctrl (оба), Shift (оба)"
        : "W A S D, Z, X, P, C, Ctrl (оба), Shift (оба)";
}
