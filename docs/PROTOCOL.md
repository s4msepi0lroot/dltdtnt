# Протокол CoopStream

Транспорт — один WebSocket на клиента: `ws://хост:PORT/ws` (или `wss://...` за TLS-прокси).

- **Текстовые кадры** — JSON-сообщения, всегда с полем `t` (тип).
- **Бинарные кадры** — только видео, только в направлении хост → сервер → гость.

## HTTP-эндпойнты

| Метод | Путь | Ответ |
|---|---|---|
| GET | `/health` | `{"ok":true,"uptime":123.4,"lobbies":1,"clients":2}` |
| GET | `/` | то же самое что `/health` |
| GET | `/stats` | `{"lobbies":[...],"clients":2}` |
| GET | `/ws` | Upgrade → WebSocket |

## Клиент → сервер

### Без авторизации

| `t` | Поля | Описание |
|---|---|---|
| `register` | `login`, `password` | создать аккаунт и сразу войти |
| `login` | `login`, `password` | войти |
| `auth_token` | `token` | войти по сохранённому токену |
| `ping` | — | проверка связи, ответ `pong` |

Ответ на все три вида входа: `auth_ok`.

Валидация: `login` — `/^[A-Za-z0-9_.-]{3,24}$/`, `password` — не менее 6 символов.

### После авторизации

| `t` | Поля | Описание |
|---|---|---|
| `list_lobbies` | — | список открытых лобби → `lobby_list` |
| `create_lobby` | `name`, `hostRole` (`"P1"`\|`"P2"`) | создать лобби → `lobby_created` |
| `join_lobby` | `code` | войти в лобби → `lobby_joined` (хосту — `peer_joined`) |
| `leave_lobby` | — | выйти → `lobby_left` |
| `start` | — | только хост и только при двух участниках → `started` обоим |
| `stop` | — | остановить сессию → `stopped` обоим |
| `input` | `key`, `down` (bool), `ts?` | нажатие/отпускание клавиши |
| `release_all` | — | просьба отпустить все клавиши |
| `chat` | `text` | короткое сообщение напарнику |
| `stats` | — | статистика сервера → `stats` |

Примеры:

```json
{"t":"create_lobby","name":"Моя игра","hostRole":"P1"}
{"t":"join_lobby","code":"K7M2QD"}
{"t":"input","key":"Left","down":true,"ts":1767000000000}
{"t":"input","key":"RShift","down":false}
```

## Сервер → клиент

| `t` | Поля | Когда |
|---|---|---|
| `hello` | `server`, `version`, `allowRegister` | сразу после подключения |
| `auth_ok` | `login`, `token`, `expiresAt` | успешный вход |
| `lobby_list` | `lobbies[]` | ответ на `list_lobbies` |
| `lobby_created` | `lobby`, `role` | вы создали лобби |
| `lobby_joined` | `lobby`, `role` | вы вошли в лобби |
| `peer_joined` | `login`, `role` | к вам присоединились |
| `peer_left` | — | напарник вышел/отвалился |
| `lobby_left` | — | вы вышли |
| `lobby_closed` | — | хост закрыл лобби |
| `started` | `hostRole`, `guestRole` | игра началась |
| `stopped` | — | сессия остановлена |
| `input` | `key`, `down`, `ts?` | ретрансляция нажатия напарника |
| `release_all` | — | отпустить всё |
| `chat` | `from`, `text` | сообщение в чат |
| `pong` | `time` | ответ на `ping` |
| `stats` | `lobbies`, `clients` | ответ на `stats` |
| `error` | `code`, `message` | любая ошибка |

Объект `lobby`:

```json
{
  "code": "K7M2QD",
  "name": "Моя игра",
  "host": "player1",
  "hostRole": "P1",
  "guestRole": "P2",
  "guest": null,
  "running": false,
  "createdAt": 1767000000000
}
```

## Коды ошибок

| Код | Значение |
|---|---|
| `unauthorized` | действие требует входа |
| `bad_json` | сообщение не разобралось |
| `register_disabled` | `ALLOW_REGISTER=0` |
| `bad_login` / `bad_password` | не прошла валидация |
| `user_exists` | логин занят |
| `bad_credentials` | неверный логин/пароль |
| `bad_token` | токен истёк или подделан |
| `no_lobby` | вы не в лобби / код не найден |
| `lobby_full` | в лобби уже двое |
| `self_join` | нельзя войти в своё же лобби |
| `not_host` | действие доступно только хосту |
| `no_guest` | некому играть вторым |
| `key_not_allowed` | клавиша вне белого списка роли |
| `unknown_type` | неизвестный `t` |
| `internal` | внутренняя ошибка сервера |

## Белый список клавиш

```js
P1: ['W','A','S','D','Z','X','P','C','LCtrl','RCtrl','LShift','RShift']
P2: ['Up','Down','Left','Right','Enter','NumEnter','C','LCtrl','RCtrl','LShift','RShift']
```

Скан-коды (PS/2 set 1), которые используются при инжекции:

| Имя | Scan | Extended |
|---|---|---|
| `W` `A` `S` `D` | `0x11` `0x1E` `0x1F` `0x20` | нет |
| `Z` `X` `P` `C` | `0x2C` `0x2D` `0x19` `0x2E` | нет |
| `LShift` / `RShift` | `0x2A` / `0x36` | нет |
| `LCtrl` / `RCtrl` | `0x1D` / `0x1D` | нет / да |
| `Up` `Down` `Left` `Right` | `0x48` `0x50` `0x4B` `0x4D` | да |
| `Enter` / `NumEnter` | `0x1C` / `0x1C` | нет / да |

## Формат бинарного кадра

Всего 17 байт заголовка, далее — JPEG «as is». Порядок байт — little-endian.

| Смещение | Размер | Тип | Поле |
|---|---|---|---|
| 0 | 1 | uint8 | тип кадра, `0x01` = JPEG |
| 1 | 4 | uint32 | номер кадра |
| 5 | 2 | uint16 | ширина |
| 7 | 2 | uint16 | высота |
| 9 | 8 | int64 | время захвата, Unix ms (для замера задержки) |
| 17 | … | bytes | JPEG |

Сервер не заглядывает внутрь кадра — он просто пересылает байты.

## Служебное

- Heartbeat: сервер шлёт `ping` каждые 20 секунд, не ответившие соединения разрываются.
- Максимальный размер кадра — `MAX_FRAME_KB` (по умолчанию 2048 КБ); превышение закрывает соединение.
- Если буфер отправки гостю превышает 4 МБ, очередной кадр отбрасывается.
