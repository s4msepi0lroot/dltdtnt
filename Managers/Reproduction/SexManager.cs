using ScavPrototypeSexMod.Data;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace ScavPrototypeSexMod.Managers.Reproduction;

public record SexValid
{
    public bool Ok { get; set; } = true;
    public bool CondomInInventory { get; set; } = false;
}

public static class SexManager
{
    // At totalHappiness ±100, horniness changes by ±0.2 per second. At a neutral mood
    // it remains unchanged; the multiplier below scales linearly between those limits.
    private const float HorninessMoodRateAtMaximum = 0.2f;

    public static void Initialize()
    {
        ModdedLogger.Info("SexManager Initialized");
    }

    /// <summary>
    /// Applies the passive mood effect to horniness. <see cref="Body.totalHappiness"/>
    /// includes the game's health, hunger, thirst, pain and sickness modifiers, unlike
    /// the raw <see cref="Body.happiness"/> value.
    /// </summary>
    public static void UpdateHorninessFromMood(Body body)
    {
        ExtraBodyData data = RunContext.BodyData;
        if (body == null || data == null)
        {
            return;
        }

        // TODO: Make it more simplier and add a timer that dosen't allow to cheat it.
        float mood = Mathf.Clamp(body.totalHappiness + 10, -100f, 100f);
        float change = mood * 0.01f * HorninessMoodRateAtMaximum * Time.deltaTime;

        data.horniness = Mathf.Clamp(
            data.horniness + change,
            0f,
            100f);
    }

    public static void ResetVals()
    {
        ExtraBodyData data = RunContext.BodyData;
        if (data == null)
        {
            return;
        }

        data.hasSTD = false;
        foreach (var key in SharedState.stdTypes.Keys.ToList())
        {
            SharedState.stdTypes[key] = false;
        }
        data.condomInInventory = false;
        data.wearingCondom = false;
        data.horniness = 0f;
        data.hardness = 0f;
        data.durHorny = 0f;
        data.breakChance = 0f;
        data.infectprog = 0f;
    }

    // The main function for trader sex
    public static IEnumerator TriggerSex(PlayerCamera cam)
    {
        var res = new SexValid();

        yield return CheckRep(cam, res);
        if (!res.Ok)
            yield break;

        yield return CheckSTD(cam, res);
        if (!res.Ok)
            yield break;

        yield return CheckHappiness(cam, res);
        if (!res.Ok)
            yield break;

        yield return CheckHorny(cam, res);
        if (!res.Ok)
            yield break;

        yield return CheckGender(cam, res);
        if (!res.Ok)
            yield break;

        yield return CheckCondomInv(cam);

        UseCondomInSex(cam, res.CondomInInventory);
        ApplySexOutcomes(cam);

        if (cam.tradeMenu.activeSelf)
        {
            cam.ToggleTradeMenu();
        }

        cam.radialOpen = false;
    }

    private static IEnumerator CheckRep(PlayerCamera cam, SexValid res)
    {
        TraderScript trader = cam != null ? cam.currentTrader : null;
        if (trader == null) yield break;
        ModdedLogger.Info("The current trader rep is... " + trader.reputation);

        if (trader.reputation >= 100) yield break;
        res.Ok = false;

        ModdedLogger.Info("Trader rep check passed");
        cam.PlayUISound(PlayerCamera.UISoundType.Deny, 1f);

        if (cam.tradeMenu.activeSelf)
        {
            cam.ToggleTradeMenu();
        }
        if (cam.radialOpen)
        {
            cam.radialOpen = false;
        }

        trader.talker.Talk("What the hell is wrong with you?", null, true, false);
        trader.reputation -= 15f;
    }

    private static IEnumerator CheckSTD(PlayerCamera cam, SexValid res)
    {
        ExtraBodyData data = RunContext.BodyData;
        if (data == null || !data.hasSTD) yield break;
        res.Ok = false;

        ModdedLogger.Info("STD check failed.");
        cam.PlayUISound(PlayerCamera.UISoundType.Deny, 1f);

        if (cam.tradeMenu.activeSelf)
        {
            cam.ToggleTradeMenu();
        }
        if (cam.radialOpen)
        {
            cam.radialOpen = false;
        }

        cam.body.talker.Talk("I don't feel in the mood for that...", null, true, false);
        yield return new WaitForSeconds(3);
        cam.body.talker.Talk("I think something's wrong with my body.", null, true, false);
        yield return new WaitForSeconds(3);
    }

    private static IEnumerator CheckHappiness(PlayerCamera cam, SexValid res)
    {
        if (cam.body.happiness >= 1f)
        {
            yield break;
        }
        res.Ok = false;

        ModdedLogger.Info("Happiness check failed.");
        cam.PlayUISound(PlayerCamera.UISoundType.Deny, 1f);

        if (cam.tradeMenu.activeSelf)
        {
            cam.ToggleTradeMenu();
        }
        if (cam.radialOpen)
        {
            cam.radialOpen = false;
        }

        cam.body.talker.Talk("What's the point...?", null, true, false);
    }

