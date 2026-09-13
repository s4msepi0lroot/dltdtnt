# BoozeCraft

Алкоголь, газировка, энергетики, кофе, посуда, барная стойка и полноценная система
опьянения для **Minecraft 1.21.1 / NeoForge 21.1.248**.

* 77 напитка, 9 предмета, 4 блока, 8 эффекта
* 45 крафтов, 30 рецептов аппаратов, 19 рецептов смешивания
* мультиплеер и гибридные сервера (Youer, Mohist, Arclight)
* вырубание пьяного игрока через GSit (`lay`), встать раньше времени нельзя

Полное описание крафтов, механик и конфига — в [DOCUMENTATION.md](DOCUMENTATION.md).

## Быстрая сборка

Нужен JDK 21.

```bash
./build.sh          # Linux / macOS
build.bat           # Windows
```

Или без установки инструментов: загрузите проект на GitHub — workflow `build`
соберёт jar и положит его в Artifacts.

Результат: `build/libs/boozecraft-1.0.0.jar` → в папку `mods` сервера и клиента.

## Структура

```
src/main/java/com/s4fmer/boozecraft/   исходники мода
src/main/resources/                    модели, тексты, рецепты, текстуры
tools/                                 генераторы контента (Python)
DOCUMENTATION.md                       документация на русском
```

## Просмотрщики рецептов

Рецепты аппаратов и коктейлей видны в JEI и EMI (EMI читает плагин JEI через встроенный JEMI;
если EMI стоит без JEI — добавьте TooManyRecipeViewers). API JEI подключается как
`compileOnly`, версия — `jei_version` в `gradle.properties`.

Сборка без просмотрщиков (если недоступен maven.blamejared.com):

```bash
./gradlew build -Pjei_support=false
```

## Генерация исходников

Java-файлы, ресурсы и датапак собираются двумя скриптами из `tools/`:

```bash
python3 tools/boozecraft_gen.py     # генерирует мод целиком
python3 tools/boozecraft_patch.py   # события опьянения и плагин JEI
```
