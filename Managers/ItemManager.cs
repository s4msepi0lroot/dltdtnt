using ScavPrototypeSexMod.Data;
using ScavPrototypeSexMod.Registers;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;

namespace ScavPrototypeSexMod.Managers;

public static class ItemManager
{
    private static Coroutine _viagraCoroutine;

    public static void Initialize()
    {
        ModdedLogger.Info("ItemManager Initialized");
    }

    public static void RegisterItems()
    {
        // TODO: THINK ABOUT THE CRAFTING RECIPES!!!

        string[] condomcolors =
        [
            "redcondom",
            "silvercondom",
            "goldcondom",
            "pinkcondom"
        ];
        int rand = UnityEngine.Random.Range(0, condomcolors.Length);

        Sprite sextoySprite = FileLoader.LoadEmbeddedSprite("Assets.example.png", 100);
        ItemRegistry.Register("sextoy", new ItemInfo
        {
            fullName = Locale.GetItem("sextoy"),
            description = Locale.GetItem("sextoydsc"),
            category = "utility",
            weight = 1f,
            value = 50,
            usable = true,
            usableWithLMB = true,
            usableOnLimb = true,
            rec = new Recognition(2),
            useAction = TestItemUsed,
            useLimbAction = TestItemUsedLimb
        }, sextoySprite);

        Sprite vyagraSprite = FileLoader.LoadEmbeddedSprite("Assets.vyagra.png", 16);
        ItemRegistry.Register("vyagra", new ItemInfo
        {
            fullName = Locale.GetItem("vyagra"),
            description = Locale.GetItem("vyagradsc"),
            category = "drug",
            weight = 0.2f,
            value = 15,
            usable = true,
            usableWithLMB = true,
            scaleWeightWithCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(2),
            useAction = UseViagra,
        }, vyagraSprite);

        Sprite abortSprite = FileLoader.LoadEmbeddedSprite("Assets.vyagra.png", 16);
        ItemRegistry.Register("abortpills", new ItemInfo
        {
            fullName = Locale.GetItem("abort"),
            description = Locale.GetItem("abortdsc"),
            category = "drug",
            weight = 0.2f,
            value = 15,
            usable = true,
            usableWithLMB = true,
            scaleWeightWithCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(2),
            useAction = UseViagra,
        }, abortSprite);

        // Make each condom spawned have a different color.
        Sprite condomSprite = FileLoader.LoadEmbeddedSprite("Assets." + condomcolors[rand] + ".png", 32);
        ItemRegistry.Register("condom", new ItemInfo
        {
            fullName = Locale.GetItem("condom"),
            description = Locale.GetItem("condomdsc"),
            category = "utility",
            weight = 0.15f,
            value = 30,
            usable = true,
            usableWithLMB = true,
            destroyAtZeroCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(5),
            useAction = UseCondom,
        }, condomSprite);

        Sprite lubeSprite = FileLoader.LoadEmbeddedSprite("Assets.tubelube.png", 32);
        ItemRegistry.Register("lube", new LiquidItemInfo
        {
            fullName = Locale.GetItem("lube"),
            description = Locale.GetItem("lubedsc"),
            category = "water",
            weight = 0.75f,
            value = 10,
            usable = true,
            destroyAtZeroCondition = false,
            slotRotation = 0f,
            combineable = true,
            rec = new Recognition(5),
            useAction = UseLube,
            capacity = 500f,
            defaultContents = new List<LiquidStack>
            {
                new("lube", 500f)
            }
        }, lubeSprite);

        Sprite testosteroneSprite = FileLoader.LoadEmbeddedSprite("Assets.progesterone.jpg", 32);
        ItemRegistry.Register("testosterone", new LiquidItemInfo
        {
            fullName = Locale.GetItem("testosterone"),
            description = Locale.GetItem("testosteronedsc"),
            category = "water",
            autoFill = false,
            weight = 0.75f,
            value = 10,
            usable = false,
            destroyAtZeroCondition = false,
            slotRotation = 0f,
            combineable = true,
            rec = new Recognition(5),
            useLimbAction = delegate (Limb limb, Item item)
            {
                WaterContainerItem wat = item.GetComponent<WaterContainerItem>();
                MinigameBase.main.StartMinigame(new SyringeMinigame(delegate (float mult)
                {
                    wat.Inject(limb, mult * 95f);
                }, limb, Color.blue), item);
            },
            capacity = 500f,
            defaultContents = new List<LiquidStack>
            {
                new("progesterone", 500f)
            }
        }, testosteroneSprite);

        Sprite estrogenSprite = FileLoader.LoadEmbeddedSprite("Assets.estradiol.jpg", 32);
        ItemRegistry.Register("estrogen", new LiquidItemInfo
        {
            fullName = Locale.GetItem("estrogen"),
            description = Locale.GetItem("estrogendsc"),
            category = "water",
            autoFill = false,
            weight = 0.75f,
            value = 10,
            usable = false,
            destroyAtZeroCondition = false,
            slotRotation = 0f,
            rec = new Recognition(5),
            capacity = 500f,
            defaultContents = new List<LiquidStack>
            {
                new("estrogen", 500f)
            },
            useLimbAction = delegate (Limb limb, Item item)
            {
                WaterContainerItem wat = item.GetComponent<WaterContainerItem>();
                MinigameBase.main.StartMinigame(new SyringeMinigame(delegate (float mult)
                {
                    wat.Inject(limb, mult * 95f);
                }, limb, Color.red), item);
            },
        }, estrogenSprite);

        // Vaccine for HPV before you get it
        ItemRegistry.Register("gardasil9", new LiquidItemInfo
        {
            fullName = Locale.GetItem("estrogen"),
            description = Locale.GetItem("estrogendsc"),
            category = "water",
            autoFill = false,
            weight = 0.75f,
            value = 10,
            usable = false,
            destroyAtZeroCondition = false,
            slotRotation = 0f,
            rec = new Recognition(5),
            capacity = 500f,
            defaultContents = new List<LiquidStack>
            {
                new("estrogen", 500f)
            },
            useLimbAction = delegate (Limb limb, Item item)
            {
                WaterContainerItem wat = item.GetComponent<WaterContainerItem>();
                MinigameBase.main.StartMinigame(new SyringeMinigame(delegate (float mult)
                {
                    wat.Inject(limb, mult * 95f);
                }, limb, Color.cyan), item);
            },
        }, estrogenSprite);

        // Used to treat gonorrhea
        ItemRegistry.Register("ceftriaxone", new LiquidItemInfo
        {
            fullName = Locale.GetItem("estrogen"),
            description = Locale.GetItem("estrogendsc"),
            category = "water",
            autoFill = false,
            weight = 0.75f,
            value = 10,
            usable = false,
            destroyAtZeroCondition = false,
            slotRotation = 0f,
            rec = new Recognition(5),
            capacity = 500f,
            defaultContents = new List<LiquidStack>
            {
                new("estrogen", 500f)
            },
            useLimbAction = delegate (Limb limb, Item item)
            {
                WaterContainerItem wat = item.GetComponent<WaterContainerItem>();
                MinigameBase.main.StartMinigame(new SyringeMinigame(delegate (float mult)
                {
                    wat.Inject(limb, mult * 95f);
                }, limb, Color.cyan), item);
            },
        }, estrogenSprite);

        ItemRegistry.Register("analbeads", new ItemInfo
        {
            fullName = Locale.GetItem("condom"),
            description = Locale.GetItem("condomdsc"),
            category = "utility",
            weight = 0.15f,
            value = 30,
            usable = true,
            usableWithLMB = true,
            destroyAtZeroCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(5),
            useAction = UseCondom,
        }, sextoySprite);

        // Needs batteries
        ItemRegistry.Register("vibrator", new ItemInfo
        {
            fullName = Locale.GetItem("condom"),
            description = Locale.GetItem("condomdsc"),
            category = "utility",
            weight = 0.15f,
            value = 30,
            usable = true,
            usableWithLMB = true,
            destroyAtZeroCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(5),
            useAction = UseCondom,
        }, sextoySprite);

        ItemRegistry.Register("fleshlight", new ItemInfo
        {
            fullName = Locale.GetItem("condom"),
            description = Locale.GetItem("condomdsc"),
            category = "utility",
            weight = 0.15f,
            value = 30,
            usable = true,
            usableWithLMB = true,
            destroyAtZeroCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(5),
            useAction = UseCondom,
        }, sextoySprite);

        // MEDICINES!
        // Used to treat HIV
        ItemRegistry.Register("tenofoviralafenamide", new ItemInfo
        {
            fullName = Locale.GetItem("condom"),
            description = Locale.GetItem("condomdsc"),
            category = "utility",
            weight = 0.15f,
            value = 30,
            usable = true,
            usableWithLMB = true,
            destroyAtZeroCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(5),
            useAction = UseCondom,
        }, condomSprite);

        // Used to treat syphilis
        ItemRegistry.Register("benzathinebenzylpenicillin", new ItemInfo
        {
            fullName = Locale.GetItem("condom"),
            description = Locale.GetItem("condomdsc"),
            category = "utility",
            weight = 0.15f,
            value = 30,
            usable = true,
            usableWithLMB = true,
            destroyAtZeroCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(5),
            useAction = UseCondom,
        }, condomSprite);

        // Used to treat herpes
        ItemRegistry.Register("acyclovir", new ItemInfo
        {
            fullName = Locale.GetItem("condom"),
            description = Locale.GetItem("condomdsc"),
            category = "utility",
            weight = 0.15f,
            value = 30,
            usable = true,
            usableWithLMB = true,
            destroyAtZeroCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(5),
            useAction = UseCondom,
        }, condomSprite);

        ItemRegistry.Register("acyclovir", new ItemInfo
        {
            fullName = Locale.GetItem("condom"),
            description = Locale.GetItem("condomdsc"),
            category = "utility",
            weight = 0.15f,
            value = 30,
            usable = true,
            usableWithLMB = true,
            destroyAtZeroCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(5),
            useAction = UseCondom,
        }, condomSprite);

        // Used to treat scabies
        ItemRegistry.Register("ivermectin", new ItemInfo
        {
            fullName = Locale.GetItem("condom"),
            description = Locale.GetItem("condomdsc"),
            category = "utility",
            weight = 0.15f,
            value = 30,
            usable = true,
            usableWithLMB = true,
            destroyAtZeroCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(5),
            useAction = UseCondom,
        }, condomSprite);

        // Used to treat trichomoniasis
        ItemRegistry.Register("Metronidazole", new ItemInfo
        {
            fullName = Locale.GetItem("condom"),
            description = Locale.GetItem("condomdsc"),
            category = "utility",
            weight = 0.15f,
            value = 30,
            usable = true,
            usableWithLMB = true,
            destroyAtZeroCondition = true,
            slotRotation = 0f,
            combineable = true,
            qualities = new List<CraftingQuality>
            {
                new("rippable")
            },
            rec = new Recognition(5),
            useAction = UseCondom,
        }, condomSprite);

        ItemRegistry.RefreshLootPool();
    }

