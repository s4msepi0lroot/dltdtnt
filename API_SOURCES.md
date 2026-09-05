# API: источники для проверки ветки 1.21.1

- Data Attachments: https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/
  - AttachmentType.serializable, INBTSerializable, NBT persistence, copy-on-death/Clone semantics.
- RegisterCapabilitiesEvent: https://lexxie.dev/neoforge/1.21.1/net/neoforged/neoforge/capabilities/RegisterCapabilitiesEvent.html
  - registerEntity(capability, entityType, provider).
- LivingDamageEvent.Pre: https://lexxie.dev/neoforge/1.21.1/net/neoforged/neoforge/event/entity/living/LivingDamageEvent.Pre.html
  - Absorption применяется ПОСЛЕ этого события.
- LivingDamageEvent.Post: https://aldak0.ru/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/entity/living/LivingDamageEvent.Post.html
  - Javadoc 21.1.233; getNewDamage возвращает реально прошедший урон.
- Payloads 1.21.1: https://docs.neoforged.net/docs/1.21.1/networking/payload/
  - RegisterPayloadHandlersEvent, StreamCodec, PacketDistributor, default MAIN-thread handling.
- Official MDK build: https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/blob/main/build.gradle
  - ModDevGradle DSL и Java 21. Основной шаблон изменяется со временем; в проекте версии закреплены.
- RenderGuiEvent: https://lexxie.dev/neoforge/1.21.1/net/neoforged/neoforge/client/event/RenderGuiEvent.html
- ServerGamePacketListenerImpl mappings: https://mappings.dev/1.21.1/net/minecraft/server/network/ServerGamePacketListenerImpl.html

Чтение API/Javadoc не равно компиляции проекта и не подтверждает успешное применение mixin в runtime.
