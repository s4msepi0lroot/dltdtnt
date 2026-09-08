using NAudio.Wave;
using Newtonsoft.Json.Linq;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Security.Cryptography;
using UnityEngine;

namespace ScavPrototypeSexMod.Managers;

public static class FileLoader
{
    // Loads files that have been set to Embedded Resource in the Build Action file properties
    public static (string, Stream) LoadFileStream(string fileName, string folderName = null)
    {
        // Gets the file name. If it isn't exact, it returns null
        Assembly asm = Assembly.GetExecutingAssembly();
        string mathched = asm.GetManifestResourceNames().FirstOrDefault(n => n.EndsWith(fileName, StringComparison.OrdinalIgnoreCase));

        if (fileName == null)
        {
            Debug.LogError($"File by the name of {fileName} does not exist. Check capitalization and file extension");
            return (null, null);
        }

        fileName = mathched;

        // Gets the file stream
        Stream stream = asm.GetManifestResourceStream(fileName);
        if (!fileName.StartsWith(Assembly.GetExecutingAssembly().GetName().Name + "." + (folderName != null ? folderName + "." : "")))
        {
            Debug.LogError($"File does not exist in embedded resources");
            return (null, null);
        }
        int lastDot = fileName.LastIndexOf(".");
        int secondLastDot = fileName.LastIndexOf(".", lastDot - 1);
        return (fileName[(secondLastDot + 1)..], stream);
    }

    public static (string, byte[]) LoadFileBytes(string fileName)
    {
        (string, Stream) fileInfo = LoadFileStream(fileName);
        Stream stream = fileInfo.Item2;
        byte[] fileData = new byte[stream.Length];
        stream.Read(fileData, 0, fileData.Length);
        return (fileInfo.Item1, fileData);
    }

    public static AudioClip LoadEmbeddedAudio(string fileName)
    {
        (string, Stream) streamData = LoadFileStream(fileName);
        string newFileName = streamData.Item1;
        Stream stream = streamData.Item2;

        string fileExt = Path.GetExtension(newFileName)
            .TrimStart('.')
            .ToLowerInvariant();

        ISampleProvider provider;
        switch (fileExt)
        {
            case "wav":
                provider = new WaveFileReader(stream).ToSampleProvider();
                break;

            case "mp1":
            case "mp2":
            case "mp3":
                provider = new Mp3FileReader(stream).ToSampleProvider();
                break;

            case "cue":
                provider = new CueWaveFileReader(stream).ToSampleProvider();
                break;

            case "aif":
            case "aiff":
                provider = new AiffFileReader(stream).ToSampleProvider();
                break;

            default:
                Debug.LogError($"Could not load audio file {fileName}: Unknown file extension {fileExt}");
                return null;
        }

        // Reads file data sample rate and channels to make a samples data array
        List<float> samples = [];
        float[] buffer = new float[provider.WaveFormat.SampleRate * provider.WaveFormat.Channels];
        int read;
        while ((read = provider.Read(buffer, 0, buffer.Length)) > 0)
        {
            for (int i = 0; i < read; i++)
            {
                float amplified = buffer[i];

                // Clamp so we don't distort into NaNs
                amplified = Mathf.Clamp(amplified, -1f, 1f);

                samples.Add(amplified);
            }
        }
        // Sets mandatory variables for sample rate, channels, and samples/channel
        WaveFormat waveFormat = provider.WaveFormat;
        int sampleRate = waveFormat.SampleRate;
        int channels = waveFormat.Channels;
        int samplesPerChannel = samples.Count / channels;

        // Creates and returns the audio clip
        AudioClip clip = AudioClip.Create(fileName, samplesPerChannel, channels, sampleRate, false);
        clip.SetData([.. samples], 0);
        return clip;
    }

    public static Sprite LoadEmbeddedSprite(string fileName, float ppu = 100, FilterMode filterMode = FilterMode.Point, Vector2? pivot = null)
    {
        try
        {
            (string, Stream) streamData = LoadFileStream(fileName);
            string newFilename = streamData.Item1;
            Stream stream = streamData.Item2;

            if (stream == null)
            {
                ModdedLogger.Warning($"[LoadEmbeddedSprite] Stream is null for file: {fileName}");
                return null;
            }

            byte[] fileData = new byte[stream.Length];
            int bytesRead = stream.Read(fileData, 0, fileData.Length);
            if (bytesRead != fileData.Length)
            {
                ModdedLogger.Warning($"[LoadEmbeddedSprite] Read {bytesRead}/{fileData.Length} bytes for file: {fileName}");
            }

            Texture2D texture = new(2, 2, TextureFormat.RGBAHalf, false)
            {
                wrapMode = TextureWrapMode.Clamp,
                filterMode = filterMode,
                name = newFilename
            };

            if (!texture.LoadImage(fileData))
            {
                ModdedLogger.Warning($"[LoadEmbeddedSprite] Failed to load image data for file: {fileName}");
                return null;
            }

            texture.Apply();

            Sprite sprite = Sprite.Create(
                texture,
                new Rect(0, 0, texture.width, texture.height),
                pivot ?? new Vector2(0.5f, 0.5f),
                ppu
            );
            sprite.name = newFilename;

            return sprite;
        }
        catch (Exception ex)
        {
            ModdedLogger.Error($"[LoadEmbeddedSprite] Exception while loading sprite '{fileName}': {ex}");
            return null;
        }
    }

