using HarmonyLib;
using ScavPrototypeSexMod.Data;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Managers.Reproduction;
using ScavPrototypeSexMod.Patches.Debug;
using System;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

// Patching the player camera
[HarmonyPatch(typeof(PlayerCamera))]
internal static class MenuShenanigans
{
    // My stupid BS patches. Make them optional.
    [HarmonyPatch(typeof(PlayerCamera), "Awake")]
    [HarmonyPrefix]
    private static void PlayerCamera_Awake_Prefix(PlayerCamera __instance)
    {
        PatchLogging.LogPatchCall();

        if (SharedState.funnyStuff)
        {
            __instance.deathScreenSprites[0] = SharedState.expieThink;
            __instance.deathScreenSprites[1] = SharedState.expieJoy;
            __instance.deathScreenSprites[2] = SharedState.expieWater;
        }
    }

    [HarmonyPatch("ToggleTradeMenu")]
    [HarmonyPostfix]
    public static void PlayerCamera_ToggleTradeMenu_Postfix(PlayerCamera __instance)
    {
        PatchLogging.LogPatchCall();
        if (__instance.tradeMenu.activeSelf && __instance.currentTrader != null)
        {
            __instance.StartCoroutine(UIManager.CreateTraderSexButton(__instance));

            __instance.currentTrader.reputation += 100;
        }
    }

    [HarmonyPatch("ToggleWoundView")]
    [HarmonyPostfix]
    public static void PlayerCamera_ToggleWoundView_Postfix(PlayerCamera __instance)
    {
        PatchLogging.LogPatchCall();
        if (__instance?.woundView == null)
        {
            return;
        }
        if (!__instance.woundView.gameObject.activeSelf)
        {
            return;
        }

        ModdedLogger.Info("ToggleWoundView postfix call after activeSelf check");

        WoundView view = __instance.woundView.GetComponent<WoundView>();
        bool needMasturbationButton = !UIManager.HasWorkoutButton(view, "Masturbate");
        bool needUrinationButton = Plugin.kinkConfig["Watersports"].Value &&
                                   !UIManager.HasWorkoutButton(view, "Urinate");

        if (RunContext.WoundView != null)
        {
            if (needMasturbationButton || needUrinationButton)
            {
                __instance.StartCoroutine(UIManager.InitSexModWorkoutList(__instance));
            }
        }
    }

    [HarmonyPatch("Update")]
    [HarmonyPostfix]
    public static void PlayerCamera_Update_Postfix2(PlayerCamera __instance)
    {
        PatchLogging.LogPatchCall();
        var data = RunContext.BodyData;
        if (data != null && data.horniness >= 75f)
        {
            DickManager.StartHornyRoutine(__instance);
        }

        STDManager.UpdateSTD();
        HardnessManager.Update(__instance.body, UnityEngine.Time.deltaTime);
    }

    [HarmonyPatch("HandleWorldUI")]
    [HarmonyPostfix]
    public static void PlayerCamera_HandleWorldUI_Postfix(PlayerCamera __instance)
    {
        var data = RunContext.BodyData;
        
        if (data.bladderAmount >= 0f && __instance.body.liquidDrinkTime == 0f)
        {
            __instance.waitImage.fillAmount = data.bladderAmount / 100f;
        }

        __instance.waitBackImage.enabled = (__instance.waitImage.fillAmount > 0f);
    }

    [HarmonyPatch("LastStandSequence")]
    [HarmonyPrefix]
    public static void PlayerCamera_LastStandSequence_Prefix(PlayerCamera __instance)
    {
        PatchLogging.LogPatchCall();

        if (SharedState.funnyStuff)
        {
            if (__instance.lastStandImages == null || __instance.lastStandImages.Length == 0)
                return;

            for (int i = 0; i < __instance.lastStandImages.Length; i++)
            {
                __instance.lastStandImages[i] = SharedState.sugarCoated;
            }
        }
    }
}
