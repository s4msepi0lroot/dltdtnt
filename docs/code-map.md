# Карта кода

## Сервер — `server/src/`

| Файл | За что отвечает |
|------|-----------------|
| `index.js` | HTTP-сервер, CORS, все REST-эндпоинты, поднятие WebSocket, админ-API |
| `store.js` | Хранилище `db.json`: аккаунты, баны, MOTD; отложенное сохранение, корректное завершение |
| `auth.js` | Регистрация/вход, scrypt-хеши, HMAC-токены, определение владельца (`s4msepi0l`) |
| `lobbies.js` | Лобби в памяти: слоты, пароли/вайтлист, кик/бан, качество, передача хостства |
| `ws.js` | WebSocket-релей: авторизация сокета, разбор сообщений, раздача кадров и ввода, heartbeat |

## Общая библиотека — `src/DeltaDotNet.Core/`

| Файл | За что отвечает |
|------|-----------------|
| `Protocol.cs` | DTO сообщений, `GameAction`, `LobbyInfo`, `UserInfo`, `QualitySettings` с пресетами |
| `KeyBindings.cs` | Виртуальные коды, дефолты для 4 слотов, читаемые имена клавиш |
| `AppSettings.cs` | Загрузка/сохранение `settings.json`, пути к `themes`/`cache` |
| `ApiClient.cs` | HTTP-клиент: авторизация, лобби, все админ-вызовы |
| `RelayClient.cs` | WebSocket-клиент: события лобби/чата/видео/ввода, упаковка бинарных кадров |
| `ThemePackage.cs` | Чтение и запись `.ddntheme`, манифест темы |

## Клиент — `src/DeltaDotNet.Client/`

| Файл | За что отвечает |
|------|-----------------|
| `App.xaml.cs` | Глобальные `Settings`, `Api`, `Relay`, `Theme`, `User`; перехват ошибок |
| `MainWindow.xaml(.cs)` | Шапка с логотипом, навигация, статус-бар, анимация радужных ников |
| `Styles/Deltarune.xaml` | Все стили в духе DELTARUNE, цвета через `DynamicResource` (их меняют темы) |
| `Services/ThemeManager.cs` | Применение тем, импорт `.ddntheme`, фоновая музыка |
| `Services/ScreenCapture.cs` | Захват окна или экрана, масштабирование, JPEG-кодирование |
| `Services/InputInjector.cs` | `SendInput` со скан-кодами, фокус окна игры, отпускание всех клавиш |
| `Views/LoginView` | Адрес сервера, вход/регистрация, автовход по токену |
| `Views/LobbyBrowserView` | Список лобби, вход по коду, создание (число игроков, доступ, качество) |
| `Views/LobbyRoomView` | Участники, готовность, чат, кик/бан, START GAME, CLOSE LOBBY |
| `Views/GameView` | Трансляция (хост) / просмотр (гость), перехват клавиш, статистика |
| `Views/SettingsView` | Качество, бинды для 4 слотов, захват, темы, аккаунт |
| `Views/AdminView` | Админка владельца: радужные ники, бейджи, баны, лобби, броадкаст, MOTD |
| `Views/PromptDialog` | Маленькое модальное окно ввода текста |

## Theme Studio — `src/DeltaDotNet.ThemeStudio/`

| Файл | За что отвечает |
|------|-----------------|
| `MainWindow.xaml(.cs)` | Редактор цветов/шрифта/ресурсов, живой предпросмотр, компиляция в `.ddntheme` |

## Как что-то добавить

- **Новое игровое действие:** добавьте строку в `GameAction.All` и дефолты в `KeyBindings` —
  интерфейс настроек и протокол подхватят его автоматически.
- **Больше игроков, чем 4:** поднимите предел в `lobbies.js` и добавьте дефолтные бинды слота.
- **Новый пресет качества:** `QualitySettings.Preset` в `Protocol.cs` и пункт в `SettingsView.xaml`.
- **Новый цвет темы:** ключ в `ThemeManifest`, кисть в `ThemeManager.ApplyManifest`,
  поле в Theme Studio.