    // Loading embedded fonts
    public static Font LoadEmbeddedFont(string EmbeddedfontPath)
    {
        (string, byte[]) fileInfo = FileLoader.LoadFileBytes(EmbeddedfontPath);
        string modDir = Path.Combine(Application.persistentDataPath, Assembly.GetExecutingAssembly().GetName().Name);
        Directory.CreateDirectory(modDir);
        string fontPath = Path.Combine(modDir, fileInfo.Item1);
        SHA256 sha256 = SHA256.Create();
        if (!File.Exists(fontPath))
            File.WriteAllBytes(fontPath, fileInfo.Item2);
        else
        {
            byte[] existingFileSha256 = sha256.ComputeHash(File.OpenRead(fontPath));
            byte[] embeddedFileSha256 = sha256.ComputeHash(fileInfo.Item2);
            if (!embeddedFileSha256.SequenceEqual(existingFileSha256))
            {
                ModdedLogger.Info("Font hash isn't the same, overwriting");
                File.WriteAllBytes(fontPath, fileInfo.Item2);
            }
        }

        Font cusFont = new(fontPath);

        return cusFont;
    }

    public static AnimationClip[] LoadEmbeddedBundle(byte[] bundleData, params string[] clipNames)
    {
        AssetBundle bundle = AssetBundle.LoadFromMemory(bundleData);
        if (bundle == null)
        {
            Debug.LogError("Failed to load AssetBundle from embedded data!");
            return null;
        }

        AnimationClip[] clips = new AnimationClip[clipNames.Length];
        for (int i = 0; i < clipNames.Length; i++)
            clips[i] = bundle.LoadAsset<AnimationClip>(clipNames[i]);

        bundle.Unload(false);
        return clips;
    }

    public static T LoadEmbeddedBundleAsset<T>(byte[] bundleData, string assetName) where T : UnityEngine.Object
    {
        AssetBundle bundle = AssetBundle.LoadFromMemory(bundleData);
        if (bundle == null)
        {
            ModdedLogger.Error("Failed to load AssetBundle from embedded data!");
            return null;
        }
        T asset = bundle.LoadAsset<T>(assetName);
        if (asset == null)
        {
            ModdedLogger.Error(
                $"Asset '{assetName}' not found in bundle. Available: {string.Join(", ", bundle.GetAllAssetNames())}");
        }
        bundle.Unload(false);
        return asset;
    }

    public static Dictionary<string, AnimationClip> LoadEmbeddedClipMap(byte[] bundleData)
    {
        AssetBundle bundle = AssetBundle.LoadFromMemory(bundleData);
        if (bundle == null)
        {
            ModdedLogger.Error("Failed to load AssetBundle from embedded data!");

            return [];
        }

        Dictionary<string, AnimationClip> clips = [];
        foreach (AnimationClip clip in bundle.LoadAllAssets<AnimationClip>())
        {
            if (clip != null && !clips.ContainsKey(clip.name))
            {
                clips[clip.name] = clip;
            }
        }

        bundle.Unload(false);

        return clips;
    }

    public static JObject LoadLocale(string localeCode)
    {
        try
        {
            string resourceName = $"ScavPrototypeSexMod.Assets.Locale.{localeCode}.json";

            Assembly assembly = Assembly.GetExecutingAssembly();

            using Stream stream = assembly.GetManifestResourceStream(resourceName);
            if (stream == null)
            {
                ModdedLogger.Error($"Locale {localeCode}: resource not found");
                return null;
            }

            using StreamReader reader = new(stream);
            string jsonText = reader.ReadToEnd();

            return JObject.Parse(jsonText);
        }
        catch (Exception ex)
        {
            ModdedLogger.Error($"Failed to load locale {localeCode}: {ex.Message}");

            return null;
        }
    }

    public static string GetLocale(string category, string key)
    {
        JObject locale = FileLoader.LoadLocale("en")[category] as JObject;
        return locale[key].ToString();
    }
}

public static class ObjectFinder
{
    public static Transform FindRecursive(Transform parent, string name)
    {
        foreach (Transform child in parent)
        {
            if (child.name == name)
                return child;

            var result = FindRecursive(child, name);

            if (result != null)
                return result;
        }

        return null;
    }
}
