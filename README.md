# SepiolBlackMarket 0.1.0

Чёрный рынок sepiolSMP — Season II. Скрытый торговец, нелегальные лоты, цены по спросу, розыск и облавы.

- Платформа: Youer 1.21.1 (Paper + NeoForge), Java 21
- Зависимости: нет жёстких. `SepiolEconomy` (или Vault) — мягкая, через рефлексию
- Моды, с которыми работает из коробки: `badhabits`, `boozecraft`

## Идея

Раз в сутки в случайной точке мира (в пределах `trader.radius`, не ближе `trader.min-distance` от спавна) встаёт барыга.
Адрес не объявляется: координаты — товар. Их покупают за сепиолы (`/bm find`), или ждут `trader.reveal-after-hours`,
когда адрес сольют всем. Кто пришёл ногами и кликнул по торговцу — получает витрину сразу.

Цена живая: каждая покупка поднимает её на `prices.step-percent` (или личный `step` лота), потолок — `prices.max-factor`.
Спрос остывает по полураспаду (`prices.half-life-hours`), а при новом завозе сбрасывается на `prices.restock-cooldown`.
То есть чем больше народу скупает войд-пыль, тем она дороже — и тем выгоднее варить свою.

За контрабанду в инвентаре сажают в розыск: свечение силуэта, оповещение, право стаффа на облаву с изъятием.
Автодосмотр работает в зонах (по умолчанию — радиус вокруг спавна). Отмыться можно лотом `clean_papers`.

## Команды

`/blackmarket` (алиасы: `/bm`, `/rynok`)

| Команда | Что делает | Право |
| --- | --- | --- |
| `/bm info` | сводка: где барыга, сколько лотов, баланс, розыск | `sepiolblackmarket.use` |
| `/bm find` | купить координаты за `trader.coords-price` | `sepiolblackmarket.find` |
| `/bm where` | показать адрес, если ты его знаешь | `sepiolblackmarket.use` |
| `/bm shop` | открыть витрину (только вблизи) | `sepiolblackmarket.use` |
| `/bm buy <лот>` | купить без GUI | `sepiolblackmarket.buy` |
| `/bm list [стр]` | прайс-лист текстом | `sepiolblackmarket.use` |
| `/bm wanted` | свой статус розыска | `sepiolblackmarket.use` |
| `/bm wanted list` | список всех в розыске | `sepiolblackmarket.staff` |
| `/bm wanted <ник> [мин]` | выдать розыск руками | `sepiolblackmarket.staff` |
| `/bm scan <ник>` | досмотр без изъятия | `sepiolblackmarket.scan` |
| `/bm raid <ник>` | облава: изъятие + розыск | `sepiolblackmarket.raid` |
| `/bm clear <ник\|all>` | снять розыск | `sepiolblackmarket.staff` |
| `/bm spawn [here]` | поставить торговца сразу | `sepiolblackmarket.admin` |
| `/bm despawn` | убрать торговца | `sepiolblackmarket.admin` |
| `/bm restock` | новый завоз (сброс остатков) | `sepiolblackmarket.admin` |
| `/bm reload` | перечитать config.yml | `sepiolblackmarket.admin` |
| `/bm export` | выгрузить JSON для сайта | `sepiolblackmarket.admin` |

## Ассортимент

37 лотов в трёх категориях:

1. **Дурь и сырьё** — всё из `badhabits`: сигареты, таблетки, сыворотки, войд-пыль, реагенты, подпольная лаба.
2. **Алкоголь** — из `boozecraft`: самогон ящиками, эверклер, абсинт, драконий абсинт, нелегальный самогонный аппарат, левая лицензия.
3. **Краденое и бумаги** — осколки реликвий, краденые элитры и тотемы, карты городов, чертежи осады, яйцо дракона, чистые документы.

Лот описывается так:

```yaml
lots:
  void_dust:
    name: "&5Войд-пыль"
    category: drugs
    base: 560.0        # базовая цена
    qty: 1             # сколько штук выдать
    stock: 2           # остаток на завоз, -1 = без лимита
    icon: minecraft:gunpowder
    contraband: true
    commands:
      - "minecraft:give %player% badhabits:void_dust %qty%"
```

- `item:` — ванильный предмет, выдаётся напрямую с именем, лором и скрытой меткой
- `commands:` — для модовых предметов, всегда `minecraft:give` (EssentialsX не умеет модовые id)
- `effect: "clear-wanted"` — особый эффект (снятие розыска)
- `step:` — личный шаг роста цены в процентах

## Контрабанда

Список `contraband:` — маски id с `*` на конце:

```yaml
contraband:
  - "badhabits:cig_*"
  - "boozecraft:moonshine"
```

Сравнение идёт и по `namespace:key`, и по `namespace_key` — так работает и на гибриде, где модовые предметы попадают в `Material`.
Дополнительно любой купленный нелегальный лот помечен в PDC (`sepiolblackmarket:contraband`) — его видно даже после переименования.

## Файлы данных

```
plugins/SepiolBlackMarket/
  config.yml
  catalog.json          # спрос и остатки
  trader.json           # где стоит, кому проданы координаты
  wanted.json           # розыск
  deals/2026-09-01.jsonl
  web/blackmarket.json  # выгрузка для сайта
```

Журнал сделок — одна строка JSON на событие (`buy`, `coords`, `scan`, `raid`, `wanted`). Удобно грепать и считать `jq`.

## Связка с другими кирпичами

- **SepiolEconomy** — списание сепиолов через `EconomyApi`; если его нет — Vault; если нет и Vault — торговля выключается с понятным сообщением
- **SepiolCore** — читает `web/blackmarket.json` и отдаёт его на веб-профиле
- **BadHabits / BoozeCraft** — источник товара: рынок даёт сбыт и спрос на их предметы

## Сборка

```bash
./gradlew build
# build/libs/SepiolBlackMarket-0.1.0.jar
```

GitHub Actions собирает на каждый push и кладёт jar в артефакт `SepiolBlackMarket-jar`; тег `v*` делает релиз.

## Настройка под сезон

- `trader.radius: 5200` под мир 12 000 блоков — торговец не вылезет за границу
- `trader.interval-hours: 24`, `stay-hours: 20` — суточный цикл с паузой в 4 часа
- `trader.remote-shop: false` — купить можно только приехав на место
- `scan.confiscate: false` — автодосмотр только палит, изъятие — руками стаффа
- `web.expose-coordinates: false` — на сайте адрес появляется только после общего раскрытия
