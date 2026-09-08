using HarmonyLib;
using ScavPrototypeSexMod.Patches.Debug;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

// For last stand.
[HarmonyPatch(typeof(Sound))]
internal static class ReplaceLastStandSounds
{
    public static GameObject GOlastStandReplacement = new("LastStandReplacement");

    [HarmonyPatch("Play", [typeof(string), typeof(Vector2), typeof(bool), typeof(bool), typeof(Transform), typeof(float), typeof(float), typeof(bool), typeof(bool)])]
    [HarmonyPrefix]
    public static bool Sound_Play_Prefix(ref string clip, Vector2 pos, bool twoDimensional, bool pitchShift, Transform follow, float volume, float pitch, bool noReverb, bool ignoreMixer)
    {
        PatchLogging.LogPatchCall();
        if (!SharedState.funnyStuff)
        {
            return true;
        }

        if (clip == "laststandheartbeat")
        {
            PlayReplacement(Plugin.silenceClip, pos, volume);
            return false;
        }

        if (clip == "laststanddrone")
        {
            PlayReplacement(Plugin.timeMusicClip, pos, volume);
            return false;
        }

        return true;
    }

    private static void PlayReplacement(AudioClip clip, Vector2 pos, float volume)
    {
        if (clip == null)
        {
            return;
        }

        GameObject go = GOlastStandReplacement;
        UnityEngine.Object.DontDestroyOnLoad(go);

        var src = go.AddComponent<AudioSource>();
        go.transform.position = pos;

        src.spatialBlend = 0f;
        src.dopplerLevel = 0f;
        src.volume = volume;
        src.clip = clip;
        src.bypassReverbZones = true;

        src.PlayOneShot(clip);

        UnityEngine.Object.Destroy(go, clip.length + 0.25f);
    }
}
