# Spotify Sync — Minecraft 1.21.1 (NeoForge)

Полная синхронизация Minecraft со Spotify: мини-плеер в HUD, расширенный плеер по клику,
текст песни на экране и 3D-текст вокруг игрока. Оформление — по дизайн-системе
**void-canvas agent noir** (чёрный фон, near-black карточки, hairline-границы, один
рационированный акцент `#0099FF`, плёночное зерно, GT-Walsheim-подобная типографика).

Мод полностью клиентский (`side = "CLIENT"`), на серверах работает без установки на сервер.

---

## Возможности

### Мини-плеер
- Обложка трека, название (с marquee-прокруткой), исполнитель, эквалайзер, таймкод,
  тонкая полоса прогресса.
- Позиция: 6 вариантов привязки (верх/низ × лево/центр/право) + смещения X/Y,
  масштаб и прозрачность.
- Рисуется поверх любого экрана с курсором (инвентарь, чат, меню) и там становится
  кликабельным. Клик → расширенный плеер.

### Расширенный плеер
- Большая обложка, display-заголовок, исполнитель, альбом.
- Прошедшее время / полная длина трека, перетаскиваемый seek-бар (клик, drag, колесо = ±5 c).
- Управление: предыдущий / пуск-пауза / следующий / shuffle / repeat (off → context → track).
- Слайдер громкости, glass-плитки с метаданными (устройство, режим повтора, статус лирики),
  превью текста песни, кнопка «Открыть в Spotify».

### Текст песни (LRCLIB, synced `.lrc`)
Режимы (переключение клавишей `L` или в настройках):
1. **Off** — выключено
2. **Screen** — строки по центру экрана: активная белым, соседние — приглушённые,
   karaoke-линия заполняется акцентом
3. **3D around player** — строки орбитой вращаются вокруг игрока в мире, billboard к камере,
   лёгкое парение, опционально видны через блоки
4. **Screen + 3D** — одновременно

Настройки: радиус, высота, масштаб, количество строк, скорость вращения, прозрачность,
сдвиг тайминга (±5 с), количество контекстных строк.

---

## Установка и подключение Spotify

1. Установите NeoForge для Minecraft **1.21.1** и положите jar мода в `mods/`.
2. Создайте приложение на <https://developer.spotify.com/dashboard>:
   - Redirect URI: `http://127.0.0.1:8910/callback`
   - API: **Web API**
3. Скопируйте **Client ID** (Client Secret не нужен — используется OAuth PKCE).
4. В игре нажмите `K` → вкладка **Account** → вставьте Client ID → **Connect**.
   Откроется браузер, подтвердите доступ — окно скажет, что можно вернуться в игру.
5. Токены хранятся в `config/spotifysync-auth.json`, настройки — в `config/spotifysync.json`.

> Для перемотки/пауза/следующий трек Spotify требует **Premium**. Чтение текущего трека
> работает и на бесплатном аккаунте.

### Клавиши
| Клавиша | Действие |
|---|---|
| `K` | Настройки |
| `J` | Расширенный плеер |
| `L` | Режим текста песни |
| — | Мини-плеер вкл/выкл, пуск/пауза, вперёд, назад (по умолчанию не назначены) |

---

## Сборка

### GitHub Actions (основной способ)
1. Создайте репозиторий и запушьте содержимое этой папки.
2. Workflow `.github/workflows/build.yml` запускается на push/PR и вручную
   (**Actions → Build → Run workflow**).
3. Готовый jar лежит в артефактах запуска: `spotifysync-jar` (и `spotifysync-sources`).
4. Тег `v1.0.0` → автоматически создаётся GitHub Release с jar-ами.

```bash
git init && git add . && git commit -m "Spotify Sync 1.0.0"
git branch -M main
git remote add origin git@github.com:<user>/<repo>.git
git push -u origin main
```

### Локально
```bash
./gradlew build        # jar в build/libs
./gradlew runClient    # запуск клиента с модом
```
Нужен JDK 21.

### Версии
`gradle.properties`:
- `minecraft_version=1.21.1`
- `neo_version=21.1.209` (при желании поднимите до последней 21.1.x)
- ModDevGradle `2.0.78` (в `build.gradle`), Gradle `8.10.2` (в workflow)

---

## Структура

```
src/main/java/com/voidcanvas/spotifysync/
├── SpotifySync.java              точка входа
├── config/                       SyncConfig (JSON), LyricsMode, HudAnchor
├── spotify/                      SpotifyAuth (PKCE + loopback), SpotifyApi, PlaybackState
├── lyrics/                       LrcLibClient, LyricsManager, TrackLyrics, LyricLine
└── client/
    ├── SpotifyManager.java       опрос состояния, управление, кэши
    ├── CoverArtCache.java        загрузка обложек в DynamicTexture
    ├── ClientEvents.java         HUD-слои, экраны, клавиши, RenderLevelStage
    ├── render/                   UiTheme, UiRender, Lyrics3DRenderer
    ├── hud/                      MiniPlayerHud, LyricsHud
    └── screen/                   ExpandedPlayerScreen, SpotifySettingsScreen, widget/
```

## Приватность
Мод обращается только к `accounts.spotify.com`, `api.spotify.com`, CDN обложек
(`i.scdn.co`) и `lrclib.net`. Никакой телеметрии, токены не покидают ваш компьютер.

## Лицензия
MIT — см. `LICENSE`.
