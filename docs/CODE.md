# Разбор кода DeltaDotNet

Файл поясняет назначение каждого исходника и его ключевые типы.

```
deltadotnet/
├─ server/
│  ├─ src/{index,ws,auth,store,lobby}.js
│  ├─ e2e-test.js
│  ├─ Dockerfile, package.json, .env.example
├─ client/DeltaDotNet.Client/
│  ├─ Program.cs, AppConfig.cs, DeltaDotNet.Client.csproj
│  ├─ Ui/DeltaTheme.cs
│  ├─ Forms/{MainForm,HostForm,ViewerForm,BindingsForm}.cs
│  ├─ Net/RelayClient.cs
│  ├─ Capture/ScreenCapturer.cs
│  └─ Input/{KeyMap,Bindings,InputInjector}.cs
├─ docs/, .github/workflows/build.yml
```

## Сервер

### `server/src/ws.js`
Реализация WebSocket с нуля по RFC 6455, чтобы обойтись без npm-зависимостей.

- `attach(server, { path, maxPayload, onConnection })` — перехват `upgrade`,
  вычисление `Sec-WebSocket-Accept` = `base64(sha1(key + GUID))`.
- `encodeFrame(op, payload, { mask })` — сборка кадра (используется и в тесте).
- `WsConnection` — `sendJson`, `sendBinary`, `bufferedAmount`, `ctx`,
  события `message` / `close` / `error`, сборка фрагментов, ping каждые 20 с.

### `server/src/store.js`
`UserStore` — хранилище пользователей в JSON-файле: `create`, `verify`, `has`.
Пароль — scrypt с солью, сравнение `timingSafeEqual`. Запись атомарная
(временный файл + `rename`).

### `server/src/auth.js`
`TokenService.sign(login)` и `.verify(token)` — подписанные HMAC-SHA256 токены
со сроком жизни. Секрет — `AUTH_SECRET`.

### `server/src/lobby.js`
Модель лобби на 2–4 игроков.

| Экспорт | Смысл |
| --- | --- |
| `ROLES` | `['P1','P2','P3','P4']` |
| `MIN_PLAYERS` / `MAX_PLAYERS` | 2 / 4 |
| `normalizeMaxPlayers(v)` | проверка размера лобби, `null` при ошибке |
| `randomCode()` | код из 6 символов без похожих букв (без O, I, 0, 1) |
| `Lobby` | слоты игроков, `toPublic()`, `broadcast()`, `detach()` |
| `LobbyManager` | создание, поиск по коду, удаление, список |

`detach()` различает уход гостя (`peer_left`, слот освобождается) и уход хоста
(`lobby_closed` всем, лобби удаляется).

### `server/src/index.js`
Точка входа: HTTP `/health` и WebSocket `/ws`, вся маршрутизация сообщений
протокола v2. Проверки: авторизация, размер лобби, роль отправителя,
допустимость действия (`ACTIONS`). Бинарные кадры рассылаются всем гостям
с пропуском перегруженных соединений.

### `server/e2e-test.js`
Сквозной тест без фреймворков: поднимает сервер и играет полный сценарий
на сырых сокетах — рукопожатие, авторизация, лобби на троих, видео, действия,
обрывы. Запускается в CI перед сборкой клиента.

## Клиент

### `Program.cs`
Включает DPI-режим `PerMonitorV2`, читает конфиг и открывает `MainForm`,
после показа окна вызывает `TryAutoLoginAsync()`.

### `AppConfig.cs`
Настройки в `%APPDATA%\DeltaDotNet\config.json`.

| Поле | Назначение |
| --- | --- |
| `ServerUrl`, `Login`, `Token` | подключение и автовход |
| `Fps`, `JpegQuality`, `MaxWidth` | качество трансляции |
| `PlayerCount` | сколько игроков создавать в лобби (2–4) |
| `MyBindings` | мои клавиши → действия |
| `GameKeys` | для хоста: действия → клавиши игры, по ролям |

`Normalized()` обрезает значения до допустимых диапазонов, чтобы испорченный
файл не ломал запуск.

### `Ui/DeltaTheme.cs` (новый)
Вся стилистика Deltarune в одном месте.

- Палитра: чёрный фон, белые рамки и текст, жёлтый `#FFD500` для выбора,
  красный для сердечка.
