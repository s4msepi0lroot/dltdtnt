using HarmonyLib;
using ScavPrototypeSexMod.Patches.Debug;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(WorldGeneration))]
internal static class DamageBlockPatch
{
    [HarmonyPatch("DamageBlock", typeof(Vector2Int), typeof(float), typeof(bool), typeof(bool), typeof(bool))]
    [HarmonyPrefix]
    static bool WorldGeneration_DamageBlock_Prefix(Vector2Int pos)
    {
        PatchLogging.LogPatchCall();
        if (WorldGeneration.world == null)
        {
            return false;
        }

        BlockInfo blockInfo = WorldGeneration.world.GetBlockInfo(WorldGeneration.world.GetBlock(pos));
        if (blockInfo == null || blockInfo.health <= 0f)
        {
            return false;
        }

        return true;
    }

    // TODO: UNCOMMENT WHEN ADDING TRADERS. COMMENTED FOR 0.1.0

    //[HarmonyPatch("GenerateLifePods")]
    //[HarmonyTranspiler]
    //static IEnumerable<CodeInstruction> Transpiler(IEnumerable<CodeInstruction> instructions)
    //{
    //    var original = SymbolExtensions.GetMethodInfo(() => Resources.Load(default(string)));

    //    var replacement = typeof(DamageBlockPatch).GetMethod(nameof(CustomLoad), BindingFlags.Static | BindingFlags.NonPublic);

    //    foreach (var instruction in instructions)
    //    {
    //        if (instruction.Calls(original))
    //        {
    //            ModdedLogger.Warning("Replacing Resources.Load");
    //            yield return new CodeInstruction(OpCodes.Call, replacement);
    //        }
    //        else
    //        {
    //            yield return instruction;
    //        }
    //    }
    //}

    // Interesting Findings...
    private static UnityEngine.Object CustomLoad(string path)
    {
        if (path.StartsWith("trader"))
        {
            ModdedLogger.Warning("Loaded Custom Trader Prefab!");
            return SharedState.bundle.LoadAsset<GameObject>("trader1");
        }

        return Resources.Load(path);
    }
}
