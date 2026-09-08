using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Reflection.Emit;
using HarmonyLib;
using ScavPrototypeSexMod.Registers;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(PlayerCamera), nameof(PlayerCamera.RefreshTraderInventories))]
internal static class TraderInventoryPatch
{
    private static readonly MethodInfo ResourcesLoad =
        typeof(Resources)
            .GetMethods(BindingFlags.Public | BindingFlags.Static)
            .Single(method =>
                method.Name == nameof(Resources.Load) &&
                !method.IsGenericMethod &&
                method.ReturnType == typeof(Object) &&
                method.GetParameters() is [{ ParameterType: var parameterType }] &&
                parameterType == typeof(string));

    private static readonly MethodInfo ResolveItemPrefab =
        AccessTools.Method(typeof(ItemRegistry), nameof(ItemRegistry.ResolvePrefab));

    [HarmonyTranspiler]
    private static IEnumerable<CodeInstruction> RefreshTraderInventories_Transpiler(
        IEnumerable<CodeInstruction> instructions)
    {
        foreach (CodeInstruction instruction in instructions)
        {
            if (instruction.Calls(ResourcesLoad))
            {
                instruction.opcode = OpCodes.Call;
                instruction.operand = ResolveItemPrefab;
            }

            yield return instruction;
        }
    }
}
