# Протокол DeltaDotNet, версия 2

Транспорт — WebSocket (RFC 6455) по пути `/ws`. Сервер реализует его сам,
без библиотек. Два типа кадров:

- **текстовые** — JSON-сообщения с обязательным полем `t` (тип);
- **бинарные** — видеокадры от хоста.

## Роли

| Роль | Кто |
| --- | --- |
| `P1` | хост, создатель лобби, шлёт видео и жмёт клавиши в игре сам |
| `P2`–`P4` | гости, шлют действия и получают видео |

Слоты выдаются по порядку; освободившийся слот может занять новый игрок.
Размер лобби (`maxPlayers`) — от 2 до 4, задаётся при создании.

## Логические действия

По сети никогда не передаются коды клавиш — только девять имён:

```
Up  Down  Left  Right  Confirm  Cancel  Menu  Extra1  Extra2
```

Преобразование «клавиша → действие» делает клиент гостя, обратное
«действие → клавиша» — клиент хоста. Сервер о клавиатурах не знает ничего.

## Сообщения клиента → сервера

| `t` | Поля | Описание |
| --- | --- | --- |
| `register` | `login`, `password` | регистрация |
| `login` | `login`, `password` | вход |
| `auth_token` | `token` | вход по сохранённому токену |
| `list_lobbies` | — | список открытых лобби |
| `create_lobby` | `name`, `maxPlayers` (2–4) | создать лобби |
| `join_lobby` | `code` | войти по коду |
| `leave_lobby` | — | выйти |
| `start` | — | только хост, нужен хотя бы один гость |
| `stop` | — | только хост |
| `input` | `action`, `down` | только гость |
| `release_all` | — | гость потерял фокус, отпустить всё |
| `chat` | `text` (≤300) | сообщение всем в лобби |
| `ping` | `time` | замер задержки |
| `stats` | — | статистика лобби |

## Сообщения сервера → клиента

| `t` | Поля |
| --- | --- |
| `hello` | `version: 2`, `minPlayers: 2`, `maxPlayers: 4`, `actions: [...9]`, `allowRegister` |
| `auth_ok` | `login`, `token` |
| `lobby_list` | `lobbies: [краткие описания]` |
| `lobby_created` | `lobby`, `you: "host"`, `role: "P1"` |
| `lobby_joined` | `lobby`, `you: "guest"`, `role` |
| `peer_joined` / `peer_left` | `lobby`, `login`, `role` |
| `lobby_left` | — |
| `lobby_closed` | `reason` |
| `started` / `stopped` | `lobby` |
| `input` | `role`, `login`, `action`, `down` — только хосту |
| `release_all` | `role`, `login` — только хосту |
| `chat` | `from`, `role`, `text` |
| `pong` | `time` |
| `stats` | `stats: { frames, bytes, players, maxPlayers, running }` |
| `error` | `code`, `message` |

### Объект лобби

```json
{
  "code": "K7QM2X",
  "name": "Вечерняя партия",
  "host": "player_one",
  "maxPlayers": 3,
  "playerCount": 2,
  "running": false,
  "createdAt": 1770000000000,
  "players": [
    { "login": "player_one", "role": "P1", "host": true },
    { "login": "player_two", "role": "P2", "host": false }
  ]
}
```

## Коды ошибок

`unauthorized`, `bad_json`, `register_disabled`, `bad_login`, `bad_password`,
`user_exists`, `bad_credentials`, `bad_token`, `no_lobby`, `lobby_full`,
`self_join`, `not_host`, `not_guest`, `no_guest`, `bad_action`,
`bad_max_players`, `unknown_type`, `internal`.

## Бинарный кадр

Заголовок 17 байт, дальше JPEG:

| Смещение | Размер | Значение |
| --- | --- | --- |
| 0 | 1 | тип кадра, `0x01` = JPEG |
| 1 | 4 | номер кадра, uint32 LE |
| 5 | 2 | ширина, uint16 LE |
| 7 | 2 | высота, uint16 LE |
| 9 | 8 | время Unix в мс, int64 LE |
| 17 | … | тело JPEG |

Сервер пересылает кадр всем гостям без разбора. Если у конкретного гостя
в буфере накопилось больше 4 МБ, кадр для него пропускается (медленный канал
одного игрока не тормозит остальных).

## Пример сеанса

```
← {"t":"hello","version":2,"maxPlayers":4,"actions":[...]}
→ {"t":"register","login":"player_one","password":"secret123"}
← {"t":"auth_ok","login":"player_one","token":"..."}
→ {"t":"create_lobby","name":"Игра","maxPlayers":3}
← {"t":"lobby_created","role":"P1","lobby":{...}}
← {"t":"peer_joined","login":"player_two","role":"P2","lobby":{...}}
→ {"t":"start"}
← {"t":"started","lobby":{...}}
→ <бинарные кадры>
← {"t":"input","role":"P2","login":"player_two","action":"Left","down":true}
```

## Совместимость

Версия 1 передавала имена клавиш (`{t:"input", key:"Left"}`) и знала только
двух игроков. Клиенты версии 1 с сервером версии 2 не работают: поле
`action` обязательно, иначе возвращается `bad_action`. Обновляйте всех сразу.
