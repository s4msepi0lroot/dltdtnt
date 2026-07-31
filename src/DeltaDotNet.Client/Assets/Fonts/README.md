# Pixel font for DeltaDotNet

Put a **.ttf** pixel font in **this folder** (`src/DeltaDotNet.Client/Assets/Fonts/`).
The project automatically embeds every `*.ttf` found here (see `DeltaDotNet.Client.csproj`).

## Which font?

Deltarune uses two fonts:

| Game usage | Free look-alike | Where to get it |
|---|---|---|
| Menus / overworld text ("Determination Mono") | **Determination Mono** | fontsgeek / dafont, search "Determination Mono" |
| Battle / dialogue ("Determination Sans") | **Determination Sans** | same sources |
| Generic pixel alternative | **Pixel Operator Mono** | https://www.dafont.com/pixel-operator.font (free, CC0) |

Any of those three works out of the box — the style sheet asks for them in this order:

```
Determination Mono  ->  Pixel Operator Mono  ->  Perfect DOS VGA 437  ->  Consolas
```

## Steps

1. Download the `.ttf`.
2. Drop it here, e.g. `Assets/Fonts/DeterminationMono.ttf`.
3. Rebuild. That's it — no code changes needed.

## If your font has a different family name

Open `src/DeltaDotNet.Client/Styles/Deltarune.xaml` and edit the `UiFont` resource:

```xml
<FontFamily x:Key="UiFont">pack://application:,,,/Assets/Fonts/#YOUR FAMILY NAME, Consolas</FontFamily>
```

The name after `#` is the **family name inside the font file** (visible in the Windows font preview),
not the file name.

> Note: the same folder/instructions apply to the Theme Studio
> (`src/DeltaDotNet.ThemeStudio/Assets/Fonts/`) if you want it to match.
