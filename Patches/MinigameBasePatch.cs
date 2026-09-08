using HarmonyLib;
using ScavPrototypeSexMod.GameScripts;
using ScavPrototypeSexMod.Managers.Reproduction;
using ScavPrototypeSexMod.Patches.Debug;
using System;
using UnityEngine;
using UnityEngine.UI;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(MinigameBase))]
internal static class MinigameBasePatch
{
    // MASTURBATION ONLY!
    // ================================================================================
    // Fixed visual offset of the whole hand relative to handPos, in rendered pixels.
    // Applied through the pivot (which vanilla never overwrites), so it persists every
    // frame and never touches sizeDelta. +X moves the hand right, +Y moves it up.
    private static readonly Vector2 HandDrawOffset = new(35f, 0f);
    // ================================================================================

    private static Image _masturbationHandFingers;
    private static RectTransform _configuredHandRect;

    private static Vector2 _vanillaHandPivot;
    private static Vector2 _vanillaHandSize;

    private static bool _vanillaPreserveAspect;
    private static float _vanillaSpriteScale = 1f;

    public static void ResetRunState()
    {
        _masturbationHandFingers = null;
        _configuredHandRect = null;
        _vanillaHandPivot = default;
        _vanillaHandSize = default;
        _vanillaPreserveAspect = false;
        _vanillaSpriteScale = 1f;
    }

    [HarmonyPatch("Start")]
    [HarmonyPostfix]
    public static void MinigameBasePatch_Start_Postfix(MinigameBase __instance)
    {
        PatchLogging.LogPatchCall();

        Array.Resize(ref __instance.handSprites, __instance.handSprites.Length + 1);
        __instance.handSprites[^1] = SharedState.handBaseMaturbate;

        EnsureMasturbationFingers(__instance);
    }

    [HarmonyPatch("UpdateHandSprite")]
    [HarmonyPostfix]
    public static void MinigameBasePatch_UpdateHandSprite_Postfix(MinigameBase __instance, bool reset)
    {
        PatchLogging.LogPatchCall();

        Image handSprite = Traverse.Create(__instance).Field("handSprite").GetValue<Image>();
        Minigame minigame = __instance.currentMinigame;

        // Masturbation stuff
        EnsureMasturbationFingers(__instance);

        if ((int)minigame.HandType() != (int)MinigameExtensions.HandSpriteTypeExtension.MasturbationHandMale)
        {
            RestoreVanillaHandLayout(handSprite);
            _masturbationHandFingers.gameObject.SetActive(false);

            return;
        }

        int handIndex = __instance.body.handSlot == 0 ? 5 : 8;
        if (__instance.body.limbs[handIndex].dismembered)
        {
            RestoreVanillaHandLayout(handSprite);
            _masturbationHandFingers.gameObject.SetActive(false);

            return;
        }

        // Three hand states, evaluated every frame (UpdateHandSprite runs each frame):
        //   1) gripping the shaft  -> two-part hand (base arm + fingers)
        //   2) clicking off-shaft  -> single "grabbing air" sprite
        //   3) idle (not clicking) -> single idle sprite following the cursor
        var mg = minigame as MasturbationMinigame;

        if (mg.IsOrgasmHappend)
        {
            RestoreVanillaHandLayout(handSprite);

            handSprite.sprite = __instance.handSprites[0];
            _masturbationHandFingers.gameObject.SetActive(false);

            return;
        }

        bool holdingDick = __instance.handClicking && mg.Held;

        if (holdingDick)
        {
            ApplyMasturbationHandLayout(handSprite);

            handSprite.sprite = __instance.handSprites[^1];

            ConfigureFingersLayout(handSprite.rectTransform);
            _masturbationHandFingers.color = handSprite.color;
            _masturbationHandFingers.gameObject.SetActive(true);
        }
        else if (__instance.handClicking)
        {
            RestoreVanillaHandLayout(handSprite);

            handSprite.sprite = __instance.handSprites[2];
            _masturbationHandFingers.gameObject.SetActive(false);
        }

        if (__instance.handStoppedClicking || reset)
        {
            RestoreVanillaHandLayout(handSprite);

            handSprite.sprite = __instance.handSprites[UnityEngine.Random.Range(0, 2)];
            _masturbationHandFingers.gameObject.SetActive(false);
        }
    }

    [HarmonyPatch("EndMinigame")]
    [HarmonyPrefix]
    public static void MinigameBasePatch_EndMinigame_Prefix(MinigameBase __instance)
    {
        PatchLogging.LogPatchCall();

        if (__instance.currentMinigame is MasturbationMinigame)
        {
            MasturbationManager.EndMasturbation(PlayerCamera.main);
        }
    }

