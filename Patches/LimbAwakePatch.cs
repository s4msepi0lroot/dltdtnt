using HarmonyLib;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Patches.Debug;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(Limb))]
internal static class LimbAwakePatch
{
    [HarmonyPatch("Awake")]
    [HarmonyPrefix]
    public static bool Limb_Awake_Prefix()
    {
        PatchLogging.LogPatchCall();

        if (LimbManager.IsConstructing)
        {
            return false;
        }

        return true;
    }
}
