using Newtonsoft.Json.Bson;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace ScavPrototypeSexMod.Managers;

public static class AnimationOverrideManager
{
    private static Dictionary<string, AnimationClip> _tempOriginals = new();
    private static Dictionary<string, AnimationClip> _bodyClipMap;
    private static bool _bodyClipMapLoaded;

    public static void ApplyRunOverrides(Body body)
    {
        if (!ShouldApply(body))
        {
            return;
        }

        EnsureBodyClipMap();

        if (_bodyClipMap == null || _bodyClipMap.Count == 0)
        {
            ModdedLogger.Warning("bodyNewAnimations.bundle has no clips to apply.");

            return;
        }

        int bodyCount = ApplyAnimatorOverrides(body.bodyAnimator, _bodyClipMap);
        int armsCount = ApplyAnimatorOverrides(body.armsAnimator, _bodyClipMap);

        ModdedLogger.Info($"Run animation overrides applied: body={bodyCount}, arms={armsCount}");
    }

    private static bool ShouldApply(Body body)
    {
        if (body?.limbs == null)
        {
            ModdedLogger.Info($"body or limbs were null, aborting...");

            return false;
        }

        // BROKEN!
        // int bottom = SharedState.savedBodyOptions[0].bottom;
        // if (bottom != 1 && bottom != 2)
        // {
        //     ModdedLogger.Info($"bottom type is not defined, aborting...");
        // 
        //     return false;
        // }

        var limbFound = body.limbs.Any(l => l != null && l.gameObject.name == "Dick");
        if (limbFound)
        {
            ModdedLogger.Info($"Dick limb was found");
        }

        return limbFound;
    }

    private static void EnsureBodyClipMap()
    {
        if (_bodyClipMapLoaded)
        {
            return;
        }

        _bodyClipMap = FileLoader.LoadEmbeddedClipMap(SharedState.bodyBundleBytes);
        _bodyClipMapLoaded = true;

        if (_bodyClipMap.Count > 0)
        {
            ModdedLogger.Info("Loaded body animation clips: " + string.Join(", ", _bodyClipMap.Keys));
        }
    }

    private static int ApplyAnimatorOverrides(Animator animator, Dictionary<string, AnimationClip> modClips)
    {
        if (animator?.runtimeAnimatorController == null)
        {
            return 0;
        }

        RuntimeAnimatorController baseController = GetBaseController(animator.runtimeAnimatorController);
        AnimatorOverrideController overrideController = new(baseController);

        int replaced = 0;
        foreach (AnimationClip originalClip in baseController.animationClips)
        {
            if (originalClip == null)
            {
                continue;
            }

            if (!modClips.TryGetValue(originalClip.name, out AnimationClip replacement))
            {
                continue;
            }

            overrideController[originalClip.name] = replacement;
            replaced++;
        }

        if (replaced > 0)
            animator.runtimeAnimatorController = overrideController;

        return replaced;
    }

    private static RuntimeAnimatorController GetBaseController(RuntimeAnimatorController controller)
    {
        if (controller is AnimatorOverrideController overrideController)
        {
            return overrideController.runtimeAnimatorController;
        }

        return controller;
    }

    public static AnimatorOverrideController GetOrCreateOverride(Animator animator)
    {
        if (animator.runtimeAnimatorController is AnimatorOverrideController existing)
        {
            return existing;
        }

        AnimatorOverrideController created = new AnimatorOverrideController(animator.runtimeAnimatorController);
        animator.runtimeAnimatorController = created;

        return created;
    }

    public static void SetTemporary(Animator anim, string clipName, AnimationClip repl)
    {
        AnimatorOverrideController ctrl = GetOrCreateOverride(anim);

        if (!_tempOriginals.ContainsKey(clipName))
            _tempOriginals[clipName] = ctrl[clipName];

        ctrl[clipName] = repl;
    }

    public static void RestoreTemporary(Animator anim, string clipName)
    {
        AnimatorOverrideController ctrl = GetOrCreateOverride(anim);

        if (!_tempOriginals.TryGetValue(clipName, out AnimationClip original))
        {
            return;
        }

        ctrl[clipName] = original;
        _tempOriginals.Remove(clipName);
    }

    public static void ResetRunState()
    {
        _tempOriginals.Clear();
    }
}
