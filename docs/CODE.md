# Разбор кода

Документ описывает каждый файл проекта: что внутри, какие функции он экспортирует и где его править.

---

# Сервер (`server/`)

Ни одной внешней зависимости — только встроенные модули Node (`http`, `crypto`, `fs`, `path`).

## `src/ws.js` — WebSocket с нуля (RFC 6455)

Почему свой: чтобы сервер запускался без `npm install` на любом хостинге.

| Экспорт | Описание |
|---|---|
| `attach(server, { path, onConnection, maxPayload, heartbeatMs })` | вешает обработчик `upgrade` на HTTP-сервер, делает рукопожатие (SHA-1 + GUID `258EAFA5-…`), запускает heartbeat |
| `encodeFrame(opcode, payload, { mask, fin })` | собирает WS-кадр (используется и тестом как клиент) |
| `WsConnection` | обёртка над TCP-сокетом |
| `OP` | `{ CONT:0x0, TEXT:0x1, BINARY:0x2, CLOSE:0x8, PING:0x9, PONG:0xa }` |

`WsConnection`:
- методы `sendText`, `sendJson`, `sendBinary`, `ping`, `close`;
- свойства `bufferedAmount` (сколько байт ждёт отправки), `ctx` (произвольные данные сессии), `isAlive`;
- события `message` (`{ type: 'text'|'binary', data }`), `close`, `error`;
- сам собирает фрагментированные кадры (`CONT`), снимает маску клиента, отвечает на `PING`;
- закрывает соединение при превышении `maxPayload`;
- обрабатывает `end` и `close` сокета — важно для мгновенного `peer_left` при обрыве.

## `src/store.js` — аккаунты

Класс `UserStore(filePath)`:

| Метод | Описание |
|---|---|
| `has(login)` | есть ли пользователь |
| `create(login, password)` | создать; бросает `Error('user_exists')` |
| `verify(login, password)` | проверка пароля через `timingSafeEqual` |
| `UserStore.hashPassword(password, salt?)` | `scrypt`, keylen 32 |

Хранение — обычный JSON-файл, запись атомарная (`.tmp` + `rename`), чтобы не потерять базу при падении.
Формат записи: `{ login, salt, hash, createdAt }`. Пароли в открытом виде не хранятся нигде.

## `src/auth.js` — токены

`TokenService(secret, ttlSeconds = 7*24*3600)`:
- `sign(login)` → `base64url(payload) + '.' + base64url(HMAC-SHA256)`;
- `verify(token)` → `{ login, exp }` или `null` (подпись сравнивается `timingSafeEqual`, срок проверяется).

Похоже на JWT, но без лишних сущностей. Смена `AUTH_SECRET` мгновенно аннулирует все токены.

## `src/lobby.js` — лобби