    public static void UseAbortPills(Body body, Item item)
    {
        item.condition -= 0.5f;

        var data = RunContext.BodyData;
        if (data != null)
            data.isPregnant = false;
    }

    // For the viagra
    public static void UseViagra(Body body, Item item)
    {
        int chance = UnityEngine.Random.Range(1, 250);
        item.condition -= 0.3f;

        if (_viagraCoroutine != null)
        {
            body.StopCoroutine(_viagraCoroutine);
        }

        // TODO: remake it with DoTimedOp
        _viagraCoroutine = body.StartCoroutine(ViagraRoutine(body, chance));
    }

    private static IEnumerator ViagraRoutine(Body body, int chance)
    {
        float duration = 60f;

        while (duration > 0f)
        {
            var data = RunContext.BodyData;
            if (body == null || data == null)
            {
                break;
            }

            body.bloodViscosity = Mathf.Max(body.bloodViscosity - 0.15f * Time.deltaTime, -25f);
            body.forcedSleepQuality = Body.SleepQuality.Bad;

            if (chance == 49)
            {
                body.sicknessAmount = Mathf.Min(body.sicknessAmount + Time.deltaTime * 0.15f, 25f);
                body.averagePain = Mathf.Min(body.averagePain + Time.deltaTime * 0.15f, 10f);
            }
            else if (chance == 2)
            {
                body.sicknessAmount = Mathf.Min(body.sicknessAmount + Time.deltaTime * 0.15f, 95f);
                if (body.sicknessAmount >= 75)
                {
                    body.vomiter.Vomit();
                    body.consciousness = Mathf.Max(body.consciousness - Time.deltaTime * 0.10f, 65f);
                    body.averagePain = Mathf.Min(body.averagePain + Time.deltaTime * 0.15f, 15f);
                }
            }

            if (ExtraBodyData.CurrentBodyStatus == Status.Male || ExtraBodyData.CurrentBodyStatus == Status.Intersex)
            {
                data.hardness = Mathf.Min(
                    data.hardness + Time.deltaTime * 25f,
                    100f);
                if (data.hardness > 95f)
                {
                    body.wetness = Mathf.Min(body.wetness + Time.deltaTime * 0.25f, 30f);
                    data.horniness = Mathf.Min(data.horniness + Time.deltaTime * 1f, 100f);
                }
            }
            else if (ExtraBodyData.CurrentBodyStatus == Status.Female)
            {
                body.wetness = Mathf.Min(body.wetness + Time.deltaTime * 0.15f, 30f);
            }
            else
            {
                body.talker.Talk("Why did I do that?...");
                body.wetness = Mathf.Min(body.wetness + Time.deltaTime * 0.15f, 15f);
                duration = 0f;
            }

            duration -= Time.deltaTime;
            yield return null;
        }

        _viagraCoroutine = null;
    }

