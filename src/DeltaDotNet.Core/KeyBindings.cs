using System.Text.Json.Serialization;

namespace DeltaDotNet.Core;

/// <summary>
/// Maps logical <see cref="GameAction"/> names to virtual-key codes.
/// Two independent layers exist:
///  * INPUT map  – which key on MY keyboard triggers which action (per player, fully rebindable)
///  * OUTPUT map – which key the HOST injects into the game for a given player slot
///                 (this is what the local co-op mod actually listens to)
/// </summary>
public class KeyBindings
{
    /// <summary>action -> virtual key code (Windows VK_*)</summary>
    [JsonPropertyName("map")] public Dictionary<string, int> Map { get; set; } = new();

    public int Get(string action) => Map.TryGetValue(action, out var vk) ? vk : 0;
    public void Set(string action, int vk) => Map[action] = vk;

    public KeyBindings Clone() => new() { Map = new Dictionary<string, int>(Map) };

    // ---- Windows virtual key codes used by the defaults ----
    public const int VK_LEFT = 0x25, VK_UP = 0x26, VK_RIGHT = 0x27, VK_DOWN = 0x28;
    public const int VK_RETURN = 0x0D, VK_CONTROL = 0x11, VK_LCONTROL = 0xA2, VK_RCONTROL = 0xA3;
    public const int VK_LSHIFT = 0xA0, VK_RSHIFT = 0xA1;
    public const int VK_A = 0x41, VK_C = 0x43, VK_D = 0x44, VK_P = 0x50, VK_S = 0x53;
    public const int VK_W = 0x57, VK_X = 0x58, VK_Z = 0x5A;

    /// <summary>Player 1 defaults: WASD, Z, X, P, C, Ctrl, both Shifts.</summary>
    public static KeyBindings DefaultPlayer1() => new()
    {
        Map = new Dictionary<string, int>
        {
            [GameAction.Up] = VK_W,
            [GameAction.Down] = VK_S,
            [GameAction.Left] = VK_A,
            [GameAction.Right] = VK_D,
            [GameAction.Confirm] = VK_Z,
            [GameAction.Cancel] = VK_X,
            [GameAction.Menu] = VK_C,
            [GameAction.Pause] = VK_P,
            [GameAction.Ctrl] = VK_LCONTROL,
            [GameAction.ShiftLeft] = VK_LSHIFT,
            [GameAction.ShiftRight] = VK_RSHIFT
        }
    };

    /// <summary>Player 2 defaults: arrows, Enter, Ctrl, both Shifts, C.</summary>
    public static KeyBindings DefaultPlayer2() => new()
    {
        Map = new Dictionary<string, int>
        {
            [GameAction.Up] = VK_UP,
            [GameAction.Down] = VK_DOWN,
            [GameAction.Left] = VK_LEFT,
            [GameAction.Right] = VK_RIGHT,
            [GameAction.Confirm] = VK_RETURN,
            [GameAction.Cancel] = VK_RCONTROL,
            [GameAction.Menu] = VK_C,
            [GameAction.Pause] = 0,
            [GameAction.Ctrl] = VK_RCONTROL,
            [GameAction.ShiftLeft] = VK_LSHIFT,
            [GameAction.ShiftRight] = VK_RSHIFT
        }
    };

    /// <summary>Players 3 and 4 have no defaults in the mod, so they start on the numpad / IJKL.</summary>
    public static KeyBindings DefaultPlayer3() => new()
    {
        Map = new Dictionary<string, int>
        {
            [GameAction.Up] = 0x49,        // I
            [GameAction.Down] = 0x4B,      // K
            [GameAction.Left] = 0x4A,      // J
            [GameAction.Right] = 0x4C,     // L
            [GameAction.Confirm] = 0x55,   // U
            [GameAction.Cancel] = 0x4F,    // O
            [GameAction.Menu] = 0x59,      // Y
            [GameAction.Pause] = 0,
            [GameAction.Ctrl] = VK_LCONTROL,
            [GameAction.ShiftLeft] = VK_LSHIFT,
            [GameAction.ShiftRight] = VK_RSHIFT
        }
    };

    public static KeyBindings DefaultPlayer4() => new()
    {
        Map = new Dictionary<string, int>
        {
            [GameAction.Up] = 0x68,        // Numpad 8
            [GameAction.Down] = 0x62,      // Numpad 2
            [GameAction.Left] = 0x64,      // Numpad 4
            [GameAction.Right] = 0x66,     // Numpad 6
            [GameAction.Confirm] = 0x60,   // Numpad 0
            [GameAction.Cancel] = 0x6E,    // Numpad .
            [GameAction.Menu] = 0x6B,      // Numpad +
            [GameAction.Pause] = 0,
            [GameAction.Ctrl] = VK_RCONTROL,
            [GameAction.ShiftLeft] = VK_LSHIFT,
            [GameAction.ShiftRight] = VK_RSHIFT
        }
    };

    public static KeyBindings DefaultForSlot(int slot) => slot switch
    {
        0 => DefaultPlayer1(),
        1 => DefaultPlayer2(),
        2 => DefaultPlayer3(),
        _ => DefaultPlayer4()
    };

    /// <summary>Human readable name for a virtual key code.</summary>
    public static string KeyName(int vk) => vk switch
    {
        0 => "—",
        VK_LEFT => "Left", VK_UP => "Up", VK_RIGHT => "Right", VK_DOWN => "Down",
        VK_RETURN => "Enter", VK_CONTROL => "Ctrl", VK_LCONTROL => "LCtrl", VK_RCONTROL => "RCtrl",
        VK_LSHIFT => "LShift", VK_RSHIFT => "RShift",
        0x20 => "Space", 0x1B => "Esc", 0x09 => "Tab", 0x08 => "Backspace",
        >= 0x30 and <= 0x39 => ((char)vk).ToString(),           // 0-9
        >= 0x41 and <= 0x5A => ((char)vk).ToString(),           // A-Z
        >= 0x60 and <= 0x69 => "Num" + (vk - 0x60),
        0x6A => "Num*", 0x6B => "Num+", 0x6D => "Num-", 0x6E => "Num.", 0x6F => "Num/",
        >= 0x70 and <= 0x87 => "F" + (vk - 0x6F),
        _ => "VK" + vk
    };
}
