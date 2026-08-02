using System;
using System.Collections.Generic;
using System.Windows.Input;

namespace DeltaDotNet.Client.Core
{
    /// <summary>
    /// DeltaDotNet never sends raw keys over the network - it sends *actions*.
    ///
    ///   local physical key  ->  action  ->  the key the mod expects for that player slot
    ///   (each player picks)     (network)   (host injects it into the game window)
    ///
    /// That is what makes "every player can rebind their own keyboard" possible
    /// while the game still receives exactly the keys the local co-op mod wants.
    /// </summary>
    public static class Keybinds
    {
        /// <summary>All logical actions, in display order.</summary>
        public static readonly string[] Actions = new[]
        {
            "Up", "Down", "Left", "Right",
            "Confirm", "Cancel", "Menu", "Special", "Run", "Ctrl"
        };

        public static readonly Dictionary<string, string> ActionTitles = new Dictionary<string, string>
        {
            { "Up", "Up" },
            { "Down", "Down" },
            { "Left", "Left" },
            { "Right", "Right" },
            { "Confirm", "Confirm (Z / Enter)" },
            { "Cancel", "Cancel (X / Ctrl)" },
            { "Menu", "Menu (P / Shift)" },
            { "Special", "Special (C)" },
            { "Run", "Run (Shift)" },
            { "Ctrl", "Ctrl" }
        };

        /// <summary>Keys this client grabs from your own keyboard by default.</summary>
        public static Dictionary<string, string> DefaultLocalBinds()
        {
            return new Dictionary<string, string>
            {
                { "Up", "W" },
                { "Down", "S" },
                { "Left", "A" },
                { "Right", "D" },
                { "Confirm", "Z" },
                { "Cancel", "X" },
                { "Menu", "P" },
                { "Special", "C" },
                { "Run", "LeftShift" },
                { "Ctrl", "LeftCtrl" }
            };
        }

        /// <summary>
        /// The keys the local co-op mod listens to, per player slot.
        /// Slot 1 = WASD / Z / X / P / C / Ctrl / Shift.
        /// Slot 2 = arrows / Enter / Ctrl / Shift / C.
        /// Slots 3-8 are empty by default: fill them in Settings if your mod
        /// build supports more than two local players.
        /// </summary>
        public static Dictionary<string, Dictionary<string, string>> DefaultSlotGameKeys()
        {
            var map = new Dictionary<string, Dictionary<string, string>>();

            map["1"] = new Dictionary<string, string>
            {
                { "Up", "W" },
                { "Down", "S" },
                { "Left", "A" },
                { "Right", "D" },
                { "Confirm", "Z" },
                { "Cancel", "X" },
                { "Menu", "P" },
                { "Special", "C" },
                { "Run", "LeftShift" },
                { "Ctrl", "LeftCtrl" }
            };

            map["2"] = new Dictionary<string, string>
            {
                { "Up", "Up" },
                { "Down", "Down" },
                { "Left", "Left" },
                { "Right", "Right" },
                { "Confirm", "Return" },
                { "Cancel", "RightCtrl" },
                { "Menu", "RightShift" },
                { "Special", "C" },
                { "Run", "RightShift" },
                { "Ctrl", "RightCtrl" }
            };

            for (int slot = 3; slot <= 8; slot++)
            {
                var d = new Dictionary<string, string>();
                foreach (var a in Actions) d[a] = "";
                map[slot.ToString()] = d;
            }

            return map;
        }

        /// <summary>Reverse lookup: which action is bound to this local key?</summary>
        public static string ActionForKey(Dictionary<string, string> binds, Key key)
        {
            if (binds == null) return null;
            var name = key.ToString();
            foreach (var kv in binds)
                if (string.Equals(kv.Value, name, StringComparison.OrdinalIgnoreCase))
                    return kv.Key;
            return null;
        }

        /// <summary>Converts a stored key name back to a WPF <see cref="Key"/>.</summary>
        public static bool TryParseKey(string name, out Key key)
        {
            key = Key.None;
            if (string.IsNullOrWhiteSpace(name)) return false;
            return Enum.TryParse<Key>(name, true, out key);
        }

        /// <summary>Human readable key name for the UI.</summary>
        public static string Pretty(string keyName)
        {
            if (string.IsNullOrWhiteSpace(keyName)) return "— not set —";
            switch (keyName)
            {
                case "LeftShift": return "Shift (left)";
                case "RightShift": return "Shift (right)";
                case "LeftCtrl": return "Ctrl (left)";
                case "RightCtrl": return "Ctrl (right)";
                case "Return": return "Enter";
                case "Up": return "Arrow Up";
                case "Down": return "Arrow Down";
                case "Left": return "Arrow Left";
                case "Right": return "Arrow Right";
                case "Space": return "Space";
                default: return keyName;
            }
        }
    }
}
