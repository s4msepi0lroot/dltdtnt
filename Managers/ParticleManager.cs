using ScavPrototypeSexMod.GameScripts;
using ScavPrototypeSexMod.Managers.Reproduction;
using System.Collections;
using UnityEngine;

namespace ScavPrototypeSexMod.Managers;

internal static class ParticleManager
{
    private const float GameSpritePpu = 8f;

    private static readonly Sprite CumSprite =
        FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cummiesfloor.png", GameSpritePpu, FilterMode.Point, new Vector2(0.5f, 0.5f));

    private static readonly Sprite CumWallSprite =
        FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cummiesvom.png", GameSpritePpu, FilterMode.Point, new Vector2(0.5f, 0.5f));

    private static readonly Color CumColorMin = new(0.97f, 0.94f, 0.88f, 1f);
    private static readonly Color CumDecalColor = new(1f, 0.97f, 0.92f, 1f);

    private static GameObject _groundDecalTemplate;
    private static GameObject _wallDecalTemplate;

    public static void Initialize()
    {
        EnsureDecalTemplates();

        if (CumSprite == null)
        {
            ModdedLogger.Warning("Cum ground sprite missing (Assets/cummiesfloor.png). Cum floor decals will not render.");
        }

        if (CumWallSprite == null)
        {
            ModdedLogger.Warning("Cum wall sprite missing (Assets/cummiesvom.png). Cum wall decals will not render.");
        }

        ModdedLogger.Info("ParticleManager Initialized");
    }

    private static void EnsureDecalTemplates()
    {
        if (_groundDecalTemplate == null)
        {
            _groundDecalTemplate = CreateVanillaDecalTemplate("Special/blockblood", "CumGroundDecal", CumSprite, 5000);
            Object.DontDestroyOnLoad(_groundDecalTemplate);
        }

        if (_wallDecalTemplate == null)
        {
            _wallDecalTemplate = CreateVanillaDecalTemplate("wallblood", "CumWallDecal", CumWallSprite, -9000);
            Object.DontDestroyOnLoad(_wallDecalTemplate);
        }
    }

    #region Urine

    public static IEnumerator UrinateSpray(PlayerCamera cam, float sprayDuration, float settleDuration = 2f)
    {
        if (cam?.body == null)
            yield break;

        Limb dick = DickManager.GetDickLimb(cam.body);
        if (dick == null)
        {
            ModdedLogger.Warning("No Dick limb found for urine particles.");
            yield break;
        }

        GameObject emitterGo = UrineParticleSystemFactory.CreateEmitter(dick.transform, GetDickTipLocalPosition(dick));
        if (emitterGo == null)
        {
            yield break;
        }

        ParticleSystem ps = emitterGo.GetComponent<ParticleSystem>();
        if (ps == null)
        {
            Object.Destroy(emitterGo);
            yield break;
        }

        ParticleSystem.EmissionModule emission = ps.emission;
        emission.rateOverTime = 28f;

        float elapsed = 0f;
        float drainPerSecond = RunContext.BodyData.bladderAmount / sprayDuration; // 10
        while (elapsed < sprayDuration)
        {
            if (cam.body.rb.velocity.magnitude > 1.5f || !cam.body.standing)
            {
                RunContext.BodyData.bladderAmount = 0f;
                break;
            }

            cam.body.thirst = Mathf.Max(0f, cam.body.thirst - Time.deltaTime * 1f);
            RunContext.BodyData.bladderAmount = Mathf.Max(0f, RunContext.BodyData.bladderAmount - drainPerSecond * Time.deltaTime);
            Plugin.Log.LogWarning($"Bladder is {RunContext.BodyData.bladderAmount} & spray duration is {sprayDuration}");
            elapsed += Time.deltaTime;
            yield return null;
        }

        emission.rateOverTime = 0f;
        yield return new WaitForSeconds(settleDuration);
        Object.Destroy(emitterGo);
    }

    #endregion Urine

    #region Cum

