using System.IO;
using System.Runtime.CompilerServices;
using BepInEx.Logging;

namespace ScavPrototypeSexMod;

// Thin wrapper around Plugin.Log that auto-prefixes the calling type's name.
// [CallerFilePath] is filled in by the compiler at each call site (a baked string literal),
// so there's zero runtime cost — no stack walking. The file name is assumed to match the class
// name; if it doesn't, pass an explicit tag.
public static class ModdedLogger
{
    private static ManualLogSource Source => Plugin.Log;

    public static void Info(object msg, [CallerFilePath] string file = "") =>
        Source.LogInfo(Format(msg, file));

    public static void Warning(object msg, [CallerFilePath] string file = "") =>
        Source.LogWarning(Format(msg, file));

    public static void Error(object msg, [CallerFilePath] string file = "") =>
        Source.LogError(Format(msg, file));

    public static void Message(object msg, [CallerFilePath] string file = "") =>
        Source.LogMessage(Format(msg, file));

    public static void Debug(object msg, [CallerFilePath] string file = "") =>
        Source.LogDebug(Format(msg, file));

    public static void Fatal(object msg, [CallerFilePath] string file = "") =>
        Source.LogFatal(Format(msg, file));

    private static string Format(object msg, string file) =>
        $"[{Path.GetFileNameWithoutExtension(file)}] {msg}";
}
