using HarmonyLib;
using ScavPrototypeSexMod.Managers;

namespace ScavPrototypeSexMod.Patches;

// Inject the mod's locale sections every time the game (re)loads a language, so native
// Locale.GetItem/GetMoodle/GetOther/... resolve our strings. Runs on the lazy first access
// and again after Locale.ChangeLanguage reloads.
[HarmonyPatch(typeof(Locale))]
internal static class LocalePatch
{
    [HarmonyPatch(nameof(Locale.LoadLanguage))]
    [HarmonyPostfix]
    private static void LoadLanguage_Postfix()
    {
        LocaleManager.Inject(Locale.currentLang);
    }
}
