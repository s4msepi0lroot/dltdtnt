using HarmonyLib;
using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace ScavPrototypeSexMod.Managers;

public static class LimbManager
{
    internal static bool IsConstructing { get; private set; }

    public static void ResetRunState() => IsConstructing = false;

    public static void Initialize(Body body)
    {
        ModdedLogger.Info("LimbManager Initialized");
        RegisterLimbs(body);
    }

    // TODO: it always registers a _dick, must be fixed later on
    public static GameObject RegisterLimbs(Body body)
    {
        if (body == null)
        {
            ModdedLogger.Warning("Body is null!");
            return null;
        }

        if (body.limbs.Any(l => l.name == "Dick"))
        {
            ModdedLogger.Warning("Dick already exists, skipping.");
            return null;
        }

        Limb abdomen = body.limbs.FirstOrDefault(limb => limb != null && limb.gameObject.name == "DownTorso");

        if (abdomen == null)
        {
            ModdedLogger.Warning("Can't find required limbs!");
            return null;
        }

        // Make the limb GameObject
        GameObject limbGO = new("Dick");
        limbGO.transform.SetParent(body.transform, false);
        limbGO.layer = LayerMask.NameToLayer("Limb");
        //this works only in ragdoll mode while limabs are not simulated
        limbGO.transform.localPosition = new Vector3(1.175f, -0.132f, 0);
        limbGO.SetActive(false);

        var sr = limbGO.AddComponent<SpriteRenderer>();
        var col = limbGO.AddComponent<BoxCollider2D>();
        var rb = limbGO.AddComponent<Rigidbody2D>();
        var hj = limbGO.AddComponent<HingeJoint2D>();
        //var ps = limbGO.AddComponent<ParticleSystem>();

        // Setup SpriteRenderer
        sr.sprite = SharedState.savedDickType switch
        {
            DickTypes.Humanoid => SharedState.humanoidDick,
            DickTypes.Canine => SharedState.canineDick,
            _ => SharedState.humanoidDick,
        };
        sr.sortingLayerName = "Body";
        sr.sortingOrder = 25;
        sr.material = WorldGeneration.world.defaultMat;

        // BoxCollider
        col.size = sr.sprite.bounds.size;
        col.offset = sr.sprite.bounds.center;
        col.enabled = true;

        // Rigidbody2D
        rb.mass = 1f;
        rb.angularDrag = 0.05f;
        rb.gravityScale = 1f;
        rb.interpolation = RigidbodyInterpolation2D.Interpolate;
        rb.collisionDetectionMode = CollisionDetectionMode2D.Continuous;
        rb.sleepMode = RigidbodySleepMode2D.StartAwake;

        IsConstructing = true;

        // Add Limb component
        Limb limb = limbGO.AddComponent<Limb>();
        limb.body = body;
        limb.rb = rb;
        limb.joint = hj;
        limb.baseMass = rb.mass;
        limb.distanceToHeart = 2;

        // Health and bleeding defaults
        limb.skinHealth = 100f;
        limb.muscleHealth = 100f;

        body.limbs = body.limbs.AddToArray(limb);
        SyncLimbDependentArrays(body);

        // Animation bone: Body standing loop copies pose from animLimb each frame.
        if (!AttachAnimationLimb(limb, body))
        {
            ModdedLogger.Warning("AttachAnimationLimb retunred false, destroying dick");

            IsConstructing = false;
            UnityEngine.Object.Destroy(limbGO);
            return null;
        }

        // Configure HingeJoint
        JointAngleLimits2D lim = new()
        {
            min = -110f,
            max = 110.3229f
        };

        hj.tag = "Player";
        hj.name = "Dick";
        hj.connectedBody = body.limbs[2].rb;
        hj.autoConfigureConnectedAnchor = false;
        hj.anchor = new Vector2(-0.5666137f, 0.1536713f);
        hj.limits = lim;
        hj.useLimits = true;

        // Connect to abdomen
        Limb abdomenLimb = body.limbs[2];
        abdomenLimb.connectedLimbs = AppendLimb(abdomenLimb.connectedLimbs, limb);
        limb.connectedLimbs = AppendLimb(limb.connectedLimbs, abdomenLimb);

        IgnoreCollisionsWithOtherLimbs(limb, body);

        // Let Unity run Awake on activation. Manual Awake invoke also goes through
        // LimbAwakePatch, so it would be skipped while bodyIsConstructing is true.
        IsConstructing = false;
        limbGO.SetActive(true);

        ModdedLogger.Warning("Dick is connected to: " + limb.joint.connectedBody);
        ModdedLogger.Info("Custom limb attached to abdomen.");

        return limbGO;
    }

