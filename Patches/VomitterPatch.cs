using HarmonyLib;
using ScavPrototypeSexMod.Patches.Debug;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(Vomiter), "Update")]
internal static class VomiterAwakePatch
{
    [HarmonyPostfix]
    public static void Vomiter_Update_Postfix(Vomiter __instance)
    {
        PatchLogging.LogPatchCall();
    }
}