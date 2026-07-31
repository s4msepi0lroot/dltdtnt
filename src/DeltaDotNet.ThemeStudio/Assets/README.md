# Theme Studio assets

| File | What it is | Recommended size |
|------|------------|------------------|
| `studio.ico` | Windows icon of `DeltaDotNet.ThemeStudio.exe` | multi-size .ico: 16/32/48/256 px |

Drop `studio.ico` into this folder and push. If it is missing, the build simply
uses the default .NET icon (the build target `DropMissingIcon` removes the
reference automatically, so the workflow never fails).