    public static IEnumerator OrgasmSpray(PlayerCamera cam, float sprayDuration = 2.5f, float settleDuration = 3f)
    {
        if (cam?.body == null)
            yield break;

        Limb dick = DickManager.GetDickLimb(cam.body);
        if (dick == null)
        {
            ModdedLogger.Warning("No Dick limb found for cum particles.");
            yield break;
        }

        EnsureDecalTemplates();

        GameObject emitterGo = CumParticleSystemFactory.CreateEmitter(
            dick.transform,
            GetDickTipLocalPosition(dick),
            _groundDecalTemplate,
            _wallDecalTemplate,
            CumSprite,
            CumColorMin,
            CumDecalColor);

        if (emitterGo == null)
            yield break;

        ParticleSystem ps = emitterGo.GetComponent<ParticleSystem>();
        ParticleSystem.EmissionModule emission = ps.emission;
        ParticleSystem.MainModule main = ps.main;

        main.startLifetime = new ParticleSystem.MinMaxCurve(0.05f, 1.2f);

        const float decayStrength = 0.8f;
        float count = 30f;
        float speedMin = 10f;
        float speedMax = 30f;
        int cumCount = Mathf.Max(2, Mathf.RoundToInt(sprayDuration));

        float[] gaps = new float[cumCount];
        for (int i = 0; i < cumCount; i++)
        {
            float t = i / (float)cumCount;
            gaps[i] = Mathf.Lerp(0.3f, 2f, t);
        }

        for (int i = 0; i < cumCount; i++)
        {
            emitterGo.transform.localRotation = Quaternion.Euler(0f, cam.body.isRight ? 0f : 180f, 0f);
            main.startSpeed = new ParticleSystem.MinMaxCurve(speedMin, speedMax);
            ps.Emit(Mathf.RoundToInt(count));
            OrgasmShake(cam.body);

            count *= decayStrength;
            speedMin *= decayStrength;
            speedMax *= decayStrength;

            yield return new WaitForSeconds(gaps[i]);

        }

        main.startSpeed = new ParticleSystem.MinMaxCurve(1f, 5f);
        emission.rateOverTime = 20f;
        yield return new WaitForSeconds(1f);
        emission.rateOverTime = 0f;

        yield return new WaitForSeconds(settleDuration);
        Object.Destroy(emitterGo);
    }

    static void OrgasmShake(Body body)
    {
        const float rotateAmount = 6f;
        float sign = body.isRight ? 1f : -1f;
        Vector2 dir = body.transform.right * sign;

        body.attackRot -= rotateAmount * sign;
        body.visualBodyOffset += dir * (rotateAmount * 0.03f);
        body.miscShakeIntensity = 1;
        body.eyeCloseTime = 0.5f;
    }

    #endregion Cum

    private static GameObject CreateVanillaDecalTemplate(string resourcePath, string templateName, Sprite sprite, int sortingOrder)
    {
        GameObject prefab = Resources.Load<GameObject>(resourcePath);
        GameObject template;

        if (prefab != null)
        {
            template = Object.Instantiate(prefab);
            template.name = templateName;
            template.SetActive(false);

            SpriteRenderer sr = template.GetComponent<SpriteRenderer>();
            if (sprite != null)
            {
                sr.sprite = sprite;
            }

            sr.color = CumDecalColor;
            sr.sortingOrder = sortingOrder;
            return template;
        }

        template = new GameObject(templateName);
        template.SetActive(false);

        SpriteRenderer fallback = template.AddComponent<SpriteRenderer>();
        if (sprite != null)
        {
            fallback.sprite = sprite;
        }

        fallback.color = CumDecalColor;
        fallback.sortingOrder = sortingOrder;
        return template;
    }

    private static Vector3 GetDickTipLocalPosition(Limb dick)
    {
        if (dick == null)
        {
            return new Vector3(0.42f, 0.04f, 0f);
        }

        SpriteRenderer sr = dick.GetComponent<SpriteRenderer>();
        if (sr == null || sr.sprite == null)
        {
            return new Vector3(0.42f, 0.04f, 0f);
        }

        Bounds bounds = sr.sprite.bounds;
        const float tipInset = 0.03f;

        if (sr.flipX)
        {
            return new Vector3(bounds.min.x + tipInset, bounds.center.y, 0f);
        }

        return new Vector3(bounds.max.x - tipInset, bounds.center.y, 0f);
    }
}
