using System;
using System.Collections.Generic;
using System.Linq;
using ScavPrototypeSexMod.Data;
using UnityEngine;
using UnityEngine.UI;


namespace ScavPrototypeSexMod.Managers.Reproduction;

public static class STDManager
{
    public static void Initialize()
    {
        ModdedLogger.Info("UIManager Initialized");
    }

    public static void PrintSTDs()
    {
        foreach (var kvp in SharedState.stdTypes)
        {
            string name = kvp.Key;
            bool hasIt = kvp.Value;
            Console.WriteLine($"{name}: {hasIt}");
        }
    }

    public static void UpdateSTD()
    {
        WoundView view = RunContext.WoundView;
        ExtraBodyData data = RunContext.BodyData;
        if (view == null || data == null)
        {
            return;
        }

        bool hasStd = SharedState.stdTypes.Values.Any(v => v);
        if (!hasStd)
        {
            return;
        }

        Transform iconTransform = ObjectFinder.FindRecursive(view.transform, "stdinfect");
        Image infectIcon = iconTransform != null ? iconTransform.GetComponent<Image>() : null;
        if (infectIcon == null)
        {
            GameObject iconGo = new("stdinfect");
            iconGo.transform.SetParent(view.limbImages[15].transform, false);

            infectIcon = iconGo.AddComponent<UnityEngine.UI.Image>();
            infectIcon.sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.stdinfect.png", 125);

            iconGo.transform.SetAsLastSibling();

            ModdedLogger.Info("Added icon!");
        }

        // I really have no idea what the fuck this math is about
        float xOffset = (4 * 50f - (6 - 1) * 25f) * (view.limbImageLerp[2] * 0.8f + 0.2f);
        Vector2 offset = new(xOffset, xOffset * 0.2f);
        infectIcon.rectTransform.anchoredPosition = offset;

        // Updates color through a lerp up to ourple
        infectIcon.color = Color32.Lerp(
            Color.white,
            new Color32(163, 0, 182, byte.MaxValue),
            data.infectprog * 0.01f
        );

        foreach (var std in SharedState.stdTypes)
        {
            string stdName = std.Key;
            bool hasIt = std.Value;

            if (!hasIt) continue;

            // Detail the effects of the STDs here in simplistic scav prototype form
            switch (stdName)
            {
                case "syphilis":
                    // thing
                    break;
                case "hiv":
                    // thing
                    break;
                case "hpv":
                    // thing
                    break;
                case "herpes":
                    // thing
                    break;
                case "gonorrhoea":
                    // thing
                    break;
                case "scabies":
                    // thing
                    break;
                case "trichomoniasis":
                    // thing
                    break;
            }

            data.infectprog += Time.deltaTime * 0.5f;
        }
    }
}
