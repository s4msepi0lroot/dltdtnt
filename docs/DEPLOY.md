# Развёртывание сервера

Сервер — обычное Node.js-приложение без зависимостей. Ему нужен один открытый порт.
Главный критерий выбора хостинга — близость к игрокам и ширина канала, а не CPU.

Ориентир по трафику: 20 FPS x 1280px x качество 55 = примерно 3–8 Мбит/с в каждую сторону
(сервер принимает и тут же отдаёт, то есть удваивает трафик). Следите за лимитами трафика на тарифе.

## Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `PORT` | `8080` | порт HTTP/WS |
| `AUTH_SECRET` | `change-me-please` | секрет подписи токенов — обязательно сменить |
| `DATA_FILE` | `./data/users.json` | где хранятся аккаунты |
| `ALLOW_REGISTER` | `1` | разрешить регистрацию |
| `MAX_FRAME_KB` | `2048` | максимальный размер WS-кадра |
| `ADMIN_LOGIN` | `s4msepi0l` | логин владельца админ-панели |

Учётка с логином из `ADMIN_LOGIN` получает роль администратора сразу при регистрации,
и её нельзя разжаловать, забанить или удалить через админку. После развёртывания
зарегистрируйте этот логин первым, чтобы его никто не занял.

Генерация секрета:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

## Вариант 1. Обычный VPS + systemd

```bash
sudo apt update && sudo apt install -y nodejs git
git clone https://github.com/USER/REPO.git /opt/deltadotnet
cd /opt/deltadotnet/server
node src/index.js   # проверочный запуск, Ctrl+C
```

Файл `/etc/systemd/system/deltadotnet.service`:

```ini
[Unit]
Description=DeltaDotNet relay
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/deltadotnet/server
ExecStart=/usr/bin/node src/index.js
Environment=PORT=8080
Environment=AUTH_SECRET=ВАШ_ДЛИННЫЙ_СЕКРЕТ
Environment=DATA_FILE=/opt/deltadotnet/server/data/users.json
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now deltadotnet
sudo systemctl status deltadotnet
sudo ufw allow 8080/tcp   # если без обратного прокси
```

Адрес для клиента: `ws://IP_СЕРВЕРА:8080/ws`

## Вариант 2. Docker

```bash
cd server
docker build -t deltadotnet .
docker run -d --name deltadotnet --restart unless-stopped \
  -p 8080:8080 \
  -e AUTH_SECRET=ВАШ_СЕКРЕТ \
  -v deltadotnet-data:/app/data \
  deltadotnet
```

Том `/app/data` обязателен — иначе аккаунты стираются при пересоздании контейнера.

Файл `docker-compose.yml`:

```yaml
services:
  deltadotnet:
    build: ./server
    restart: unless-stopped
    ports: ["8080:8080"]
    environment:
      AUTH_SECRET: "ВАШ_СЕКРЕТ"
      ALLOW_REGISTER: "1"
      ADMIN_LOGIN: "s4msepi0l"
    volumes:
      - deltadotnet-data:/app/data
volumes:
  deltadotnet-data:
```

## Вариант 3. Railway / Render / Fly.io

Общие правила:

- корневая директория проекта — `server/`;
- команда запуска — `node src/index.js`;
- порт берётся из `PORT` — платформа подставляет его сама, вручную ничего не задавайте;
- задайте `AUTH_SECRET` в панели переменных;
- подключите persistent volume и укажите `DATA_FILE` внутри него (без тома аккаунты пропадут при каждом деплое);
- такие платформы выдают HTTPS-домен, значит адрес будет `wss://ВАШ_ДОМЕН/ws`.

WebSocket работает из коробки на Railway, Render и Fly.io. На serverless-платформах
(Vercel Functions, Netlify Functions) он не работает — туда ставить нельзя.

## Вариант 4. nginx + TLS (wss)

Рекомендуется, если сервер смотрит в интернет.

```nginx
server {
    listen 443 ssl http2;
    server_name coop.example.com;

    ssl_certificate     /etc/letsencrypt/live/coop.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/coop.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        proxy_read_timeout  3600s;   # иначе nginx рвёт долгие сессии
        proxy_send_timeout  3600s;
        proxy_buffering     off;     # важно для низкой задержки
        client_max_body_size 8m;
    }
}
```

Адрес для клиента: `wss://coop.example.com/ws`

Альтернатива — Caddy с автосертификатом:

```
coop.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

## Вариант 5. Локальная сеть / один ПК

Для теста можно запустить сервер на машине хоста:

```powershell
cd server
$env:AUTH_SECRET="local-test"
node src/index.js
```

Адреса: у хоста `ws://127.0.0.1:8080/ws`, у второго ПК — `ws://ЛОКАЛЬНЫЙ_IP:8080/ws`.
Не забудьте разрешить порт в брандмауэре Windows.
Для игры через интернет такой вариант плох — канал хоста будет грузиться дважды.

## После запуска

1. Проверьте здоровье:

   ```bash
   curl https://coop.example.com/health
   # {"ok":true,"uptime":12.3,"lobbies":0,"clients":0}
   ```

2. Зарегистрируйте два аккаунта из клиента.
3. Поставьте `ALLOW_REGISTER=0` и перезапустите — чужие больше не зарегистрируются.
4. Проверяйте нагрузку через `/stats`.

## Резервная копия

Всё состояние — это один файл `DATA_FILE`. Скопируйте его, и это полный бэкап.
Лобби живут только в памяти и бэкапа не требуют.
