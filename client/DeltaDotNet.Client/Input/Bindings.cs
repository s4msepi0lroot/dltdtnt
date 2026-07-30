namespace DeltaDotNet.Client.Input;

/// <summary>
/// Набор привязок "действие -&gt; клавиша".
///
/// Используется в двух разных местах:
///
/// 1. На стороне гостя — какую клавишу он жмёт у себя (личное удобство).
///    Нажатие превращается в действие и уходит на сервер.
///
/// 2. На стороне хоста — какую клавишу ждёт сама игра для игрока P2/P3/P4
///    (т.е. что настроено в самом моде). Действие превращается обратно
///    в клавишу и отправляется в игру через SendInput.
/// </summary>
public sealed class Bindings
{
    /// <summary>Действие -&gt; имя клавиши из <see cref="KeyMap.ByName"/>.</summary>
    public Dictionary<string, string> Map { get; init; } = new(StringComparer.Ordinal);

    public string this[string action]
    {
        get => Map.TryGetValue(action, out var key) ? key : null;
        set
        {
            if (value == null) Map.Remove(action);
            else Map[action] = value;
        }
    }

    /// <summary>Найти действие по нажатой клавише (для гостя). null, если клавиша не назначена.</summary>
    public string ActionFor(string keyName)
    {
        if (keyName == null) return null;
        foreach (var pair in Map)
            if (string.Equals(pair.Value, keyName, StringComparison.Ordinal)) return pair.Key;
        return null;
    }

    public Bindings Clone() => new() { Map = new Dictionary<string, string>(Map, StringComparer.Ordinal) };

    /// <summary>Короткое описание для строки состояния: "↑ ↓ ← →, Enter, Shift, C".</summary>
    public string Describe()
    {
        var parts = new List<string>();
        foreach (var action in GameAction.All)
        {
            var key = this[action];
            if (key != null) parts.Add(KeyMap.Title(key));
        }
        return parts.Count == 0 ? "ничего не назначено" : string.Join(" ", parts);
    }

    /// <summary>
    /// Раскладки по умолчанию.
    /// P1 — классика Deltarune на WASD, P2 — на стрелках,
    /// P3 и P4 — свободные участки клавиатуры, чтобы четверо помещались за одной.
    /// </summary>
    public static Bindings Default(string role) => role switch
    {
        "P2" => Make("Up", "Down", "Left", "Right", "Enter", "RShift", "C", "RCtrl", "Period"),
        "P3" => Make("I", "K", "J", "L", "U", "O", "H", "Y", "N"),
        "P4" => Make("Num8", "Num5", "Num4", "Num6", "Num7", "Num9", "Num0", "Num1", "Num3"),
        _ => Make("W", "S", "A", "D", "Z", "X", "C", "LCtrl", "P"),
    };

    private static Bindings Make(string up, string down, string left, string right,
                                 string confirm, string cancel, string menu, string extra1, string extra2)
    {
        var b = new Bindings();
        b[GameAction.Up] = up;
        b[GameAction.Down] = down;
        b[GameAction.Left] = left;
        b[GameAction.Right] = right;
        b[GameAction.Confirm] = confirm;
        b[GameAction.Cancel] = cancel;
        b[GameAction.Menu] = menu;
        b[GameAction.Extra1] = extra1;
        b[GameAction.Extra2] = extra2;
        return b;
    }

    /// <summary>Собирает набор из словаря конфига, дополняя пропуски значениями по умолчанию.</summary>
    public static Bindings FromDictionary(Dictionary<string, string> source, string role)
    {
        var result = Default(role);
        if (source == null) return result;
        foreach (var pair in source)
        {
            if (!GameAction.IsValid(pair.Key)) continue;
            if (pair.Value != null && !KeyMap.ByName.ContainsKey(pair.Value)) continue;
            result[pair.Key] = pair.Value;
        }
        return result;
    }

    public Dictionary<string, string> ToDictionary() => new(Map, StringComparer.Ordinal);
}
