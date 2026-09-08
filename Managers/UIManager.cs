using BepInEx.Configuration;
using ScavPrototypeSexMod.Data;
using ScavPrototypeSexMod.GameScripts;
using ScavPrototypeSexMod.Managers.Reproduction;
using System;
using System.Collections;
using System.Collections.Generic;
using TMPro;
using UnityEngine;
using UnityEngine.UI;
using Object = UnityEngine.Object;

namespace ScavPrototypeSexMod.Managers;

public static class UIManager
{
    private const float WoundViewSpritePpu = 100f / 3f;

    private static readonly HashSet<int> wiredKinkDisplays = [];

    private static Color _normalColor = new(1f, 1f, 1f, 0f);
    private static Color _hoverColor = new(1f, 1f, 1f, 18f / 255f);
    private static Color _pressedColor = new(1f, 1f, 1f, 47f / 255f);
    private static Color _selectedColor = new(245f / 255f, 245f / 255f, 245f / 255f, 1f);
    private static Color _disabledColor = new(200f / 255f, 200f / 255f, 200f / 255f, 128f / 255f);

    private static Coroutine _urinateCoroutine;

    public static void Initialize()
    {
        ModdedLogger.Info("UIManager Initialized");
    }

    public static void CreateSettingsMenu(PreRunScript prs)
    {
        if (prs?.runSettingObjects == null)
        {
            ModdedLogger.Warning("PreRunSettings doesn't exist.");
            return;
        }

        foreach (RunSettingDisplay display in prs.runSettingObjects)
        {
            if (display?.associated == null)
            {
                continue;
            }

            int displayId = display.GetInstanceID();
            if (wiredKinkDisplays.Contains(displayId))
            {
                continue;
            }

            string key = display.associated.name;
            if (!Plugin.kinkConfig.TryGetValue(key, out ConfigEntry<bool> configEntry))
            {
                continue;
            }

            // Gets the toggle of the runsettingdisplay, perhaps for the bool values of the kinks
            Toggle toggle = display.GetComponentInChildren<Toggle>();
            bool value = configEntry.Value;

            prs.runSettings[key] = value;
            toggle.SetIsOnWithoutNotify(value);

            toggle.onValueChanged.AddListener(on =>
            {
                ModdedLogger.Warning(configEntry.Value);
                configEntry.Value = on;
                SharedState.kinkOptions[key] = on;
                prs.runSettings[key] = on;
            });

            wiredKinkDisplays.Add(displayId);
        }
    }

    // Creates the Sex Button on the trader menu
    public static IEnumerator CreateTraderSexButton(PlayerCamera cam)
    {
        yield return null;

        GameObject tradeMenu = cam.tradeMenu.gameObject;

        if (tradeMenu.transform.Find("Fuck") != null)
        {
            yield break;
        }

        // Create object
        GameObject imgGO = new("SexModImage");
        imgGO.transform.SetParent(tradeMenu.transform, false);

        Image img = imgGO.AddComponent<Image>();
        img.sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.sex.png", 100);

        Button btn = imgGO.AddComponent<Button>();
        btn.image = img;
        btn.interactable = true;
        btn.enabled = true;

        UITooltip tooltip = imgGO.AddComponent<UITooltip>();
        tooltip.skipLocale = true;
        tooltip.tipName = FileLoader.GetLocale("other", "fuck");
        tooltip.tipDesc = FileLoader.GetLocale("other", "fuckdsc");
        tooltip.enabled = true;

        RectTransform rt = img.rectTransform;
        // Putting the pivot point directly in the center of the button
        rt.anchorMin = new Vector2(0.5f, 0.5f);
        rt.anchorMax = new Vector2(0.5f, 0.5f);
        rt.pivot = new Vector2(0.5f, 0.5f);

        // offset relative to radialMenu
        rt.anchoredPosition = new Vector2(63f, -245f);
        rt.sizeDelta = new Vector2(128, 128);
        rt.localScale = Vector3.one;

        btn.onClick.RemoveAllListeners();
        btn.onClick.AddListener(() =>
        {
            cam.StartCoroutine(SexManager.TriggerSex(cam));
        });
    }

