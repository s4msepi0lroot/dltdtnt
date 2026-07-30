# Разбор кода

## Сервер

### `server/src/ws.js`
Своя реализация WebSocket поверх `http`.
- `attach(server, { path, maxPayload, onConnection })` — подвешивает обработку `upgrade`,
  считает `Sec-WebSocket-Accept` = base64(sha1(key + GUID)).
- `WsConnection` — `sendJson`, `sendBinary`, `close(code, reason)`, `bufferedAmount`, `ctx`,
  события `message`, `close`, `error`. Поддерживает фрагменты, ping/pong (20 с),
  собирает кадры из потока в `_readFrame`.
- `encodeFrame(opcode, payload)`, `OP` — коды операций.

### `server/src/store.js`
Хранилище учёток в JSON-файле.
- `UserStore(filePath, adminLogin)`; поля записи: `login, salt, hash, role, banned, banReason,
  cosmetic {rainbow, color, tag}, createdAt, lastSeen`.
- Методы: `has, raw, publicUser, isAdmin, create, verify, touch, setBanned, setRole,
  setCosmetic, setPassword, remove, list`.
- Пароли — `scrypt` с солью, сравнение через `timingSafeEqual`.
- Логин из `adminLogin` получает роль admin автоматически.
- `normalizeCosmetic` режет тег до 16 символов и проверяет цвет по `#RRGGBB`.

### `server/src/auth.js`
`TokenService(secret)` — выдаёт и проверяет токены `login.exp.подпись` (HMAC-SHA256).

### `server/src/lobby.js`
- `Lobby` — `code, name, host, maxPlayers, visibility, joinMode, password, allowList (Set),
  bans (Map), slots, guests, running`.
  Методы: `findByLogin, isBanned, checkJoin, addGuest, removeGuest, ban, unban, banList,
  roleOf, toPublic(full), broadcast(msg, except)`.
- `LobbyManager` — `create, get, list({includePrivate}), remove, close(lobby, reason), detach(conn)`.
- `randomCode()` — 6 символов без похожих букв/цифр.
- Константы: `ROLES` (P1-P4), `MIN_PLAYERS`, `MAX_PLAYERS`, `JOIN_MODES`.

### `server/src/index.js`
Точка входа: HTTP `/health`, поднятие WebSocket, весь разбор протокола в `onJson`.
- `requireAuth`, `requireAdmin`, `hostLobby`, `fail(conn, code, message)`.
- Гостевой `input` проверяется по списку `ACTIONS` и пересылается только хосту.
- Бинарные кадры принимаются только от хоста запущенного лобби.
- Блок admin-команд: списки, косметика, роли, баны, пароль, удаление, закрытие
  чужих лобби, рассылка, статистика. Учётка из `ADMIN_LOGIN` защищена от изменений.

### `server/e2e-test.js`
Сквозной тест без зависимостей. `TestClient` — мини-клиент WebSocket с очередью
входящих сообщений и методом `wait(type)`; `fakeFrame(seq)` строит тестовый кадр.

## Клиент

### `AppConfig.cs`
Сохраняемые настройки: адрес сервера, логин, токен, FPS/качество/ширина,
`PlayerCount`, раскладки клавиш, `LobbyVisibility`, `LobbyJoinMode`, `LobbyPassword`,
`LobbyAllowList`. Все значения нормализуются при загрузке.

### `Net/RelayClient.cs`
- `ConnectAsync(url)` — сначала штатный `ClientWebSocket`, при ошибке рукопожатия —
  собственный `ConnectRawAsync` по голому TCP (до трёх попыток, прокси отключено).
- `SendJsonAsync`, `SendBinaryAsync`, `Close`, `NormalizeUrl`, `ProbeHealthAsync`,
  события `OnJson`, `OnBinary`, `OnClosed`, пояснение `ConnectNote`.

### `Capture/ScreenCapturer.cs`
Снимает окно или экран, масштабирует до `MaxWidth`, жмёт в JPEG с `Quality`,
добавляет 17-байтовый заголовок. `TryParse` разбирает кадр на стороне гостя.
`WindowList.Enumerate()` даёт список окон.

### `Input/`
- `KeyMap.cs` — имена клавиш ↔ скан-коды ↔ `Keys` WinForms.
- `Bindings.cs` — раскладка «действие → клавиша», `Default(role)` для P1-P4.
- `InputInjector.cs` — `SendInput` скан-кодами, учёт зажатых клавиш, `ReleaseAll()`.

### `Ui/`
- `DeltaTheme.cs` — цвета, шрифты и контролы в стиле Deltarune: `DeltaButton`, `DeltaTextBox`,
  `DeltaListBox`, `DeltaPanel`, рамки и сердечко-курсор.
- `DeltaAssets.cs` — поиск картинок в `%APPDATA%\DeltaDotNet\assets` и в папке `assets`
  рядом с exe, кэш, `ApplyIcon(Form)`, контрол `LogoBanner` с текстовым запасным вариантом.
- `RainbowText.cs` — отрисовка переливающихся ников (`Cosmetic`, `ColorAt`, `CosmeticLabel`).

### `Forms/`
- `MainForm.cs` — вход/регистрация, список лобби, выбор числа игроков и типа доступа,
  кнопки КАЧЕСТВО, МОЁ УПРАВЛЕНИЕ и АДМИНКА (только админу).
- `HostForm.cs` — трансляция, настройки качества, список игроков с киком/баном/бан-листом,
  управление доступом, клавиши для гостей, закрытие лобби, журнал.
- `ViewerForm.cs` — окно трансляции, отправка действий, обработка `kicked`,
  `announce`, `lobby_closed`, полный экран по F11.
- `SettingsForm.cs` — три ползунка качества и пресеты.
- `BindingsForm.cs` — редактор клавиш для любого слота.
- `AdminForm.cs` — панели ПОЛЬЗОВАТЕЛИ / ЛОББИ / ЖУРНАЛ, выдача радуги, цвета и тега,
  баны, роли, сброс пароля, удаление, закрытие чужих лобби, рассылка, статистика.

### `assets/`
Папка для ваших картинок: `logo.png`, `icon.ico`, `icon.png`, `heart.png`.
Копируется рядом с exe при сборке, подробности в `assets/README.md`.
