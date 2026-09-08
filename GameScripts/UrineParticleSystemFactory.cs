using ScavPrototypeSexMod;
using ScavPrototypeSexMod.Managers;
using UnityEngine;

namespace ScavPrototypeSexMod.GameScripts;

internal static class UrineParticleSystemFactory
{
    public static GameObject CreateEmitter(Transform parent, Vector3 localPosition)
    {
        if (parent == null)
        {
            ModdedLogger.Warning("Cannot create urine emitter: parent transform is null.");
            return null;
        }

        GameObject emitterGo = new("UrineParticle");
        emitterGo.transform.SetParent(parent, false);
        emitterGo.transform.localPosition = localPosition;
        emitterGo.transform.localRotation = Quaternion.identity;

        ParticleSystem ps = emitterGo.AddComponent<ParticleSystem>();
        ConfigureParticleSystem(ps);
        emitterGo.AddComponent<UrineParticle>();

        return emitterGo;
    }

    private static void ConfigureParticleSystem(ParticleSystem ps)
    {
        ParticleSystem.MainModule main = ps.main;
        main.startLifetime = 1f;
        main.startSpeed = 5f;
        main.startSize = 0.22f;
        main.gravityModifier = 2.5f;
        main.gravitySource = ParticleSystemGravitySource.Physics2D;
        main.simulationSpace = ParticleSystemSimulationSpace.World;
        main.simulationSpeed = 0.7f;
        main.startColor = Fluids.Urine.StreamColor;

        ParticleSystem.ShapeModule shape = ps.shape;
        shape.enabled = true;
        shape.shapeType = ParticleSystemShapeType.Box;
        shape.scale = new Vector3(0.08f, 0.08f, 0.08f);

        ParticleSystem.VelocityOverLifetimeModule velocity = ps.velocityOverLifetime;
        velocity.enabled = true;
        velocity.space = ParticleSystemSimulationSpace.World;

        ParticleSystem.RotationOverLifetimeModule rotation = ps.rotationOverLifetime;
        rotation.enabled = true;
        rotation.y = 90f;
        rotation.z = 180f;

        ParticleSystem.ColorOverLifetimeModule colorOverLifetime = ps.colorOverLifetime;
        colorOverLifetime.enabled = true;
        colorOverLifetime.color = new Gradient
        {
            colorKeys =
            [
                new GradientColorKey(Fluids.Urine.StreamColor, 0f),
                new GradientColorKey(Fluids.Urine.FluidColor, 1f)
            ],
            alphaKeys =
            [
                new GradientAlphaKey(0.85f, 0f),
                new GradientAlphaKey(0f, 1f)
            ]
        };

        ParticleSystem.CollisionModule collision = ps.collision;
        collision.enabled = true;
        collision.type = ParticleSystemCollisionType.World;
        collision.mode = ParticleSystemCollisionMode.Collision2D;
        collision.sendCollisionMessages = true;
        collision.collidesWith = GetLayerMask();

        ParticleSystem.EmissionModule emission = ps.emission;
        emission.enabled = true;
        emission.rateOverTime = 0f;

        ParticleSystemRenderer renderer = ps.GetComponent<ParticleSystemRenderer>();
        renderer.enabled = true;
        renderer.renderMode = ParticleSystemRenderMode.Billboard;
        renderer.lengthScale = 2f;
        renderer.rotateWithStretchDirection = true;
        renderer.material = FluidVisuals.CreateSquareParticleMaterial();
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
