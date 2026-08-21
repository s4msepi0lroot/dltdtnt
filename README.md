# Bad Habits — 1.1.0

Сигареты и выдуманная синтетика для **Minecraft 1.21.1 / NeoForge 21.1.248**
с зависимостью, ломкой, передозом, кашлем в чате, лабораторией синтеза и HUD.

* Полная документация (крафты, добыча ингредиентов, механика, конфиг): **[DOCS.md](DOCS.md)**
* Мультиплеер: вся логика серверная, совместимо с NeoForge-сервером и гибридами
  **Youer / Mohist / Magma / Arclight**. Мод нужен и клиенту, и серверу.

## Кратко

| Что | Сколько |
|---|---|
| Предметы | 28 (8 сигарет, 9 видов синтетики, инструменты, заготовки, детокс) |
| Блоки | 1 — Лаборатория синтеза (GUI, 12 рецептов) |
| Рецепты верстака/печи | 32 |
| Шкалы зависимости | 2 — «Никотин» и «Синтетика» |
| Команды | `/badhabits status | clear | set` |
| Конфиг | `<world>/serverconfig/badhabits-server.toml`, ~30 параметров |

## Как это играется

1. Жаришь листву в печи → **табачный лист**, крафтишь бумагу и фильтр → первые сигареты.
2. Курить нужно с **зажигалкой** (расходуется), колоть — **шприцем**, вдыхать — **стеклянной трубкой**.
3. Каждое употребление даёт эффекты, но растит **зависимость**. Чем выше зависимость,
   тем короче эффекты (толерантность).
4. Не принимаешь — начинается **ломка**: 4 стадии, дебаффы, на 3–4 стадии течёт здоровье
   до 1 ♥ (по умолчанию не убивает).
5. Выйти можно **снижением дозы** (принять вещество слабее предыдущего) или **Тоником «Детокс»**.
6. При никотиновой зависимости твои сообщения в чате получают `*кашель*` перед текстом.

## Сборка jar

GitHub Actions уже настроен (`.github/workflows/build.yml`):
загрузи проект в репозиторий → вкладка **Actions** → workflow **Build mod jar** →
артефакт **badhabits-jar** содержит `badhabits-1.1.0.jar`.

Локально: JDK 21 + `gradle build` (файла `gradle-wrapper.jar` в архиве нет, он бинарный).

## Структура

```
src/main/java/ru/s4fmer/badhabits/
  BadHabits.java, BhConfig.java
  addiction/   Substance, Meter, PlayerAddiction, AddictionManager, AddictionLogic
  item/        SubstanceItem, LighterItem, DetoxItem, EffectSpec, UseTool, ToolHelper
  block/       LabBlock, LabBlockEntity, LabRecipes
  menu/        LabMenu
  client/      LabScreen, BhHud, BhClientSetup
  network/     StatusPayload, BhNetwork, BhStatusHolder
  registry/    ModItems, ModBlocks, ModBlockEntities, ModMenus, ModCreativeTabs
  event/       BhEvents
  command/     BhCommands
  util/        Msg, CoughHelper
src/main/resources/
  META-INF/neoforge.mods.toml, pack.mcmeta
  assets/badhabits/  lang (en_us, ru_ru), models, blockstates, textures (item 32x32, block, gui)
  data/badhabits/    recipe (32), loot_table
  data/minecraft/    tags/block/mineable/pickaxe
```

Лицензия: MIT. Автор: s4fmer.
