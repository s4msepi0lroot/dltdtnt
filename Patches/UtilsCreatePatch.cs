using HarmonyLib;
using ScavPrototypeSexMod.Registers;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(Utils))]
internal static class UtilsCreatePatch
{
    [HarmonyPatch(nameof(Utils.Create), typeof(string), typeof(Vector2), typeof(float))]
    [HarmonyPrefix]
    private static bool UtilsCreatePatch_Create_PosRot_Prefix(string id, Vector2 pos, float rot, ref GameObject __result)
    {
        if (string.IsNullOrWhiteSpace(id) || !ItemRegistry.IsRegistered(id))
        {
            return true; // let vanilla handle it
        }

        // If a real resource exists for this id, don't override.
        if (Resources.Load<GameObject>(id) != null)
        {
            return true;
        }

        __result = ItemRegistry.CreateInstance(id, pos, rot);
        return false;
    }

    [HarmonyPatch(nameof(Utils.Create), typeof(string), typeof(Transform))]
    [HarmonyPrefix]
    private static bool UtilsCreatePatch_Create_Parent_Prefix(string id, Transform trans, ref GameObject __result)
    {
        if (string.IsNullOrWhiteSpace(id) || !ItemRegistry.IsRegistered(id))
        {
            return true;
        }

        if (Resources.Load<GameObject>(id) != null)
        {
            return true;
        }

        __result = ItemRegistry.CreateInstance(id, trans);
        return false;
    }

    [HarmonyPatch(nameof(Utils.Create), typeof(string), typeof(Transform))]
    [HarmonyPrefix]
    private static bool UtilsCreatePatch_Create_Prefab_Prefix(string id, Transform trans, ref GameObject __result)
    {
        if (string.IsNullOrWhiteSpace(id) || !SharedState.customPrefabs.TryGetValue(id, out GameObject prefab))
        {
            return true;
        }
        
        __result = Object.Instantiate(prefab, trans);
        return false;
    }
}
