using HarmonyLib;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Patches.Debug;
using System.Reflection;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

// Patching Music Manager
[HarmonyPatch(typeof(MusicManager))]
internal static class MusicManagerPatch
{
    private static FieldInfo _playedDeadField;
    private static AudioClip _customDeathClip;
    private static float _currentVolume = 0f;

    public static AudioSource audSource;
    public static bool initialized;
    public static bool startedPlaying = false;

    //Replace death music
    [HarmonyPatch("Update")]
    [HarmonyPostfix]
    public static void MusicManager_Update_Postfix(MusicManager __instance)
    {
        PatchLogging.LogPatchCall();

        if (SharedState.funnyStuff)
        {
            if (__instance == null)
            {
                ModdedLogger.Error("__instance is null.");
            }

            if (!initialized)
            {
                _playedDeadField = typeof(MusicManager).GetField("playedDead", BindingFlags.Instance | BindingFlags.NonPublic);

                _customDeathClip = FileLoader.LoadEmbeddedAudio("ScavPrototypeSexMod.Assets.Music.shatter.wav");

                if (_customDeathClip == null)
                {
                    ModdedLogger.Error("Failed to load custom death clip!");
                    return;
                }

                GameObject audSourceGO = new("Death");
                audSource = audSourceGO.AddComponent<AudioSource>();

                if (audSource == null)
                {
                    ModdedLogger.Error("Audio Source is null.");
                    initialized = false;
                    return;
                }

                audSource.bypassReverbZones = true;
                audSource.dopplerLevel = 0f;
                audSource.spatialBlend = 0f;
                audSource.playOnAwake = false;
                audSource.loop = false;

                audSource.clip = _customDeathClip;

                initialized = true;

                ModdedLogger.Info("Custom death audio source initialized.");
            }

            bool playedDead = (bool)_playedDeadField.GetValue(__instance);

            if (__instance.deadClip != Plugin.silenceClip)
            {
                ModdedLogger.Info("Silencing Death Music.");
                __instance.deadClip = Plugin.silenceClip;
                __instance.critSource.clip = Plugin.silenceClip;
                __instance.critSourceUnc.clip = Plugin.silenceClip;
            }

            if (playedDead)
            {
                if (!startedPlaying)
                {
                    // TODO: WTF IF THIS? WHY IT SETS VOLUME TO ZERO??
                    audSource.volume = 0f;
                    audSource.Play();
                    startedPlaying = true;
                }

                _currentVolume = Mathf.MoveTowards(_currentVolume, 1f, Time.deltaTime * 0.25f);

                audSource.volume = _currentVolume;
            }
            else
            {
                if (audSource != null && startedPlaying)
                {
                    audSource.Stop();
                    // TODO: SAME! WHY??
                    audSource.volume = 0f;
                    _currentVolume = 0f;
                    startedPlaying = false;
                }
            }
        }
    }
}