    private static IEnumerator CheckHorny(PlayerCamera cam, SexValid res)
    {
        ExtraBodyData data = RunContext.BodyData;
        if (data != null && data.horniness >= 45f) yield break;
        res.Ok = false;

        ModdedLogger.Info("Horniness check failed. Or is the incorrect gender.");

        if (cam.tradeMenu.activeSelf)
        {
            cam.ToggleTradeMenu();
        }
        if (cam.radialOpen)
        {
            cam.radialOpen = false;
        }

        cam.PlayUISound(PlayerCamera.UISoundType.Deny, 1f);
        cam.body.talker.Talk("I'm not doing that.", null, true, false);
    }

    private static IEnumerator CheckGender(PlayerCamera cam, SexValid res)
    {
        if (SharedState.savedBodyOptions.top != BodyTypeTop.None || SharedState.savedBodyOptions.bottom != BodyTypeBottom.None)
        {
            yield break;
        }

        res.Ok = false;

        cam.ToggleTradeMenu();
        cam.PlayUISound(PlayerCamera.UISoundType.Deny, 1f);
        cam.body.talker.Talk("Sorry, but I'm not interested.", null, true, false);

        yield return new WaitForSeconds(5);

        if (cam.currentTrader != null)
            cam.currentTrader.talker.Talk("Understandable...", null, true, false);
    }

    private static IEnumerator CheckCondomInv(PlayerCamera cam)
    {
        // Gets all items in the players inventory, checking for a condom.
        List<Item> curinventory = cam.body.GetAllItems();
        bool condomInInv = curinventory.Any(item => item.id == "condom");

        if (!condomInInv)
        {
            yield break;
        }

        ModdedLogger.Info("Condom detected in Inventory.");
        cam.currentTrader.talker.Talk("We should probably use that condom you have, huh?", null, true, false);

        if (cam.tradeMenu.activeSelf)
        {
            cam.ToggleTradeMenu();
        }

        cam.radialOpen = false;
        cam.radialMenu.gameObject.SetActive(false);
        cam.CloseContainer();

        yield return new WaitForSeconds(4);
    }

    public static void PickSTD()
    {
        var keys = SharedState.stdTypes.Keys.ToList();
        int rand = UnityEngine.Random.Range(0, keys.Count);
        string randomKey = keys[rand];

        SharedState.stdTypes[randomKey] = true;
    }

    public static void ApplySTD(int characterType)
    {
        switch (characterType)
        {
            case 0: // Expie
                if (UnityEngine.Random.value <= 0.15f)
                {
                    ModdedLogger.Info("STD Time!");
                    RunContext.BodyData.hasSTD = true;
                    PickSTD();
                }
                else
                {
                    ModdedLogger.Warning("You just narrowly avoided getting an STD.");
                }
                break;
            case 1: // Milky
                if (UnityEngine.Random.value <= 0.35f)
                {
                    ModdedLogger.Info("STD Time!");
                    RunContext.BodyData.hasSTD = true;
                    PickSTD();
                }
                else
                {
                    ModdedLogger.Warning("You just narrowly avoided getting an STD.");
                }
                break;
            case 2: // Dune
                if (UnityEngine.Random.value <= 0.45f)
                {
                    ModdedLogger.Info("STD Time!");
                    RunContext.BodyData.hasSTD = true;
                    PickSTD();
                }
                else
                {
                    ModdedLogger.Warning("You just narrowly avoided getting an STD.");
                }
                break;
            default:
                ModdedLogger.Error("What? Something has gone wrong.");
                break;
        }
    }

    private static void UseCondomInSex(PlayerCamera cam, bool hasCondom)
    {
        ExtraBodyData data = RunContext.BodyData;
        if (data == null) return;
        if (!data.wearingCondom && hasCondom)
        {
            data.wearingCondom = true;
        }

        // If you do have a condom, calculate the break chance for the condom.
        data.breakChance = UnityEngine.Random.value < 0.5f ? 0.15f : 0.35f;

        if (!data.wearingCondom || UnityEngine.Random.value <= data.breakChance)
        {
            ModdedLogger.Info("No condom!");

            cam.body.immunity -= 15f;
            
            if (Plugin.kinkConfig["STDs"].Value)
            {
                if (cam.currentTrader != null)
                    ApplySTD(cam.currentTrader.character);
            }
        }
    }

    private static void ApplySexOutcomes(PlayerCamera cam)
    {
        cam.body.energy = -5f;
        cam.body.limbs[9].skinHealth -= 5f;
        cam.body.thirst = cam.body.thirst - UnityEngine.Random.Range(0f, 25f);
        cam.body.hunger = cam.body.hunger - UnityEngine.Random.Range(0f, 25f);
        cam.body.happiness += UnityEngine.Random.Range(45f, 66f);
        cam.body.forcedSleepQuality = new Body.SleepQuality?(Body.SleepQuality.Good);

        if (cam.currentTrader != null)
            cam.currentTrader.reputation += UnityEngine.Random.Range(50f, 55f);

        ModdedLogger.Info(cam.body.curSleep);

        if (!cam.body.conscious)
        {
            return;
        }

        ExtraBodyData data = RunContext.BodyData;
        if (data == null) return;
        data.horniness = 0f;
        data.condomInInventory = false;

        if (SharedState.savedBodyOptions.bottom == BodyTypeBottom.Dick || SharedState.savedBodyOptions.bottom == BodyTypeBottom.Both)
        {
            data.hardness = 0f;
            data.wearingCondom = false;
        }
    }
}
