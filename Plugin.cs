using BepInEx;
using BepInEx.Configuration;
using BepInEx.Logging;
using HarmonyLib;
using Newtonsoft.Json.Linq;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Managers.Reproduction;
using ScavPrototypeSexMod.Patches;
using ScavPrototypeSexMod.Patches.Debug;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace ScavPrototypeSexMod;

[BepInPlugin(Guid, Name, Version)]
[HarmonyPatch]
public class Plugin : BaseUnityPlugin
{
    public const string Guid = "mfs.casualtiesunknown.scavsexmod";
    public const string Name = "ScavSexMod";
    public const string Version = "0.1.3";

    private const string RunSceneName = "SampleScene";

    public static Plugin Instance;
    public static ManualLogSource Log;
    public static bool doOnce = false;

    public static AudioClip silenceClip;
    public static AudioClip timeMusicClip;
    public static AudioClip cumSplatClip;
    public static AudioClip[] handjobClips;

    public static ConfigEntry<BodyTypeTop> configSettingsTop;
    public static ConfigEntry<BodyTypeBottom> configSettingsBottom;
    public static ConfigEntry<DickTypes> configSettingsDickType;
    public static Dictionary<string, ConfigEntry<bool>> kinkConfig = [];
    public static ConfigEntry<bool> configSettingsfunnyStuff;

    public static ConfigFile SexModConfig { get; private set; }

    public void Awake()
    {
        Instance = this;
        Log = Logger;

        SceneManager.sceneUnloaded += OnSceneUnload;

        silenceClip = FileLoader.LoadEmbeddedAudio(
            "ScavPrototypeSexMod.Assets.Sounds.silence.wav");

        timeMusicClip = FileLoader.LoadEmbeddedAudio(
            "ScavPrototypeSexMod.Assets.Music.time.wav");

        cumSplatClip = FileLoader.LoadEmbeddedAudio("ScavPrototypeSexMod.Assets.Sounds.cumSplat.mp3");

        handjobClips =
        [
            FileLoader.LoadEmbeddedAudio("ScavPrototypeSexMod.Assets.Sounds.handjob_1.mp3"),
            FileLoader.LoadEmbeddedAudio("ScavPrototypeSexMod.Assets.Sounds.handjob_2.mp3"),
            FileLoader.LoadEmbeddedAudio("ScavPrototypeSexMod.Assets.Sounds.handjob_3.mp3"),
            FileLoader.LoadEmbeddedAudio("ScavPrototypeSexMod.Assets.Sounds.handjob_4.mp3"),
            FileLoader.LoadEmbeddedAudio("ScavPrototypeSexMod.Assets.Sounds.handjob_5.mp3"),
        ];

        Log.LogInfo("ScavSexMod Loaded");

        // Log every embedded resource in the mod
        foreach (var res in Assembly.GetExecutingAssembly().GetManifestResourceNames())
        {
            Log.LogInfo("RESOURCE FOUND: " + res);
        }

        SexModConfig = new ConfigFile(Path.Combine(Paths.ConfigPath, "ScavPrototypeSexMod.cfg"), true);

        configSettingsTop = SexModConfig.Bind("General", "configTop", BodyTypeTop.Flat, "Gender option for the upper half of the body");
        configSettingsBottom = SexModConfig.Bind("General", "configBottom", BodyTypeBottom.Dick, "Gender option for the lower half of the body");
        configSettingsDickType = SexModConfig.Bind("General", "configDickType", DickTypes.Humanoid, "Dick Type for the lower half of the body");
        configSettingsfunnyStuff = SexModConfig.Bind("General", "configfunnyStuff", false, "Some funny things on the death screen and last stand.");

        ColliderDebugDrawer.DrawLimbColliders = SexModConfig.Bind("Debug", "drawDebugColliders", false, "Draw 2D collider outlines for all limbs");

        JObject embeddedOther = FileLoader.LoadLocale("en")?["other"] as JObject;

        foreach (var (key, value) in SharedState.kinkOptions)
        {
            string nameKey = "runset" + key;
            string descKey = nameKey + "dsc";

            string name = FileLoader.GetLocale("other", nameKey);
            if (name == nameKey)
            {
                name = embeddedOther?[nameKey]?.ToString() ?? key;
            }

            string desc = FileLoader.GetLocale("other", descKey);
            if (desc == descKey)
            {
                desc = embeddedOther?[descKey]?.ToString() ?? string.Empty;
            }

            kinkConfig[key] = SexModConfig.Bind("Kinks", name, value, desc);
        }

        // Apply the saved settings.
        SharedState.savedBodyOptions = (configSettingsTop.Value, configSettingsBottom.Value);
        SharedState.savedDickType = configSettingsDickType.Value;

        // Just checking if everything is loading.
        ItemManager.Initialize();
        UIManager.Initialize();
        DickManager.Initialize();
        SexManager.Initialize();
        MasturbationManager.Initialize();
        STDManager.Initialize();
        NetPlayManager.Initialize();
        ParticleManager.Initialize();

        new Harmony(Guid).PatchAll();
    }

    private void OnSceneUnload(Scene scene)
    {
        ModdedLogger.Info($"Scene unloaded: {scene.name}");

        if (scene.name == RunSceneName)
        {
            DickManager.ResetRunState();
            ItemManager.ResetRunState();
            LimbManager.ResetRunState();
            UIManager.ResetRunState();
            AnimationOverrideManager.ResetRunState();
            FluidManagerPatches.ResetRunState();
            MinigameBasePatch.ResetRunState();
        }

        if (MusicManagerPatch.audSource != null)
        {
            MusicManagerPatch.audSource.Stop();
            GameObject.Destroy(MusicManagerPatch.audSource.gameObject);
            MusicManagerPatch.audSource = null;
        }

        MusicManagerPatch.initialized = false;
        MusicManagerPatch.startedPlaying = false;
    }
}
