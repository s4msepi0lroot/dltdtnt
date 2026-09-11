# Rift Spotify

A client-side NeoForge 1.21.1 mod that adds a Spotify companion layer to Minecraft:

- compact player overlay visible over HUDs and screens;
- click the mini-player to open the full player;
- synced progress, duration, play/pause, previous and next;
- lyric HUD and lyrics floating in the world around the player;
- PKCE OAuth login with no client secret stored in the mod;
- GitHub Actions build with Java 21 + Gradle 8.8.

## Build

```bash
gradle build
```

GitHub Actions runs the same command and uploads `build/libs/*.jar` as an artifact.

## Spotify setup

1. Create an app at <https://developer.spotify.com/dashboard>.
2. Add this Redirect URI exactly: `http://127.0.0.1:8765/callback`.
3. Copy the app's Client ID.
4. Start Minecraft, press **O**, paste the Client ID and press **CONNECT SPOTIFY**.
5. Authorize in the browser. Start music in Spotify, then return to Minecraft.

The mod uses the Authorization Code flow with PKCE, so a client secret is not required. The token stays in memory for the current game session; the client ID and visual preferences are stored in `config/rift-spotify.json`.

## Lyrics and licensing

Spotify's public Web API does not provide lyric text. Rift Spotify uses LRCLIB as an optional lyric source and never bundles lyric data. Disable automatic lyrics in the in-game menu if you do not want that network request. Only display lyrics where you have permission to use them.

## Controls

- **O**: open Rift Spotify settings.
- **Click mini-player**: open expanded player on any screen.
- `showHudLyrics`: show the active line at the bottom of the HUD.
- `showWorldLyrics`: billboard the active line around the player in 3D.

## Design direction

The UI follows the attached Void-canvas design prompt: true-black canvas, `#111111` surfaces, electric-blue `#0099FF` used as a rationed accent, compact tool-like panels, and quiet mono-style metadata. Minecraft's built-in font is used for compatibility; the geometry and palette remain themeable without distributing proprietary font files.