    public static void UpdateGenderUI()
    {
        WoundView view = RunContext.WoundView;
        Transform statusRoot = view != null ? ObjectFinder.FindRecursive(view.transform, "GenRoot") : null;
        if (statusRoot == null)
        {
            return;
        }

        var img = statusRoot.GetComponent<Image>();
        var tooltip = statusRoot.GetComponent<UITooltip>();
        var imgRT = img.rectTransform;
        var status = ExtraBodyData.CurrentBodyStatus;

        switch (status)
        {
            case Status.Male:
                imgRT.sizeDelta = new Vector2(250, 250);
                imgRT.localScale = new Vector3(0.15f, 0.15f, 1f);
                break;
            case Status.Female:
                imgRT.sizeDelta = new Vector2(250, 250);
                imgRT.localScale = new Vector3(0.20f, 0.20f, 1f);
                break;
            case Status.Intersex:
                imgRT.sizeDelta = new Vector2(250, 340);
                imgRT.localScale = new Vector3(0.13f, 0.13f, 1f);
                break;
            case Status.NonBinary:
                imgRT.sizeDelta = new Vector2(250, 250);
                imgRT.localScale = new Vector3(0.15f, 0.15f, 1f);
                break;
        }

        tooltip.tipDesc = "You are " + ExtraBodyData.CurrentBodyStatus.ToString() + ".";
        img.sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets." + ExtraBodyData.CurrentBodyStatus.ToString().ToLower() + "symbol.png", 32);
    }

    public static void UpdateWVUI(WoundView wv, DickTypes dicktype)
    {
        // Implement the updating of the wound view sprite when you change it with commands.
        ModdedLogger.Info("Update woundview's dick sprite here...");

        var limbs = ObjectFinder.FindRecursive(wv.transform, "Limbs");

        var dick = limbs.Find("Dick");

        var innerDick = dick.GetChild(0);

        Image refLimb = wv.limbImages[2];

        Image dickImageOuter = dick.GetComponent<Image>();
        Image dickImageInner = innerDick.GetComponent<Image>();

        RectTransform outerRT = dickImageOuter.rectTransform;
        RectTransform innerRT = dickImageInner.rectTransform;

        switch (dicktype)
        {
            case DickTypes.Humanoid:
                dickImageInner.sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.humanoiddickbase.png", WoundViewSpritePpu);
                dickImageOuter.sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.humanoiddickoutline.png", WoundViewSpritePpu);
                break;
            case DickTypes.Canine:
                dickImageInner.sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.caninedickbase.png", WoundViewSpritePpu);
                dickImageOuter.sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.caninedickoutline.png", WoundViewSpritePpu);
                break;
            default:
                dickImageInner.sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.humanoiddickbase.png", WoundViewSpritePpu);
                dickImageOuter.sprite = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.humanoiddickoutline.png", WoundViewSpritePpu);
                break;
        }

        outerRT.sizeDelta = WoundViewSizeFromReferenceSprite(dickImageOuter.sprite, refLimb);
        innerRT.sizeDelta = WoundViewSizeFromReferenceSprite(dickImageInner.sprite, refLimb);
    }

