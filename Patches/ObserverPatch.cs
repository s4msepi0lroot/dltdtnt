using HarmonyLib;
using ScavPrototypeSexMod.Patches.Debug;
using System.Reflection;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(Observer))]
internal static class ObserverChanges
{
    [HarmonyPatch("RolledLastStand")]
    [HarmonyPostfix]
    public static void Observer_RolledLastStand_Postfix(Observer __instance)
    {
        PatchLogging.LogPatchCall();

        var _distanceField = typeof(Observer).GetField("distance", BindingFlags.Instance | BindingFlags.NonPublic);
        _distanceField.SetValue(__instance, 99999f);
    }
}
