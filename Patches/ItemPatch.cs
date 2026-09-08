using HarmonyLib;
using ScavPrototypeSexMod.Data;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Managers.Reproduction;
using ScavPrototypeSexMod.Patches.Debug;
using System.Collections.Generic;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(Item))]
internal static class ItemPatch
{
    [HarmonyPatch("SetupItems")]
    [HarmonyPostfix]
    public static void Item_SetupItems_Postfix()
    {
        PatchLogging.LogPatchCall();
        EnsureLiquidsForItems();

        ItemManager.RegisterItems();

        // Reset the values so they don't persist between runs
        SexManager.ResetVals();
    }

    [HarmonyPatch("Update")]
    [HarmonyPostfix]
    public static void Item_Update_Postfix()
    {
        PatchLogging.LogPatchCall();
    }

    private static void EnsureLiquidsForItems()
    {
        Liquids.Registry["lube"] = new()
        {
            localeName = "lube",
            color = new Color(byte.MaxValue, byte.MaxValue, byte.MaxValue, byte.MaxValue),
            valuePerLiter = 13f,
            injectionSickness = 2f,
            onDrink = (ml, body) =>
            {
                float num = ml * 0.001f;
                body.Drink(num * 90f);
                body.Eat(30f * num, 4f * num);
                body.temperature -= num * 2.5f;
                body.happiness += 5f * num;
            },
            qualities = new List<CraftingQuality>
            {
                new CraftingQuality("water", 0.5f)
            }
        };

        // TODO: separate progesterone and testosterone, these are different hormones
        Liquids.Registry["progesterone"] = new()
        {
            localeName = "progresterone",
            color = new Color(byte.MaxValue, byte.MaxValue, byte.MaxValue, byte.MaxValue),
            valuePerLiter = 13f,
            injectable = true,
            injectionSickness = 2f,
            onHealthUse = (ml, limb) =>
            {
                ModdedLogger.Warning("Injecting!!...");
                // Turn you into a boy...
                if (ExtraBodyData.switchStatus <= 100)
                {
                    ExtraBodyData.switchStatus += 0.1f;
                }
            }
        };

        // Estradiol is a GROUP of sex hormones, where estrogen is ONE OF THREE types of it
        Liquids.Registry["estrogen"] = new()
        {
            localeName = "estrogen",
            color = new Color(byte.MaxValue, byte.MaxValue, byte.MaxValue, byte.MaxValue),
            valuePerLiter = 13f,
            injectable = true,
            injectionSickness = 0.1f,
            onHealthUse = (ml, limb) =>
            {
                ModdedLogger.Warning("Injecting!!...");
                // Turn you into a girl...
                if (ExtraBodyData.switchStatus <= 100)
                {
                    ExtraBodyData.switchStatus += 0.1f;
                }
            }
        };
    }
}
