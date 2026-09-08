using System;

namespace ScavPrototypeSexMod.Data;

[Serializable]
public class ExtraBodyData
{
    // TODO: strange that this is static, but it works for now.
    // Used for changing gender
    public static float switchStatus = 0f;

    public float horniness = 100f;
    public float hardness = 100f;
    public float bladderAmount = 0f;
    public float urineDensity = 0f;
    public float durHorny = 0f;
    public float breakChance;
    public float infectprog = 0f;

    public bool havingSex = false;
    public bool wearingCondom = false;
    public bool condomInInventory = false;
    public bool hasSTD = false;
    public bool isPregnant = false;

    public bool knot = false;
    public DickTypes CurrentDick;

    public static (RunSettingDropdown top, RunSettingDropdown bottom) bodySettings = (
        new RunSettingDropdown(
            "Top",
            ["Flat", "Breasts", "None"]
        ),
        new RunSettingDropdown(
            "Bottom",
            ["Pussy", "Dick", "Both", "None"]
        )
    );

    public static RunSettingDropdown dicktypeSetting = (
        new RunSettingDropdown(
            "Dicktype",
            ["Humanoid", "Canine", "Tapered", "Equine", "Barbed", "Spiked"]
        )
    );

    public static Status CurrentBodyStatus => ExtraBodyData.GetStatus(
            SharedState.savedBodyOptions.top,
            SharedState.savedBodyOptions.bottom
        );

    // This is still wonky and broken I think.
    public static Status GetStatus(BodyTypeTop top, BodyTypeBottom bottom)
    {
        ModdedLogger.Warning("Top is " + top + ". Bottom is " + bottom);
        return (top, bottom) switch
        {
            (BodyTypeTop.Flat, BodyTypeBottom.Dick) => Status.Male, // Male
            (BodyTypeTop.Flat, BodyTypeBottom.Pussy) or (BodyTypeTop.Breasts, BodyTypeBottom.Pussy) => Status.Female, // Female
            (BodyTypeTop.Flat, BodyTypeBottom.Both) or (BodyTypeTop.Breasts, BodyTypeBottom.Both) or (BodyTypeTop.Breasts, BodyTypeBottom.Dick) => Status.Intersex, // Intersex
            _ => Status.NonBinary, // None
        };
    }
}
