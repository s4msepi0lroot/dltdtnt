using HarmonyLib;
using ScavPrototypeSexMod.Registers;
using UnityEngine;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(CorpseScript), "Start")]
internal static class CorpseScriptPatch
{
    [HarmonyPrefix]
    private static bool CorpseScriptPatch_Start_Prefix(CorpseScript __instance)
    {
        if (__instance.animalCorpse)
        {
            return false;
        }

        int spawnCount = 0;
        float random = Random.Range(0f, 1f);
        if (random > 0.5f)
        {
            spawnCount++;
        }
        if (random > 0.85f)
        {
            spawnCount++;
        }
        if (random > 0.95f)
        {
            spawnCount++;
        }

        for (int i = 0; i < spawnCount; i++)
        {
            string category = __instance.categories[Random.Range(0, __instance.categories.Length)];
            string[] ids = ItemLootPool.pool[category].ToArray();
            string id = ids[Random.Range(0, ids.Length)];

            Vector3 position = __instance.transform.position + new Vector3(Random.Range(-3f, 3f), 3f);
            float rotation = Random.Range(0f, 360f);

            GameObject obj = ItemRegistry.IsRegistered(id)
                ? ItemRegistry.CreateInstance(id, (Vector2)position, rotation)
                : Object.Instantiate(
                    Resources.Load(id),
                    position,
                    Quaternion.Euler(0f, 0f, rotation)) as GameObject;

            if (obj == null)
            {
                ModdedLogger.Error($"Corpse failed to spawn item '{id}'.");

                continue;
            }

            Item item = obj.GetComponent<Item>();
            item.SetCondition(Random.Range(0f, 1f));
            obj.GetComponent<SpriteRenderer>().sortingOrder =
                __instance.GetComponent<SpriteRenderer>().sortingOrder + 1;
        }

        return false;
    }
}
