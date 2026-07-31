using System.Text.Json.Serialization;

namespace DeltaDotNet.Core;

/// <summary>Wire model for a user as returned by the DeltaDotNet server.</summary>
public class UserInfo
{
    [JsonPropertyName("id")] public string Id { get; set; }
    [JsonPropertyName("username")] public string Username { get; set; }
    [JsonPropertyName("role")] public string Role { get; set; } = "user";
    [JsonPropertyName("rainbow")] public bool Rainbow { get; set; }
    [JsonPropertyName("nameColor")] public string NameColor { get; set; }
    [JsonPropertyName("badge")] public string Badge { get; set; }
    [JsonPropertyName("banned")] public bool Banned { get; set; }
    [JsonPropertyName("createdAt")] public long CreatedAt { get; set; }
    [JsonPropertyName("lastSeen")] public long? LastSeen { get; set; }

    public bool IsOwner => string.Equals(Role, "owner", StringComparison.OrdinalIgnoreCase);
}

public class AuthResponse
{
    [JsonPropertyName("token")] public string Token { get; set; }
    [JsonPropertyName("user")] public UserInfo User { get; set; }
    [JsonPropertyName("error")] public string Error { get; set; }
}

public class QualitySettings
{
    /// <summary>Frames per second the host tries to send (5..60).</summary>
    [JsonPropertyName("fps")] public int Fps { get; set; } = 30;
    /// <summary>Percentage of the original resolution that is streamed (25..100).</summary>
    [JsonPropertyName("scale")] public int Scale { get; set; } = 75;
    /// <summary>JPEG encoder quality (20..95).</summary>
    [JsonPropertyName("jpegQuality")] public int JpegQuality { get; set; } = 60;

    public QualitySettings Clone() => new() { Fps = Fps, Scale = Scale, JpegQuality = JpegQuality };

    /// <summary>Built-in presets, same list the client shows in Settings.</summary>
    public static QualitySettings Preset(string name) => name switch
    {
        "Potato" => new QualitySettings { Fps = 10, Scale = 40, JpegQuality = 30 },
        "Low" => new QualitySettings { Fps = 20, Scale = 55, JpegQuality = 45 },
        "Medium" => new QualitySettings { Fps = 30, Scale = 75, JpegQuality = 60 },
        "High" => new QualitySettings { Fps = 45, Scale = 90, JpegQuality = 75 },
        "Ultra" => new QualitySettings { Fps = 60, Scale = 100, JpegQuality = 90 },
        _ => new QualitySettings()
    };

    public static readonly string[] PresetNames = { "Potato", "Low", "Medium", "High", "Ultra", "Custom" };
}

public class LobbyMember
{
    [JsonPropertyName("id")] public string Id { get; set; }
    [JsonPropertyName("username")] public string Username { get; set; }
    [JsonPropertyName("rainbow")] public bool Rainbow { get; set; }
    [JsonPropertyName("nameColor")] public string NameColor { get; set; }
    [JsonPropertyName("badge")] public string Badge { get; set; }
    [JsonPropertyName("slot")] public int Slot { get; set; }
    [JsonPropertyName("ready")] public bool Ready { get; set; }
    [JsonPropertyName("isHost")] public bool IsHost { get; set; }

    public string Display => $"P{Slot + 1}  {Username}" + (IsHost ? "  [HOST]" : "") +
                             (string.IsNullOrEmpty(Badge) ? "" : $"  <{Badge}>");
}

public class LobbyBan
{
    [JsonPropertyName("id")] public string Id { get; set; }
    [JsonPropertyName("username")] public string Username { get; set; }
    [JsonPropertyName("reason")] public string Reason { get; set; }
    [JsonPropertyName("at")] public long At { get; set; }
    public string Display => $"{Username} — {Reason}";
}

