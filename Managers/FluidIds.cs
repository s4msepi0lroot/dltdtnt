using UnityEngine;

namespace ScavPrototypeSexMod.Managers;

internal record class FluidRecord
{
    public byte ID { get; init; }

    public float MlPerCollision { get; init; }

    public float MlPerTile { get; init; }

    public Color TileColor { get; init; }

    public Color StreamColor { get; init; }

    public Color FluidColor { get; init; } 
}

internal static class Fluids
{
    public static readonly FluidRecord Urine = new()
    {
        ID = 25,
        MlPerCollision = 120f,
        MlPerTile = 200f,
        TileColor = new Color(0.50f, 0.40f, 0.07f, 0.24f),
        StreamColor = new Color(0.58f, 0.46f, 0.08f, 0.45f),
        FluidColor = new Color(0.50f, 0.40f, 0.07f, 0.88f)
    };

    public static readonly FluidRecord Cum = new()
    {
        ID = 26,
        StreamColor = new Color(0.50f, 0.40f, 0.07f, 0.24f),
    };

    // TODO: FIX COLOR
    public static readonly FluidRecord PreCum = new()
    {
        ID = 27,
        StreamColor = new Color(0.50f, 0.40f, 0.07f, 0.24f),
    };

    // TODO: FIX COLOR
    public static readonly FluidRecord VaginalFluid = new()
    {
        ID = 28,
        StreamColor = new Color(0.50f, 0.40f, 0.07f, 0.24f),
    };
}
