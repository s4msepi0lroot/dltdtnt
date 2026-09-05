# Структура проекта

## Java: src/main/java/dev/vitalstages

- `VitalStages.java` — точка входа, регистрация предметов/attachment/сети/конфига.
- `health/PlayerHealthData.java` — серверное состояние, NBT, агрегаты ран, clone-копия.
- `health/HealthView.java` — read-only контракт capability.
- `health/BodyPart.java` — части тела и веса кровопотери/шока.
- `health/LimbStatus.java` — состояния конечностей.
- `health/LifeStage.java` — стадии сознания.
- `health/Wound.java` — неизменяемая рана, NBT, перевязка и риск инфекции.
- `health/Physiology.java` — чистая тиковая модель, пороги и окно спасения.
- `config/HealthConfig.java` — SERVER-конфиг и диапазоны параметров.
- `registry/HealthAttachments.java` — AttachmentType и capability над тем же объектом.
- `registry/ModItems.java` — регистрация бинта.
- `event/DamageEventHandler.java` — Pre/Post урона, выбор части тела, промежуточная смерть.
- `event/TickHandler.java` — серверные тики, физиология, финальный урон.
- `event/LifecycleHandler.java` — login, respawn, dimension change, Clone.
- `mixin/UnconsciousInputMixin.java` — серверное ограничение действий/движения.
- `network/HealthSyncPayload.java` — ограниченный S2C payload и codec.
- `network/HealthNetwork.java` — регистрация/отправка пакета, безопасная клиентская точка входа.
- `client/ClientBootstrap.java` — подключение клиента только на Dist.CLIENT.
- `client/ClientHealthState.java` — визуальный кэш с UUID/dimension guard.
- `client/ClientEvents.java` — ввод, камера, HUD и очистка кэша.
- `client/VitalsOverlay.java` — POC-виньетка, blackout, текстовые показатели.
- `item/BandageItem.java` — лечение себя/напарника с серверными проверками.

## Ресурсы: src/main/resources

- `META-INF/neoforge.mods.toml` — метаданные, зависимости BOTH, регистрация mixin.
- `vitalstages.mixins.json` — конфигурация обязательного серверного mixin.
- `pack.mcmeta` — версия ресурсов Minecraft 1.21.1.
- `assets/vitalstages/lang/{ru_ru,en_us}.json` — предмет, HUD, перевязка и причины смерти.
- `assets/vitalstages/models/item/bandage.json` — модель предмета со встроенной текстурой бумаги.
- `data/vitalstages/recipe/bandage.json` — шерсть + нить → 4 бинта.
- `data/vitalstages/damage_type/organ_failure.json` — финальный источник урона.
- `data/minecraft/tags/damage_type/*.json` — семь дополняющих тегов обхода защит для финального источника; replace=false.

## Сборка и проверка

- `build.gradle`, `settings.gradle`, `gradle.properties` — закреплённые версии, Java 21, runClient/runServer, test task.
- `src/test/java/dev/vitalstages/health/PhysiologyTest.java` — тестируется тот же Physiology, который использует сервер.
- `scripts/test-core.sh` — офлайн-компиляция/запуск чистых тестов.
- `scripts/JavaSyntaxCheck.java` — parse-only проверка Java 21.
- `.github/workflows/build.yml` — подготовленный GitHub Actions build.
- `README_RU.md`, `MVP_CODE_RU.md`, `STRUCTURE_RU.md`, `API_SOURCES.md`, `VALIDATION_RU.md` — документация.
- `LICENSE`, `.gitignore` — MIT и исключения из VCS.

## Полный список файлов (без build-кэша)

```text
.github/workflows/build.yml
.gitignore
API_SOURCES.md
LICENSE
README_RU.md
VALIDATION_RU.md
build.gradle
gradle.properties
scripts/JavaSyntaxCheck.java
scripts/test-core.sh
settings.gradle
src/main/java/dev/vitalstages/VitalStages.java
src/main/java/dev/vitalstages/client/ClientBootstrap.java
src/main/java/dev/vitalstages/client/ClientEvents.java
src/main/java/dev/vitalstages/client/ClientHealthState.java
src/main/java/dev/vitalstages/client/VitalsOverlay.java
src/main/java/dev/vitalstages/config/HealthConfig.java
src/main/java/dev/vitalstages/event/DamageEventHandler.java
src/main/java/dev/vitalstages/event/LifecycleHandler.java
src/main/java/dev/vitalstages/event/TickHandler.java
src/main/java/dev/vitalstages/health/BodyPart.java
src/main/java/dev/vitalstages/health/HealthView.java
src/main/java/dev/vitalstages/health/LifeStage.java
src/main/java/dev/vitalstages/health/LimbStatus.java
src/main/java/dev/vitalstages/health/Physiology.java
src/main/java/dev/vitalstages/health/PlayerHealthData.java
src/main/java/dev/vitalstages/health/Wound.java
src/main/java/dev/vitalstages/item/BandageItem.java
src/main/java/dev/vitalstages/mixin/UnconsciousInputMixin.java
src/main/java/dev/vitalstages/network/HealthNetwork.java
src/main/java/dev/vitalstages/network/HealthSyncPayload.java
src/main/java/dev/vitalstages/registry/HealthAttachments.java
src/main/java/dev/vitalstages/registry/ModItems.java
src/main/resources/META-INF/neoforge.mods.toml
src/main/resources/assets/vitalstages/lang/en_us.json
src/main/resources/assets/vitalstages/lang/ru_ru.json
src/main/resources/assets/vitalstages/models/item/bandage.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_effects.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_enchantments.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_invulnerability.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_resistance.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_shield.json
src/main/resources/data/vitalstages/damage_type/organ_failure.json
src/main/resources/data/vitalstages/recipe/bandage.json
src/main/resources/pack.mcmeta
src/main/resources/vitalstages.mixins.json
src/test/java/dev/vitalstages/health/PhysiologyTest.java
```
