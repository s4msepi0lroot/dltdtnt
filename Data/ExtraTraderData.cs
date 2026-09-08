using System;
using System.Collections.Generic;

namespace ScavPrototypeSexMod;

[Serializable]
public class ExtraTraderData
{
    public Dictionary<string, bool> bdsm = new Dictionary<string, bool>
    {
        { "Switch", false },
        { "Top", false },
        { "Bottom", false },
        { "Masochist", false },
        { "Sadist", false }
    };

    // Determines status and what sex positions you can do with said trader.
    public Dictionary<string, List<string>> bodytype = new()
    {
        { "upperbody", new List<string> { "flat", "breasts", "none" } },
        { "lowerbody", new List<string> { "pussy", "dick", "both", "none" } }
    };

    public float tightness = 100f;
    public float horniness = 0f;
    public float hardness = 100f;
    public bool havingSex = false;
    public bool wearingCondom = false;
    public bool hasSTD = false;

    // Perhaps add a times fucked counter too? Not sure.
    public int timesFucked = 0;
    public int orgasmsHad = 0;
}
