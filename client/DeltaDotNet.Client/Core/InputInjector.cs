using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Windows.Input;

namespace DeltaDotNet.Client.Core
{
    /// <summary>
    /// Runs on the HOST only. Converts "slot N pressed action X" into a real
    /// Windows key event so the local co-op mod thinks a second keyboard is
    /// attached to the same machine.
    /// </summary>
    public static class InputInjector
    {
        // Remembers which keys we are currently holding down, so we can release
        // everything if a player disconnects mid-jump.
        private static readonly HashSet<ushort> Held = new HashSet<ushort>();
        private static readonly object Sync = new object();

        /// <summary>Sends the key that <paramref name="slot"/> uses for <paramref name="action"/>.</summary>
        public static void Send(int slot, string action, bool down)
        {
            var table = AppConfig.Current.SlotGameKeys;
            Dictionary<string, string> slotMap;
            if (table == null || !table.TryGetValue(slot.ToString(), out slotMap)) return;
            string keyName;
            if (!slotMap.TryGetValue(action ?? "", out keyName)) return;
            if (string.IsNullOrWhiteSpace(keyName)) return;

            Key key;
            if (!Keybinds.TryParseKey(keyName, out key)) return;

            int vk = KeyInterop.VirtualKeyFromKey(key);
            if (vk <= 0) return;
            SendVirtualKey((ushort)vk, down);
        }

        public static void SendVirtualKey(ushort vk, bool down)
        {
            uint scan = Native.MapVirtualKey(vk, Native.MAPVK_VK_TO_VSC);
            uint flags = Native.KEYEVENTF_SCANCODE;
            if (IsExtended(vk)) flags |= Native.KEYEVENTF_EXTENDEDKEY;
            if (!down) flags |= Native.KEYEVENTF_KEYUP;

            var input = new Native.INPUT
            {
                type = Native.INPUT_KEYBOARD,
                u = new Native.INPUTUNION
                {
                    ki = new Native.KEYBDINPUT
                    {
                        wVk = 0,
                        wScan = (ushort)scan,
                        dwFlags = flags,
                        time = 0,
                        dwExtraInfo = IntPtr.Zero
                    }
                }
            };

            Native.SendInput(1, new[] { input }, Marshal.SizeOf(typeof(Native.INPUT)));

            lock (Sync)
            {
                if (down) Held.Add(vk);
                else Held.Remove(vk);
            }
        }

        /// <summary>Releases every key we are still holding (called when the game stops).</summary>
        public static void ReleaseAll()
        {
            ushort[] keys;
            lock (Sync) { keys = new ushort[Held.Count]; Held.CopyTo(keys); }
            foreach (var vk in keys) SendVirtualKey(vk, false);
            lock (Sync) { Held.Clear(); }
        }

        /// <summary>Brings the game window to the front so it receives the keys.</summary>
        public static bool FocusGameWindow(string titlePart)
        {
            var hwnd = ScreenCapture.FindWindowByTitlePart(titlePart);
            if (hwnd == IntPtr.Zero) return false;
            return Native.SetForegroundWindow(hwnd);
        }

        private static bool IsExtended(ushort vk)
        {
            switch (vk)
            {
                case 0x25: // VK_LEFT
                case 0x26: // VK_UP
                case 0x27: // VK_RIGHT
                case 0x28: // VK_DOWN
                case 0x2D: // VK_INSERT
                case 0x2E: // VK_DELETE
                case 0x24: // VK_HOME
                case 0x23: // VK_END
                case 0x21: // VK_PRIOR
                case 0x22: // VK_NEXT
                case 0xA3: // VK_RCONTROL
                case 0xA5: // VK_RMENU
                case 0x6F: // VK_DIVIDE
                case 0x0D: // Enter (numpad variant)
                    return true;
                default:
                    return false;
            }
        }
    }
}
