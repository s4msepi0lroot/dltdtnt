# Client assets — put your images here

This folder is where **you** drop the pictures. Nothing here is required for the build:
if a file is missing, the app silently falls back to text/default visuals.

| File | What it is | Recommended size |
|------|------------|------------------|
| `logo.png` | Logo shown in the client header instead of the text "DELTA DOT NET" | ~600×160 px, transparent PNG |
| `app.ico` | Windows icon of `DeltaDotNet.exe` (taskbar, explorer, shortcut) | multi-size .ico: 16/32/48/256 px |

Steps:

1. Copy `logo.png` and `app.ico` into this exact folder
   (`src/DeltaDotNet.Client/Assets/`).
2. Commit and push — GitHub Actions picks them up automatically
   (`.csproj` includes them conditionally).
3. That's it. No code changes needed.

Themes can also override the logo: a `.ddntheme` package may carry its own logo
image, which wins over `logo.png` while that theme is active.
