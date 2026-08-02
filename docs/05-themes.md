# 05. Фреймворк тем и `.ddntheme`

## Что это

`DeltaDotNet.ThemeStudio.exe` — отдельное приложение. В нём собирается тема для
клиента и компилируется в один файл `имя.ddntheme`, который можно кинуть
другу в ЛС.

## Как пользоваться

1. `NEW` — чистая тема.
2. Слева задаёте имя, автора, версию, описание.
3. Цвета — в формате `#RRGGBB` или `#AARRGGBB`, рядом квадратик-превью.
4. Шрифт: либо системный из списка, либо вложить `.ttf` кнопкой `ATTACH A .ttf`.
5. Фон: картинка + прозрачность + режим растягивания.
6. Логотип: картинка, которая заменит надпись в шапке клиента.
7. Музыка: `.mp3` / `.wav`, громкость, зацикливание. `PLAY` — прослушать.
8. Справа — живое превью клиента, обновляется сразу.
9. `COMPILE .ddntheme` — сохранить куда угодно,
   `INSTALL TO THE CLIENT` — сразу в `%AppData%\DeltaDotNet\themes\`.

В клиенте: **SETTINGS → THEMES → LOAD A .ddntheme**. Применяется без перезапуска
и запоминается. `DEFAULT THEME` — вернуть встроенную «Dark World».

## Формат файла

`.ddntheme` — обычный ZIP (можно переименовать в `.zip` и посмотреть):

```
theme.json
assets/background.png     (необязательно)
assets/logo.png           (необязательно)
assets/music.mp3          (необязательно)
assets/font.ttf           (необязательно)
```

### theme.json

```json
{
  "format": 1,
  "name": "Dark World",
  "author": "s4msepi0l",
  "version": "1.0",
  "description": "тёмная тема с жёлтым акцентом",

  "background": "#FF0B0B12",
  "panel":      "#FF14141F",
  "border":     "#FFFFFFFF",
  "text":       "#FFFFFFFF",
  "muted":      "#FF9A9AB5",
  "accent":     "#FFFFD400",
  "accent2":    "#FF00E1FF",
  "danger":     "#FFFF3B3B",
  "success":    "#FF4CFF7A",

  "fontFamily": "Determination Mono",
  "fontFile":   "assets/font.ttf",

  "backgroundImage":   "assets/background.png",
  "backgroundOpacity": 0.35,
  "backgroundStretch": "UniformToFill",

  "music":       "assets/music.mp3",
  "musicVolume": 0.4,
  "musicLoop":   true,

  "logo": "assets/logo.png"
}
```

Все поля кроме `name` необязательны: чего нет — берётся из встроенной темы.
Файл можно собрать и руками любым архиватором.

## Что какой цвет красит

| Поле | Ресурс WPF | Где видно |
|---|---|---|
| `background` | `DdnBackgroundBrush` | фон окна |
| `panel` | `DdnPanelBrush` | шапка, панели, боксы |
| `border` | `DdnBorderBrush` | белые рамки |
| `text` | `DdnTextBrush` | основной текст |
| `muted` | `DdnMutedBrush` | подписи |
| `accent` | `DdnAccentBrush` | заголовки, наведение |
| `accent2` | `DdnAccent2Brush` | второй акцент |
| `danger` | `DdnDangerBrush` | BAN, CLOSE THE LOBBY |
| `success` | `DdnSuccessBrush` | успешные статусы |

## Советы

- Фон тяжелее 4K класть не надо: тема полностью распаковывается в память.
- Шрифты типа Determination Mono дают тот самый вид; проверьте лицензию,
  если собираетесь раздавать тему другим.
- Музыка играет только в меню; во время игры её можно выключить галочкой в настройках.
- Цвет с прозрачностью задаётся через `AA`: `#80000000` — полупрозрачный чёрный.