    public static void ResetRunState() => _viagraCoroutine = null;

    // For the condom
    // TODO: Dropping the condom after activating the coroutine destroys the condom lol
    public static void UseCondom(Body body, Item item)
    {
        // Put protection on the player!
        if (ExtraBodyData.CurrentBodyStatus == Status.Male || ExtraBodyData.CurrentBodyStatus == Status.Intersex)
        {
            item.condition -= 1f;
            var data = RunContext.BodyData;
            if (data != null)
                data.wearingCondom = true;
        }
        else if (ExtraBodyData.CurrentBodyStatus == Status.Female)
        {
            body.talker.Talk("I can't use this... but maybe someone else can?", null, true, false);
        }
        else
        {
            body.talker.Talk("I don't have any use for this. Maybe I should sell it.", null, true, false);
        }
    }

    public static void UseLube(Body body, Item item)
    {
        item.GetComponent<WaterContainerItem>().Drink(body, 10f, "drink");
        ModdedLogger.Warning("Yummers....");
    }

    public static void ApplyLube(ref float ml, Body body)
    {
        ml -= 10f;

        ModdedLogger.Warning("DO the thing here.");

        body.talker.TalkDelayed(0.5f, "That tasted terrible...", null, true, false);
    }

    // For the sex toy
    public static void TestItemUsed(Body body, Item item)
    {
        ModdedLogger.Info("Yeah you used me, now what? lol");
        // Not the way to GOlastStandReplacement here.
        SharedState.stdTypes["Syphilis"] = true;
    }

    public static void TestItemUsedLimb(Limb limb, Item item)
    {
        ModdedLogger.Info("Applied to limb: " + limb.name);
    }
}
