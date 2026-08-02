# 02. Сервер (Node.js)

Лёгкий сервер на `express` + `ws`. Никакой БД, всё в одном JSON-файле.
Зависимостей ровно две.

## Файлы

| Файл | Назначение |
|---|---|
| `src/index.js` | HTTP-сервер, REST API, поднимает WebSocket-хаб |
| `src/hub.js` | WebSocket: лобби, ретрансляция кадров и ввода, все команды |
| `src/lobbies.js` | реестр лобби в памяти (лобби не переживают перезапуск — это нормально) |
| `src/auth.js` | регистрация, scrypt-хеши паролей, HMAC-токены, проверка админа |
| `src/db.js` | JSON-хранилище пользователей и настроек с отложенной записью |
| `Dockerfile` | образ на `node:20-alpine` |
| `.env.example` | пример переменных окружения |

## Переменные окружения

| Переменная | По умолчанию | Смысл |
|---|---|---|
| `PORT` | `8080` | порт |
| `HOST` | `0.0.0.0` | интерфейс |
| `DDN_SECRET` | случайная при старте | ключ подписи токенов. **Обязательно задайте свой**, иначе после перезапуска все разлогинятся |
| `DDN_ADMIN_USERNAME` | `s4msepi0l` | единственный аккаунт с админкой |
| `DDN_DATA_DIR` | `./data` | где лежит `db.json` |

## Запуск

```bash
cd server
npm install
cp .env.example .env      # отредактируйте DDN_SECRET
npm start
```

Проверка: `curl http://127.0.0.1:8080/api/health` → `{"ok":true,...}`.

### Docker

```bash
cd server
docker build -t deltadotnet-server .
docker run -d --name ddn -p 8080:8080 \
  -e DDN_SECRET=длинная_случайная_строка \
  -e DDN_ADMIN_USERNAME=s4msepi0l \
  -v ddn-data:/app/data \
  deltadotnet-server
```

### systemd

```ini
[Unit]
Description=DeltaDotNet server
After=network.target

[Service]
WorkingDirectory=/opt/deltadotnet/server
ExecStart=/usr/bin/node src/index.js
Environment=DDN_SECRET=длинная_случайная_строка
Environment=DDN_ADMIN_USERNAME=s4msepi0l
Restart=always
User=ddn

[Install]
WantedBy=multi-user.target
```

### За nginx (нужен для HTTPS/wss)

```nginx
server {
  listen 443 ssl;
  server_name ddn.example.com;

  location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;   # обязательно для WebSocket
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 600s;
    client_max_body_size 8m;
  }
}
```

В клиенте тогда пишите `https://ddn.example.com` — он сам переключится на `wss://`.

### Бесплатные площадки

Подойдёт любой хостинг, который умеет WebSocket и постоянный процесс:
Fly.io, Railway, Render (платный тариф для постоянного процесса), обычный VPS.
Шаред-хостинги без WS и «serverless» функции **не подойдут**.

## Ресурсы

Сервер только пересылает байты. На один стрим 1280×720 / 30 fps / q62 уходит
примерно **1.5–4 Мбит/с** на каждого гостя. Считайте канал по формуле
`битрейт хоста × количество гостей`. Память — десятки мегабайт.

## Ограничения по умолчанию

| Что | Значение | Где менять |
|---|---|---|
| размер WS-сообщения | 4 МБ | `hub.js`, `maxPayload` |
| лобби всего | 200 | `db.js`, `settings.maxLobbies` |
| игроков в лобби | 2–8 | `db.js`, `settings.maxPlayersHardCap` |
| срок жизни токена | 14 дней | `auth.js`, `TOKEN_TTL_MS` |
| keep-alive пинг | 20 с | `hub.js` |

## Безопасность

- Пароли — `scrypt` со случайной солью, в открытом виде нигде не хранятся.
- Токен — `base64url(payload).HMAC-SHA256`, подписан `DDN_SECRET`.
- Все `admin.*` команды сервер сверяет с `DDN_ADMIN_USERNAME`; подделать клиент
  бесполезно.
- Режим обслуживания (`maintenance`) пускает только админа.
