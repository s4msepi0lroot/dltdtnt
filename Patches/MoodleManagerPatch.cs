using HarmonyLib;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Patches.Debug;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

// Moodle Manager!
[HarmonyPatch(typeof(MoodleManager))]
internal static class MoodleManagerPatch
{
    [HarmonyPatch("AddAllMoodles")]
    [HarmonyPrefix]
    public static void MoodleManagerPatch_AddAllMoodles_Prefix()
    {
        PatchLogging.LogPatchCall();

        Body body = RunContext.Body;
        if (body != null)
        {
            LimbManager.SyncLimbDependentArrays(body);
        }
    }

    [HarmonyPatch("AddAllMoodles")]
    [HarmonyPrefix]
    public static void AddAllMoodles_Postfix(MoodleManager __instance)
    {
        PatchLogging.LogPatchCall();
        Body body = RunContext.Body;
        var data = RunContext.BodyData;
        if (data == null || body == null || !body.alive)
        {
            return;
        }

        float horniness = data.horniness;
        if (data.wearingCondom)
        {
            AddMoodle(__instance, intens: 6, iconId: "protectionmoodle", spritePath: "Assets.protectionmoodle.png", nameKey: FileLoader.GetLocale("moodle", "protection"), descKey: FileLoader.GetLocale("moodle", "protectiondsc"));
        }

        if (horniness < 1f)
        {
            return;
        }
        else if (horniness <= 29f)
        {
            AddMoodle(__instance, intens: 0, iconId: "hornymoodle", spritePath: "Assets.hornymoodle.png", nameKey: FileLoader.GetLocale("moodle", "horny"), descKey: FileLoader.GetLocale("moodle", "hornydsc"));
        }
        else if (horniness <= 54f)
        {
            AddMoodle(__instance, intens: 1, iconId: "horniermoodle", spritePath: "Assets.horniermoodle.png", nameKey: FileLoader.GetLocale("moodle", "hornier"), descKey: FileLoader.GetLocale("moodle", "hornierdsc"));
        }
        else if (horniness <= 79f)
        {
            AddMoodle(__instance, intens: 2, iconId: "evenhorniermoodle", spritePath: "Assets.evenhorniermoodle.png", nameKey: FileLoader.GetLocale("moodle", "evenhornier"), descKey: FileLoader.GetLocale("moodle", "evenhornierdsc"));
        }
        else
        {
            AddMoodle(__instance, intens: 3, iconId: "horniestmoodle", spritePath: "Assets.horniestmoodle.png", nameKey: FileLoader.GetLocale("moodle", "horniest"), descKey: FileLoader.GetLocale("moodle", "horniestdsc"), true);
        }
    }

    private static void AddMoodle(MoodleManager manager, int intens, string iconId, string spritePath, string nameKey, string descKey, bool critical = false)
    {
        int ppu = 33;
        Sprite sprite = FileLoader.LoadEmbeddedSprite(spritePath, ppu);
        manager.icons[iconId] = sprite;
        manager.AddMoodle(intens, iconId, nameKey, descKey, critical);
    }
}