- Подбор шрифта: ищет в системе Determination Mono, 8bitoperator, Pixel Operator,
  Press Start 2P; если ничего нет — Consolas.
- `ApplyForm`, `DrawFrame`, `DrawHeart` (пиксельное сердечко-курсор), `Title`, `Caption`.
- Контролы: `DeltaButton`, `DeltaTextBox`, `DeltaListBox`, `DeltaPanel` — все с ручной
  отрисовкой, без стандартного вида Windows.

### `Input/KeyMap.cs`
Таблица физических клавиш.

- `KeyDef(Scan, Extended, Title)` — аппаратный код и человеческое название.
- `FromScan(scan, extended)` — обратный поиск для редактора управления.
- `GameAction` — девять действий, их русские названия и `IsValid`.

Работа идёт по scan-кодам, а не по символам — русская раскладка не мешает.

### `Input/Bindings.cs`
Набор привязок «действие ↔ клавиша»: `ActionFor(key)`, индексатор по действию,
`Default(role)` с заводскими раскладками P1–P4, `Clone`, `Describe`,
`ToDictionary`/`FromDictionary` для сохранения в конфиг.

### `Input/InputInjector.cs`
Синтез нажатий через `SendInput` со скан-кодами. Помнит удерживаемые
клавиши и умеет `ReleaseAll()` — защита от «залипания» при обрыве связи.
У хоста свой экземпляр на каждую роль.

### `Net/RelayClient.cs`
Обёртка над WebSocket.

- `ConnectAsync` — три попытки: штатный `ClientWebSocket`, затем **ручное
  рукопожатие** `ConnectRawAsync` — обход ошибки `Sec-WebSocket-Accept`,
  которую дают некоторые сетевые фильтры.
- `NormalizeUrl` — принимает `http://`, `https://`, адрес без схемы и без `/ws`.
- `ProbeHealthAsync` и `ConnectNote` — понятные сообщения об ошибках.
- События `OnJson`, `OnBinary`, `OnClosed`.

### `Capture/ScreenCapturer.cs`
Захват экрана или окна (`BitBlt`), масштабирование, сжатие в JPEG и сборка
заголовка из 17 байт. `TryParse` делает обратное на стороне гостя.
`WindowList.Enumerate()` возвращает список видимых окон для выбора источника.

### `Forms/MainForm.cs`
Главное окно: панели «СЕРВЕР И ВХОД», «ЛОББИ», «АКТИВНЫЕ ИГРЫ».
Здесь же кнопка-переключатель **ИГРОКОВ: 2→3→4** (уходит в `maxPlayers`
при создании) и кнопка **МОЁ УПРАВЛЕНИЕ**.

### `Forms/BindingsForm.cs` (новый)
Редактор привязок. Нажатия перехватываются в `ProcessKeyPreview` на уровне
сообщений Windows, scan-код берётся из `lParam`. При конфликте клавиша
снимается со старого действия. Одна форма обслуживает и «мои клавиши», и
«клавиши игры» у хоста.

### `Forms/HostForm.cs`
Окно хоста: код лобби, выбор источника, старт/стоп, список игроков со
свободными слотами, кнопки КЛАВИШИ P2/P3/P4 (активны по размеру лобби),
журнал событий. Таймер по `Fps` гонит кадры, входящие `input` переводятся
в `SendInput` через словарь `InputInjector` по ролям.

### `Forms/ViewerForm.cs`
Окно гостя: отрисовка кадров с сохранением пропорций, перехват клавиш по
scan-коду, отсечение автоповтора, отправка `input`, автоматический `release_all`
при потере фокуса, вызов редактора управления по F2, строка состояния
с числом кадров и задержкой.

### `DeltaDotNet.Client.csproj`
`net8.0-windows`, WinForms, сборка в один self-contained `DeltaDotNet.exe`.
Важно: `<InvariantGlobalization>false</InvariantGlobalization>` — без этого
смена раскладки в поле ввода роняет приложение (`CultureNotFoundException`).

## Сборка

`.github/workflows/build.yml`: тест сервера → публикация клиента → артефакты
`DeltaDotNet-client-win-x64` и `DeltaDotNet-server`; по тегу `v*` собирается релиз.
