# 06. Сборка

## GitHub Actions (главный способ)

Файл `.github/workflows/build.yml` запускается на каждый push, PR и вручную
(кнопка **Run workflow**). Две задачи:

### 1. `windows` — клиент и Theme Studio

- `windows-latest`, .NET 8;
- `dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true`;
- артефакты: `DeltaDotNet-client-win-x64`, `DeltaDotNet-themestudio-win-x64`.

Самодостаточная сборка: **.NET устанавливать не нужно**, один `.exe` на ~70–100 МБ.

### 2. `server` — Node.js

- `npm install`, `node --check` на каждый файл, smoke-тест `/api/health`;
- артефакт `DeltaDotNet-server` — готовая папка для заливки на хостинг.

### Релизы

```bash
git tag v1.0.0
git push origin v1.0.0
```

По тегу `v*` workflow дополнительно создаст GitHub Release с двумя zip-архивами.

Где скачать: **Actions → нужный запуск → блок Artifacts внизу**.

## Локальная сборка (Windows)

Нужен [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0).

```powershell
# клиент
dotnet publish client/DeltaDotNet.Client/DeltaDotNet.Client.csproj `
  -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o out/client

# редактор тем
dotnet publish client/DeltaDotNet.ThemeStudio/DeltaDotNet.ThemeStudio.csproj `
  -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o out/themestudio
```

Отладка: `dotnet run --project client/DeltaDotNet.Client`.

Сервер:

```bash
cd server && npm install && npm run dev
```

## Картинки перед сборкой

Положите файлы в `Assets/` обоих проектов и закоммитьте — GitHub Actions
заберёт их автоматически. Список имён — в `README.md` и в `Assets/README.txt`.
Иконка `.exe` подхватывается только если лежит `Assets\app.ico` и это настоящий
ICO (переименованный PNG не подойдёт, сборка упадёт).

## Частые ошибки сборки

| Ошибка | Причина |
|---|---|
| `MSB3822 / иконка невалидна` | `app.ico` на самом деле PNG |
| `NETSDK1100` | пытаетесь собрать WPF на Linux/macOS — нужен Windows-раннер |
| пустой артефакт | путь в `-o` не совпадает с `path:` в `upload-artifact` |
