using System.Runtime.InteropServices;

namespace DeltaDotNet.Client.Input;

/// <summary>
/// Ввод клавиш в активное окно хоста через WinAPI SendInput с аппаратными scan code.
/// Именно scan code позволяет играм на DirectInput/RawInput увидеть нажатие.
/// </summary>
public sealed class InputInjector
{
    private const uint INPUT_KEYBOARD = 1;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_SCANCODE = 0x0008;
    private const uint KEYEVENTF_EXTENDEDKEY = 0x0001;

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct HARDWAREINPUT
    {
        public uint uMsg;
        public ushort wParamL;
        public ushort wParamH;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct INPUTUNION
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
        [FieldOffset(0)] public HARDWAREINPUT hi;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public INPUTUNION u;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    /// <summary>Клавиши, которые сейчас удерживаются нажатыми удалённым игроком.</summary>
    private readonly HashSet<string> _down = new(StringComparer.Ordinal);
    private readonly object _lock = new();

    /// <summary>Сколько клавиш сейчас зажато (для индикатора в UI).</summary>
    public int HeldCount { get { lock (_lock) return _down.Count; } }

    /// <summary>Нажатие/отпускание клавиши по её имени в протоколе.</summary>
    public bool Send(string keyName, bool down)
    {
        if (!KeyMap.ByName.TryGetValue(keyName, out var key)) return false;

        lock (_lock)
        {
            if (down)
            {
                if (!_down.Add(keyName)) return true; // автоповтор — не дублируем
            }
            else
            {
                _down.Remove(keyName);
            }
        }

        uint flags = KEYEVENTF_SCANCODE;
        if (key.Extended) flags |= KEYEVENTF_EXTENDEDKEY;
        if (!down) flags |= KEYEVENTF_KEYUP;

        var inputs = new INPUT[1];
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].u.ki = new KEYBDINPUT
        {
            wVk = 0,
            wScan = key.Scan,
            dwFlags = flags,
            time = 0,
            dwExtraInfo = IntPtr.Zero,
        };

        return SendInput(1, inputs, Marshal.SizeOf<INPUT>()) == 1;
    }

    /// <summary>Отпускает все удерживаемые клавиши (при обрыве связи, паузе, выходе).</summary>
    public void ReleaseAll()
    {
        string[] held;
        lock (_lock)
        {
            held = _down.ToArray();
            _down.Clear();
        }
        foreach (var name in held)
        {
            if (!KeyMap.ByName.TryGetValue(name, out var key)) continue;
            uint flags = KEYEVENTF_SCANCODE | KEYEVENTF_KEYUP;
            if (key.Extended) flags |= KEYEVENTF_EXTENDEDKEY;
            var inputs = new INPUT[1];
            inputs[0].type = INPUT_KEYBOARD;
            inputs[0].u.ki = new KEYBDINPUT { wScan = key.Scan, dwFlags = flags };
            SendInput(1, inputs, Marshal.SizeOf<INPUT>());
        }
    }
}
