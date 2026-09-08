using HarmonyLib;
using ScavPrototypeSexMod.Data;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Patches.Debug;
using System.Collections.Generic;
using TMPro;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

// Make it actually do smth when u choose ur gender
[HarmonyPatch(typeof(PreRunScript))]
internal static class PreRunScriptPatch
{
    private static bool _injected;

    private static TMP_Dropdown _wiredTop;
    private static TMP_Dropdown _wiredBottom;
    private static TMP_Dropdown _wiredDicktype;

    private static UnityEngine.Events.UnityAction<int> _topListener;
    private static UnityEngine.Events.UnityAction<int> _bottomListener;
    private static UnityEngine.Events.UnityAction<int> _dicktypeListener;

    [HarmonyPatch("Start")]
    [HarmonyPrefix]
    public static void PreRunScript_Start_Prefix(PreRunScript __instance)
    {
        PatchLogging.LogPatchCall();
        if (!_injected)
        {
            var (top, bottom) = ExtraBodyData.bodySettings;

            RunSettings.settingTypes.Insert(0, top);
            RunSettings.settingTypes.Insert(1, bottom);
            RunSettings.settingTypes.Insert(2, ExtraBodyData.dicktypeSetting);

            foreach (var key in SharedState.kinkOptions.Keys)
            {
                RunSettings.settingTypes.Add(new RunSettingBool(key));
            }

            _injected = true;
        }

        EnsureModRunSettings(__instance.runSettings);
    }

    [HarmonyPatch("Start")]
    [HarmonyPostfix]
    public static void PreRunScript_Start_Postfix(PreRunScript __instance)
    {
        PatchLogging.LogPatchCall();
        var canvas = GameObject.Find("Canvas").transform;

        if (canvas == null)
        {
            ModdedLogger.Warning("Canvas is null!");
            return;
        }

        var content = ObjectFinder.FindRecursive(canvas, "Content");

        var top = content.GetChild(0).GetChild(1).GetComponent<TMP_Dropdown>();
        var bottom = content.GetChild(1).GetChild(1).GetComponent<TMP_Dropdown>();
        var dicktype = content.GetChild(2).GetChild(1).GetComponent<TMP_Dropdown>();

        var bodyData = SharedState.savedBodyOptions;

        // Sets the value for top and bottom for the dropdowns to what is in SharedState?
        top.value = (int)bodyData.top;
        bottom.value = (int)bodyData.bottom;
        dicktype.value = (int)SharedState.savedDickType;

        if (_wiredTop != top || _wiredBottom != bottom)
        {
            if (_wiredTop != null && _topListener != null)
            {
                _wiredTop.onValueChanged.RemoveListener(_topListener);
            }

            if (_wiredBottom != null && _bottomListener != null)
            {
                _wiredBottom.onValueChanged.RemoveListener(_bottomListener);
            }

            _topListener = index =>
            {
                BodyOptionsSync.SaveTop((BodyTypeTop)index, __instance.runSettings);
                ModdedLogger.Warning("Top is " + SharedState.savedBodyOptions.top);
                UIManager.UpdateGenderUI();
            };

            _bottomListener = index =>
            {
                BodyOptionsSync.SaveBottom((BodyTypeBottom)index, __instance.runSettings);
                ModdedLogger.Warning("Bottom is " + SharedState.savedBodyOptions.bottom);
                UIManager.UpdateGenderUI();
            };

            top.onValueChanged.AddListener(_topListener);
            bottom.onValueChanged.AddListener(_bottomListener);

            _wiredTop = top;
            _wiredBottom = bottom;
        }

        if (_wiredDicktype != dicktype)
        {
            if (_wiredDicktype != null && _dicktypeListener != null)
                _wiredDicktype.onValueChanged.RemoveListener(_dicktypeListener);

            _dicktypeListener = index =>
            {
                BodyOptionsSync.SaveDicktype((DickTypes)index, __instance.runSettings);

                ModdedLogger.Warning("Current Dicktype is " + SharedState.savedDickType);
            };

            dicktype.onValueChanged.AddListener(_dicktypeListener);

            _wiredDicktype = dicktype;
        }

        UIManager.CreateSettingsMenu(__instance);
    }

    [HarmonyPatch("StartRun")]
    [HarmonyPrefix]
    public static void PreRunScript_StartRun_Prefix(PreRunScript __instance)
    {
        PatchLogging.LogPatchCall();
        BodyOptionsSync.OverrideRunSettings(__instance.runSettings);
    }

    [HarmonyPatch("LoadRun")]
    [HarmonyPrefix]
    public static void PreRunScript_LoadRun_Prefix(PreRunScript __instance)
    {
        PatchLogging.LogPatchCall();
        BodyOptionsSync.ApplyRunSettings(__instance.runSettings);
    }

    [HarmonyPatch("UpdateAllSettingDisplays")]
    [HarmonyPrefix]
    public static void PreRunScript_UpdateAllSettingDisplays_Prefix(PreRunScript __instance)
    {
        EnsureModRunSettings(__instance.runSettings);
    }

    private static void EnsureModRunSettings(Dictionary<string, object> runSettings)
    {
        if (runSettings == null)
        {
            return;
        }

        runSettings["Top"] = (int)SharedState.savedBodyOptions.top;
        runSettings["Bottom"] = (int)SharedState.savedBodyOptions.bottom;
        runSettings["Dicktype"] = (int)SharedState.savedDickType;

        foreach (var key in SharedState.kinkOptions.Keys)
        {
            if (!runSettings.ContainsKey(key) && Plugin.kinkConfig.TryGetValue(key, out var configEntry))
            {
                runSettings[key] = configEntry.Value;
            }
        }
    }

}
