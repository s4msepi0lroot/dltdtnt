using HarmonyLib;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Patches.Debug;
using System;
using System.Collections.Generic;
using System.Reflection;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(FluidManager))]
internal static class FluidManagerPatches
{
    private const float UrineTileSizeScale = 0.84f;

    private static ParticleSystem _urineOverlaySystem;
    private static Material _urineOverlayMaterial;

    public static void ResetRunState()
    {
        if (_urineOverlayMaterial != null)
        {
            UnityEngine.Object.Destroy(_urineOverlayMaterial);
        }

        _urineOverlaySystem = null;
        _urineOverlayMaterial = null;
    }

    // RegisterUrineFluid
    [HarmonyPatch("Start")]
    [HarmonyPostfix]
    public static void FluidManager_Start_Postfix(FluidManager __instance)
    {
        PatchLogging.LogPatchCall();

        FluidManager.WorldFluidToLiquidID[Fluids.Urine.ID] = "urine";

        RegisterUrineLiquidType();
        EnsureLiquidColor(__instance, Fluids.Urine.ID, Fluids.Urine.FluidColor);
        EnsureLiquidParticleSystem(__instance, Fluids.Urine.ID);
        EnsureUrineOverlay(__instance);

        ModdedLogger.Info($"Urine fluid registered as tile id {Fluids.Urine.ID}");
    }

    [HarmonyPatch("DrinkLiquid")]
    [HarmonyPrefix]
    private static bool FluidManager_DrinkLiquid_Prefix(
        FluidManager __instance,
        Vector2Int pos,
        Body body)
    {
        if (__instance?.fluid != null && __instance.GetLiquid(pos.x, pos.y) == Fluids.Urine.ID)
        {
            __instance.SetLiquid(pos.x, pos.y, 0);

            Liquids.Registry["urine"].onDrink(200f, body);
            Sound.Play("drink", body.transform.position);

            return false; // ваниль обрабатывает свои жидкости
        }

        return true;
    }

    [HarmonyPatch("RenderFluids")]
    [HarmonyPostfix]
    public static void FluidManager_RenderFluids_Postfix(FluidManager __instance)
    {
        PatchLogging.LogPatchCall();
        if (_urineOverlaySystem == null)
        {
            return;
        }

        FieldInfo particlesField = AccessTools.Field(typeof(FluidManager), "liquidParticles");
        if (particlesField?.GetValue(__instance) is not List<ParticleSystem> particleSystems)
        {
            return;
        }

        int urineIndex = Fluids.Urine.ID - 1;
        if (particleSystems.Count <= urineIndex)
        {
            return;
        }

        ParticleSystem source = particleSystems[urineIndex];
        int count = source.particleCount;
        if (count <= 0)
        {
            _urineOverlaySystem.Clear();
            return;
        }

        ParticleSystem.Particle[] particles = new ParticleSystem.Particle[count];
        source.GetParticles(particles);

        Color32 tileColor = Fluids.Urine.TileColor;
        for (int i = 0; i < count; i++)
        {
            particles[i].startColor = tileColor;
            particles[i].rotation = 0f;

            Vector3 size = particles[i].startSize3D;
            particles[i].startSize3D = new Vector3(
                size.x * UrineTileSizeScale,
                size.y * UrineTileSizeScale,
                size.z);
        }

        source.Clear();
        _urineOverlaySystem.SetParticles(particles, count);
    }

    [HarmonyPatch("LiquidName")]
    [HarmonyPostfix]
    public static void FluidManager_LiquidName_Postfix(FluidManager __instance, Vector2Int pos, ref (string, string) __result)
    {
        if (__instance == null || __instance.fluid == null ||
            __instance.GetLiquid(pos.x, pos.y) != Fluids.Urine.ID)
        {
            return;
        }

        __result = (
            FileLoader.GetLocale("other", "urine"),
            FileLoader.GetLocale("other", "urinedsc")
        );
    }

    [HarmonyPatch("LiquidColor")]
    [HarmonyPrefix]
    public static bool FluidManager_LiquidColor_Prefix(FluidManager __instance, Vector2Int pos, ref Color __result)
    {
        if (__instance == null || __instance.fluid == null ||
            __instance.GetLiquid(pos.x, pos.y) != Fluids.Urine.ID)
        {
            return true;
        }

        __result = Fluids.Urine.FluidColor;

        return false;
    }

