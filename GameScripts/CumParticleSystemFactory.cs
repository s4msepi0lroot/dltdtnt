using ScavPrototypeSexMod.Managers;
using UnityEngine;

namespace ScavPrototypeSexMod.GameScripts;

internal static class CumParticleSystemFactory
{
    public static GameObject CreateEmitter(
        Transform parent,
        Vector3 localPosition,
        GameObject groundDecal,
        GameObject wallDecal,
        Sprite streamSprite,
        Color streamColor,
        Color fluidColor)
    {
        if (parent == null)
        {
            ModdedLogger.Warning("Cannot create cum emitter: parent transform is null.");
            return null;
        }

        if (groundDecal == null || wallDecal == null)
        {
            ModdedLogger.Warning(
                $"Cannot create cum emitter: decal templates are missing (ground={groundDecal != null}, wall={wallDecal != null}).");
            return null;
        }

        GameObject emitterGo = new("CumParticle");
        emitterGo.transform.SetParent(parent, false);
        emitterGo.transform.localPosition = localPosition;
        emitterGo.transform.localRotation = Quaternion.identity;

        ParticleSystem ps = emitterGo.AddComponent<ParticleSystem>();
        ConfigureParticleSystem(ps, streamSprite, streamColor, fluidColor);

        CumParticle cum = emitterGo.AddComponent<CumParticle>();
        cum.Initialize(groundDecal, wallDecal);

        return emitterGo;
    }

    private static void ConfigureParticleSystem(ParticleSystem ps, Sprite streamSprite, Color streamColor, Color fluidColor)
    {
        ParticleSystem.MainModule main = ps.main;
        main.startLifetime = 1f;
        main.startSpeed = 5f;
        main.startSize = 0.22f;
        main.gravityModifier = 2.5f;
        main.gravitySource = ParticleSystemGravitySource.Physics2D;
        main.simulationSpace = ParticleSystemSimulationSpace.World;
        main.simulationSpeed = 0.7f;
        main.startColor = streamColor;
        main.maxParticles = 256;

        ParticleSystem.ShapeModule shape = ps.shape;
        shape.enabled = true;
        shape.shapeType = ParticleSystemShapeType.Cone;
        shape.scale = new Vector3(0.08f, 0.08f, 0.08f);
        shape.angle = 20f;
        shape.rotation = new Vector3(0f, 90f, 0f);

        ParticleSystem.VelocityOverLifetimeModule velocity = ps.velocityOverLifetime;
        velocity.enabled = true;
        velocity.space = ParticleSystemSimulationSpace.World;

        ParticleSystem.RotationOverLifetimeModule rotation = ps.rotationOverLifetime;
        rotation.enabled = true;

        ParticleSystem.ColorOverLifetimeModule colorOverLifetime = ps.colorOverLifetime;
        colorOverLifetime.enabled = true;
        colorOverLifetime.color = new Gradient
        {
            colorKeys =
            [
                new GradientColorKey(streamColor, 0f),
                new GradientColorKey(fluidColor, 1f)
            ],
            alphaKeys =
            [
                new GradientAlphaKey(0.95f, 0f),
                new GradientAlphaKey(0f, 1f)
            ]
        };

        ParticleSystem.CollisionModule collision = ps.collision;
        collision.enabled = true;
        collision.type = ParticleSystemCollisionType.World;
        collision.mode = ParticleSystemCollisionMode.Collision2D;
        collision.lifetimeLoss = 1f;
        collision.collidesWith = GetLayerMask();

        ParticleSystem.EmissionModule emission = ps.emission;
        emission.enabled = true;
        emission.rateOverTime = 0f;

        ParticleSystemRenderer renderer = ps.GetComponent<ParticleSystemRenderer>();
        renderer.enabled = true;
        renderer.renderMode = ParticleSystemRenderMode.Billboard;
        renderer.lengthScale = 1f;
        renderer.rotateWithStretchDirection = false;

        Material partMat = new(Shader.Find("Sprites/Default"));
        Texture2D texture = streamSprite != null ? streamSprite.texture : FluidVisuals.SquareParticleTexture;
        if (texture != null)
        {
            partMat.mainTexture = texture;
        }

        partMat.color = Color.white;
        renderer.material = partMat;
        renderer.sortingOrder = 5000;
    }

    private static LayerMask GetLayerMask()
    {
        int mask = Physics2D.AllLayers;
        int limbLayer = LayerMask.NameToLayer("Limb");
        if (limbLayer >= 0)
        {
            mask &= ~(1 << limbLayer);
        }

        int playerLayer = LayerMask.NameToLayer("Player");
        if (playerLayer >= 0)
        {
            mask &= ~(1 << playerLayer);
        }

        return mask;
    }
}
