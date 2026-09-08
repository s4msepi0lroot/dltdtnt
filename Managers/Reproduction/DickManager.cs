using System.Collections;
using ScavPrototypeSexMod.Data;
using UnityEngine;

namespace ScavPrototypeSexMod.Managers.Reproduction;

public static class DickManager
{
    private static Coroutine _horninessCoroutine;

    public static void Initialize()
    {
        ModdedLogger.Info("DickManager Initialized");
    }

    // For being too horny.
    public static void StartHornyRoutine(PlayerCamera cam)
    {
        if (_horninessCoroutine == null && cam != null)
        {
            _horninessCoroutine = cam.StartCoroutine(HornyRoutine(cam));
        }
    }

    public static void ResetRunState() => _horninessCoroutine = null;

    public static IEnumerator HornyRoutine(PlayerCamera cam)
    {
        ExtraBodyData data = RunContext.BodyData;
        if (cam?.body == null || data == null)
        {
            _horninessCoroutine = null;
            yield break;
        }

        cam.body.talker.TalkDelayed(0.5f, "F- fuck... I need some relief soon...", null, true, false);

        float penalty;

        while (data.horniness > 75f && cam != null && cam.body != null)
        {
            data.durHorny += 1f;

            float mitigation = 1f - (cam.body.skills.RESFrom10 * 0.05f);
            mitigation = Mathf.Clamp(mitigation, 0.2f, 1f);

            float h = data.horniness;
            penalty = 0f;

            if (data.durHorny >= 500f)
            {
                penalty = h * 0.015f;
            }
            else if (data.durHorny >= 300f)
            {
                penalty = h * 0.005f;
            }
            else if (data.durHorny >= 100f)
            {
                penalty = h * 0.001f;
            }

            cam.body.happiness -= penalty * mitigation;
            yield return new WaitForSeconds(1f);
        }

        _horninessCoroutine = null;
        if (data != null)
        {
            data.durHorny = 0f;
        }
    }

    public static IEnumerator Urinate(PlayerCamera cam)
    {
        if (!Plugin.kinkConfig["Watersports"].Value)
        {
            yield break;
        }

        if (cam.body.bodyAnimator.GetBool("exercising"))
        {
            yield break;
        }

        Limb dick = GetDickLimb(cam.body);
        if (dick == null)
        {
            cam.ToggleWoundView(true);
            cam.PlayUISound(PlayerCamera.UISoundType.Deny, 1f);
            cam.UseFailUnhappiness();
            yield break;
        }

        if (cam.body.thirst <= 5f)
        {
            cam.ToggleWoundView(true);
            cam.PlayUISound(PlayerCamera.UISoundType.Deny, 1f);
            cam.body.talker.Talk("I don't think I can...", null, true, false);
            yield break;
        }

        cam.ToggleWoundView(true);
        cam.body.talker.Talk("Ahh... relief...", null, true, false);

        float duration = 10f;
        RunContext.BodyData.bladderAmount = 100f;

        yield return ParticleManager.UrinateSpray(cam, duration);
    }

    // Do what is said here, orgasm during trader sex and also during masturbation.
    public static void Orgasm(PlayerCamera cam)
    {
        if (cam == null)
        {
            ModdedLogger.Error("Cam is null! Cannot proceed.");
            return;
        }

        cam.body.talker.Talk("F- fuuck!!~", null, true, false);

        if (cam.woundView.activeSelf)
        {
            cam.ToggleWoundView(true);
        }

        cam.StartCoroutine(ParticleManager.OrgasmSpray(cam));
    }

    public static Limb GetDickLimb(Body body)
    {
        if (body?.limbs == null)
        {
            return null;
        }

        foreach (Limb limb in body.limbs)
        {
            if (limb != null && limb.gameObject.name == "Dick")
            {
                return limb;
            }
        }

        return null;
    }
}
