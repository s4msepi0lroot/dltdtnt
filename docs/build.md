# Сборка DeltaDotNet

## 1. Сборка на GitHub Actions (основной путь)

1. Создайте репозиторий на GitHub и залейте туда всё содержимое папки проекта.
2. Actions запустится сам при `push` в `main`/`master`, при pull request и вручную
   (**Actions → Build DeltaDotNet → Run workflow**).
3. Workflow `.github/workflows/build.yml` состоит из трёх задач:

| Задача | Раннер | Что делает |
|--------|--------|-----------|
| `windows-apps` | `windows-latest` | `dotnet restore/build`, публикует клиент и Theme Studio как **self-contained single-file** win-x64 |
| `server` | `ubuntu-latest` | `npm install`, `node --check` всех файлов, поднимает сервер и дёргает `/health`, пакует `DeltaDotNet-Server.zip` |
| `release` | `ubuntu-latest` | Только для тегов `v*`: собирает все артефакты и создаёт GitHub Release |

4. Готовые файлы: вкладка запуска → **Artifacts**:
   - `DeltaDotNet-Client-win-x64` → `DeltaDotNet.exe`
   - `DeltaDotNet-ThemeStudio-win-x64` → `DeltaDotNet.ThemeStudio.exe`
   - `DeltaDotNet-Server` → `DeltaDotNet-Server.zip`

5. Чтобы получить релиз с файлами:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## 2. Локальная сборка

Нужен .NET SDK 8 и Node.js 18+.

```bash
# клиент + theme studio
dotnet build DeltaDotNet.sln -c Release

dotnet publish src/DeltaDotNet.Client/DeltaDotNet.Client.csproj \
  -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o publish/client

dotnet publish src/DeltaDotNet.ThemeStudio/DeltaDotNet.ThemeStudio.csproj \
  -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o publish/themestudio

# сервер
cd server && npm install && node src/index.js
```

## 3. Частые проблемы

| Симптом | Причина / решение |
|---------|-------------------|
| `NETSDK1100: сборка WPF только на Windows` | WPF-проекты собираются исключительно на `windows-latest`, так и настроено |
| Иконка не применилась | Файл `Assets/app.ico` не добавлен. Билд не падает: цель `DropMissingIcon` убирает ссылку. См. `docs/assets.md` |
| Логотип не видно | Нет `Assets/logo.png` — показывается текст «DELTA DOT NET» |
| Артефакты не появились | Задача упала: смотрите лог шага, чаще всего это синтаксис в `server/src/*.js` |

## 4. Версии

- `TargetFramework`: `net8.0` (Core), `net8.0-windows` (Client, ThemeStudio)
- Единственный NuGet-пакет: `System.Drawing.Common` (JPEG-кодирование кадров)
- Единственная npm-зависимость сервера: `ws`
