# Localization (RU / EN)

Every user-visible phrase in the client lives in **one file**:

```
src/DeltaDotNet.Client/Localization/Strings.cs
```

## Format

```csharp
["room.send"] = new Phrase("SEND", "ОТПРАВИТЬ"),
//   key            English      Russian
```

To change wording in both languages you edit one line. To add a new phrase:

1. add a `["my.key"] = new Phrase("English", "Русский"),` entry to the table;
2. use it in XAML: `Text="{loc:Tr my.key}"`
   (the file needs `xmlns:loc="clr-namespace:DeltaDotNet.Client.Localization"`);
3. or in code: `Loc.T("my.key")`, or `Loc.F("my.key", arg0, arg1)` for phrases with `{0}` placeholders.

## Runtime switching

* Default language: **English** (`AppSettings.Language = "en"`).
* Change it in **Settings → General → Language**, press **SAVE**.
  The current screen is rebuilt immediately, so every `{loc:Tr}` is re-evaluated.
* The choice is stored in `%AppData%\DeltaDotNet\settings.json` (`"language": "ru"`).
* `Loc.SetLanguage(code)` is called at startup in `App.OnStartup`, **before** the first
  window is created — that is why markup extensions resolve in the right language.

## Files

| File | Purpose |
|---|---|
| `Localization/Strings.cs` | the phrase table (EN + RU side by side) |
| `Localization/Loc.cs` | `Loc.T` / `Loc.F`, current language, `{loc:Tr}` markup extension |

Strings that are pure numbers/technical (fps counters, hex process ids, lobby codes)
are intentionally not translated.
