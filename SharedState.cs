using ScavPrototypeSexMod.Managers;
using System.Collections.Generic;
using UnityEngine;

namespace ScavPrototypeSexMod;

public enum Status
{
    Male,
    Female,
    Intersex,
    NonBinary
}

// Hemipenes = ?????
// Slits for sure. (Tapered, Spiked)
// knot option for every _dick type
// Instead of knot for humanoid, add foreskin option

public enum DickTypes
{
    Humanoid = 0,
    Canine = 1,
    Tapered = 2,
    Equine = 3,
    Barbed = 4,
    Spiked = 5
}

// Enums for top and bottom of the body
public enum BodyTypeTop
{
    Flat = 0,
    Breasts = 1,
    None = 2,
}

public enum BodyTypeBottom
{
    Pussy = 0,
    Dick = 1,
    Both = 2,
    None = 3,
}

struct SharedState
{
    // None, None for both bottom and top enums via default keyword
    public static (BodyTypeTop top, BodyTypeBottom bottom) savedBodyOptions = (BodyTypeTop.None, BodyTypeBottom.None);
    public static DickTypes savedDickType = DickTypes.Humanoid;

    // Implement this in settings menus on main menu and in run
    // Use sharedprefs to save the values. Just like how you did with gender.
    public static Dictionary<string, bool> kinkOptions = new()
    {
        { "Watersports", false },
        { "Rape", false },
        // With the animals in cas unk, technically.
        { "Teratophilia", false },
        { "STDs", false },
        { "Gore", false }
    };

    // Body variables
    public enum MoreWorkoutTypes
    {
        Masturbate
    }

    public static Dictionary<string, bool> stdTypes = new()
    {
        { "syphilis", false },
        { "hiv", false },
        { "hpv", false },
        { "herpes", false },
        { "gonorrhoea", false },
        { "scabies", false },
        { "trichomoniasis", false }
    };

    // Sprite replacements
    public static Sprite humanoidDick = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.humanoidcock.png", 9, pivot: new Vector2(0f, 0.5f));
    public static Sprite canineDick = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.caninecock.png", 10, pivot: new Vector2(0f, 0.5f));
    public static Sprite handBaseMaturbate = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.basearm.PNG", 8, pivot: new Vector2(0.5f, 0.5f));
    public static Sprite armFingersMasturbate = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.armfingers.PNG", 8, pivot: new Vector2(0.5f, 0.5f));

    // Cum animation
    public static Sprite cum1Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum1.png", 56, pivot: new Vector2(0f, 0.5f));
    public static Sprite cum2Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum2.png", 56, pivot: new Vector2(0f, 0.5f));
    public static Sprite cum3Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum3.png", 56, pivot: new Vector2(0f, 0.5f));
    public static Sprite cum4Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum4.png", 56, pivot: new Vector2(0f, 0.5f));
    public static Sprite cum5Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum5.png", 56, pivot: new Vector2(0f, 0.5f));
    public static Sprite cum6Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum6.png", 56, pivot: new Vector2(0f, 0.5f));
    public static Sprite cum7Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum7.png", 56, pivot: new Vector2(0f, 0.5f));
    public static Sprite cum8Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum8.png", 56, pivot: new Vector2(0f, 0.5f));
    public static Sprite cum9Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum9.png", 56, pivot: new Vector2(0f, 0.5f));
    public static Sprite cum10Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum10.png", 56, pivot: new Vector2(0f, 0.5f));
    public static Sprite cum11Sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.cum11.png", 56, pivot: new Vector2(0f, 0.5f));

    // Ordered playback frames. Declared AFTER the fields above so they're already loaded.
    public static readonly Sprite[] cumFrames =
    [
        cum1Sprite, cum2Sprite, cum3Sprite, cum4Sprite, cum5Sprite, cum6Sprite,
        cum7Sprite, cum8Sprite, cum9Sprite, cum10Sprite, cum11Sprite,
    ];


    public static byte[] minigamesBundleBytes =
        FileLoader.LoadFileBytes("ScavPrototypeSexMod.Assets.Bundles.minigames.bundle").Item2;
    public static Dictionary<string, GameObject> customPrefabs = new()
    {
        ["SexMod/MasturbationMinigame"] =
            FileLoader.LoadEmbeddedBundleAsset<GameObject>(minigamesBundleBytes, "MasturbationMinigame"),
    };

    public static Sprite humanoidCockMasturbation = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.humancockmasturbation.png", 56, pivot: new Vector2(0.5f, 0));
    public static Sprite canineCockMasturbation = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.caninecockmasturbation.png", 56, pivot: new Vector2(0.5f, 0));

    // Animation Clips for da animations
    public static byte[] handsBundleBytes = FileLoader.LoadFileBytes("ScavPrototypeSexMod.Assets.Bundles.animations.bundle").Item2;
    public static AnimationClip[] handsClips = FileLoader.LoadEmbeddedBundle(
        handsBundleBytes, "ArmsJerk", "ArmsJerkAction", "ExperimentJerkSit");

    internal static byte[] bodyBundleBytes = FileLoader.LoadFileBytes("ScavPrototypeSexMod.Assets.Bundles.bodyNewAnimations.bundle").Item2;
    internal static AssetBundle bundle = AssetBundle.LoadFromStream(FileLoader.LoadFileStream("ScavPrototypeSexMod.Assets.Bundles.trader1.bundle").Item2);

    public static AnimationClip armsJerk = handsClips[0];
    public static AnimationClip armsJerkAction = handsClips[1];
    public static AnimationClip experimentJerkSit = handsClips[2];

    // For values within the limb list
    // -1 means don't sprite replace for that limb.
    // TODO: Implement _dick stages of hardness.
    public static readonly int[] changeAssets =
    [
        // head, uptorso, downtorso, uparmf, downarmf, handf, uparmb, downarmb, handb, thighf, crusf, footf, thighb, crusb, footb, _dick
        -1, 0, 1, -1, -1, -1, -1, -1, -1, 2, -1, -1, 2, -1, -1, -1
    ];

    public static readonly string[] BASE_ASSET_NAMES =
    [
        "experimentUpTorso.png",
        "experimentDownTorso.png",
        "experimentThigh.png",
    ];

    // My BS
    public static bool funnyStuff = false;
    public static Sprite expieJoy = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.UnrelatedAssets.smilingexpiedeath.png");
    public static Sprite expieWater = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.UnrelatedAssets.waterexpiedeath.png");
    public static Sprite expieThink = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.UnrelatedAssets.thinkingexpiedeath.png");
    public static Sprite sugarCoated = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.UnrelatedAssets.sugarcoat.png");
}