    #region Masturbation

    private static void EnsureMasturbationFingers(MinigameBase minigameBase)
    {
        if (_masturbationHandFingers != null
            && _masturbationHandFingers.transform.parent == minigameBase.handTransform)
        {
            return;
        }

        Image handSprite = minigameBase.handTransform.GetComponent<Image>();
        _configuredHandRect = handSprite.rectTransform;
        _vanillaHandPivot = _configuredHandRect.pivot;
        _vanillaHandSize = _configuredHandRect.sizeDelta;
        _vanillaPreserveAspect = handSprite.preserveAspect;

        if (handSprite.sprite != null)
        {
            Rect vanillaSpriteRect = handSprite.sprite.rect;

            float scaleX = _vanillaHandSize.x / vanillaSpriteRect.width;
            float scaleY = _vanillaHandSize.y / vanillaSpriteRect.height;

            _vanillaSpriteScale = (scaleX + scaleY) * 0.4f;
        }

        GameObject fingersObject = new("MasturbationHandFingers")
        {
            layer = minigameBase.handTransform.gameObject.layer
        };

        fingersObject.transform.SetParent(minigameBase.handTransform, false);

        _masturbationHandFingers = fingersObject.AddComponent<Image>();
        _masturbationHandFingers.sprite = SharedState.armFingersMasturbate;
        _masturbationHandFingers.raycastTarget = false;
        _masturbationHandFingers.preserveAspect = true;

        RectTransform fingersRect = _masturbationHandFingers.rectTransform;
        fingersRect.anchorMin = new Vector2(0.5f, 0.5f);
        fingersRect.anchorMax = new Vector2(0.5f, 0.5f);
        fingersRect.pivot = new(1f, 0.075f);

        ConfigureFingersLayout(minigameBase.handTransform);

        _masturbationHandFingers.gameObject.SetActive(false);
    }

    private static void ApplyMasturbationHandLayout(Image handSprite)
    {
        Rect baseRect = SharedState.handBaseMaturbate.rect;

        // Scale first: sizeDelta is the ONLY thing that sets the hand's size.
        handSprite.rectTransform.sizeDelta = baseRect.size * _vanillaSpriteScale;
        handSprite.preserveAspect = true;

        // Position via pivot, NOT anchoredPosition: vanilla resets anchoredPosition to
        // handPos every frame (handTransform == handSprite.rectTransform), so an
        // anchoredPosition offset would only survive one frame. Pivot is never touched
        // by vanilla, so this offset persists. Base pivot is the top-left corner (0,1);
        // shifting the pivot point slides the whole hand without changing sizeDelta.
        Vector2 size = handSprite.rectTransform.sizeDelta;
        handSprite.rectTransform.pivot = new Vector2(
            0f - HandDrawOffset.x / size.x,
            1f - HandDrawOffset.y / size.y);
    }

    private static void RestoreVanillaHandLayout(Image handSprite)
    {
        if (handSprite.rectTransform != _configuredHandRect)
        {
            return;
        }

        handSprite.rectTransform.pivot = _vanillaHandPivot;
        handSprite.rectTransform.sizeDelta = _vanillaHandSize;
        handSprite.preserveAspect = _vanillaPreserveAspect;
    }

    private static void ConfigureFingersLayout(RectTransform handRect)
    {
        Rect baseSpriteRect = SharedState.handBaseMaturbate.rect;
        Rect fingersSpriteRect = SharedState.armFingersMasturbate.rect;
        Vector2 renderedBaseSize = handRect.rect.size;

        float scaleX = renderedBaseSize.x / baseSpriteRect.width;
        float scaleY = renderedBaseSize.y / baseSpriteRect.height;

        RectTransform fingersRect = _masturbationHandFingers.rectTransform;
        fingersRect.sizeDelta = new Vector2(
            fingersSpriteRect.width * scaleX,
            fingersSpriteRect.height * scaleY);

        // The source PNGs share a top-left origin, so this keeps the wrist edges
        // aligned after the base sprite is stretched into the vanilla hand rect.
        // Then subtract HandDrawOffset: the parent pivot shift moves the whole rect
        // (arm + fingers) by +HandDrawOffset, so we push the fingers child back by the
        // same amount to keep them pinned to handPos. Net result: only the arm moves.
        fingersRect.anchoredPosition = new Vector2(
            (fingersSpriteRect.width - baseSpriteRect.width) * scaleX * 0.5f,
            (baseSpriteRect.height - fingersSpriteRect.height) * scaleY * 0.5f)
            - HandDrawOffset;
    }

    #endregion Masturbation
}