    [HarmonyPatch("WaterInfo")]
    [HarmonyPrefix]
    public static bool FluidManager_WaterInfo_Prefix(FluidManager __instance, Vector2Int pos, ref (float buoyancy, float drag, int type) __result)
    {
        if (__instance == null || __instance.fluid == null ||
            __instance.GetLiquid(pos.x, pos.y) != Fluids.Urine.ID)
        {
            return true;
        }

        __result = (0.6f, 0.915f, Fluids.Urine.ID);

        return false;
    }

    private static void RegisterUrineLiquidType()
    {
        if (Liquids.Registry.ContainsKey("urine"))
        {
            return;
        }

        Liquids.Registry["urine"] = new LiquidType
        {
            localeName = "urine",
            color = Fluids.Urine.FluidColor,
            valuePerLiter = 0f,
            healthUsable = false,
            onDrink = (ml, body) =>
            {
                body.sicknessAmount += ml * 0.05f;
                body.happiness -= ml * 0.002f;
                body.talker.EatBad();
            }
        };
    }

    private static void EnsureLiquidColor(FluidManager fm, byte fluidId, Color color)
    {
        if (fm.liquidColors == null)
        {
            return;
        }

        if (fm.liquidColors.Length <= fluidId)
        {
            var expanded = new Color[fluidId + 1];
            Array.Copy(fm.liquidColors, expanded, fm.liquidColors.Length);
            fm.liquidColors = expanded;
        }

        fm.liquidColors[fluidId] = color;
    }

    private static void EnsureLiquidParticleSystem(FluidManager fm, byte fluidId)
    {
        FieldInfo particlesField = AccessTools.Field(typeof(FluidManager), "liquidParticles");
        if (particlesField?.GetValue(fm) is not List<ParticleSystem> particleSystems)
        {
            return;
        }

        if (fm.LiquidParticlePrefabs == null || fm.LiquidParticlePrefabs.Count == 0)
        {
            return;
        }

        int requiredSystems = fluidId;
        GameObject waterPrefab = fm.LiquidParticlePrefabs[0];

        while (particleSystems.Count < requiredSystems)
        {
            GameObject clone = UnityEngine.Object.Instantiate(waterPrefab, fm.transform);
            clone.name = "UrineLiquidParticle";

            clone.GetComponent<ParticleSystemRenderer>().enabled = false;
            particleSystems.Add(clone.GetComponent<ParticleSystem>());
            fm.LiquidParticlePrefabs.Add(clone);
        }
    }

    private static void EnsureUrineOverlay(FluidManager fm)
    {
        if (_urineOverlaySystem != null)
        {
            return;
        }

        FieldInfo particlesField = AccessTools.Field(typeof(FluidManager), "liquidParticles");
        if (particlesField?.GetValue(fm) is not List<ParticleSystem> particleSystems || particleSystems.Count == 0)
        {
            return;
        }

        ParticleSystemRenderer waterRenderer = particleSystems[0].GetComponent<ParticleSystemRenderer>();

        GameObject overlayGo = new("UrineTileOverlay");
        overlayGo.transform.SetParent(fm.transform, false);

        _urineOverlaySystem = overlayGo.AddComponent<ParticleSystem>();
        ParticleSystemRenderer overlayRenderer = overlayGo.GetComponent<ParticleSystemRenderer>();

        ParticleSystem.MainModule main = _urineOverlaySystem.main;
        main.loop = false;
        main.playOnAwake = false;
        main.maxParticles = 10000;
        main.simulationSpace = ParticleSystemSimulationSpace.World;
        main.startLifetime = 999f;
        main.startSpeed = 0f;
        main.scalingMode = ParticleSystemScalingMode.Shape;

        ParticleSystem.EmissionModule emission = _urineOverlaySystem.emission;
        emission.enabled = false;

        _urineOverlayMaterial = FluidVisuals.CreateSquareParticleMaterial();
        overlayRenderer.material = _urineOverlayMaterial;
        overlayRenderer.renderMode = ParticleSystemRenderMode.Billboard;

        if (waterRenderer != null)
        {
            overlayRenderer.renderMode = waterRenderer.renderMode;
            overlayRenderer.lengthScale = waterRenderer.lengthScale;
            overlayRenderer.sortingLayerID = waterRenderer.sortingLayerID;
            overlayRenderer.sortingOrder = waterRenderer.sortingOrder;
        }
    }
}
