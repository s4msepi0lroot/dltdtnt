using UnityEngine;

namespace ScavPrototypeSexMod.Managers;

internal static class FluidVisuals
{
    internal static Texture2D squareParticleTexture;

    internal static Material CreateSquareParticleMaterial()
    {
        Material material = new(Shader.Find("Sprites/Default"))
        {
            mainTexture = SquareParticleTexture,
            color = Color.white
        };

        return material;
    }

    internal static Texture2D SquareParticleTexture
    {
        get
        {
            if (squareParticleTexture != null)
            {
                return squareParticleTexture;
            }

            squareParticleTexture = new Texture2D(1, 1, TextureFormat.RGBA32, false)
            {
                filterMode = FilterMode.Point,
                wrapMode = TextureWrapMode.Clamp
            };
            squareParticleTexture.SetPixel(0, 0, Color.white);
            squareParticleTexture.Apply();

            return squareParticleTexture;
        }
    }
}
