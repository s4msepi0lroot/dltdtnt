using ScavPrototypeSexMod.Data;
using ScavPrototypeSexMod.GameScripts;
using UnityEngine;

namespace ScavPrototypeSexMod.Managers.Reproduction;

public static class HardnessManager
{
    private static Limb _dick;

    public static void Initialize()
    {
        _dick = null;

        ModdedLogger.Info("HardnessManager Initialized");
    }

    public static void Update(Body body, float dt)
    {
        ExtraBodyData data = RunContext.BodyData;
        if (body == null || data == null)
        {
            return;
        }

        if (MinigameBase.main.currentMinigame is MasturbationMinigame)
        {
            UpdateWhileMasturbation(body, dt);

            return;
        }

        float horniness01 = data.horniness * 0.01f;
        float hardness = data.hardness;

        float risingStrength = horniness01 * 8f;
        float decayStrength = (1f - horniness01) * 3f;
        float delta = (risingStrength - decayStrength) * dt;

        data.hardness = Mathf.Clamp(hardness + delta, 0f, 100f);

        ApplyScaleToDick(body);
    }

    public static void UpdateWhileMasturbation(Body body, float dt)
    {
        ExtraBodyData data = RunContext.BodyData;
        if (body == null || data == null)
        {
            return;
        }

        data.hardness = Mathf.Clamp(
            data.hardness + 25f * dt,
            0f,
            100f);

        ApplyScaleToDick(body);
    }

    public static void ApplyScaleToDick(Body body)
    {
        if (_dick is null)
        {
            _dick = DickManager.GetDickLimb(body);

            ModdedLogger.Debug($"ParticleManager.GetDickLimb returned: {_dick}");
        }

        ExtraBodyData data = RunContext.BodyData;
        if (_dick == null || data == null)
        {
            return;
        }

        float scale = Mathf.Lerp(0.35f, 1f, data.hardness * 0.01f);
        _dick.transform.localScale = new Vector3(scale, 1f, 1f);
    }
}
