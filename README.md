# Bad Habits — NeoForge 1.21.1

Мод добавляет сигареты (5 видов), вымышленную синтетику (5 видов), зависимость, ломку, передоз,
толерантность, лечение через снижение дозы и кашель в чате.

* **Minecraft:** 1.21.1
* **NeoForge:** 21.1.248 (`[21.1.248,)`)
* **Java:** 21
* **Сторона:** нужен и на сервере, и на клиенте (`side = BOTH`)
* **Лицензия:** MIT

Полная документация крафтов и механик: [DOCS.md](DOCS.md)

---

## Сборка

### Вариант 1 — GitHub Actions (ничего не ставить на ПК)

1. Создайте новый репозиторий на GitHub.
2. Залейте туда содержимое архива (файл `build.gradle` должен лежать в корне).
3. Вкладка **Actions** → workflow `Build mod jar` запустится сам.
4. Готовый jar — в артефакте `badhabits-jar` (внизу страницы сборки).

Всё уже настроено в `.github/workflows/build.yml`.

### Вариант 2 — локально

Нужны JDK 21 и Gradle 8.8+ (или 8.12.1).

```bash
cd badhabits
gradle wrapper          # один раз: создаст ./gradlew и gradle-wrapper.jar
./gradlew build         # Windows: gradlew.bat build
```

Готовый файл: `build/libs/badhabits-1.0.0.jar`

Полезные задачи:

```bash
./gradlew runClient     # тестовый клиент
./gradlew runServer     # тестовый сервер (--nogui)
```

### Вариант 3 — IntelliJ IDEA

`File → Open` → папка `badhabits` → доверить Gradle-проекту → Gradle сам скачает NeoForge 21.1.248.
Затем задача `build` или конфигурация запуска `runClient`.

> Первая сборка тянет маппинги и артефакты NeoForge — нужен интернет и ~2 ГБ на кэш Gradle.

---

## Установка

Кинуть jar в `mods/` на сервере **и** у каждого игрока. Зависимостей больше нет.

### Гибридные ядра (Youer / Mohist / Arclight / Magma-Neo)

Мод намеренно написан так, чтобы жить на гибридах:

* нет миксинов и Access Transformers — нечему конфликтовать с патчами ядра;
* нет своих сетевых пакетов и нет обращений к клиентским классам в общем коде;
* всё состояние живёт на сервере и пишется в `<мир>/badhabits/addiction.json`;
* команды требуют permission level 2 — работает и с ванильными опами, и с LuckPerms;
* если чат-плагин игнорирует правку сообщения — включите `cough.rebroadcastInsteadOfEditing = true`.

---

## Структура

```
badhabits/
  build.gradle, settings.gradle, gradle.properties
  .github/workflows/build.yml          — сборка jar без локального Gradle
  src/main/java/ru/s4fmer/badhabits/
    BadHabits.java                     — точка входа
    BhConfig.java                      — серверный конфиг (30+ параметров)
    addiction/                         — доза, зависимость, ломка, сохранение
    item/                              — предметы и способы приёма
    registry/                          — регистрация предметов и креатив-вкладки
    event/BhEvents.java                — тик, чат-кашель, вход/выход/респавн
    command/BhCommands.java            — /badhabits
    util/                              — сообщения и кашель
  src/main/resources/
    META-INF/neoforge.mods.toml
    assets/badhabits/{lang,models/item,textures/item}
    data/badhabits/recipe/             — 24 рецепта
```

Текстуры — простые 16×16 спрайты, сгенерированные скриптом; смело заменяйте своими файлами
в `assets/badhabits/textures/item/` — имена менять не надо.
