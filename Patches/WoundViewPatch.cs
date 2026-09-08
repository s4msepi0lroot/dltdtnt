using HarmonyLib;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Patches.Debug;
using UnityEngine;
using UnityEngine.UI;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(WoundView))]
internal static class WoundViewPatch
{
    [HarmonyPatch("Awake")]
    [HarmonyPostfix]
    private static void WoundView_Awake_Postfix(WoundView __instance)
    {
        PatchLogging.LogPatchCall();

        if (__instance.gameObject.activeSelf)
        {
            UIManager.ResizeLimbSlots(__instance);
            UIManager.ResizeSkillsBackOverlay(__instance);
        }
    }

    [HarmonyPatch("UpdateView")]
    [HarmonyPrefix]
    private static void WoundView_UpdateView_Prefix(WoundView __instance)
    {
        PatchLogging.LogPatchCall();
        DickMouseOver(__instance);
        ClampLimbLookingAt(__instance);
    }

    [HarmonyPatch("UpdateView")]
    [HarmonyPostfix]
    private static void WoundView_UpdateView_Postfix(WoundView __instance)
    {
        PatchLogging.LogPatchCall();
        KeepDickBaseVisible(__instance);
        UIManager.UpdateSexBars();
    }

    // Think that this adds the dickImage to the woundview
    private static void DickMouseOver(WoundView view)
    {
        Image dickImage = view.limbImages[^1];
        Transform mouseOverOverlay = ObjectFinder.FindRecursive(view.transform, "MouseOver");
        if (dickImage == null || mouseOverOverlay == null)
        {
            return;
        }

        Transform dickHit = mouseOverOverlay.Find("dickHitbox");
        if (dickHit == null)
        {
            Transform temp = null;
            foreach (Transform c in mouseOverOverlay)
            {
                if (c.GetComponent<WoundViewLimb>() != null)
                {
                    temp = c;
                    break;
                }
            }

            if (temp == null)
            {
                return;
            }

            dickHit = UnityEngine.Object.Instantiate(temp.gameObject, mouseOverOverlay).transform;
            dickHit.name = "dickHitbox";

            var wvl = dickHit.GetComponent<WoundViewLimb>();
            wvl.limb = view.limbImages.Length - 1;
            wvl.woundview = view;
        }

        dickHit.SetAsLastSibling();

        //hitbox for dickImage
        RectTransform hitRT = (RectTransform)dickHit;
        RectTransform srcRT = dickImage.rectTransform;
        hitRT.anchorMin = srcRT.anchorMin;
        hitRT.anchorMax = srcRT.anchorMax;
        hitRT.pivot = srcRT.pivot;
        hitRT.sizeDelta = srcRT.sizeDelta;
        hitRT.position = srcRT.position;
    }

    // UpdateView zeroes inner RGB at full muscle health; caninedickbase would disappear under outline fill.
    private static void KeepDickBaseVisible(WoundView view)
    {
        if (view.limbImages == null || view.limbImages.Length == 0)
        {
            return;
        }

        Image outer = view.limbImages[^1];
        if (outer == null || outer.gameObject.name != "Dick" || outer.transform.childCount == 0)
        {
            return;
        }

        Image inner = outer.transform.GetChild(0).GetComponent<Image>();
        Color c = inner.color;
        inner.color = new Color(1f, c.g, c.b, c.a);
    }

    private static void ClampLimbLookingAt(WoundView view)
    {
        if (view.limbImages == null || view.limbImages.Length == 0)
        {
            return;
        }

        if (view.limbLookingAt < 0 || view.limbLookingAt >= view.limbImages.Length)
        {
            view.limbLookingAt = 0;
        }
    }
}

[HarmonyPatch(typeof(WoundView))]
internal class LegalStuff
{
    // Yeaaaah no we're not doing that here.
    [HarmonyPatch("SetCharDetails")]
    [HarmonyPrefix]
    public static void WoundView_SetCharDetails_Prefix(ref int age)
    {
        PatchLogging.LogPatchCall();
        int randomAddition = UnityEngine.Random.Range(0, 18);
        age += randomAddition;

        age = Mathf.Clamp(age, 18, 40);
    }
}