    public static void ResizeLimbSlots(WoundView view)
    {
        int needed = view.body.limbs.Length;
        int oldCount = view.limbImages.Length;

        ModdedLogger.Info($"limbs.Length is {view.body.limbs.Length}");
        ModdedLogger.Info($"oldCount is {view.limbImages.Length}");
        if (needed <= oldCount)
        {
            return;
        }

        ModdedLogger.Info("Start resizing");
        Array.Resize(ref view.limbImages, needed);
        Array.Resize(ref view.origPositions, needed);
        Array.Resize(ref view.limbImageFlash, needed);
        Array.Resize(ref view.limbImageLerp, needed);
        Array.Resize(ref view.limbTooltips, needed);

        var dickOutlineImage = SharedState.savedDickType switch
        {
            DickTypes.Humanoid => FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.humanoiddickoutline.png", WoundViewSpritePpu),
            DickTypes.Canine => FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.caninedickoutline.png", WoundViewSpritePpu),
            _ => FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.humanoiddickoutline.png", WoundViewSpritePpu)
        };
        var dickBaseImage = SharedState.savedDickType switch
        {
            DickTypes.Humanoid => FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.humanoiddickbase.png", WoundViewSpritePpu),
            DickTypes.Canine => FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.caninedickbase.png", WoundViewSpritePpu),
            _ => FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.humanoiddickbase.png", WoundViewSpritePpu)
        };

        Image refLimb = view.limbImages[2];
        RectTransform refRT = refLimb.rectTransform;
        Transform limbs = ObjectFinder.FindRecursive(view.transform, "Limbs");

        GameObject dickBase = new("Dick");
        dickBase.transform.SetParent(limbs, false);

        Image dickImage = dickBase.AddComponent<Image>();
        dickImage.sprite = dickOutlineImage;

        RectTransform dickRT = dickImage.rectTransform;
        dickRT.anchorMin = refRT.anchorMin;
        dickRT.anchorMax = refRT.anchorMax;
        dickRT.pivot = refRT.pivot;
        dickRT.localScale = Vector3.one;
        Vector2 dickSize = WoundViewSizeFromReferenceSprite(dickImage.sprite, refLimb);
        dickRT.sizeDelta = dickSize;
        dickRT.anchoredPosition = refRT.anchoredPosition + new Vector2(0f, -80f);

        GameObject dickInner = new("DickInner");
        dickInner.transform.SetParent(dickBase.transform, false);

        Image dickInnerImage = dickInner.AddComponent<Image>();
        dickInnerImage.sprite = dickBaseImage;

        RectTransform innerRT = dickInnerImage.rectTransform;
        innerRT.anchorMin = refRT.anchorMin;
        innerRT.anchorMax = refRT.anchorMax;
        innerRT.pivot = refRT.pivot;
        innerRT.sizeDelta = WoundViewSizeFromReferenceSprite(dickInnerImage.sprite, refLimb);
        innerRT.anchoredPosition = Vector2.zero;

        ModdedLogger.Info($"Done dick GO for body view (size {dickSize})");
        view.limbImages[oldCount] = dickImage;
        view.origPositions[oldCount] = dickRT.anchoredPosition;
    }

    // WoundView limb slots use sizeDelta ~= sprite pixels * (100 / 33.333), not raw tex size.
    private static Vector2 WoundViewSizeFromReferenceSprite(Sprite sprite, Image referenceLimb)
    {
        Sprite refSprite = referenceLimb.sprite;

        Vector2 refSize = referenceLimb.rectTransform.sizeDelta;
        Vector2 px = sprite.rect.size;

        return new(
            px.x * refSize.x / refSprite.rect.width,
            px.y * refSize.y / refSprite.rect.height);
    }

