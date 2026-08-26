# SepiolCore 0.1.0 — кирпич №️1 сборки sepiolSMP S2

Веб-профили игроков, которые раздаёт **сам игровой сервер** на выделенном порту — точно по схеме Dynmap, без внешнего хостинга.

## Что умеет

- HTTP-сервер на встроенном JDK `HttpServer` (нуль зависимостей, ничего шейдить не надо).
- Страница: статус сервера, онлайн, топ сезона, карточка профиля.
- **Интеграция твоих модов**: читает `badhabits/addiction.json` и `boozecraft_players.json`
  прямо из папки мира и показывает зависимости, ломку, алкоголь в крови, отключения.
- Ванильная статистика берётся из `world/stats/*.json`, так что работает и для оффлайн-игроков.
- Ник→UUID из `usercache.json` — корректно работает при `online-mode=false` + AuthMe.
- Все обращения к Bukkit API — только в основном потоке (снимок раз в 2 секунды),
  HTTP-потоки читают только готовый снимок → никаких фризов и гонок.

## Сборка

```bash
gradle wrapper --gradle-version 8.10   # один раз, если нет враппера
./gradlew build
# готовый файл: build/libs/SepiolCore-0.1.0.jar
```

Нужен JDK 21. Кидать в `plugins/`, как обычный плагин Paper/Youer.

## Настройка

`plugins/SepiolCore/config.yml`:

- `web.port` — твой выделенный порт (по умолчанию 8300). Не забудь открыть в фаерволле.
- `web.bind` — `0.0.0.0` если смотрит наружу, `127.0.0.1` если спереди nginx.
- `web.public-url` — что выдавать игрокам в `/sepiol profile`.
- `web.expose-coordinates` — по умолчанию `false`, чтобы профиль не стал читом для PvP.
- `season.*` — название, даты, граница 12000.
- `integration.*` — пути до json модов и максимумы для полосок.

## Команды

| Команда | Право | Что делает |
|---|---|---|
| `/sepiol profile [ник]` | `sepiolcore.profile` (всем) | ссылка на веб-профиль |
| `/sepiol web` | `sepiolcore.admin` | статус и адрес веб-сервера |
| `/sepiol reload` | `sepiolcore.admin` | перечитать конфиг и перезапустить порт |

## API

| Эндпоинт | Ответ |
|---|---|
| `GET /api/status` | сезон, онлайн, TPS, наличие модов |
| `GET /api/players` | топ сезона по наигранному |
| `GET /api/profile?player=Ник` | полный профиль (ник или UUID) |

## nginx за доменом (опционально)

```nginx
server {
    server_name profile.sepiolsmp.ru;
    location / {
        proxy_pass http://127.0.0.1:8300;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## Дальше (кирпич №2)

- `SepiolSkins`: свой скин-резолвер (лицензия → Ely.by → MineSkin) + раздача аватарок
  через этот же порт (`/avatar/<ник>`), чтобы аватарки не зависели от внешних сервисов.
