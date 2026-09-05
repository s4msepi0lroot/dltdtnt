# 0.2.0 — MVP-2

Добавлены FractureProfile/FractureEffects, RecoveryClock, WoundNbtCodec, MedicalItem/SplintItem, AnatomyHud/HudConfig/ClientKeys, OP debug fracture/reset, рецепт и иконки шины/бинта, новые модели и NBT-тесты.

Фиксация сразу снимает собственный штраф, но не стирает перелом. Прогресс заживления сохраняется; новый удар сбрасывает его, но не окно обморока.

Исправлено падение Gradle 9 при отсутствии JUnit: failOnNoDiscoveredTests=false. physiologyTest/mvp2Test/mvp2NbtTest остаются обязательными задачами check.

NBT: читать 0/1/2, писать 2; healingTicks=0 для старых ран. Протокол сети 2: добавлены splintedMask/openCutMask. Обновлять сервер и клиентов вместе, сделать backup мира.

Добавлен ROADMAP с будущими JSON-репликами бреда. Авточат в этой версии НЕ реализован.
