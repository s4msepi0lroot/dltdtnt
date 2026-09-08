using HarmonyLib;
using ScavPrototypeSexMod.Patches.Debug;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

// Patching the trader reputation
[HarmonyPatch(typeof(TraderScript))]
internal static class CheatReputation
{
    private static bool _lastFlipped;

    [HarmonyPatch("Awake")]
    [HarmonyPostfix]
    public static void TraderScript_Awake_Postfix(TraderScript __instance)
    {
        PatchLogging.LogPatchCall();
        // Not sure how to influence reputation, seems like there's an extra check.
        //__instance.reputation += 9999f;
        __instance.valueGiven += 60;
        __instance.totalValueGiven += 60;
        __instance.minHugReputation = 0f;

        ModdedLogger.Info("Value given is set to max!");

        // Don't think this would work for multiple traders?
        // Gonna have to revamp how this works.
        //ExtraTraderData extraTraderData = __instance.GetAdditionalTraderData();
    }

    // Eh??? Doesn't look right
    /*[HarmonyPatch("MeetPlayer")]
    [HarmonyPostfix]
    public static void TraderScript_MeetPlayer_Postfix(TraderScript __instance)
    {
        PatchLogging.LogPatchCall();
        if (__instance.character == 0)
        {
            // 0, 1 and 2
            //__instance.eyeSprites[0]
            __instance.headSprites[0] = SharedState.pc.body.limbs[0].GetComponent<SpriteRenderer>().sprite;
            __instance.headSprites[1] = SharedState.pc.body.limbs[0].GetComponent<SpriteRenderer>().sprite;
            __instance.torso.GetComponent<SpriteRenderer>().sprite = SharedState.pc.body.limbs[1].GetComponent<SpriteRenderer>().sprite;
            __instance.lArm.GetComponent<SpriteRenderer>().sprite = SharedState.pc.body.limbs[4].GetComponent<SpriteRenderer>().sprite;
            __instance.rArm.GetComponent<SpriteRenderer>().sprite = SharedState.pc.body.limbs[7].GetComponent<SpriteRenderer>().sprite;
            __instance.lThigh.GetComponent<SpriteRenderer>().sprite = SharedState.pc.body.legLimbs[9].GetComponent<SpriteRenderer>().sprite;
            __instance.rThigh.GetComponent<SpriteRenderer>().sprite = SharedState.pc.body.legLimbs[12].GetComponent<SpriteRenderer>().sprite;
            __instance.lFoot.GetComponent<SpriteRenderer>().sprite = SharedState.pc.body.legLimbs[11].GetComponent<SpriteRenderer>().sprite;
            __instance.rFoot.GetComponent<SpriteRenderer>().sprite = SharedState.pc.body.legLimbs[14].GetComponent<SpriteRenderer>().sprite;
        }

        ModdedLogger.Info("Replaced Sprites!");
    }*/

    // TODO: UNCOMMENT WHEN ADDING TRADERS. COMMENTED FOR 0.1.0

    [HarmonyPatch("Update")]
    [HarmonyPostfix]
    public static void TraderScript_Update_Postfix(TraderScript __instance)
    {
        PatchLogging.LogPatchCall();

        //var pc = SharedState.pc;
        //if (pc?.body == null || __instance == null)
        //{
        //    return;
        //}
        //
        //float playerX = pc.body.transform.position.x;
        //float traderX = __instance.transform.position.x;
        //
        //bool shouldFlip = playerX < traderX;
        //
        //if (shouldFlip == _lastFlipped)
        //{
        //    return;
        //}
        //
        //_lastFlipped = shouldFlip;
        //
        //SetFlip(__instance, shouldFlip);
    }

    private static void SetFlip(TraderScript t, bool flip)
    {
        t.headRender.flipX = flip;

        SetFlipSafe(t.torso, flip);
        SetFlipSafe(t.lArm, flip);
        SetFlipSafe(t.rArm, flip);
        SetFlipSafe(t.lThigh, flip);
        SetFlipSafe(t.rThigh, flip);
        SetFlipSafe(t.lFoot, flip);
        SetFlipSafe(t.rFoot, flip);
    }

    private static void SetFlipSafe(Component c, bool flip)
    {
        if (c == null)
        {
            return;
        }

        var sr = c.GetComponent<SpriteRenderer>();
        if (sr != null)
        {
            sr.flipX = flip;
        }
    }
}
