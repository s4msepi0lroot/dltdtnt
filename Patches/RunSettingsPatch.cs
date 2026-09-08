using HarmonyLib;
using ScavPrototypeSexMod.Patches.Debug;

namespace ScavPrototypeSexMod.Patches;

// TODO: Use the embedded json file and make cucorelib load it dynamically
// TODO: I don't think loading it dynamically is possible rn so just use LoadLocale and GetLocale from FileLoader in the meantime.

[HarmonyPatch(typeof(RunSettings))]
internal static class RunSettingsPatch
{
    [HarmonyPatch("GetPreset")]
    [HarmonyPostfix]
    public static void RunSettings_GetPreset_Postfix(string presetName, ref RunSettingsPreset __result)
    {
        PatchLogging.LogPatchCall();

        if (__result?.presetValues == null)
        {
            return;
        }

        if (!__result.presetValues.ContainsKey("Top"))
        {
            __result.presetValues["Top"] = (int)SharedState.savedBodyOptions.top;
        }

        if (!__result.presetValues.ContainsKey("Bottom"))
        {
            __result.presetValues["Bottom"] = (int)SharedState.savedBodyOptions.bottom;
        }

        foreach (var key in SharedState.kinkOptions.Keys)
        {
            if (!__result.presetValues.ContainsKey(key)
                && Plugin.kinkConfig.TryGetValue(key, out var configEntry))
            {
                __result.presetValues[key] = configEntry.Value;
            }
        }
    }
}