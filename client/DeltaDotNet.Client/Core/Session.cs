using System;
using System.Collections.Generic;
using System.Text.Json;

namespace DeltaDotNet.Client.Core
{
    /// <summary>One player row of the current lobby.</summary>
    public class MemberInfo
    {
        public string Login;
        public string Display;
        public int Slot;
        public bool IsHost;
        public bool Rainbow;
        public string NameColor;
        public string Badge;
        public string Rank;
    }

    /// <summary>Everything about the current lobby, parsed from lobby.state.</summary>
    public class LobbyInfo
    {
        public string Id = "";
        public string Name = "";
        public string Host = "";
        public string Visibility = "open";
        public bool HasPassword;
        public int Players;
        public int MaxPlayers = 2;
        public string State = "waiting";
        public List<MemberInfo> Members = new List<MemberInfo>();
        public List<string> Bans = new List<string>();
        public List<string> AllowList = new List<string>();

        public static LobbyInfo Parse(JsonElement el)
        {
            var l = new LobbyInfo();
            l.Id = Json.Str(el, "id");
            l.Name = Json.Str(el, "name");
            l.Host = Json.Str(el, "host");
            l.Visibility = Json.Str(el, "visibility", "open");
            l.HasPassword = Json.Bool(el, "hasPassword");
            l.Players = Json.Int(el, "players");
            l.MaxPlayers = Json.Int(el, "maxPlayers", 2);
            l.State = Json.Str(el, "state", "waiting");

            JsonElement arr;
            if (el.TryGetProperty("members", out arr) && arr.ValueKind == JsonValueKind.Array)
            {
                foreach (var m in arr.EnumerateArray())
                {
                    l.Members.Add(new MemberInfo
                    {
                        Login = Json.Str(m, "login"),
                        Display = Json.Str(m, "display"),
                        Slot = Json.Int(m, "slot", 1),
                        IsHost = Json.Bool(m, "isHost"),
                        Rainbow = Json.Bool(m, "rainbow"),
                        NameColor = Json.Str(m, "nameColor"),
                        Badge = Json.Str(m, "badge"),
                        Rank = Json.Str(m, "rank", "player")
                    });
                }
            }
            if (el.TryGetProperty("bans", out arr) && arr.ValueKind == JsonValueKind.Array)
                foreach (var b in arr.EnumerateArray()) l.Bans.Add(b.GetString());
            if (el.TryGetProperty("allowList", out arr) && arr.ValueKind == JsonValueKind.Array)
                foreach (var b in arr.EnumerateArray()) l.AllowList.Add(b.GetString());
            return l;
        }
    }

    /// <summary>Small helpers for reading System.Text.Json elements safely.</summary>
    public static class Json
    {
        public static string Str(JsonElement el, string name, string fallback = "")
        {
            JsonElement v;
            if (el.ValueKind == JsonValueKind.Object && el.TryGetProperty(name, out v) && v.ValueKind == JsonValueKind.String)
                return v.GetString();
            return fallback;
        }

        public static int Int(JsonElement el, string name, int fallback = 0)
        {
            JsonElement v;
            if (el.ValueKind == JsonValueKind.Object && el.TryGetProperty(name, out v) && v.ValueKind == JsonValueKind.Number)
                return v.GetInt32();
            return fallback;
        }

        public static bool Bool(JsonElement el, string name, bool fallback = false)
        {
            JsonElement v;
            if (el.ValueKind == JsonValueKind.Object && el.TryGetProperty(name, out v))
            {
                if (v.ValueKind == JsonValueKind.True) return true;
                if (v.ValueKind == JsonValueKind.False) return false;
            }
            return fallback;
        }

        public static JsonElement Obj(JsonElement el, string name)
        {
            JsonElement v;
            if (el.ValueKind == JsonValueKind.Object && el.TryGetProperty(name, out v)) return v;
            return default(JsonElement);
        }
    }

    /// <summary>Global state of the signed in user and the active connection.</summary>
    public static class Session
    {
        public static readonly Net Net = new Net();

        public static string Token = "";
        public static string Login = "";
        public static string Display = "";
        public static string Rank = "player";
        public static bool IsAdmin;
        public static bool Rainbow;
        public static string Motd = "";

        public static LobbyInfo Lobby;
        public static int MySlot = 1;
        public static bool IsHost;

        public static void ApplyProfile(JsonElement profile)
        {
            Login = Json.Str(profile, "login", Login);
            Display = Json.Str(profile, "display", Login);
            Rank = Json.Str(profile, "rank", "player");
            IsAdmin = Json.Bool(profile, "isAdmin");
            Rainbow = Json.Bool(profile, "rainbow");
        }

        public static void Reset()
        {
            Token = ""; Login = ""; Display = ""; Rank = "player";
            IsAdmin = false; Rainbow = false; Lobby = null; IsHost = false; MySlot = 1;
        }
    }
}
