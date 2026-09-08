using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace ScavPrototypeSexMod.Registers;

/// <summary>
/// Self-contained item registration
///
/// It replicates the two things the game needs for a modded item:
///   1. A data entry in <see cref="Item.GlobalItems"/> (stats + behaviour), which also feeds
///      <see cref="ItemLootPool"/> so the item drops / shows up at traders.
///   2. A world prefab. Vanilla spawns items with <c>Utils.Create(id)</c> -> <c>Resources.Load(id)</c>,
///      and custom items have no such Resource. We build a runtime template by cloning a vanilla
///      base prefab (e.g. "bandage" / "waterbottle") and swapping in our sprite + collider.
///      The <c>Utils.Create</c> patch calls <see cref="CreateInstance"/> when the id is ours.
/// </summary>
public static class ItemRegistry
{
    public static IEnumerable<string> RegisteredIds => Icons.Keys;

    // id -> icon/world sprite. Also doubles as the "is this one of ours?" set.
    private static readonly Dictionary<string, Sprite> Icons =
        new(StringComparer.OrdinalIgnoreCase);

    // id -> inactive template GameObject (kept across scene loads).
    private static readonly Dictionary<string, GameObject> Templates =
        new(StringComparer.OrdinalIgnoreCase);

    private static readonly List<Vector2> PhysicsShapeBuffer = [];

    public static bool IsRegistered(string id) =>
        !string.IsNullOrEmpty(id) && Icons.ContainsKey(id);

    public static Sprite GetIcon(string id) =>
        id != null && Icons.TryGetValue(id, out Sprite sprite) ? sprite : null;

    public static GameObject ResolvePrefab(string id)
    {
        if (string.IsNullOrWhiteSpace(id))
        {
            return null;
        }

        GameObject vanillaPrefab = Resources.Load<GameObject>(id);
        return vanillaPrefab != null
            ? vanillaPrefab
            : IsRegistered(id) ? GetOrCreateTemplate(id) : null;
    }

    public static void Register(string id, ItemInfo info, Sprite icon)
    {
        if (string.IsNullOrWhiteSpace(id) || info == null)
        {
            ModdedLogger.Warning("ModItemRegistry: ignored registration with empty id/info.");
            return;
        }

        if (Item.GlobalItems == null)
        {
            ModdedLogger.Error($"ModItemRegistry: GlobalItems is null while registering '{id}'. " +
                                "Register from an Item.SetupItems postfix.");
            return;
        }

        // Mirror the per-item setup the vanilla SetupItems loop does (it already ran before our
        // postfix, so it never touched our items).
        info.tags ??= string.Empty;
        info.SetTags();
        if (info.decayMinutes > 0f)
        {
            info.rotSpeed = 1.666f / info.decayMinutes;
        }

        Item.GlobalItems[id] = info;
        Icons[id] = icon;

        // Drop any stale template so a re-registration (e.g. new random condom color per run)
        // rebuilds it with the new sprite.
        if (Templates.TryGetValue(id, out GameObject old))
        {
            if (old != null)
            {
                UnityEngine.Object.Destroy(old);
            }

            Templates.Remove(id);
        }
    }

    public static void RefreshLootPool()
    {
        if (Item.GlobalItems != null)
        {
            ItemLootPool.InitializePool();
        }
    }

    public static GameObject CreateInstance(string id, Vector2 pos, float rot)
    {
        GameObject template = GetOrCreateTemplate(id);
        if (template == null)
        {
            return null;
        }

        GameObject instance = UnityEngine.Object.Instantiate(
            template, pos, Quaternion.Euler(0f, 0f, rot));
        instance.name = id;
        instance.SetActive(true);

        return instance;
    }

    public static GameObject CreateInstance(string id, Transform parent)
    {
        GameObject template = GetOrCreateTemplate(id);
        if (template == null)
        {
            return null;
        }

        GameObject instance = UnityEngine.Object.Instantiate(template, parent);
        instance.name = id;
        instance.SetActive(true);

        return instance;
    }