    public static void ResizeSkillsBackOverlay(WoundView view)
    {
        Transform skillsBackOverlay = ObjectFinder.FindRecursive(view.transform, "SkillsBack");
        Transform timeBackOverlay = ObjectFinder.FindRecursive(view.transform, "TimeBack");

        var backgroundImage = skillsBackOverlay.GetComponent<Image>();

        var newBackground = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.skillscreenexpanded.png", WoundViewSpritePpu);
        backgroundImage.sprite = newBackground;
        backgroundImage.SetNativeSize();

        var glowImage = ObjectFinder.FindRecursive(skillsBackOverlay, "Image");
        var glowImageRT = glowImage.gameObject.transform.GetComponent<RectTransform>();
        glowImageRT.anchorMin = new(0.5f, 1f);
        glowImageRT.anchorMax = new(0.5f, 1f);
        glowImageRT.anchoredPosition = new(0, -108f);

        FilledImagePP strBar = view.skillBars[0];
        FilledImagePP resBar = view.skillBars[1];
        FilledImagePP intBar = view.skillBars[2];

        var strBarRT = strBar.gameObject.transform.GetComponent<RectTransform>();
        strBarRT.anchorMin = new(0.5f, 1f);
        strBarRT.anchorMax = new(0.5f, 1f);
        strBarRT.anchoredPosition = new(30, -30f);

        var resBarRT = resBar.gameObject.transform.GetComponent<RectTransform>();
        resBarRT.anchorMin = new(0.5f, 1f);
        resBarRT.anchorMax = new(0.5f, 1f);
        resBarRT.anchoredPosition = new(30, -90f);

        var intBarRT = intBar.gameObject.transform.GetComponent<RectTransform>();
        intBarRT.anchorMin = new(0.5f, 1f);
        intBarRT.anchorMax = new(0.5f, 1f);
        intBarRT.anchoredPosition = new(30, -150f);

        Transform mouseover1 = skillsBackOverlay.Find("Mouseover1");
        Transform mouseover2 = skillsBackOverlay.Find("Mouseover2");
        Transform mouseover3 = skillsBackOverlay.Find("Mouseover3");

        var mouseOver1RT = mouseover1.gameObject.transform.GetComponent<RectTransform>();
        mouseOver1RT.anchorMin = new(0.5f, 1f);
        mouseOver1RT.anchorMax = new(0.5f, 1f);
        mouseOver1RT.anchoredPosition = new(-0.2704926f, -31.1708f);

        var mouseOver2RT = mouseover2.gameObject.transform.GetComponent<RectTransform>();
        mouseOver2RT.anchorMin = new(0.5f, 1f);
        mouseOver2RT.anchorMax = new(0.5f, 1f);
        mouseOver2RT.anchoredPosition = new(-0.2704926f, -90.6f);

        var mouseOver3RT = mouseover3.gameObject.transform.GetComponent<RectTransform>();
        mouseOver3RT.anchorMin = new(0.5f, 1f);
        mouseOver3RT.anchorMax = new(0.5f, 1f);
        mouseOver3RT.anchoredPosition = new(-0.2704926f, -150.4f);

        GameObject lowerGO = new("LowerSexImage");
        lowerGO.transform.SetParent(skillsBackOverlay.transform, false);

        var lowerBackgroundImage = FileLoader.LoadEmbeddedSprite("ScavPrototypeSexMod.Assets.screensexstuff.png", WoundViewSpritePpu);
        var newLowerBackgroundImage = lowerGO.AddComponent<Image>();

        newLowerBackgroundImage.sprite = lowerBackgroundImage;
        newLowerBackgroundImage.SetNativeSize();

        var lowerTransform = lowerGO.GetComponent<RectTransform>();

        lowerTransform.pivot = new(0.5f, 0.5f);
        lowerTransform.anchorMin = new(0.5f, 0f);
        lowerTransform.anchorMax = new(0.5f, 0f);
        lowerTransform.anchoredPosition = new(0, 66f);

        TextMeshProUGUI template = view.timeTextBar;

        GameObject txtGO = new("SexTitle");
        txtGO.transform.SetParent(lowerGO.transform, false);

        TextMeshProUGUI txt = txtGO.AddComponent<TextMeshProUGUI>();
        txt.text = "GENDER";
        txt.font = template.font;
        txt.fontSize = template.fontSize;
        txt.color = template.color;
        txt.alignment = TextAlignmentOptions.MidlineLeft;

        RectTransform rt = txt.rectTransform;
        rt.anchorMin = new Vector2(0.5f, 0f);
        rt.anchorMax = new Vector2(0.5f, 0f);
        rt.pivot = new Vector2(0.5f, 0.5f);
        rt.sizeDelta = new Vector2(302f, 24f);
        rt.anchoredPosition = new Vector2(-26.5f, 17f);

        CreateBar(
            view,
            lowerGO.transform,
            "SexBarHardness",
            new(-41f, 73.5f),
            new Vector2(264, 9),
            "hardness",
            "hardnessdsc");

        CreateBar(
            view,
            lowerGO.transform,
            "SexBarHorniness",
            new(-41f, 43.5f),
            new Vector2(264, 9),
            "honriness",
            "honrinessdsc");

        UpdateSexBars();

        CreateGenderIcon(lowerGO.transform);

        var timeRT = timeBackOverlay.GetComponent<RectTransform>();
        timeRT.anchoredPosition = new(timeRT.anchoredPosition.x, timeRT.anchoredPosition.y - 96);
    }

