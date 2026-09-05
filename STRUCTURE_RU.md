# Структура 0.2.0

- health: состояние, чистые раны/штрафы/заживление и NBT codec.
- event: урон, атрибуты, тики, жизненный цикл, отладочные команды.
- item: общий MedicalItem, бинт, шина.
- network: owner-only snapshot протокола 2.
- client: HUD, клавиша H, эффекты и ввод.
- config: серверная физиология и клиентский HUD.
- registry: предметы/attachment/capability. mixin: блокировка действий при обмороке.

## Все файлы

```text
.github/workflows/build.yml
.gitignore
API_SOURCES.md
CHANGELOG_RU.md
LICENSE
MVP_CODE_RU.md
README_RU.md
ROADMAP_RU.md
STRUCTURE_RU.md
VALIDATION_RU.md
build.gradle
docs/planned/delirium_phrases.example.json
gradle.properties
scripts/JavaSyntaxCheck.java
scripts/test-core.sh
settings.gradle
src/main/java/dev/vitalstages/VitalStages.java
src/main/java/dev/vitalstages/client/AnatomyHud.java
src/main/java/dev/vitalstages/client/ClientBootstrap.java
src/main/java/dev/vitalstages/client/ClientEvents.java
src/main/java/dev/vitalstages/client/ClientHealthState.java
src/main/java/dev/vitalstages/client/ClientKeys.java
src/main/java/dev/vitalstages/client/VitalsOverlay.java
src/main/java/dev/vitalstages/config/HealthConfig.java
src/main/java/dev/vitalstages/config/HudConfig.java
src/main/java/dev/vitalstages/event/DamageEventHandler.java
src/main/java/dev/vitalstages/event/DebugCommands.java
src/main/java/dev/vitalstages/event/FractureEffects.java
src/main/java/dev/vitalstages/event/LifecycleHandler.java
src/main/java/dev/vitalstages/event/TickHandler.java
src/main/java/dev/vitalstages/health/BodyPart.java
src/main/java/dev/vitalstages/health/FractureProfile.java
src/main/java/dev/vitalstages/health/HealthView.java
src/main/java/dev/vitalstages/health/LifeStage.java
src/main/java/dev/vitalstages/health/LimbStatus.java
src/main/java/dev/vitalstages/health/Physiology.java
src/main/java/dev/vitalstages/health/PlayerHealthData.java
src/main/java/dev/vitalstages/health/RecoveryClock.java
src/main/java/dev/vitalstages/health/Wound.java
src/main/java/dev/vitalstages/health/WoundNbtCodec.java
src/main/java/dev/vitalstages/item/BandageItem.java
src/main/java/dev/vitalstages/item/MedicalItem.java
src/main/java/dev/vitalstages/item/SplintItem.java
src/main/java/dev/vitalstages/mixin/UnconsciousInputMixin.java
src/main/java/dev/vitalstages/network/HealthNetwork.java
src/main/java/dev/vitalstages/network/HealthSyncPayload.java
src/main/java/dev/vitalstages/registry/HealthAttachments.java
src/main/java/dev/vitalstages/registry/ModItems.java
src/main/resources/META-INF/neoforge.mods.toml
src/main/resources/assets/vitalstages/lang/en_us.json
src/main/resources/assets/vitalstages/lang/ru_ru.json
src/main/resources/assets/vitalstages/models/item/bandage.json
src/main/resources/assets/vitalstages/models/item/splint.json
src/main/resources/assets/vitalstages/textures/item/bandage.png
src/main/resources/assets/vitalstages/textures/item/splint.png
src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_effects.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_enchantments.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_invulnerability.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_resistance.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_shield.json
src/main/resources/data/vitalstages/damage_type/organ_failure.json
src/main/resources/data/vitalstages/recipe/bandage.json
src/main/resources/data/vitalstages/recipe/splint.json
src/main/resources/pack.mcmeta
src/main/resources/vitalstages.mixins.json
src/test/java/dev/vitalstages/health/Mvp2CoreTest.java
src/test/java/dev/vitalstages/health/Mvp2NbtTest.java
src/test/java/dev/vitalstages/health/PhysiologyTest.java
```
