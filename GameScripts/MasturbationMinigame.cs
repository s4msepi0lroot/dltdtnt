using ScavPrototypeSexMod.Managers.Reproduction;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace ScavPrototypeSexMod.GameScripts;

internal class MasturbationMinigame : Minigame
{
    private const float StrokeBottom = -150f;
    private const float StrokeTop = 35f;
    private const float EndZone = 0.1f;
    private const float AngleCompliance = 5f;
    private const float CumFrameRate = 6f;

    private RectTransform _dick;
    private RectTransform _cumSprite;

    // -1 - didn't start
    //  0 - bottom is last one
    //  1 - up is lat one
    private int _lastEnd = -1;
    private bool _held;
    private bool _isOrgasmHappend;

    public bool Held => _held;
    public bool IsOrgasmHappend => _isOrgasmHappend;

    private Vector2 DickCenter => _dick.anchoredPosition + new Vector2(
        (0.5f - _dick.pivot.x) * _dick.rect.width * _dick.localScale.x,
        (0.5f - _dick.pivot.y) * _dick.rect.height * _dick.localScale.y);

    public override string GuideLocaleString()
    {
        return "masturbationMinigameGuide";
    }

    public override HandSpriteType HandType()
    {
        // This shit looks ridiculous but i can't do anything about it
        return (Minigame.HandSpriteType)MinigameExtensions.HandSpriteTypeExtension.MasturbationHandMale;
    }

    public override bool NeedsItem()
    {
        return false;
    }

    public override void Start()
    {
        Minigame.game.CreateScreen("SexMod/MasturbationMinigame");
        _dick = Minigame.game.spawnedMiniGame.GetChild(1).GetComponent<RectTransform>();
        _cumSprite = Minigame.game.spawnedMiniGame.GetChild(2).GetComponent<RectTransform>();
        _cumSprite.gameObject.SetActive(false);

        var dickImage = _dick.GetComponent<Image>();
        dickImage.sprite = SharedState.savedDickType switch
        {
            DickTypes.Humanoid => SharedState.humanoidCockMasturbation,
            DickTypes.Canine => SharedState.canineCockMasturbation,
            _ => SharedState.humanoidCockMasturbation,
        };
        dickImage.SetNativeSize();

        var cumImage = _cumSprite.GetComponent<Image>();
        cumImage.sprite = SharedState.cum1Sprite;

        Minigame.game.handPos = _dick.anchoredPosition;
        Minigame.game.handVelocity = Vector2.zero;

        ModdedLogger.Info($"dick anchored position: {_dick.anchoredPosition}");
        ModdedLogger.Info($"dick position: {_dick.position}");

        MasturbationManager.BeginMasturbation(PlayerCamera.main);
    }

    public override void Update(List<RaycastResult> uiCasts)
    {
        if (_isOrgasmHappend)
        {
            return;
        }

        if (Minigame.game.handStartedClicking)
        {
            var distance = Vector2.Distance(Minigame.game.handPos, DickCenter);

            ModdedLogger.Info($"distance value: {distance}");
            ModdedLogger.Info($"hand position: {Minigame.game.handPos}");
            ModdedLogger.Info($"dick center: {DickCenter}");

            _held = distance < 150f;
            _lastEnd = -1;

            ModdedLogger.Info($"_held state: {_held}");
        }

        if (Minigame.game.handStoppedClicking)
        {
            _held = false;
        }

        if (!_held)
        {
            return;
        }

        float centerY = DickCenter.y;
        float t = Mathf.InverseLerp(
            centerY + StrokeBottom,
            centerY + StrokeTop,
            Minigame.game.handPos.y);

        PlayerCamera.main.body.armsAnimator.Play("ArmsSit", 0, t);

        if (t <= EndZone)
        {
            if (_lastEnd == 1)
            {
                _isOrgasmHappend = MasturbationManager.OnMasturbationStroke(PlayerCamera.main);

                if (_isOrgasmHappend)
                {
                    Minigame.game.StartCoroutine(UpdateCumAnimation());
                }
            }

            _lastEnd = 0;
        }
        else if (t >= 1f - EndZone)
        {
            if (_lastEnd == 0)
            {
                _isOrgasmHappend = MasturbationManager.OnMasturbationStroke(PlayerCamera.main);

                if (_isOrgasmHappend)
                {
                    Minigame.game.StartCoroutine(UpdateCumAnimation());
                }
            }

            _lastEnd = 1;
        }
    }

    public override void PhysicsUpdate(float deltaTime)
    {
        if (_isOrgasmHappend)
        {
            return;
        }

        if (!_held)
        {
            return;
        }

        Vector2 center = DickCenter;

        // The regular minigame hand physics still follows the mouse vertically.
        // Clamp Y to the usable stroke shown on the sprite.
        Minigame.game.handPos.y = Mathf.Clamp(
            Minigame.game.handPos.y,
            center.y + StrokeBottom,
            center.y + StrokeTop);

        // The shaft leans toward the hand. It pivots around its BOTTOM
        // (_dick.anchoredPosition, because the sprite pivot is (0.5, 0)). Measure the
        // SIGNED angle from the resting "straight up" axis to the base->hand direction.
        Vector2 basePivot = _dick.anchoredPosition;

        float dy = Mathf.Max(Minigame.game.handPos.y - basePivot.y, 0f);
        float maxDx = dy * Mathf.Tan(AngleCompliance * Mathf.Deg2Rad);
        float clampedX = Mathf.Clamp(
            Minigame.game.handPos.x,
            basePivot.x - maxDx,
            basePivot.x + maxDx);

        Vector2 toHand = Minigame.game.handPos - basePivot;
        float signedAngle = Vector2.SignedAngle(Vector2.up, toHand);

        ModdedLogger.Info($"hand angle: {signedAngle}");

        if (Minigame.game.handPos.x != clampedX)
        {
            Minigame.game.handPos.x = clampedX;
            Minigame.game.handVelocity.x = 0f;
        }

        _dick.localRotation = Quaternion.Euler(0f, 0f, signedAngle);
        _cumSprite.localRotation = Quaternion.Euler(0f, 0f, signedAngle);

        Vector3 worldTop = _dick.TransformPoint(new Vector3(
            (0.5f - _dick.pivot.x) * _dick.rect.width,
            (1f - _dick.pivot.y) * _dick.rect.height - 5f, //this is a error fix for overlapping GO in Unity editor
            0f));
        _cumSprite.transform.position = worldTop;

        bool pushingBelowBottom = Minigame.game.handPos.y <= center.y + StrokeBottom
            && Minigame.game.handVelocity.y < 0f;
        bool pushingAboveTop = Minigame.game.handPos.y >= center.y + StrokeTop
            && Minigame.game.handVelocity.y > 0f;

        if (pushingBelowBottom || pushingAboveTop)
        {
            Minigame.game.handVelocity.y = 0f;
        }
    }

    private IEnumerator UpdateCumAnimation()
    {
        Image image = _cumSprite.GetComponent<Image>();
        _cumSprite.gameObject.SetActive(true);

        // One reusable WaitForSeconds so we don't allocate every frame.
        var frameDelay = new WaitForSeconds(1f / CumFrameRate);

        foreach (Sprite frame in SharedState.cumFrames)
        {
            image.sprite = frame;

            // TODO: Redo so it will correctly play before cum splash
            if (image.sprite.name.Contains("cum8"))
            {
                Sound.Play(Plugin.cumSplatClip, Vector2.zero, twoDimensional: true, pitchShift: false);
            }

            yield return frameDelay;
        }

        yield return new WaitForSeconds(5f);

        Minigame.game.EndMinigame();
    }
}
