using System;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.CompilerServices;

namespace ScavPrototypeSexMod;

// Serves NAudio (and any other bundled dependency) from the mod's embedded resources.
//
// NAudio.Core / NAudio are shipped as <EmbeddedResource> inside this DLL instead of loose
// files, so the CLR can't find them by normal probing. This hook loads them straight from
// the assembly's manifest stream on demand.
//
// It MUST be registered before the first NAudio-touching code runs. FileLoader.LoadEmbeddedAudio
// is invoked from Plugin's static field initializers (Plugin..cctor), which run before Awake().
// A [ModuleInitializer] runs before any other code in this assembly, so the resolver is always
// in place first.
internal static class EmbeddedAssemblyResolver
{
    private static bool _registered;

    [ModuleInitializer]
    internal static void Init()
    {
        if (_registered)
        {
            return;
        }

        _registered = true;
        AppDomain.CurrentDomain.AssemblyResolve += Resolve;
    }

    private static Assembly Resolve(object sender, ResolveEventArgs args)
    {
        string requested = new AssemblyName(args.Name).Name;

        // Only handle the deps we actually bundle (NAudio + its sub-assemblies: NAudio.Core,
        // NAudio.WinMM, ...). Return null for everything else so other resolvers (BepInEx,
        // CUCoreLib, ...) still get their chance.
        if (requested != "NAudio" && !requested.StartsWith("NAudio.", StringComparison.Ordinal))
        {
            return null;
        }

        Assembly self = typeof(EmbeddedAssemblyResolver).Assembly;

        // Match the manifest resource by suffix so we don't hard-code the exact namespace prefix.
        string resourceName = self
            .GetManifestResourceNames()
            .FirstOrDefault(n => n.EndsWith("." + requested + ".dll", StringComparison.OrdinalIgnoreCase));

        if (resourceName == null)
        {
            return null;
        }

        using Stream stream = self.GetManifestResourceStream(resourceName);
        if (stream == null)
        {
            return null;
        }

        byte[] bytes = new byte[stream.Length];
        int offset = 0;
        int read;
        while (offset < bytes.Length && (read = stream.Read(bytes, offset, bytes.Length - offset)) > 0)
        {
            offset += read;
        }

        return Assembly.Load(bytes);
    }
}
