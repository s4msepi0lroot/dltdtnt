# Куда класть свои картинки

Коротко: всего три файла, все необязательные — без них сборка всё равно пройдёт.

| Куда положить | Имя файла | Что это | Размер |
|----------------|------------|---------|--------|
| `src/DeltaDotNet.Client/Assets/` | `logo.png` | Логотип в шапке клиента вместо надписи «DELTA DOT NET» | ≈600×160 px, PNG с прозрачностью |
| `src/DeltaDotNet.Client/Assets/` | `app.ico` | Иконка `DeltaDotNet.exe` | .ico с размерами 16/32/48/256 |
| `src/DeltaDotNet.ThemeStudio/Assets/` | `studio.ico` | Иконка `DeltaDotNet.ThemeStudio.exe` | .ico с размерами 16/32/48/256 |

## Как добавить

1. Скопируйте файлы ровно в эти папки с ровно такими именами (регистр важен).
2. `git add . && git commit -m "assets" && git push`.
3. GitHub Actions подхватит их автоматически — править код не надо.

В `.csproj` уже прописаны условные включения:

```xml
<ItemGroup>
  <Resource Include="Assets\logo.png" Condition="Exists('Assets\logo.png')" />
  <Resource Include="Assets\app.ico"  Condition="Exists('Assets\app.ico')" />
</ItemGroup>

<Target Name="DropMissingIcon" BeforeTargets="BeforeBuild" Condition="!Exists('Assets\app.ico')">
  <PropertyGroup><ApplicationIcon></ApplicationIcon></PropertyGroup>
</Target>
```

То есть если файла нет — билд не ломается, просто берётся запасной вариант:

- нет `logo.png` → в шапке текст «DELTA DOT NET»;
- нет `.ico` → стандартная иконка .NET.

## Где взять .ico

Любой онлайн-конвертер PNG → ICO с галочкой «multiple sizes». Исходник лучше брать квадратный
256×256 с прозрачным фоном.

## Логотип из темы

Логотип можно подменить и без пересборки: если в активной теме `.ddntheme` есть своё изображение
логотипа, оно перекрывает `Assets/logo.png`, пока тема включена (см. `docs/theme-format.md`).
Иконки `.exe` темами не меняются — только через `.ico` при сборке.
