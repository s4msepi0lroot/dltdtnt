# 03. Клиент (C# / WPF)

Одно приложение и для хоста, и для гостя. `net8.0-windows`, WPF, **без NuGet-
пакетов** — только базовый .NET и WinAPI.

## Структура

```
DeltaDotNet.Client/
  DeltaDotNet.Client.csproj
  App.xaml(.cs)              запуск, загрузка конфига и темы, crash.log
  MainWindow.xaml(.cs)       окно, шапка, навигация, статус-бар
  Themes/Deltarune.xaml      встроенная тема и все стили
  Assets/                    ваши картинки (см. README.txt внутри)
  Core/                      вся логика
  Views/                     экраны
```

## Core

| Файл | Что делает |
|---|---|
| `AppConfig.cs` | `config.json` в `%AppData%\DeltaDotNet\`: адрес сервера, токен, качество, бинды, тема, громкость, размер окна |
| `Keybinds.cs` | список действий, дефолтные раскладки, поиск действия по клавише, красивые имена клавиш |
| `ApiClient.cs` | REST: health, register, login, me, password |
| `Net.cs` | WebSocket: подключение, события `Message` / `Frame` / `Disconnected`, отправка JSON и бинарных кадров |
| `Native.cs` | все P/Invoke: `BitBlt`, `SendInput`, `EnumWindows`, `GetCursorInfo` и др. |
| `ScreenCapture.cs` | поиск окна по части заголовка, захват и JPEG-сжатие |
| `InputInjector.cs` | `Send(slot, action, down)` — превращает действие в реальное нажатие (scancode + extended), `ReleaseAll`, `FocusGameWindow` |
| `ThemeEngine.cs` | чтение `.ddntheme`, применение цветов/шрифта/фона/логотипа, фоновая музыка |
| `Rainbow.cs` | переливающиеся ники (таймер 50 мс, градиент по HSV) |
| `Session.cs` | текущий вход, профиль, лобби, слот, флаги хоста/админа + хелперы JSON |
| `Streamer.cs` | цикл хоста: захват → JPEG → отправка, счётчики fps и KB/s |

## Экраны (`Views/`)

| Экран | Что там |
|---|---|
| `LoginView` | адрес сервера, логин/пароль, `ENTER`, `CREATE ACCOUNT`, `CHECK SERVER`, автовход по сохранённому токену |
| `LobbyListView` | список открытых лобби, вход по ID+паролю, форма создания (имя, **2–8 игроков**, open/closed, пароль, список гостей), MOTD |
| `LobbyRoomView` | слоты игроков, чат, `KICK` / `BAN` / `UNBAN`, `SLOTS`, `RENAME`, `ACCESS`, **`START THE GAME`**, **`CLOSE THE LOBBY`**, `LEAVE` |
| `GameView` | стрим на весь экран, статистика, у гостя — перехват клавиатуры, у хоста — `FOCUS THE GAME` и `STOP THE GAME` |
| `SettingsView` | вкладки QUALITY / MY KEYS / MOD KEYS / THEMES / ACCOUNT |
| `AdminView` | только для `s4msepi0l`, см. `07-admin.md` |
| `Prompt.cs` | маленькое окно ввода в стиле игры (замена `InputBox`) |

## Как окно всегда помещается

Всё содержимое лежит внутри `Viewbox` над сеткой 1280×720. Что бы вы ни делали
с размером окна — интерфейс масштабируется целиком, ничего не обрезается.
Галочка **SETTINGS → THEMES → «Scale the whole interface to the window»** переключает
`Uniform` (с полями, пропорции сохраняются) и `Fill` (растягивание на всё окно).
Размер и положение окна запоминаются в `config.json`.

## Настройки качества

Вкладка **QUALITY**:

- пресеты Potato / Low / Medium / High / Ultra / Custom;
- ползунки fps, масштаб разрешения, качество JPEG (любое ручное движение ставит Custom);
- режим захвата: окно по заголовку / весь экран / область X-Y-W-H;
- `PICK A WINDOW` — список всех открытых окон;
- курсор в кадре, пропуск одинаковых кадров, оверлей статистики, автофокус игры;
- `TEST THE CAPTURE` — один кадр сразу в превью с размером и весом.

## Настройка клавиш

- **MY KEYS** — ваши собственные кнопки. Нажали `SET` → нажали любую клавишу.
  У каждого игрока свои, хранятся локально.
- **MOD KEYS** — только для хоста: какую реальную клавишу нажимать за игрока №N.
  Здесь же заполняются слоты 3–8.

## Горячие клавиши

| Клавиша | Действие |
|---|---|
| `F11` | полный экран |
| `Esc` в игре | вернуться в лобби |
| `Enter` в чате | отправить |

## Где что лежит на диске

```
%AppData%\DeltaDotNet\
  config.json     все настройки и токен
  crash.log       необработанные ошибки
  themes\         установленные .ddntheme
  cache\          распакованная текущая тема
```