    public static void UpdateSexBars()
    {
        ExtraBodyData data = RunContext.BodyData;
        WoundView view = RunContext.WoundView;
        if (data == null || view == null)
        {
            return;
        }

        FilledImagePP hardnessBar = ObjectFinder.FindRecursive(view.transform, "SexBarHardness")
            ?.GetComponent<FilledImagePP>();
        TextMeshProUGUI hardnessText = ObjectFinder.FindRecursive(view.transform, "SexBarHardnessText")
            ?.GetComponent<TextMeshProUGUI>();
        FilledImagePP horninessBar = ObjectFinder.FindRecursive(view.transform, "SexBarHorniness")
            ?.GetComponent<FilledImagePP>();
        TextMeshProUGUI horninessText = ObjectFinder.FindRecursive(view.transform, "SexBarHorninessText")
            ?.GetComponent<TextMeshProUGUI>();

        if (hardnessBar != null)
        {
            hardnessBar.fillAmount = data.hardness * 0.01f;
        }

        if (hardnessText != null)
        {
            hardnessText.text = $"{Mathf.RoundToInt(data.hardness)}";
        }

        if (horninessBar != null)
        {
            horninessBar.fillAmount = data.horniness * 0.01f;
        }

        if (horninessText != null)
        {
            horninessText.text = $"{Mathf.RoundToInt(data.horniness)}";
        }
    }

    private static (FilledImagePP bar, TextMeshProUGUI text) CreateBar(
        WoundView view,
        Transform parent,
        string name,
        Vector2 anchoredPosition,
        Vector2 size,
        string tooltipNameKey,
        string tooltipDescKey)
    {
        Transform limbView = ObjectFinder.FindRecursive(view.transform, "LimbView");
        Transform barTemplate = limbView != null ? limbView.Find("MuscleBar") : null;
        Transform textTemplate = limbView != null ? limbView.Find("MuscleText") : null;

        if (barTemplate == null)
        {
            ModdedLogger.Error("MuscleBar template not found under LimbView");
            return (null, null);
        }

        GameObject barGO = Object.Instantiate(barTemplate.gameObject, parent);
        barGO.name = name;
        barGO.SetActive(true);

        if (barGO.TryGetComponent(out UITooltip barTooltip))
        {
            Object.Destroy(barTooltip);
        }

        RectTransform barRT = barGO.GetComponent<RectTransform>();
        barRT.anchorMin = new Vector2(0.5f, 0f);
        barRT.anchorMax = new Vector2(0.5f, 0f);
        barRT.pivot = new Vector2(0.5f, 0.5f);
        barRT.sizeDelta = size;
        barRT.anchoredPosition = anchoredPosition;
        barRT.localScale = Vector3.one;

        FilledImagePP bar = barGO.GetComponent<FilledImagePP>();

        TextMeshProUGUI text = null;
        if (textTemplate != null)
        {
            GameObject textGO = Object.Instantiate(textTemplate.gameObject, parent);
            textGO.name = name + "Text";
            textGO.SetActive(true);

            if (textGO.TryGetComponent(out UITooltip tooltip))
            {
                tooltip.skipLocale = true;
                tooltip.tipName = FileLoader.GetLocale("other", tooltipNameKey);
                tooltip.tipDesc = FileLoader.GetLocale("other", tooltipDescKey);
            }

            RectTransform textRT = textGO.GetComponent<RectTransform>();
            textRT.anchorMin = new Vector2(0.5f, 0f);
            textRT.anchorMax = new Vector2(0.5f, 0f);
            textRT.pivot = new Vector2(0f, 0.5f);
            textRT.sizeDelta = new Vector2(65f, 25f);
            textRT.anchoredPosition = anchoredPosition + new Vector2(size.x * 0.5f + 5f, 2f);
            textRT.localScale = Vector3.one;

            text = textGO.GetComponent<TextMeshProUGUI>();
            text.raycastTarget = true;
            textGO.transform.SetAsLastSibling();
        }

        return (bar, text);
    }

    private static void CreateGenderIcon(Transform parent)
    {
        if (parent.Find("GenRoot") != null)
        {
            return;
        }

        GameObject statusGO = new("GenRoot");
        statusGO.transform.SetParent(parent, false);

        Image genimg = statusGO.AddComponent<Image>();
        genimg.raycastTarget = true;

        RectTransform imgRT = genimg.rectTransform;
        imgRT.anchorMin = new Vector2(0.5f, 0f);
        imgRT.anchorMax = new Vector2(0.5f, 0f);
        imgRT.pivot = new Vector2(0.5f, 0.5f);
        imgRT.anchoredPosition = new Vector2(160f, 17f);

        UITooltip tooltip = statusGO.AddComponent<UITooltip>();
        tooltip.skipLocale = true;
        tooltip.tipName = FileLoader.GetLocale("other", "status");

        UpdateGenderUI();
    }

