using System.Runtime.InteropServices;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Services;

/// <summary>
/// Host-side keyboard injection. When a guest presses a key, the host receives the
/// logical action and presses the key the local co-op mod expects for that player slot.
/// </summary>
public static class InputInjector
{
    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint count, INPUT[] inputs, int size);

    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern uint MapVirtualKey(uint code, uint mapType);

    private const int INPUT_KEYBOARD = 1;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_SCANCODE = 0x0008;
    private const uint KEYEVENTF_EXTENDEDKEY = 0x0001;

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public int type;
        public INPUTUNION u;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct INPUTUNION
    {
        [FieldOffset(0)] public KEYBDINPUT ki;
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public HARDWAREINPUT hi;
    }

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
        public int dx, dy;
        public uint mouseData, dwFlags, time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct HARDWAREINPUT
    {
        public uint uMsg;
        public ushort wParamL, wParamH;
    }

    /// <summary>Extended keys need the extended flag or games read them as numpad keys.</summary>
    private static bool IsExtended(int vk) => vk switch
    {
        KeyBindings.VK_LEFT or KeyBindings.VK_UP or KeyBindings.VK_RIGHT or KeyBindings.VK_DOWN => true,
        KeyBindings.VK_RCONTROL => true,
        0x2D or 0x2E or 0x24 or 0x23 or 0x21 or 0x22 => true, // Ins Del Home End PgUp PgDn
        _ => false
    };

    /// <summary>Presses or releases a raw virtual key.</summary>
    public static void SendKey(int virtualKey, bool down)
    {
        if (virtualKey <= 0) return;

        uint scan = MapVirtualKey((uint)virtualKey, 0);
        uint flags = KEYEVENTF_SCANCODE;
        if (IsExtended(virtualKey)) flags |= KEYEVENTF_EXTENDEDKEY;
        if (!down) flags |= KEYEVENTF_KEYUP;

        var inputs = new INPUT[1];
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].u.ki = new KEYBDINPUT
        {
            wVk = scan == 0 ? (ushort)virtualKey : (ushort)0,
            wScan = (ushort)scan,
            dwFlags = scan == 0 ? (down ? 0 : KEYEVENTF_KEYUP) : flags,
            time = 0,
            dwExtraInfo = IntPtr.Zero
        };
        SendInput(1, inputs, Marshal.SizeOf<INPUT>());
    }

    /// <summary>
    /// Translates a remote logical action into the physical key the game expects
    /// for that player slot and injects it.
    /// </summary>
    public static void SendAction(AppSettings settings, int slot, string action, bool down)
    {
        var vk = settings.OutputFor(slot).Get(action);
        if (vk > 0) SendKey(vk, down);
    }

    /// <summary>Releases every key of every slot — used when the game stops or a player leaves.</summary>
    public static void ReleaseAll(AppSettings settings)
    {
        foreach (var slot in settings.OutputBindings.Keys.ToList())
            foreach (var action in GameAction.All)
            {
                var vk = settings.OutputFor(slot).Get(action);
                if (vk > 0) SendKey(vk, false);
            }
    }

    /// <summary>Brings the captured game window to the foreground so injected keys reach it.</summary>
    public static void FocusGameWindow(AppSettings settings)
    {
        var hWnd = ScreenCapture.FindWindowByTitle(settings.CaptureWindowTitle);
        if (hWnd != IntPtr.Zero) SetForegroundWindow(hWnd);
    }

    public static IntPtr Foreground => GetForegroundWindow();
}