    private static GameObject GetOrCreateTemplate(string id)
    {
        if (string.IsNullOrEmpty(id))
        {
            return null;
        }

        if (Templates.TryGetValue(id, out GameObject cached) && cached != null)
        {
            return cached;
        }

        if (Item.GlobalItems == null || !Item.GlobalItems.TryGetValue(id, out ItemInfo info))
        {
            return null;
        }

        string baseId = ChooseBaseId(info);
        GameObject basePrefab = Resources.Load<GameObject>(baseId);
        if (basePrefab == null)
        {
            ModdedLogger.Error($"ModItemRegistry: base prefab '{baseId}' not found for '{id}'.");
            return null;
        }

        GameObject template = UnityEngine.Object.Instantiate(basePrefab);
        template.SetActive(false);
        template.name = id;
        UnityEngine.Object.DontDestroyOnLoad(template);

        Item item = template.GetComponent<Item>();
        if (item != null)
        {
            // Stats are resolved via Item.Stats => GlobalItems[id], so the id is all that's needed.
            item.id = id;
        }

        // The base waterbottle's Awake already populated its stack before we changed item.id.
        // Replace that vanilla water with this registered item's declared default liquid.
        if (info is LiquidItemInfo { defaultContents: not null } liquidInfo &&
            template.TryGetComponent(out WaterContainerItem waterContainer))
        {
            waterContainer.stack = liquidInfo.defaultContents
                .Where(stack => stack != null)
                .Select(stack => new LiquidStack(stack.liquidId, stack.amount))
                .ToList();
        }

        SpriteRenderer sr = template.GetComponent<SpriteRenderer>();
        Sprite icon = GetIcon(id);
        if (sr != null && icon != null)
        {
            sr.sprite = icon;
            RebuildCollider(template, icon);
        }

        Templates[id] = template;
        return template;
    }

    // Pick a vanilla prefab whose components match what the item needs.
    private static string ChooseBaseId(ItemInfo info)
    {
        if (info is LiquidItemInfo liquid &&
            (liquid.capacity > 0f || (liquid.defaultContents != null && liquid.defaultContents.Count > 0)))
        {
            // Has a WaterContainerItem + LiquidFill child renderer.
            return "waterbottle";
        }

        // Generic small pickup: SpriteRenderer + Item + Rigidbody2D + Collider2D.
        return "bandage";
    }

    // Fit the collider to the new sprite. We reuse/toggle colliders instead of destroying them so
    // the freshly-cloned template (which we instantiate in the same frame) never carries a leftover
    // collider from a pending Destroy.
    private static void RebuildCollider(GameObject obj, Sprite sprite)
    {
        Collider2D[] existing = obj.GetComponents<Collider2D>();
        int shapeCount = sprite.GetPhysicsShapeCount();

        if (shapeCount > 0)
        {
            PolygonCollider2D poly = obj.GetComponent<PolygonCollider2D>()
                ?? obj.AddComponent<PolygonCollider2D>();
            poly.pathCount = shapeCount;

            for (int i = 0; i < shapeCount; i++)
            {
                PhysicsShapeBuffer.Clear();
                sprite.GetPhysicsShape(i, PhysicsShapeBuffer);
                poly.SetPath(i, PhysicsShapeBuffer);
            }

            poly.offset = Vector2.zero;
            poly.enabled = true;
            DisableOthers(existing, poly);
        }
        else
        {
            BoxCollider2D box = obj.GetComponent<BoxCollider2D>() ?? obj.AddComponent<BoxCollider2D>();
            Bounds bounds = sprite.bounds;
            box.size = bounds.size;
            box.offset = bounds.center;
            box.enabled = true;
            DisableOthers(existing, box);
        }
    }

    private static void DisableOthers(Collider2D[] colliders, Collider2D keep)
    {
        foreach (Collider2D collider in colliders)
        {
            if (collider != null && collider != keep)
            {
                collider.enabled = false;
            }
        }
    }
}
