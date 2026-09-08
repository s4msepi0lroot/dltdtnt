using UnityEngine;

namespace ScavPrototypeSexMod.Managers.Reproduction;

public static class MasturbationManager
{
    private const float PerStrokeHorniness = 1.5f;
    private const float OrgasmThreshold = 40f;

    private static System.Random _rand = new();

    private static readonly System.Reflection.PropertyInfo exercising = typeof(Body).GetProperty("exercising",
        System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.NonPublic);

    public static void Initialize()
    {
        ModdedLogger.Info("MasturbationManager Initialized");
    }

    private static void OverrideSitAnim(PlayerCamera cam)
    {
        AnimationOverrideManager.SetTemporary(cam.body.armsAnimator, "ArmsSit", SharedState.armsJerk);
        //AnimationOverrideManager.SetTemporary(cam.body.bodyAnimator, "ExperimentSit", SharedState.experimentJerkSit);
    }

    private static void RestoreSitAnim(PlayerCamera cam)
    {
        AnimationOverrideManager.RestoreTemporary(cam.body.armsAnimator, "ArmsSit");
        //AnimationOverrideManager.RestoreTemporary(cam.body.bodyAnimator, "ExperimentSit");
    }

    public static bool CanMasturbate(PlayerCamera cam)
    {
        var data = RunContext.BodyData;
        if (cam?.body == null || data == null || cam.body.bodyAnimator.GetBool("exercising"))
        {
            return false;
        }

        if (!(cam.body.happiness > 0 && data.horniness > 40))
        {
            cam.ToggleWoundView(true);
            cam.PlayUISound(PlayerCamera.UISoundType.Deny, 1f);
            cam.UseFailUnhappiness();
            return false;
        }

        return true;
    }

    public static void BeginMasturbation(PlayerCamera cam)
    {
        cam.body.bodyAnimator.SetBool("exercising", true);
        exercising.SetValue(cam.body, true);

        OverrideSitAnim(cam);

        cam.body.bodyAnimator.Play("ExperimentSit");
        cam.body.armsAnimator.Play("ArmsSit");

        cam.body.armsAnimator.speed = 0f;
    }

    public static bool OnMasturbationStroke(PlayerCamera cam)
    {
        var data = RunContext.BodyData;
        if (cam?.body == null || data == null)
        {
            return false;
        }

        data.horniness = Mathf.Max(data.horniness - PerStrokeHorniness, 0f);

        Sound.Play(Plugin.handjobClips[_rand.Next(0, 5)], Vector2.zero, twoDimensional: true, pitchShift: false);

        if (data.horniness <= OrgasmThreshold)
        {
            cam.body.happiness += 45f;
            data.horniness = 0f;

            DickManager.Orgasm(cam);

            return true;
        }

        return false;
    }

    public static void EndMasturbation(PlayerCamera cam)
    {
        cam.body.armsAnimator.speed = 1f;

        cam.body.bodyAnimator.SetBool("exercising", false);
        exercising.SetValue(cam.body, false);

        RestoreSitAnim(cam);

        cam.body.bodyAnimator.Play("ExperimentSit");
        cam.body.armsAnimator.Play("ArmsSit");
        cam.body.standLerpTime = 0;
        cam.body.idleTime = 0;
    }
}
