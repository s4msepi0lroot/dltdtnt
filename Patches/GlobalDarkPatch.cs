using HarmonyLib;
using UnityEngine.Device;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(GlobalDark))]
internal static class GlobalDarkPatch
{
    [HarmonyPatch("Awake")]
    [HarmonyPostfix]
    public static void GlobalDark_Awake_Patch(GlobalDark __instance)
    {
        __instance.betaBuild.text = "Casualties: Unknown Sex Extension (Game version: " + Application.version + ", Mod version: " + Plugin.Version + "); Things are subject to change.";
        __instance.betaBuild.color = new(__instance.betaBuild.color.r, __instance.betaBuild.color.g, __instance.betaBuild.color.b, 0.35f);
    }
}
