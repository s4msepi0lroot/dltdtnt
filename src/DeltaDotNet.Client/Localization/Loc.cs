using System;
using System.Windows.Markup;

namespace DeltaDotNet.Client.Localization;

/// <summary>Supported interface languages. English is the default.</summary>
public enum Language { English = 0, Russian = 1 }

/// <summary>
/// Runtime access to <see cref="Strings"/>.
///
///   C#   : Loc.T("login.title")  /  Loc.F("login.welcome", name)
///   XAML : xmlns:loc="clr-namespace:DeltaDotNet.Client.Localization"
///          Text="{loc:Tr login.title}"
///
/// Markup extension values are resolved when the view is created, so after a
/// language change the shell simply rebuilds the current view.
/// </summary>
public static class Loc
{
    /// <summary>Active language. Default is English.</summary>
    public static Language Current { get; set; } = Language.English;

    /// <summary>Raised after <see cref="Current"/> changes.</summary>
    public static event Action LanguageChanged;

    public static string CurrentCode => Current == Language.Russian ? "ru" : "en";

    public static void SetLanguage(string code)
    {
        var next = string.Equals(code, "ru", StringComparison.OrdinalIgnoreCase)
            ? Language.Russian
            : Language.English;
        if (next == Current) return;
        Current = next;
        LanguageChanged?.Invoke();
    }

    /// <summary>Translated phrase, or the key itself when it is missing from the table.</summary>
    public static string T(string key)
    {
        if (key == null) return "";
        if (!Strings.Table.TryGetValue(key, out var phrase)) return key;
        var text = Current == Language.Russian ? phrase.Ru : phrase.En;
        return string.IsNullOrEmpty(text) ? phrase.En : text;
    }

    /// <summary>Translated phrase with {0}, {1}... replaced.</summary>
    public static string F(string key, params object[] args)
    {
        var text = T(key);
        try { return string.Format(text, args); }
        catch { return text; }
    }
}

/// <summary>XAML markup extension: <c>Text="{loc:Tr login.title}"</c>.</summary>
public class TrExtension : MarkupExtension
{
    public string Key { get; set; }

    public TrExtension() { }
    public TrExtension(string key) { Key = key; }

    public override object ProvideValue(IServiceProvider serviceProvider) => Loc.T(Key);
}
