using HarmonyLib;
using ScavPrototypeSexMod.Data;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Managers.Reproduction;
using ScavPrototypeSexMod.Patches.Debug;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

// Adding a body part to the body before it grabs a list of body parts.
[HarmonyPatch(typeof(Body))]
internal static class BodyStartPatch
{
    [HarmonyPatch("Start")]
    [HarmonyPrefix]
    public static void Body_Start_Prefix(Body __instance)
    {
        PatchLogging.LogPatchCall();

        LimbManager.Initialize(__instance);
        HardnessManager.Initialize();
        AnimationOverrideManager.ApplyRunOverrides(__instance);

        SexManager.ResetVals();
    }

    [HarmonyPatch("Start")]
    [HarmonyPostfix]
    public static void Body_Start_Postfix(Body __instance)
    {
        PatchLogging.LogPatchCall();

        // If Loaded from a run, keeps the current player conf, else if new run is started then override.
        if (SaveSystem.loadedRun)
        {
            BodyOptionsSync.ApplyRunSettings();
        }
        else
        {
            BodyOptionsSync.OverrideRunSettings();
        }

        SpriteReplacement.ReplaceSprites(__instance);
    }

    // Keep the passive horniness update on the player's Body.Update instead of a global MonoBehaviour.
    // Traders use Body too, so only the body currently owned by PlayerCamera may change state.

    [HarmonyPatch("Update")]
    [HarmonyPostfix]
    public static void Body_Update_Postfix(Body __instance)
    {
        if (PlayerCamera.main == null || PlayerCamera.main.body != __instance)
        {
            return;
        }

        SexManager.UpdateHorninessFromMood(__instance);
    }

    [HarmonyPatch("PlaceBody")]
    [HarmonyPostfix]
    public static void Body_PlaceBody_Hook(Body __instance)
    {
        PatchLogging.LogPatchCall();

        SpriteReplacement.ReplaceSprites(__instance);
    }

    [HarmonyPatch("WearWearable")]
    [HarmonyPostfix]
    public static void Body_WearWearable_Postfix(Body __instance)
    {
        PatchLogging.LogPatchCall();

        SpriteReplacement.ReplaceSprites(__instance);
    }

    [HarmonyPatch("DropWearable")]
    [HarmonyPostfix]
    public static void Body_DropWearable_Postfix(Body __instance)
    {
        PatchLogging.LogPatchCall();

        SpriteReplacement.ReplaceSprites(__instance);
    }
}

// Instead of adding limbs atm just replace sprites on body cuz that's easier.
// TODO: remake it because it's basically a legacy system
internal class SpriteReplacement
{
    // TODO: remake it because it's basically a legacy system
    public static void ReplaceSprites(Body body)
    {
        if (!body || body.limbs == null || body.limbs.Length == 0)
        {
            return;
        }

        string[] assetNames = GetAssetNames(body);

        int count = body.limbs.Length;

        Sprite[] replacements = new Sprite[assetNames.Length];
        for (int j = 0; j < assetNames.Length; j++)
        {
            replacements[j] = FileLoader.LoadEmbeddedSprite(assetNames[j], 8f);
        }

        int replaceLimit = Mathf.Min(count, SharedState.changeAssets.Length);
        for (int i = 0; i < replaceLimit; i++)
        {
            int assetIndex = SharedState.changeAssets[i];

            if (assetIndex == -1)
            {
                ModdedLogger.Error(
                    $"Invalid assetIndex {assetIndex} at limb {i}. replacements.Length={replacements.Length}"
                );

                continue;
            }

            if (assetIndex < 0 || assetIndex >= replacements.Length)
            {
                ModdedLogger.Error(
                    $"Invalid assetIndex {assetIndex} at limb {i}. replacements.Length={replacements.Length}"
                );
                continue;
            }

            ModdedLogger.Warning($"{i}: assetIndex={assetIndex}, sprite={replacements[assetIndex].name}");
        }
    }

    private static string[] GetAssetNames(Body body)
    {
        if (!body || body.limbs == null)
        {
            return [];
        }

        string[] assetNames = (string[])SharedState.BASE_ASSET_NAMES.Clone();
        var (top, bottom) = SharedState.savedBodyOptions;

        assetNames[0] = top switch
        {
            BodyTypeTop.Breasts => "expieboobs.png",
            _ => "experimentUpTorso.png",
        };

        assetNames[1] = bottom switch
        {
            BodyTypeBottom.Pussy => "expiepussy.png",
            _ => "experimentDownTorso.png",
        };

        var upTorso = body.GetWearableBySlotID("outertorso") || body.GetWearableBySlotID("torsofront");
        var armor = body.GetWearable("striderpelt") || body.GetWearable("tornshirt") || body.GetWearable("bellyarmor");

        if (upTorso && !armor)
        {
            assetNames[0] = "experimentUpTorso.png";
        }

        return assetNames;
    }
}
