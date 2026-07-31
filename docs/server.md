# Сервер DeltaDotNet (Node.js)

Лёгкий сервер без базы данных: HTTP API + WebSocket-релей. Единственная зависимость — `ws`.

## Запуск

```bash
cd server
npm install
DDN_SECRET="длинная-случайная-строка" node src/index.js
```

По умолчанию: `http://0.0.0.0:8080`, WebSocket на пути `/ws`.

### Переменные окружения

| Переменная | По умолчанию | Описание |
|------------|---------------|----------|
| `PORT` | `8080` | Порт |
| `HOST` | `0.0.0.0` | Интерфейс |
| `DDN_SECRET` | случайный при старте | Ключ подписи токенов. **Задайте его**, иначе все сессии слетают при перезапуске |
| `DDN_OWNER` | `s4msepi0l` | Ник владельца, которому открывается админка |
| `DDN_DATA_DIR` | `./data` | Куда пишется `db.json` |

### Хранилище

`data/db.json` — обычный JSON: аккаунты (пароли — scrypt-хеши с солью), глобальные баны, MOTD.
Лобби живут только в памяти и исчезают при перезапуске — это нормально.

### Деплой

Любой хостинг с Node.js и WebSocket: VPS, Railway, Render, Fly.io, домашний ПК с пробросом порта.

**systemd** (пример `/etc/systemd/system/deltadotnet.service`):

```ini
[Unit]
Description=DeltaDotNet server
After=network.target

[Service]
WorkingDirectory=/opt/deltadotnet/server
ExecStart=/usr/bin/node src/index.js
Environment=PORT=8080
Environment=DDN_SECRET=замените-меня
Environment=DDN_OWNER=s4msepi0l
Restart=always

[Install]
WantedBy=multi-user.target
```

**Nginx + TLS** (если хотите `https://`/`wss://`):

```nginx
location / {
    proxy_pass         http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header   Upgrade $http_upgrade;
    proxy_set_header   Connection "upgrade";
    proxy_read_timeout 600s;
}
```

В клиенте тогда пишите `https://ваш-домен` — клиент сам превратит это в `wss://ваш-домен/ws`.

## HTTP API

Все защищённые запросы требуют заголовка `Authorization: Bearer <token>`.

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/health` | Проверка живости |
| POST | `/api/auth/register` | `{username, password}` → `{token, user}` |
| POST | `/api/auth/login` | `{username, password}` → `{token, user}` |
| GET | `/api/me` | Текущий пользователь + MOTD |
| GET | `/api/lobbies` | Список лобби |
| POST | `/api/lobbies` | Создать лобби |
| GET | `/api/lobbies/:id` | Детали лобби |
| DELETE | `/api/lobbies/:id` | Закрыть лобби (хост или владелец) |

### Админ-эндпоинты (только владелец)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/admin/stats` | Онлайн, лобби, аккаунты, аптайм |
| GET | `/api/admin/users?q=` | Поиск аккаунтов |
| PATCH | `/api/admin/users/:id` | `rainbow`, `nameColor`, `badge`, `role`, `banned`, `username` |
| DELETE | `/api/admin/users/:id` | Удалить аккаунт |
| GET | `/api/admin/lobbies` | Все лобби, включая закрытые |
| DELETE | `/api/admin/lobbies/:id` | Принудительно закрыть |
| POST | `/api/admin/broadcast` | `{message}` всем онлайн |
| POST | `/api/admin/motd` | `{motd}` |

## Безопасность

- Пароли — scrypt с индивидуальной солью, в открытом виде нигде не хранятся.
- Токены — HMAC-SHA256, срок жизни 14 дней.
- Пароли лобби хранятся как SHA-256 и никогда не отдаются клиентам.
- Максимальный размер WS-сообщения — 8 МБ; если буфер клиента переполнен (>4 МБ), кадр пропускается.
- Heartbeat каждые 20 секунд отстреливает мёртвые соединения.
