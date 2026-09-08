using System.Collections.Generic;

namespace ScavPrototypeSexMod.Data;

internal static class BodyOptionsSync
{
    public static void SaveTop(BodyTypeTop top, Dictionary<string, object> runSettings)
    {
        var current = SharedState.savedBodyOptions;
        SharedState.savedBodyOptions = (top, current.bottom);

        if (runSettings != null)
        {
            runSettings["Top"] = (int)top;
        }

        Plugin.configSettingsTop.Value = top;
    }

    public static void SaveBottom(BodyTypeBottom bottom, Dictionary<string, object> runSettings)
    {
        var current = SharedState.savedBodyOptions;
        SharedState.savedBodyOptions = (current.top, bottom);

        if (runSettings != null)
        {
            runSettings["Bottom"] = (int)bottom;
        }

        Plugin.configSettingsBottom.Value = bottom;
    }

    public static void SaveDicktype(DickTypes dicktype, Dictionary<string, object> runSettings)
    {
        if (runSettings != null)
        {
            runSettings["Dicktype"] = (int)dicktype;
        }

        ModdedLogger.Error("Dicktype is " + dicktype);

        Plugin.configSettingsDickType.Value = dicktype;
        SharedState.savedDickType = Plugin.configSettingsDickType.Value;

        // Body data exists only during a run; resolve it from the current scene.
        var bodyData = RunContext.BodyData;
        if (bodyData != null)
        {
            bodyData.CurrentDick = SharedState.savedDickType;
        }
    }

    // For starting a new save
    public static void OverrideRunSettings(Dictionary<string, object> runSettings = null)
    {
        if (runSettings == null)
        {
            return;
        }

        runSettings["Top"] = (int)SharedState.savedBodyOptions.top;
        runSettings["Bottom"] = (int)SharedState.savedBodyOptions.bottom;
        runSettings["Dicktype"] = (int)SharedState.savedDickType;
    }

    // For loading a save
    public static void ApplyRunSettings(Dictionary<string, object> runSettings = null)
    {
        runSettings ??= WorldGeneration.runSettings;
        if (runSettings == null)
        {
            return;
        }

        BodyTypeTop top = SharedState.savedBodyOptions.top;
        BodyTypeBottom bottom = SharedState.savedBodyOptions.bottom;
        DickTypes dicktype = SharedState.savedDickType;

        if (runSettings.TryGetValue("Top", out object topValue))
        {
            top = (BodyTypeTop)(int)topValue;
            ModdedLogger.Error("topValue for RunSettings is: " + topValue);
        }

        if (runSettings.TryGetValue("Bottom", out object bottomValue))
        {
            bottom = (BodyTypeBottom)(int)bottomValue;
            ModdedLogger.Error("bottomValue for RunSettings is: " + bottomValue);
        }

        if (runSettings.TryGetValue("Dicktype", out object dicktypeValue))
        {
            dicktype = (DickTypes)(int)dicktypeValue;
            ModdedLogger.Error("dicktypeValue for RunSettings is: " + dicktypeValue);
        }

        SharedState.savedBodyOptions = (top, bottom);
        SharedState.savedDickType = dicktype;
    }
}