    // Live-swaps the world Dick limb sprite to match the current _dick type.
    // Uses the pre-built humanoidDick/canineDick sprites (pivot (0, 0.5), correct ppu),
    // so the base stays attached to the torso instead of sliding inside it.
    public static void UpdateDickSprite()
    {
        Body body = RunContext.Body;
        var data = RunContext.BodyData;
        if (body == null || data == null || body.limbs == null)
        {
            return;
        }

        Limb dick = body.limbs.FirstOrDefault(l => l != null && l.gameObject.name == "Dick");
        if (dick == null)
        {
            ModdedLogger.Warning("UpdateDickSprite: no Dick limb found.");
            return;
        }

        var sr = dick.GetComponent<SpriteRenderer>();
        if (sr == null)
        {
            return;
        }

        sr.sprite = data.CurrentDick switch
        {
            DickTypes.Humanoid => SharedState.humanoidDick,
            DickTypes.Canine => SharedState.canineDick,
            _ => SharedState.humanoidDick,
        };

        var col = dick.GetComponent<BoxCollider2D>();
        if (col != null && sr.sprite != null)
        {
            col.size = sr.sprite.bounds.size;
            col.offset = sr.sprite.bounds.center;
        }

        ModdedLogger.Info($"Dick sprite updated to {data.CurrentDick}.");
    }

    // Body.Awake ignores collisions between all limbs in body.limbs[] — Dick is added later in Start.
    private static void IgnoreCollisionsWithOtherLimbs(Limb newLimb, Body body)
    {
        Collider2D newCol = newLimb.GetComponent<Collider2D>();
        if (newCol == null)
        {
            return;
        }

        foreach (Limb other in body.limbs)
        {
            if (other == null || other == newLimb)
            {
                continue;
            }

            Collider2D otherCol = other.GetComponent<Collider2D>();

            if (otherCol != null)
            {
                Physics2D.IgnoreCollision(newCol, otherCol, true);
            }
        }
    }

    private static Limb[] AppendLimb(Limb[] limbs, Limb limb)
    {
        return limbs == null ? [limb] : limbs.AddToArray(limb);
    }

    // PlayerCamera.Start sizes showInfection from body.limbs before Dick is added.
    public static void SyncLimbDependentArrays(Body body)
    {
        if (body?.limbs == null || PlayerCamera.main == null)
        {
            return;
        }

        int limbCount = body.limbs.Length;
        bool[] infection = PlayerCamera.main.showInfection;

        if (infection != null && infection.Length >= limbCount)
        {
            return;
        }

        bool[] resized = new bool[limbCount];
        if (infection != null)
        {
            Array.Copy(infection, resized, infection.Length);
        }

        PlayerCamera.main.showInfection = resized;
    }

    // Body standing loop copies pose from animLimb each frame for every entry in body.limbs[].
    private static bool AttachAnimationLimb(Limb limb, Body body)
    {
        GameObject animBoneGO = new(limb.gameObject.name);
        animBoneGO.transform.SetParent(body.bodyAnimator.transform, false);
        animBoneGO.transform.localPosition = new(1.16f, -0.17f, 0f);
        animBoneGO.transform.localScale = Vector3.one;

        animBoneGO.AddComponent<SpriteRenderer>();
        animBoneGO.AddComponent<Animator>();

        limb.animLimb = animBoneGO.transform;

        return true;
    }
}
