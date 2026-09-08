using HarmonyLib;
using ScavPrototypeSexMod.Managers;
using ScavPrototypeSexMod.Patches.Debug;
using ScavPrototypeSexMod.Registers;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;

namespace ScavPrototypeSexMod.Patches;

[HarmonyPatch(typeof(ConsoleScript))]
internal static class ConsolePatch
{
    [HarmonyPatch("RegisterAllCommands")]
    [HarmonyPrefix]
    public static void ConsoleScript_RegisterAllCommands_Prefix(ConsoleScript __instance)
    {
        PatchLogging.LogPatchCall();
        MethodInfo logMethod = AccessTools.Method(typeof(ConsoleScript), "LogToConsole");

        BodyTypeTop ParseTop(string input)
        {
            return input.ToLower() switch
            {
                "flat" => 0,
                "breasts" => BodyTypeTop.Breasts,
                "none" => BodyTypeTop.None,
                _ => throw new Exception("Invalid top type. Use: flat, breasts, none")
            };
        }

        BodyTypeBottom ParseBottom(string input)
        {
            return input.ToLower() switch
            {
                "pussy" => BodyTypeBottom.Pussy,
                "dick" => BodyTypeBottom.Dick,
                "both" => BodyTypeBottom.Both,
                "none" => BodyTypeBottom.None,
                _ => throw new Exception("Invalid bottom type. Use: pussy, dick, both, none")
            };
        }

        DickTypes ParseDickType(string input)
        {
            return input.ToLower() switch
            {
                "humanoid" => DickTypes.Humanoid,
                "canine" => DickTypes.Canine,
                "tapered" => DickTypes.Tapered,
                "equine" => DickTypes.Equine,
                "barbed" => DickTypes.Barbed,
                "spiked" => DickTypes.Spiked,
                _ => throw new Exception("Invalid dick type. Use: humanoid, canine, tapered, equine, barbed, spiked")
            };
        }

        ConsoleScript.Commands.Add(new Command(
            "sethorny",
            "Sets horniness for the player. Usage: sethorny <0-100>",
            args =>
            {
                if (args.Length < 2)
                    throw new Exception("Usage: sethorny <0-100>");

                if (!float.TryParse(args[1], out float value))
                {
                    logMethod.Invoke(__instance, new object[]
                    {
                        "ARGS: " + string.Join(" | ", args)
                    });
                    throw new Exception($"'{args[1]}' is not a valid number!");
                }

                if (value < 0 || value > 100)
                    throw new Exception("Value must be between 0 and 100.");

                var data = RunContext.BodyData;
                if (data == null)
                    throw new Exception("Body data not initialized.");

                data.horniness = value;

                logMethod.Invoke(__instance, [$"Horniness set to {value}."]);
            },
            null,
            ("horniness", "Horny value (0-100)")
        ));

        ConsoleScript.Commands.Add(new Command(
            "sethardness",
            "Sets hardness for the player. Usage: sethardness <0-100>",
            args =>
            {
                if (args.Length < 2)
                    throw new Exception("Usage: sethardness <0-100>");

                if (!float.TryParse(args[1], out float value))
                {
                    logMethod.Invoke(__instance, new object[]
                    {
                        "ARGS: " + string.Join(" | ", args)
                    });
                    throw new Exception($"'{args[1]}' is not a valid number!");
                }

                if (value < 0 || value > 100)
                    throw new Exception("Value must be between 0 and 100.");

                var data = RunContext.BodyData;
                if (data == null)
                    throw new Exception("Body data not initialized.");

                data.hardness = value;

                logMethod.Invoke(__instance, [$"hardness set to {value}."]);
            },
            null,
            ("hardness", "Hardness value (0-100)")
        ));

        // Make this reload the sprite for the top and bottom body
        ConsoleScript.Commands.Add(new Command(
            "setbody",
            "Sets body options. Usage: setbody <top> <bottom>",
            args =>
            {
                if (args.Length < 3)
                    throw new Exception("Usage: setbody <flat|breasts|none> <pussy|dick|both|none>");

                var top = ParseTop(args[1]);
                var bottom = ParseBottom(args[2]);

                SharedState.savedBodyOptions = (top, bottom);

                Plugin.configSettingsTop.Value = top;
                Plugin.configSettingsBottom.Value = bottom;

                UIManager.UpdateGenderUI();

                logMethod.Invoke(__instance, [$"Top set to {top}, Bottom set to {bottom}."]);
            }, null,
            ("top", "flat | breasts | none"),
            ("bottom", "pussy | dick | both | none")
        ));

        ConsoleScript.Commands.Add(new Command("setdicktype",
            "Sets dicktype. Usage: setdicktype <dick>",
            args =>
            {
                if (args.Length < 2)
                    throw new Exception("Usage: setdicktype <dick>");

                var dicktype = ParseDickType(args[1]);

                if (!Enum.IsDefined(typeof(DickTypes), dicktype))
                    throw new Exception($"Unknown Dicktype '{dicktype}'.");

                var data = RunContext.BodyData;
                if (data == null)
                    throw new Exception("Body data not initialized.");

                data.CurrentDick = dicktype;
                SharedState.savedDickType = data.CurrentDick;
                Plugin.configSettingsDickType.Value = data.CurrentDick;

                LimbManager.UpdateDickSprite();

                WoundView view = RunContext.WoundView;
                if (view == null && RunContext.Camera != null)
                {
                    RunContext.Camera.woundView.SetActive(true);
                    RunContext.Camera.woundView.SetActive(false);
                    view = RunContext.WoundView;
                }

                if (view != null)
                    UIManager.UpdateWVUI(view, dicktype);

                logMethod.Invoke(__instance, [$"Dicktype set to {data.CurrentDick}."]);
            }, null,
            ("dicktype", "humanoid | canine | tapered | equine | barbed | spiked")
        ));

        ConsoleScript.Commands.Add(new Command(
            "setrep",
            "Sets trader reputation with the last trader interacted with. Usage: setrep <0-200>",
            args =>
            {
                if (args.Length < 2)
                    throw new Exception("Usage: setrep <0-200>");

                if (!float.TryParse(args[1], out float value))
                {
                    logMethod.Invoke(__instance, new object[]
                    {
                        "ARGS: " + string.Join(" | ", args)
                    });
                    throw new Exception($"'{args[1]}' is not a valid number!");
                }

                if (value < 0 || value > 200)
                    throw new Exception("Value must be between 0 and 200.");

                TraderScript trader = RunContext.Trader;
                if (trader == null)
                    throw new Exception("No trader is currently selected.");

                trader.reputation = value;

                logMethod.Invoke(__instance, [$"Trader Reputation set to {value}."]);
            }, null, ("traderrep", "Reputation value (0-200)")
        ));

        ConsoleScript.Commands.Add(new Command(
            "setprotection",
            "Sets whether or not the player has a condom on or not. Usage: setprotection <true|false>)",
            args =>
            {
                if (args.Length < 1)
                    throw new Exception("Usage: setprotection <true|false>");

                bool protection = Convert.ToBoolean(args[1]);

                var data = RunContext.BodyData;
                if (data == null)
                    throw new Exception("Body data not initialized.");

                data.wearingCondom = protection;

                if (!protection)
                {
                    logMethod.Invoke(__instance, [$"Removed protection."]);
                }
                else
                {
                    logMethod.Invoke(__instance, [$"Added protection."]);
                }
            },
            null,
            ("protection", "<true|false>")
        ));

        ConsoleScript.Commands.Add(new Command(
            "setstd",
            "Applies an STD to the player. Usage: setstd <std> <true|false>",
            args =>
            {
                var stdtype = args[1].ToLower();

                if (args.Length < 3)
                    throw new Exception("Usage: setstd <std> <true|false>");

                if (!bool.TryParse(args[2], out bool value))
                    throw new Exception("Expected true or false.");

                if (!SharedState.stdTypes.ContainsKey(stdtype))
                    throw new Exception($"Unknown STD '{stdtype}'.");

                SharedState.stdTypes[stdtype] = value;
                var data = RunContext.BodyData;
                if (data != null)
                    data.hasSTD = SharedState.stdTypes.Values.Any(v => v);
            },
            null,
            ("stdtype", "syphilis|hiv|herpes|gonorrhoea|scabies|trichomoniasis")
        ));
    }

    [HarmonyPatch("RegisterSpawnEntities")]
    [HarmonyPostfix]
    public static void ConsoleScript_RegisterSpawnEntities_Postfix(ConsoleScript __instance)
    {
        Command spawn = ConsoleScript.SearchExact("spawn");
        if (spawn == null)
        {
            return;
        }

        spawn.argAutofill ??= [];
        if (!spawn.argAutofill.TryGetValue(0, out List<string> fills))
        {
            fills = [];
            spawn.argAutofill[0] = fills;
        }

        foreach (string id in ItemRegistry.RegisteredIds)
        {
            if (!fills.Contains(id))
            {
                fills.Add(id);
            }
        }
    }
}