    // Makes the masturbate button in the workout list UI
    public static IEnumerator InitSexModWorkoutList(PlayerCamera cam)
    {
        if (ExtraBodyData.CurrentBodyStatus == Status.NonBinary)
            yield break;

        var workoutList = ObjectFinder.FindRecursive(cam.woundView.gameObject.transform, "WorkoutsList");
        var workoutTranform = workoutList.GetComponent<RectTransform>();
        var text = ObjectFinder.FindRecursive(cam.woundView.transform, "Text (TMP)");
        TextMeshProUGUI textMesh = text.GetComponent<TextMeshProUGUI>();

        float spacing = 90;

        CreateMasturbationButton(workoutTranform, textMesh, cam);

        if (Plugin.kinkConfig["Watersports"].Value)
        {
            spacing += 100;
            CreateUrinateButton(workoutTranform, textMesh, cam);
        }

        foreach (Transform child in workoutList)
        {
            if (child.name == "Masturbate" || child.name == "Urinate")
            {
                continue;
            }
            if (!child.TryGetComponent<Button>(out var existingBtn))
            {
                continue;
            }

            var btnTranform = existingBtn.GetComponent<RectTransform>();
            btnTranform.localPosition = new Vector2(btnTranform.localPosition.x, btnTranform.localPosition.y - spacing / 2);
        }

        if (!workoutTranform)
        {
            ModdedLogger.Error("RootRT is not defined.");
            yield break;
        }

        workoutTranform.anchoredPosition = new Vector2(-207f, 320);
        workoutTranform.sizeDelta = new Vector2(workoutTranform.sizeDelta.x, workoutTranform.sizeDelta.y + spacing);
    }

    public static bool HasWorkoutButton(WoundView view, string name)
    {
        Transform workoutList = view != null
            ? ObjectFinder.FindRecursive(view.transform, "WorkoutsList")
            : null;
        return workoutList != null && workoutList.Find(name) != null;
    }

    private static void CreateMasturbationButton(RectTransform workoutTranform, TextMeshProUGUI textmesh, PlayerCamera cam)
    {
        ModdedLogger.Info("Creating masturbate button");

        GameObject buttonObject = new("Masturbate");
        buttonObject.transform.SetParent(workoutTranform.transform, false);
        float spacing = Plugin.kinkConfig["Watersports"].Value ? 100 : 140;
        buttonObject.transform.localPosition = new(buttonObject.transform.localPosition.x, buttonObject.transform.localPosition.y + spacing);

        ModdedLogger.Info("Creating masturbate RectTransform");
        RectTransform btnRect = buttonObject.AddComponent<RectTransform>();
        btnRect.anchorMin = new Vector2(0.5f, 0.5f);
        btnRect.anchorMax = new Vector2(0.5f, 0.5f);
        btnRect.pivot = new Vector2(0.5f, 0.5f);
        btnRect.sizeDelta = new Vector2(176.35f, 80f);

        ModdedLogger.Info("Creating masturbate Image");
        Image btnImg = buttonObject.AddComponent<Image>();
        btnImg.color = Color.white;

        ModdedLogger.Info("Creating masturbate btn");
        Button btn = buttonObject.AddComponent<Button>();
        ColorBlock colors = btn.colors;

        colors.normalColor = _normalColor;
        colors.highlightedColor = _hoverColor;
        colors.pressedColor = _pressedColor;
        colors.selectedColor = _selectedColor;
        colors.disabledColor = _disabledColor;

        btn.colors = colors;
        btn.interactable = true;
        btn.enabled = true;

        ModdedLogger.Info("Creating masturbate UITooltip");
        UITooltip uiTool = buttonObject.AddComponent<UITooltip>();
        uiTool.skipLocale = true;
        uiTool.tipName = FileLoader.GetLocale("other", "masturbate");
        uiTool.tipDesc = FileLoader.GetLocale("other", "masturbatedsc");

        ModdedLogger.Info("Creating masturbate GameObject txtGO");
        GameObject txtGO = new("Text");
        txtGO.transform.SetParent(buttonObject.transform, false);

        ModdedLogger.Info("Creating masturbate TextMeshProUGUI");
        TextMeshProUGUI txt = txtGO.AddComponent<TextMeshProUGUI>();
        txt.text = FileLoader.GetLocale("other", "masturbate");
        txt.alignment = TextAlignmentOptions.Center;
        txt.fontSize = textmesh.fontSize;
        txt.font = textmesh.font;

        ModdedLogger.Info("Creating masturbate txtRT");
        RectTransform txtRT = txt.rectTransform;
        txtRT.anchorMin = Vector2.zero;
        txtRT.anchorMax = Vector2.one;
        txtRT.offsetMin = Vector2.zero;
        txtRT.offsetMax = Vector2.zero;

        btn.onClick.RemoveAllListeners();
        btn.onClick.AddListener(() =>
        {
            if (MinigameBase.main.currentMinigame is MasturbationMinigame)
            {
                MinigameBase.main.EndMinigame();
                return;
            }

            if (MasturbationManager.CanMasturbate(cam))
            {
                MinigameBase.main.StartMinigame(new MasturbationMinigame(), null);
            }
        });

        buttonObject.SetActive(true);
    }

