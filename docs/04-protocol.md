# 04. Протокол

## REST

База: `http(s)://хост:порт`

| Метод | Путь | Тело | Ответ |
|---|---|---|---|
| GET | `/api/health` | — | `{ ok, name, version, motd, maintenance, users, lobbies }` |
| POST | `/api/register` | `{ login, password }` | `{ ok, token, user }` |
| POST | `/api/login` | `{ login, password }` | `{ ok, token, user }` |
| GET | `/api/me` | заголовок `Authorization: Bearer <token>` | `{ ok, user }` |
| POST | `/api/password` | `{ oldPassword, newPassword }` + Bearer | `{ ok }` |
| GET | `/api/lobbies` | — | `{ ok, lobbies: [...] }` (только открытые) |

Ошибки: HTTP 4xx и `{ ok:false, error:"текст" }`.

Логин: 3–20 символов, `a-z A-Z 0-9 _ . -`. Пароль: от 4 символов.

## WebSocket

Адрес: `ws(s)://хост:порт/ws?token=<token>`.
Текстовые сообщения — JSON с полем `t`. Бинарные — кадры видео.

### Бинарный кадр

```
байт 0        = 0x01 (версия/тип кадра)
байты 1..8   = uint64 little-endian, метка времени в мс
байты 9..    = JPEG
```

Шлёт только хост, сервер пересылает байты остальным без изменений. Лимит 4 МБ.

### Клиент → сервер

| `t` | Поля | Кто может |
|---|---|---|
| `lobby.list` | — | все |
| `lobby.create` | `name, maxPlayers, visibility, password, allowList` | все |
| `lobby.join` | `id, password` | все |
| `lobby.leave` | — | участник |
| `lobby.close` | — | хост |
| `lobby.update` | `name?, maxPlayers?, visibility?, password?, allowList?` | хост |
| `lobby.setSlot` | `login, slot` | хост |
| `lobby.kick` | `login, reason?` | хост |
| `lobby.ban` | `login, reason?` | хост |
| `lobby.unban` | `login` | хост |
| `lobby.start` | — | хост |
| `lobby.stop` | — | хост |
| `lobby.chat` | `text` | участник |
| `input` | `action, down, seq` | гость |
| `ping` | — | все |
| `admin.*` | см. `07-admin.md` | только админ |

### Сервер → клиент

| `t` | Поля | Когда |
|---|---|---|
| `hello` | `user, motd, serverVersion` | сразу после подключения |
| `lobby.list` | `lobbies[]` | ответ на `lobby.list` |
| `lobby.joined` | `lobby, slot, isHost` | вошли/создали |
| `lobby.state` | `lobby` | любое изменение лобби |
| `lobby.chat` | `from, text, system, rainbow` | сообщение в чат |
| `lobby.closed` | `reason` | хост/админ закрыл лобби |
| `lobby.left` | — | вы вышли |
| `game.started` | `slot` | хост нажал START |
| `game.stopped` | — | игра остановлена |
| `input` | `slot, action, down` | только хосту |
| `kicked` | `reason, banned` | вас выгнали |
| `announce` | `text` | broadcast админа |
| `profile.updated` | `user` | ваш профиль изменили |
| `error` | `error` | любая ошибка |
| `pong` | `time` | ответ на ping |

### Объект лобби

```json
{
  "id": "AB12CD",
  "name": "Лобби Kris",
  "host": "s4msepi0l",
  "maxPlayers": 2,
  "visibility": "open",
  "hasPassword": false,
  "state": "idle",
  "allowList": [],
  "bans": [],
  "members": [
    { "login": "s4msepi0l", "display": "s4msepi0l", "slot": 1,
      "isHost": true, "rainbow": true, "nameColor": "", "badge": "OWNER", "rank": "admin" }
  ]
}
```

`state`: `idle` или `playing`. `visibility`: `open` или `closed`.

### Действия ввода

`Up, Down, Left, Right, Confirm, Cancel, Menu, Special, Run, Ctrl`

Любое другое значение сервер игнорирует.

### Пример полного цикла

```
гость  → { "t":"lobby.join", "id":"AB12CD", "password":"" }
сервер → { "t":"lobby.joined", "slot":2, "isHost":false, "lobby":{...} }
хост   → { "t":"lobby.start" }
сервер → { "t":"game.started" }  (всем)
хост   → <бинарный кадр> ... 30 раз в секунду
гость  → { "t":"input", "action":"Right", "down":true, "seq":41 }
сервер → { "t":"input", "slot":2, "action":"Right", "down":true }  (только хосту)
хост   → SendInput: клавиша "Right" из SlotGameKeys[2] нажата
```
