using ScavPrototypeSexMod.Data;
using UnityEngine;

namespace ScavPrototypeSexMod;

/// <summary>
/// Resolves objects owned by the currently loaded run scene. Nothing here is cached: scene reloads
/// therefore cannot leave the mod holding a destroyed UnityEngine.Object from a previous run.
/// </summary>
internal static class RunContext
{
    public static PlayerCamera Camera => PlayerCamera.main;

    public static Body Body
    {
        get
        {
            PlayerCamera camera = Camera;
            return camera != null ? camera.body : null;
        }
    }

    public static ExtraBodyData BodyData
    {
        get
        {
            Body body = Body;
            return body != null ? body.GetAdditionalData() : null;
        }
    }

    public static FluidManager Fluids => FluidManager.main;

    public static WoundView WoundView => global::WoundView.view;

    public static TraderScript Trader
    {
        get
        {
            PlayerCamera camera = Camera;
            return camera != null ? camera.currentTrader : null;
        }
    }
}
