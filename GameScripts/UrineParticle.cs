using ScavPrototypeSexMod.Managers;
using System.Collections.Generic;
using UnityEngine;

namespace ScavPrototypeSexMod.GameScripts;

public class UrineParticle : MonoBehaviour
{
    private readonly List<ParticleCollisionEvent> collisionEvents = [];
    private float pendingMl;

    private void OnParticleCollision(GameObject other)
    {
        ParticleSystem ps = GetComponent<ParticleSystem>();
        FluidManager fluids = RunContext.Fluids;
        if (ps == null || fluids == null || fluids.fluid == null || WorldGeneration.world == null)
        {
            return;
        }

        int numEvents = ps.GetCollisionEvents(other, collisionEvents);
        for (int i = 0; i < numEvents; i++)
        {
            DepositAt(collisionEvents[i].intersection);
        }
    }

    private void DepositAt(Vector3 worldPos)
    {
        Vector2Int blockPos = WorldGeneration.world.WorldToBlockPos(worldPos);

        if (WorldGeneration.world.GetBlock(blockPos) != 0)
        {
            return;
        }

        pendingMl += Fluids.Urine.MlPerCollision;

        FluidManager fluids = RunContext.Fluids;
        if (fluids == null || fluids.fluid == null)
        {
            return;
        }

        byte existing = fluids.GetLiquid(blockPos.x, blockPos.y);
        if (existing == 0)
        {
            fluids.SetLiquid(blockPos.x, blockPos.y, Fluids.Urine.ID);
        }

        while (pendingMl >= Fluids.Urine.MlPerTile)
        {
            pendingMl -= Fluids.Urine.MlPerTile;

            int spread = Mathf.Clamp(Mathf.RoundToInt(Fluids.Urine.MlPerTile / 100f), 1, 12);
            fluids.StartFill(blockPos, Fluids.Urine.ID, spread);
        }
    }
}
