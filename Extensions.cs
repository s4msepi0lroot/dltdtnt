using ScavPrototypeSexMod.Data;
using System.Runtime.CompilerServices;

namespace ScavPrototypeSexMod;

public static class BodyExtensions
{
    private static readonly ConditionalWeakTable<Body, ExtraBodyData> bodyData = [];

    public static ExtraBodyData GetAdditionalData(this Body body)
    {
        return bodyData.GetOrCreateValue(body);
    }
}

public static class TraderExtensions
{
    private static readonly ConditionalWeakTable<TraderScript, ExtraTraderData> traderData = [];

    public static ExtraTraderData GetAdditionalTraderData(this TraderScript traderScript)
    {
        return traderData.GetOrCreateValue(traderScript);
    }
}