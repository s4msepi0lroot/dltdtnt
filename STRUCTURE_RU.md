# Структура 0.3.0

- `health/` — Физиология, immutable раны/таймеры лекарств, NBT 3 и миграции.
- `item/` — Бинт, шина, 5 новых предметов, общий server-side валидатор.
- `chat/` — Валидация строк, строгий JSON codec, локальная системная рассылка.
- `event/` — Урон, тики, clone/login/dimension, OP QA и личный opt-out.
- `network/` — Owner-only snapshot протокола 3, включая P/A.
- `client/` — Виньетка/blackout, блокировка ввода, H, анатомия и индикаторы.
- `config/` — Старые параметры + medicines/deliriumChat; отдельный клиентский HUD.
- `registry/` — Одна persistent attachment/capability и реестр предметов.
- `mixin/` — Серверное ограничение действий бессознательного игрока.

- `src/test` — три чистые suites и две реальные NBT/Gson suites.
- `examples` — пустой JSON-шаблон. `src/main/resources` — RU/EN, рецепты, модели, PNG и damage tags.
- `README_RU`, `JSON_CHAT_RU`, `VALIDATION_RU`, `ROADMAP_RU`, `CHANGELOG_RU`, `MVP_CODE_RU` — инструкции и полный код.

## Полный список
```text
.github/workflows/build.yml
.gitignore
API_SOURCES.md
CHANGELOG_RU.md
JSON_CHAT_RU.md
LICENSE
MVP_CODE_RU.md
README_RU.md
ROADMAP_RU.md
STRUCTURE_RU.md
VALIDATION_RU.md
build.gradle
examples/delirium_phrases.json
gradle.properties
scripts/JavaSyntaxCheck.java
scripts/test-core.sh
settings.gradle
src/main/java/dev/vitalstages/VitalStages.java
src/main/java/dev/vitalstages/chat/DeliriumChatService.java
src/main/java/dev/vitalstages/chat/JsonPhraseCodec.java
src/main/java/dev/vitalstages/chat/PhraseBook.java
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
src/main/java/dev/vitalstages/health/MedicalRules.java
src/main/java/dev/vitalstages/health/Physiology.java
src/main/java/dev/vitalstages/health/PlayerHealthData.java
src/main/java/dev/vitalstages/health/RecoveryClock.java
src/main/java/dev/vitalstages/health/TreatmentNbtCodec.java
src/main/java/dev/vitalstages/health/TreatmentState.java
src/main/java/dev/vitalstages/health/Wound.java
src/main/java/dev/vitalstages/health/WoundNbtCodec.java
src/main/java/dev/vitalstages/item/AdrenalineItem.java
src/main/java/dev/vitalstages/item/AntisepticItem.java
src/main/java/dev/vitalstages/item/BandageItem.java
src/main/java/dev/vitalstages/item/BloodBagItem.java
src/main/java/dev/vitalstages/item/EmptyBloodBagItem.java
src/main/java/dev/vitalstages/item/MedicalItem.java
src/main/java/dev/vitalstages/item/PainkillerItem.java
src/main/java/dev/vitalstages/item/SplintItem.java
src/main/java/dev/vitalstages/mixin/UnconsciousInputMixin.java
src/main/java/dev/vitalstages/network/HealthNetwork.java
src/main/java/dev/vitalstages/network/HealthSyncPayload.java
src/main/java/dev/vitalstages/registry/HealthAttachments.java
src/main/java/dev/vitalstages/registry/ModItems.java
src/main/resources/META-INF/neoforge.mods.toml
src/main/resources/assets/vitalstages/lang/en_us.json
src/main/resources/assets/vitalstages/lang/ru_ru.json
src/main/resources/assets/vitalstages/models/item/adrenaline.json
src/main/resources/assets/vitalstages/models/item/antiseptic.json
src/main/resources/assets/vitalstages/models/item/bandage.json
src/main/resources/assets/vitalstages/models/item/blood_bag.json
src/main/resources/assets/vitalstages/models/item/empty_blood_bag.json
src/main/resources/assets/vitalstages/models/item/painkiller.json
src/main/resources/assets/vitalstages/models/item/splint.json
src/main/resources/assets/vitalstages/textures/item/adrenaline.png
src/main/resources/assets/vitalstages/textures/item/antiseptic.png
src/main/resources/assets/vitalstages/textures/item/bandage.png
src/main/resources/assets/vitalstages/textures/item/blood_bag.png
src/main/resources/assets/vitalstages/textures/item/empty_blood_bag.png
src/main/resources/assets/vitalstages/textures/item/painkiller.png
src/main/resources/assets/vitalstages/textures/item/splint.png
src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_effects.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_enchantments.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_invulnerability.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_resistance.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_shield.json
src/main/resources/data/vitalstages/damage_type/organ_failure.json
src/main/resources/data/vitalstages/recipe/adrenaline.json
src/main/resources/data/vitalstages/recipe/antiseptic.json
src/main/resources/data/vitalstages/recipe/bandage.json
src/main/resources/data/vitalstages/recipe/empty_blood_bag.json
src/main/resources/data/vitalstages/recipe/painkiller.json
src/main/resources/data/vitalstages/recipe/splint.json
src/main/resources/pack.mcmeta
src/main/resources/vitalstages.mixins.json
src/test/java/dev/vitalstages/health/Mvp2CoreTest.java
src/test/java/dev/vitalstages/health/Mvp2NbtTest.java
src/test/java/dev/vitalstages/health/Mvp3CoreTest.java
src/test/java/dev/vitalstages/health/Mvp3IntegrationTest.java
src/test/java/dev/vitalstages/health/PhysiologyTest.java
```