    private static void CreateUrinateButton(RectTransform workoutTranform, TextMeshProUGUI textmesh, PlayerCamera cam)
    {
        Transform existingButton = workoutTranform.Find("Urinate");
        if (existingButton != null)
        {
            existingButton.gameObject.SetActive(true);
            return;
        }

        ModdedLogger.Info("Creating urinate button");

        GameObject buttonObject = new("Urinate");
        buttonObject.transform.SetParent(workoutTranform.transform, false);
        buttonObject.transform.localPosition = new(buttonObject.transform.localPosition.x, buttonObject.transform.localPosition.y + 190f);

        ModdedLogger.Info("Creating urinate RectTransform");
        RectTransform btnRect = buttonObject.AddComponent<RectTransform>();
        btnRect.anchorMin = new Vector2(0.5f, 0.5f);
        btnRect.anchorMax = new Vector2(0.5f, 0.5f);
        btnRect.pivot = new Vector2(0.5f, 0.5f);
        btnRect.sizeDelta = new Vector2(176.35f, 80f);

        ModdedLogger.Info("Creating urinate Image");
        Image btnImg = buttonObject.AddComponent<Image>();
        btnImg.color = Color.white;

        ModdedLogger.Info("Creating urinate Button");
        Button btn = buttonObject.AddComponent<Button>();
        ColorBlock colors = btn.colors;

        colors.normalColor = _normalColor;
        colors.highlightedColor = _hoverColor;
        colors.pressedColor = _pressedColor;
        colors.selectedColor = _selectedColor;
        colors.disabledColor = _disabledColor;

        btn.colors = colors;
        btn.interactable = true;
        btn.enabled = true;

        ModdedLogger.Info("Creating urinate UITooltip");
        UITooltip uiTool = buttonObject.AddComponent<UITooltip>();
        uiTool.skipLocale = true;
        uiTool.tipName = FileLoader.GetLocale("other", "urinate");
        uiTool.tipDesc = FileLoader.GetLocale("other", "urinatedsc");

        ModdedLogger.Info("Creating urinate GameObject Text");
        GameObject txtGO = new("Text");
        txtGO.transform.SetParent(buttonObject.transform, false);

        ModdedLogger.Info("Creating urinate TextMeshProUGUI");
        TextMeshProUGUI txt = txtGO.AddComponent<TextMeshProUGUI>();
        txt.text = FileLoader.GetLocale("other", "urinate");
        txt.alignment = TextAlignmentOptions.Center;
        txt.fontSize = textmesh.fontSize;
        txt.font = textmesh.font;

        ModdedLogger.Info("Creating urinate RectTransform txtRT");
        RectTransform txtRT = txt.rectTransform;
        txtRT.anchorMin = Vector2.zero;
        txtRT.anchorMax = Vector2.one;
        txtRT.offsetMin = Vector2.zero;
        txtRT.offsetMax = Vector2.zero;

        btn.onClick.RemoveAllListeners();
        btn.onClick.AddListener(() =>
        {
            if (_urinateCoroutine != null)
                cam.StopCoroutine(_urinateCoroutine);

            _urinateCoroutine = cam.StartCoroutine(DickManager.Urinate(cam));
        });

        buttonObject.SetActive(true);
    }

    public static void ResetRunState()
    {
        _urinateCoroutine = null;
        wiredKinkDisplays.Clear();
    }
}
