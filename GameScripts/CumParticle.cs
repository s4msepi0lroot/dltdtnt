using UnityEngine;

namespace ScavPrototypeSexMod.GameScripts;

/// <summary>
/// Mod copy of vanilla BleedParticle decal logic. Uses mod-provided decal templates
/// instead of Resources.Load and does not attach GroundBlood.
/// </summary>
public class CumParticle : MonoBehaviour
{
    private ParticleSystem _part;
    private ParticleSystem.Particle[] _particles;
    private GameObject _wallDecal;
    private GameObject _groundDecal;
    private byte _spawned;
    private int _every = 1;

    public void Initialize(GameObject groundTemplate, GameObject wallTemplate, int spawnEvery = 1)
    {
        _groundDecal = groundTemplate;
        _wallDecal = wallTemplate;
        _every = Mathf.Max(1, spawnEvery);
    }

    private void Start()
    {
        _part = GetComponent<ParticleSystem>();
        EnsureParticleBuffer();
    }

    private void Update()
    {
        if (_part == null || _groundDecal == null || _wallDecal == null || WorldGeneration.world == null)
        {
            return;
        }

        EnsureParticleBuffer();

        int count = _part.GetParticles(_particles);
        if (count <= 0)
        {
            return;
        }

        for (int i = 0; i < count; i++)
        {
            if (_particles[i].remainingLifetime > 0.02f)
            {
                continue;
            }

            _spawned++;
            if (_spawned < _every)
            {
                continue;
            }

            _spawned = 0;
            Vector2 samplePos = _particles[i].position + Vector3.down * 0.8f;

            if (WorldGeneration.world.GetBlock(samplePos) != 0 && WorldGeneration.world.GetBlock(samplePos + Vector2.up) == 0)
            {
                SpawnGroundDecal(samplePos);
            }
            else
            {
                SpawnWallDecal(_particles[i].position);
            }

            _particles[i].remainingLifetime = 0f;
        }

        _part.SetParticles(_particles, count);
    }

    private void EnsureParticleBuffer()
    {
        if (_part == null)
        {
            return;
        }

        int max = _part.main.maxParticles;
        if (_particles == null || _particles.Length < max)
        {
            _particles = new ParticleSystem.Particle[max];
        }
    }

    private void SpawnGroundDecal(Vector2 samplePos)
    {
        GameObject decal = Object.Instantiate(
            _groundDecal,
            WorldGeneration.world.BlockToWorldPos(WorldGeneration.world.WorldToBlockPos(samplePos)),
            Quaternion.identity);

        decal.SetActive(true);
        decal.transform.localScale = new Vector2(Random.Range(0.7f, 1.3f), Random.Range(0.94f, 1.06f));

        SpriteRenderer renderer = decal.GetComponent<SpriteRenderer>();
        if (renderer != null)
        {
            renderer.flipX = Random.value > 0.5f;
            Color color = renderer.color;
            renderer.color = new Color(color.r, color.g, color.b, Random.Range(0.2f, 0.8f));
        }

        Object.Destroy(decal, 120f);
    }

    private void SpawnWallDecal(Vector3 position)
    {
        GameObject decal = Object.Instantiate(_wallDecal, position, Quaternion.Euler(0f, 0f, Random.Range(0f, 360f)));
        decal.SetActive(true);
        decal.transform.localScale = new Vector2(Random.Range(0.3f, 1.2f), Random.Range(0.3f, 1.2f));

        SpriteRenderer renderer = decal.GetComponent<SpriteRenderer>();
        if (renderer != null)
        {
            renderer.color = new Color(1f, 1f, 1f, Random.Range(0.4f, 1f));
        }

        Object.Destroy(decal, 120f);
    }
}
