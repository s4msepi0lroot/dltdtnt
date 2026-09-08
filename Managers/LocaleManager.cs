using Newtonsoft.Json.Linq;
using System.Collections.Generic;

namespace ScavPrototypeSexMod.Managers;

/// <summary>
/// Merges the mod's embedded locale (Assets/locale/&lt;code&gt;.json) into the game's native
/// <see cref="Language"/> so the vanilla <see cref="Locale"/> getters return our strings —
/// no CUCoreLib LocaleRegistry needed.
///
/// Section -> native dictionary mapping:
///   "item"     -> Language.main      (Locale.GetItem)
///   "building" -> Language.buildings (Locale.GetBuilding)
///   "moodle"   -> Language.moodles   (Locale.GetMoodle)
///   "other"    -> Language.other     (Locale.GetOther)
///
/// Item descriptions live in the same "item" section under the "&lt;id&gt;dsc" key, exactly like
/// vanilla (SetupItems reads Locale.GetItem(id + "dsc")).
/// </summary>
public static class LocaleManager
{
    public static void Inject(Language lang)
    {
        if (lang == null)
        {
            return;
        }

        string code = string.IsNullOrEmpty(Locale.currentLangName)
            ? "en"
            : Locale.currentLangName.ToLowerInvariant();

        JObject json = FileLoader.LoadLocale(code) ?? FileLoader.LoadLocale("en");
        if (json == null)
        {
            return;
        }

        // The Language ctor initializes these, but guard against a locale file that omits a section.
        lang.main ??= [];
        lang.buildings ??= [];
        lang.moodles ??= [];
        lang.other ??= [];

        Merge(json["item"] as JObject, lang.main);
        Merge(json["building"] as JObject, lang.buildings);
        Merge(json["moodle"] as JObject, lang.moodles);
        Merge(json["other"] as JObject, lang.other);
    }

    private static void Merge(JObject section, Dictionary<string, string> target)
    {
        if (section == null || target == null)
        {
            return;
        }

        foreach (KeyValuePair<string, JToken> pair in section)
        {
            // Never clobber a key the base game already owns.
            if (!target.ContainsKey(pair.Key))
            {
                target[pair.Key] = pair.Value?.ToString() ?? pair.Key;
            }
        }
    }
}
