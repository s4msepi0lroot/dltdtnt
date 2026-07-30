# Протокол DeltaDotNet, версия 3

Транспорт — WebSocket на пути `/ws`. Два вида сообщений:

- **текстовые** — JSON с обязательным полем `t` (тип сообщения);
- **бинарные** — кадры видео от хоста, сервер пересылает их гостям без разбора.

Сервер v3 несовместим с клиентами v1/v2.

## Бинарный кадр (17 байт заголовка + JPEG)

| Смещение | Размер | Поле |
|-----------|--------|------|
| 0 | 1 | тип кадра, всегда `0x01` (JPEG) |
| 1 | 4 | номер кадра, uint32 LE |
| 5 | 2 | ширина, uint16 LE |
| 7 | 2 | высота, uint16 LE |
| 9 | 8 | время Unix в мс, int64 LE |
| 17 | — | сам JPEG |

## Логические действия

Клиент никогда не передаёт коды клавиш, только действия:
`Up, Down, Left, Right, Confirm, Cancel, Menu, Extra1, Extra2`.
Какая клавиша соответствует действию — личное дело каждой стороны.

## Сообщения клиента → сервера

### Авторизация

| Сообщение | Поля |
|------------|------|
| `register` | `login`, `password` |
| `login` | `login`, `password` |
| `auth_token` | `token` |
| `whoami` | — |

### Лобби

| Сообщение | Поля |
|------------|------|
| `list_lobbies` | — (админ видит и закрытые) |
| `create_lobby` | `name`, `maxPlayers` 2-4, `visibility` `public\|private`, `joinMode` `open\|password\|whitelist`, `password`, `allowList[]` |
| `join_lobby` | `code`, `password` |
| `leave_lobby` | — |
| `close_lobby` | — (только хост) |
| `lobby_settings` | `visibility`, `joinMode`, `password`, `allowList[]` (только хост) |
| `kick` | `login`, `reason` (только хост) |
| `ban` | `login`, `reason` (только хост) |
| `unban` | `login` (только хост) |

### Игра

| Сообщение | Поля |
|------------|------|
| `start` / `stop` | — (только хост) |
| `input` | `action`, `down` (только гость) |
| `release_all` | — (отпустить всё при потере фокуса) |
| `chat` | `text` |
| `ping` / `stats` | — |

### Админ (только роль `admin`)

`admin_users`, `admin_lobbies`, `admin_stats`,
`admin_set_cosmetic {login, rainbow, color, tag}`, `admin_set_role {login, role}`,
`admin_ban {login, reason}`, `admin_unban {login}`, `admin_set_password {login, password}`,
`admin_delete_user {login}`, `admin_close_lobby {code}`, `admin_broadcast {text}`.

## Сообщения сервера → клиента

| Сообщение | Содержание |
|------------|-------------|
| `hello` | `version: 3`, `allowRegister`, `minPlayers`, `maxPlayers`, `actions[]`, `joinModes[]` |
| `auth_ok` | `login`, `token`, `role`, `isAdmin`, `cosmetic` |
| `profile` | `user` — обновлённые данные учётки (например, выдали радугу) |
| `lobby_list` | `lobbies[]` |
| `lobby_created` | `lobby`, `you: "host"`, `role: "P1"` |
| `lobby_joined` | `lobby`, `you: "guest"`, `role` |
| `lobby_state` | `lobby` (у хоста — со списками допуска и банов) |
| `peer_joined` / `peer_left` | `login`, `role`, `lobby` |
| `lobby_left` | — |
| `lobby_closed` | `code`, `reason` |
| `kicked` | `scope: "lobby"\|"server"`, `code`, `banned`, `reason` |
| `announce` | `from`, `text` |
| `started` / `stopped` | — |
| `input` | `role`, `login`, `action`, `down` (только хосту) |
| `release_all` | `role`, `login` |
| `chat` | `from`, `role`, `cosmetic`, `text` |
| `pong`, `stats` | служебные |
| `admin_users` | `users[]` |
| `admin_lobbies` | `lobbies[]` |
| `admin_user` / `admin_user_deleted` | `user` / `login` |
| `admin_lobby_closed` | `code` |
| `admin_broadcast_ok` | `sent` |
| `admin_stats` | `stats {uptimeSec, users, online, lobbies, frames, bytes, allowRegister}` |
| `error` | `code`, `message` |

### Объект лобби

```json
{
  "code": "AB12CD",
  "name": "Игра хоста",
  "host": "player_one",
  "maxPlayers": 3,
  "playerCount": 2,
  "running": true,
  "createdAt": 1750000000000,
  "visibility": "public",
  "joinMode": "open",
  "hasPassword": false,
  "players": [
    { "login": "player_one", "role": "P1", "host": true, "admin": false, "cosmetic": {} }
  ]
}
```

Хосту дополнительно приходят `allowList[]` и `bans[] {login, reason}`.

### Объект украшений (cosmetic)

```json
{ "rainbow": true, "color": "#ff66cc", "tag": "VIP" }
```

## Коды ошибок

`unauthorized, forbidden, banned, bad_json, register_disabled, bad_login, bad_password,
user_exists, bad_credentials, bad_token, no_user, no_lobby, no_player, lobby_full,
lobby_banned, bad_lobby_password, not_invited, self_join, not_host, not_guest, no_guest,
bad_action, bad_max_players, bad_join_mode, bad_role, bad_text, cannot_ban_admin,
cannot_demote_owner, cannot_delete_owner, unknown_type, internal`

## Ограничения

- Логин: `^[A-Za-z0-9_.-]{3,24}$`, пароль от 6 символов.
- Имя лобби — 40 символов, пароль лобби — 60, чат и объявления — 300, причина — 120.
- Список допуска — до 20 логинов, тег — до 16 символов, цвет — `#RRGGBB`.
- Код лобби — 6 символов из `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`.
- Кадр больше `MAX_FRAME_KB` (по умолчанию 2048 КБ) рвёт соединение.
- Если у гостя в сокете застряло более 4 МБ, кадр ему пропускается.
