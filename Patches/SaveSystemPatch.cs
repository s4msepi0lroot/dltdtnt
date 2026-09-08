using HarmonyLib;
using ScavPrototypeSexMod.Managers;
using System.Linq;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(SaveSystem))]
internal static class SaveSystemPatch
{
    [HarmonyPatch("TryLoadGame")]
    [HarmonyPrefix]
    private static void SaveSystemPatch_TryLoadGame_Prefix()
    {
        if (!SaveSystem.loadedRun || PlayerCamera.main == null)
        {
            return;
        }

        Body body = PlayerCamera.main.body;
        if (body == null || body.limbs == null)
        {
            return;
        }

        if (body.limbs.Any(limb => limb != null && limb.name == "Dick"))
        {
            return;
        }

        LimbManager.Initialize(body);
    }
}