public class LobbyInfo
{
    [JsonPropertyName("id")] public string Id { get; set; }
    [JsonPropertyName("name")] public string Name { get; set; }
    [JsonPropertyName("hostId")] public string HostId { get; set; }
    [JsonPropertyName("hostName")] public string HostName { get; set; }
    /// <summary>"open" or "closed".</summary>
    [JsonPropertyName("visibility")] public string Visibility { get; set; } = "open";
    /// <summary>"none", "password" or "whitelist".</summary>
    [JsonPropertyName("accessMode")] public string AccessMode { get; set; } = "none";
    [JsonPropertyName("maxPlayers")] public int MaxPlayers { get; set; } = 2;
    [JsonPropertyName("players")] public int Players { get; set; }
    [JsonPropertyName("state")] public string State { get; set; } = "lobby";
    [JsonPropertyName("quality")] public QualitySettings Quality { get; set; } = new();
    [JsonPropertyName("whitelist")] public List<string> Whitelist { get; set; } = new();
    [JsonPropertyName("members")] public List<LobbyMember> Members { get; set; } = new();
    [JsonPropertyName("bans")] public List<LobbyBan> Bans { get; set; } = new();

    public bool IsLocked => Visibility == "closed";
    public string Display =>
        $"{(IsLocked ? "[LOCKED] " : "")}{Name}   ({Players}/{MaxPlayers})   host: {HostName}   #{Id}   {State.ToUpperInvariant()}";
}

public class LobbyListResponse
{
    [JsonPropertyName("lobbies")] public List<LobbyInfo> Lobbies { get; set; } = new();
    [JsonPropertyName("error")] public string Error { get; set; }
}

public class LobbyResponse
{
    [JsonPropertyName("lobby")] public LobbyInfo Lobby { get; set; }
    [JsonPropertyName("error")] public string Error { get; set; }
}

public class CreateLobbyRequest
{
    [JsonPropertyName("name")] public string Name { get; set; }
    [JsonPropertyName("visibility")] public string Visibility { get; set; } = "open";
    [JsonPropertyName("accessMode")] public string AccessMode { get; set; } = "none";
    [JsonPropertyName("password")] public string Password { get; set; }
    [JsonPropertyName("whitelist")] public List<string> Whitelist { get; set; } = new();
    [JsonPropertyName("maxPlayers")] public int MaxPlayers { get; set; } = 2;
    [JsonPropertyName("quality")] public QualitySettings Quality { get; set; } = new();
}

public class AdminStats
{
    [JsonPropertyName("online")] public int Online { get; set; }
    [JsonPropertyName("lobbies")] public int Lobbies { get; set; }
    [JsonPropertyName("playing")] public int Playing { get; set; }
    [JsonPropertyName("users")] public int Users { get; set; }
    [JsonPropertyName("uptimeSec")] public int UptimeSec { get; set; }
}

public class AdminStatsResponse
{
    [JsonPropertyName("stats")] public AdminStats Stats { get; set; }
    [JsonPropertyName("online")] public List<UserInfo> Online { get; set; } = new();
}

public class AdminUsersResponse
{
    [JsonPropertyName("users")] public List<UserInfo> Users { get; set; } = new();
}

public class AdminUserPatch
{
    [JsonPropertyName("rainbow")] public bool? Rainbow { get; set; }
    [JsonPropertyName("nameColor")] public string NameColor { get; set; }
    [JsonPropertyName("badge")] public string Badge { get; set; }
    [JsonPropertyName("role")] public string Role { get; set; }
    [JsonPropertyName("username")] public string Username { get; set; }
    [JsonPropertyName("banned")] public bool? Banned { get; set; }
    [JsonPropertyName("reason")] public string Reason { get; set; }
}

/// <summary>Logical in-game actions. Every player maps their own physical keys to these.</summary>
public static class GameAction
{
    public const string Up = "up";
    public const string Down = "down";
    public const string Left = "left";
    public const string Right = "right";
    public const string Confirm = "confirm";   // P1: Z   P2: Enter
    public const string Cancel = "cancel";     // P1: X   P2: Ctrl
    public const string Menu = "menu";         // P1: C   P2: C
    public const string Pause = "pause";       // P1: P
    public const string Ctrl = "ctrl";
    public const string ShiftLeft = "shiftL";
    public const string ShiftRight = "shiftR";

    public static readonly string[] All =
    {
        Up, Down, Left, Right, Confirm, Cancel, Menu, Pause, Ctrl, ShiftLeft, ShiftRight
    };

    public static string Title(string action) => action switch
    {
        Up => "Up", Down => "Down", Left => "Left", Right => "Right",
        Confirm => "Confirm", Cancel => "Cancel", Menu => "Menu",
        Pause => "Pause", Ctrl => "Ctrl", ShiftLeft => "Left Shift", ShiftRight => "Right Shift",
        _ => action
    };
}
