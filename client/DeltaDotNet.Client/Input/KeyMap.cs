namespace DeltaDotNet.Client.Input;

/// <summary>
/// Таблица клавиш: имя в протоколе &lt;-&gt; аппаратный scan code.
/// Игры на GameMaker (и Deltarune в том числе) читают ввод на уровне scan code,
/// поэтому именно их мы шлём через SendInput.
/// </summary>
public static class KeyMap
{
    public readonly record struct KeyDef(ushort Scan, bool Extended, string Title);

    /// <summary>Имя клавиши -&gt; её описание.</summary>
    public static readonly IReadOnlyDictionary<string, KeyDef> ByName = Build();

    private static Dictionary<string, KeyDef> Build()
    {
        var d = new Dictionary<string, KeyDef>(StringComparer.Ordinal);

        void Add(string name, ushort scan, bool ext = false, string title = null)
            => d[name] = new KeyDef(scan, ext, title ?? name);

        // Буквы (scan code не зависят от раскладки — это физические позиции).
        Add("Q", 0x10); Add("W", 0x11); Add("E", 0x12); Add("R", 0x13); Add("T", 0x14);
        Add("Y", 0x15); Add("U", 0x16); Add("I", 0x17); Add("O", 0x18); Add("P", 0x19);
        Add("A", 0x1E); Add("S", 0x1F); Add("D", 0x20); Add("F", 0x21); Add("G", 0x22);
        Add("H", 0x23); Add("J", 0x24); Add("K", 0x25); Add("L", 0x26);
        Add("Z", 0x2C); Add("X", 0x2D); Add("C", 0x2E); Add("V", 0x2F); Add("B", 0x30);
        Add("N", 0x31); Add("M", 0x32);

        // Цифровой ряд.
        Add("D1", 0x02, false, "1"); Add("D2", 0x03, false, "2"); Add("D3", 0x04, false, "3");
        Add("D4", 0x05, false, "4"); Add("D5", 0x06, false, "5"); Add("D6", 0x07, false, "6");
        Add("D7", 0x08, false, "7"); Add("D8", 0x09, false, "8"); Add("D9", 0x0A, false, "9");
        Add("D0", 0x0B, false, "0");

        // Стрелки и навигация (extended-клавиши).
        Add("Up", 0x48, true, "↑"); Add("Down", 0x50, true, "↓");
        Add("Left", 0x4B, true, "←"); Add("Right", 0x4D, true, "→");
        Add("Home", 0x47, true); Add("End", 0x4F, true);
        Add("PageUp", 0x49, true); Add("PageDown", 0x51, true);
        Add("Insert", 0x52, true); Add("Delete", 0x53, true);

        // Служебные.
        Add("Enter", 0x1C); Add("NumEnter", 0x1C, true, "Enter (цифр.)");
        Add("Space", 0x39, false, "Пробел"); Add("Tab", 0x0F); Add("Backspace", 0x0E);
        Add("Escape", 0x01, false, "Esc");
        Add("LShift", 0x2A, false, "Shift левый"); Add("RShift", 0x36, false, "Shift правый");
        Add("LCtrl", 0x1D, false, "Ctrl левый"); Add("RCtrl", 0x1D, true, "Ctrl правый");
        Add("LAlt", 0x38, false, "Alt левый"); Add("RAlt", 0x38, true, "Alt правый");

        // Знаки препинания.
        Add("Comma", 0x33, false, ","); Add("Period", 0x34, false, ".");
        Add("Slash", 0x35, false, "/"); Add("Semicolon", 0x27, false, ";");
        Add("Quote", 0x28, false, "'"); Add("LBracket", 0x1A, false, "[");
        Add("RBracket", 0x1B, false, "]"); Add("Backslash", 0x2B, false, "\\");
        Add("Minus", 0x0C, false, "-"); Add("Equals", 0x0D, false, "=");
        Add("Backquote", 0x29, false, "`");

        // Цифровой блок.
        Add("Num0", 0x52, false, "Num 0"); Add("Num1", 0x4F, false, "Num 1");
        Add("Num2", 0x50, false, "Num 2"); Add("Num3", 0x51, false, "Num 3");
        Add("Num4", 0x4B, false, "Num 4"); Add("Num5", 0x4C, false, "Num 5");
        Add("Num6", 0x4D, false, "Num 6"); Add("Num7", 0x47, false, "Num 7");
        Add("Num8", 0x48, false, "Num 8"); Add("Num9", 0x49, false, "Num 9");

        // F1-F12.
        Add("F1", 0x3B); Add("F2", 0x3C); Add("F3", 0x3D); Add("F4", 0x3E);
        Add("F5", 0x3F); Add("F6", 0x40); Add("F7", 0x41); Add("F8", 0x42);
        Add("F9", 0x43); Add("F10", 0x44); Add("F11", 0x57); Add("F12", 0x58);

        return d;
    }

    /// <summary>Человеческое название клавиши для интерфейса.</summary>
    public static string Title(string name)
        => name != null && ByName.TryGetValue(name, out var def) ? def.Title : "—";

    /// <summary>Обратное преобразование: аппаратный scan code -&gt; имя клавиши (или null).</summary>
    public static string FromScan(uint scan, bool extended)
    {
        foreach (var pair in ByName)
        {
            // Цифровой блок и стрелки делят scan code — различаем флагом extended.
            if (pair.Value.Scan == scan && pair.Value.Extended == extended) return pair.Key;
        }
        return null;
    }

    /// <summary>Имена всех клавиш, отсортированные для выпадающих списков.</summary>
    public static IEnumerable<string> AllNames()
        => ByName.Keys.OrderBy(k => k, StringComparer.Ordinal);
}

/// <summary>
/// Логические действия игрока. Именно они ходят по сети, а не клавиши.
/// Благодаря этому каждый участник выбирает себе любые удобные кнопки.
/// </summary>
public static class GameAction
{
    public const string Up = "Up";
    public const string Down = "Down";
    public const string Left = "Left";
    public const string Right = "Right";
    public const string Confirm = "Confirm";
    public const string Cancel = "Cancel";
    public const string Menu = "Menu";
    public const string Extra1 = "Extra1";
    public const string Extra2 = "Extra2";

    /// <summary>Порядок действий в интерфейсе настройки управления.</summary>
    public static readonly string[] All =
    {
        Up, Down, Left, Right, Confirm, Cancel, Menu, Extra1, Extra2,
    };

    /// <summary>Подписи в стиле Deltarune.</summary>
    public static readonly IReadOnlyDictionary<string, string> Titles = new Dictionary<string, string>
    {
        [Up] = "Вверх",
        [Down] = "Вниз",
        [Left] = "Влево",
        [Right] = "Вправо",
        [Confirm] = "Подтвердить (Z / Enter)",
        [Cancel] = "Отмена (X / Shift)",
        [Menu] = "Меню (C / Ctrl)",
        [Extra1] = "Дополнительная 1",
        [Extra2] = "Дополнительная 2",
    };

    public static string Title(string action)
        => action != null && Titles.TryGetValue(action, out var t) ? t : action ?? "—";

    public static bool IsValid(string action) => Titles.ContainsKey(action ?? "");
}