- `randomCode(6)` — код из алфавита `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (без `I`, `O`, `0`, `1` — чтобы не путать при диктовке).
- `Lobby`: `code`, `name`, `host`, `guest`, `hostRole`, `guestRole`, `running`;
  методы `toPublic()`, `other(conn)`, `broadcast(msg)`, `isFull`, `stats`.
- `LobbyManager`: `create()`, `get(code)`, `list()`, `remove(code)`, `detach(conn)`
  (`detach` вызывается при любом разрыве и корректно разбирает оба случая — вышел хост или гость).

Лобби хранятся только в памяти — это осознанное решение (сессии короткие).

## `src/index.js` — точка входа и маршрутизация

1. Читает ENV: `PORT`, `AUTH_SECRET`, `DATA_FILE`, `ALLOW_REGISTER`, `MAX_FRAME_KB`.
   Если `AUTH_SECRET` не задан, в консоль печатается предупреждение.
2. Поднимает HTTP: `/health`, `/`, `/stats`.
3. `attach(server, { path: '/ws', ... })` и разбор сообщений.
4. `ALLOWED_KEYS` — белый список клавиш по ролям (дублирует `KeyPolicy` клиента).

Обрабатываемые типы: `register`, `login`, `auth_token`, `ping`, `list_lobbies`, `create_lobby`,
`join_lobby`, `leave_lobby`, `start`, `stop`, `input`, `release_all`, `chat`, `stats`.
Полное описание — в [PROTOCOL.md](PROTOCOL.md).

Бинарные кадры (`onBinary`): пропускаются только от хоста и только в активной сессии.
Если `guest.bufferedAmount > MAX_BUFFERED` (4 МБ), кадр дропается — так трансляция не «уезжает» в минутную задержку.

Экспортирует `{ server, store, tokens, lobbies, ALLOWED_KEYS }` — удобно для тестов и встраивания.

## `test/e2e.js` — сквозной тест

Запускает сервер в отдельном процессе (`PORT=18081`, временный `DATA_FILE`) и проверяет:

1. **Авторизация** — две регистрации, неверный пароль (`bad_credentials`), действие без входа (`unauthorized`), вход по токену.
2. **Лобби** — создание, список, вход, `peer_joined`.
3. **Старт и видео** — `started` обоим, бинарный кадр доходит байт-в-байт.
4. **Ввод** — `Left` от P2 доходит, `W` от P2 отклоняется (`key_not_allowed`), `release_all`, чат.
5. **Отключение** — при обрыве гостя хост получает `peer_left`.

Запуск: `node server/test/e2e.js` (или `npm test` в `server/`). Этот же тест гоняется в CI.

---

# Клиент (`client/CoopStream.Client/`)

.NET 8, WinForms, только базовые библиотеки (`System.Net.WebSockets`, `System.Text.Json`, GDI+, Win32).

## `CoopStream.Client.csproj`

`net8.0-windows`, `UseWindowsForms=true`, `AssemblyName=CoopStream`, `RuntimeIdentifier=win-x64`,
`PublishSingleFile`, `SelfContained`. На выходе — один `CoopStream.exe` без установки .NET.

## `Program.cs`

`[STAThread]`, DPI-aware, глобальный перехват исключений, загрузка конфига, показ `MainForm`,
попытка автовхода после `Shown`.

## `AppConfig.cs`

Настройки в `%APPDATA%\CoopStream\config.json`:
`ServerUrl`, `Login`, `Token`, `Fps`, `JpegQuality`, `MaxWidth`, `HostRole`.
Методы `Load()` и `Save()` никогда не бросают исключений — битый конфиг просто заменяется дефолтным.

## `Net/RelayClient.cs`

Обёртка над `ClientWebSocket`.

| Член | Описание |
|---|---|
| `ConnectAsync(url)` | подключение и запуск цикла чтения |
| `SendJsonAsync(object)` | JSON-сообщение |
| `SendBinaryAsync(byte[])` | бинарный кадр |
| `OnJson`, `OnBinary`, `OnClosed` | события (вызываются из фонового потока!) |
| `IsConnected` | состояние |

Отправка сериализуется через `SemaphoreSlim`: `ClientWebSocket` не допускает параллельных `SendAsync`.
В формах всё, что касается UI, оборачивается в `BeginInvoke`.

## `Input/KeyMap.cs`

- `KeyMap.ByName` — имя → (скан-код, extended).
- `KeyMap.FromScan(scan, extended)` — обратное преобразование для окна гостя.
- `KeyPolicy.P1` / `KeyPolicy.P2` — белые списки; `IsAllowed(role, key)`; `Describe(role)` — человеческое описание для UI.

Чтобы изменить раскладку, правьте **два** места: `KeyPolicy` в клиенте и `ALLOWED_KEYS` в `server/src/index.js`.
Если добавляете новую клавишу — также внесите её скан-код в `KeyMap.ByName`.

## `Input/InputInjector.cs`

`SendInput` с флагом `KEYEVENTF_SCANCODE` — именно так ввод видят игры на DirectInput.

| Член | Описание |
|---|---|
| `Send(key, down)` | нажать/отпустить клавишу |
| `ReleaseAll()` | отпустить всё зажатое |
| `HeldCount` | сколько клавиш сейчас зажато гостем |

Класс ведёт учёт зажатых клавиш, чтобы гарантированно отпустить их при обрыве связи.
Клавиши попадают в активное окно — отсюда требование держать игру в фокусе.

## `Capture/ScreenCapturer.cs`

- `TargetWindow`, `MaxWidth`, `Quality` — настройки захвата.
- `GetSourceBounds()` — прямоугольник окна или весь экран.
- `CaptureFrame()` → готовый пакет (заголовок + JPEG) или `null`.
- `TryParse(packet, out image, out sequence, out timestampMs)` — разбор на стороне гостя.
- `WindowList.Enumerate()` — список видимых окон для выпадающего списка.

`Bitmap` переиспользуется между кадрами — без этого GC захлёбнётся на 20 FPS.

## `Forms/MainForm.cs`

Авторизация + лобби + журнал. Владеет единственным `RelayClient`, который передаётся в окно сессии.
`TryAutoLoginAsync()` — вход по сохранённому токену. По `started` открывает `HostForm` или `ViewerForm`.

## `Forms/HostForm.cs`

- Фоновый цикл захвата (`Task.Run`) с адаптивной задержкой `1000/fps - время кадра`.
- Флаг `_sending` не даёт копить очередь при медленном канале.
- `HandleJsonFromBackground` инжектит клавиши сразу в фоновом потоке — минус один прыжок через UI-очередь.
- `peer_left`, `lobby_closed`, `stopped`, `release_all` → `ReleaseAll()`.
- F8 — пауза ввода гостя.

## `Forms/ViewerForm.cs`

- `PictureBox` в режиме `Zoom` на чёрном фоне + статус-бар.
- `ProcessKeyPreview` читает `WM_KEYDOWN/WM_KEYUP/WM_SYSKEYDOWN/WM_SYSKEYUP`:
  scan = `(lParam >> 16) & 0xFF`, extended = `(lParam >> 24) & 1`, автоповтор = `(lParam >> 30) & 1`.
- `IsInputKey => true` — иначе WinForms съел бы стрелки и Enter на навигацию.
- При `Deactivate` и закрытии — отпускание всех клавиш и `release_all`.
- Старый `Image` удаляется после замены — иначе утечка памяти.

---

# CI (`.github/workflows/build.yml`)

| Job | Раннер | Что делает |
|---|---|---|
| `client` | `windows-latest` | .NET 8, `dotnet publish` → артефакт `CoopStream-client-win-x64` |
| `server` | `ubuntu-latest` | `node --check`, E2E-тест, артефакт `CoopStream-server` |
| `release` | `ubuntu-latest` | только по тегам `v*`: zip-архивы в GitHub Release |

---

# Типовые доработки

| Задача | Где править |
|---|---|
| Добавить клавишу | `KeyMap.ByName` + `KeyPolicy` + `ALLOWED_KEYS` |
| Добавить третьего игрока | `Lobby` (массив участников вместо `guest`) + ветка `onBinary` |
| Перейти на PNG/другой кодек | `ScreenCapturer` и байт типа кадра |
| Передавать звук | новый тип бинарного кадра `0x02` + WASAPI-захват у хоста |
| Хранить лобби между рестартами | `LobbyManager` → сериализация в `DATA_FILE` |
