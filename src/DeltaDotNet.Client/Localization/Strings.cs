using System.Collections.Generic;

namespace DeltaDotNet.Client.Localization;

/// <summary>
/// ============================================================
///  ALL UI TEXT OF DELTADOTNET LIVES HERE / ВСЕ ТЕКСТЫ ЗДЕСЬ
/// ============================================================
///
/// Format:  ["key"] = new("English text", "Русский текст"),
///
/// To add a new phrase:
///   1. add a line to the table below;
///   2. use it in XAML  ->  Text="{loc:Tr key}"
///      or in C#        ->  Loc.T("key")   /   Loc.F("key", arg0, arg1)
///
/// Placeholders {0}, {1}, ... are filled by Loc.F().
/// Nothing else in the app contains hard-coded user-visible text.
/// </summary>
public static class Strings
{
    /// <summary>One phrase in every supported language.</summary>
    public readonly struct Phrase
    {
        public readonly string En;
        public readonly string Ru;
        public Phrase(string en, string ru) { En = en; Ru = ru; }
    }

    public static readonly Dictionary<string, Phrase> Table = new()
    {
        // ---------------- generic ----------------
        ["app.name"] = new("DeltaDotNet", "DeltaDotNet"),
        ["ok"] = new("OK", "ОК"),
        ["cancel"] = new("Cancel", "Отмена"),
        ["back"] = new("Back", "Назад"),
        ["save"] = new("SAVE", "СОХРАНИТЬ"),
        ["apply"] = new("APPLY", "ПРИМЕНИТЬ"),
        ["refresh"] = new("Refresh", "Обновить"),
        ["close"] = new("Close", "Закрыть"),
        ["yes"] = new("Yes", "Да"),
        ["no"] = new("No", "Нет"),
        ["none"] = new("none", "нет"),
        ["error"] = new("Error", "Ошибка"),
        ["ready"] = new("Ready.", "Готово."),
        ["unexpected"] = new("Unexpected error:", "Непредвиденная ошибка:"),
        ["announcement"] = new("Announcement from the server", "Объявление от сервера"),

        // ---------------- header ----------------
        ["header.settings"] = new("Settings", "Настройки"),
        ["header.admin"] = new("Admin", "Админка"),
        ["header.logout"] = new("Log out", "Выйти"),
        ["header.connected"] = new("connected: {0}", "подключено: {0}"),
        ["header.offline"] = new("offline", "нет соединения"),
        ["header.signedout"] = new("Signed out.", "Вы вышли из аккаунта."),

        // ---------------- login ----------------
        ["login.title"] = new("* Sign in", "* Вход"),
        ["login.hint"] = new("Your account lives on the DeltaDotNet server.",
                             "Аккаунт хранится на сервере DeltaDotNet."),
        ["login.server"] = new("Server URL", "Адрес сервера"),
        ["login.username"] = new("Username", "Логин"),
        ["login.password"] = new("Password", "Пароль"),
        ["login.remember"] = new("Stay signed in on this PC", "Оставаться в системе на этом ПК"),
        ["login.login"] = new("LOG IN", "ВОЙТИ"),
        ["login.register"] = new("REGISTER", "РЕГИСТРАЦИЯ"),
        ["login.empty"] = new("* Enter a username and a password", "* Введите логин и пароль"),
        ["login.connecting"] = new("Connecting...", "Подключение..."),
        ["login.welcome"] = new("Welcome, {0}!", "С возвращением, {0}!"),
        ["login.failed"] = new("* {0}", "* {0}"),

        // ---------------- lobby browser ----------------
        ["browser.title"] = new("* Lobbies", "* Лобби"),
        ["browser.join"] = new("Join selected", "Войти в выбранное"),
        ["browser.code"] = new("Lobby code", "Код лобби"),
        ["browser.joinbycode"] = new("Join by code", "Войти по коду"),
        ["browser.count"] = new("{0} lobbies online.", "Лобби онлайн: {0}."),
        ["browser.select"] = new("* Select a lobby first", "* Сначала выберите лобби"),
        ["browser.entercode"] = new("* Enter a lobby code", "* Введите код лобби"),
        ["browser.askpassword"] = new("Lobby password:", "Пароль лобби:"),
        ["browser.password.title"] = new("Closed lobby", "Закрытое лобби"),

        ["create.title"] = new("* Create lobby", "* Создать лобби"),
        ["create.name"] = new("Lobby name", "Название лобби"),
        ["create.players"] = new("Number of players", "Количество игроков"),
        ["create.players.2"] = new("2 players", "2 игрока"),
        ["create.players.3"] = new("3 players", "3 игрока"),
        ["create.players.4"] = new("4 players", "4 игрока"),
        ["create.visibility"] = new("Visibility", "Доступность"),
        ["create.open"] = new("Open anyone can join", "Открытое"),
        ["create.closed"] = new("Closed restricted", "Закрытое"),
        ["create.access"] = new("Access mode", "Способ входа"),
        ["create.access.password"] = new("Password", "По паролю"),
        ["create.access.whitelist"] = new("Allow list of logins", "По списку логинов"),
        ["create.lobbypassword"] = new("Lobby password", "Пароль лобби"),
        ["create.whitelist"] = new("Allowed logins (comma separated)", "Разрешённые логины (через запятую)"),
        ["create.quality"] = new("Stream quality", "Качество трансляции"),
        ["create.quality.mine"] = new("Use my settings", "Как в моих настройках"),
        ["create.button"] = new("CREATE", "СОЗДАТЬ"),
        ["create.hint"] = new("You become the host: your screen is streamed and guest keys are injected into the game on your PC.",
                              "Вы становитесь хостом: транслируется ваш экран, а клавиши гостей нажимаются на вашем ПК."),
        ["create.needname"] = new("* Enter a lobby name", "* Введите название лобби"),
        ["create.needpassword"] = new("* Enter a lobby password", "* Введите пароль лобби"),
        ["room.title"] = new("* Lobby", "* Лобби"),
        ["room.info"] = new("code #{0} {1}/{2} players {3} host: {4} quality {5}fps/{6}%/q{7} you are P{8}",
                            "код #{0} игроков {1}/{2} {3} хост: {4} качество {5}fps/{6}%/q{7} вы P{8}"),
        ["room.access.open"] = new("open", "открытое"),
        ["room.access.password"] = new("closed · password", "закрытое · пароль"),
        ["room.access.whitelist"] = new("closed · allow list", "закрытое · список логинов"),
        ["room.start"] = new("START GAME", "НАЧАТЬ ИГРУ"),
        ["room.ready"] = new("READY", "ГОТОВ"),
        ["room.ready.on"] = new("READY", "ГОТОВ"),
        ["room.leave"] = new("Leave", "Выйти"),
        ["room.close"] = new("CLOSE LOBBY", "УДАЛИТЬ ЛОББИ"),
        ["room.close.confirm"] = new("Delete this lobby for everyone?", "Удалить это лобби для всех?"),
        ["room.kick"] = new("Kick", "Кикнуть"),
        ["room.ban"] = new("Ban", "Забанить"),
        ["room.unban"] = new("Unban...", "Разбанить..."),
        ["room.chat"] = new("* Chat", "* Чат"),
        ["room.send"] = new("Send", "Отпр."),
        ["room.needmore"] = new("* Wait for at least one more player.", "* Нужен хотя бы ещё один игрок."),
        ["room.selectplayer"] = new("* Select a player first.", "* Сначала выберите игрока."),
        ["room.notyourself"] = new("* You cannot do that to yourself.", "* С самим собой так нельзя."),
        ["room.banreason"] = new("Reason for banning {0}:", "Причина бана {0}:"),
        ["room.banned.list"] = new("Banned: {0}\nType the username to unban:", "Забанены: {0}\nВведите логин для разбана:"),
        ["room.nobans"] = new("* Nobody is banned here.", "* Здесь никто не забанен."),
        ["room.nosuchban"] = new("* No such banned player.", "* Такого забаненного игрока нет."),
        ["room.share"] = new("Lobby #{0}, share this code with your friends.", "Лобби #{0}, отправьте этот код друзьям."),
        ["room.wasclosed"] = new("The lobby was closed.", "Лобби было закрыто."),
        ["room.waskicked"] = new("You were kicked from the lobby. {0}", "Вас выгнали из лобби. {0}"),
        ["room.wasbanned"] = new("You were banned from the lobby. {0}", "Вас забанили в лобби. {0}"),

        // ---------------- game ----------------
        ["game.host"] = new("* Streaming, you are the host (P{0})", "* Трансляция, вы хост (P{0})"),
        ["game.guest"] = new("* Playing as P{0}", "* Вы играете за P{0}"),
        ["game.stop"] = new("STOP GAME", "ОСТАНОВИТЬ"),
        ["game.backtolobby"] = new("Back to lobby", "Назад в лобби"),
        ["game.focus"] = new("Focus game window", "Фокус на окно игры"),
        ["game.capture"] = new("Capture my keyboard", "Перехватывать мою клавиатуру"),
        ["game.waiting"] = new("* Waiting for the host's stream...", "* Ждём трансляцию хоста..."),
        ["game.stats"] = new("{0} fps · {1} KB/s · {2}x{3}", "{0} fps · {1} КБ/с · {2}x{3}"),
        ["game.notarget"] = new("Game window not found, pick the process in Settings - Capture.",
                                "Окно игры не найдено, выберите процесс в Настройки - Захват."),

        // ---------------- settings ----------------
        ["settings.title"] = new("* Settings", "* Настройки"),
        ["settings.tab.quality"] = new("Quality", "Качество"),
        ["settings.tab.controls"] = new("Controls", "Управление"),
        ["settings.tab.capture"] = new("Capture", "Захват"),
        ["settings.tab.themes"] = new("Themes", "Темы"),
        ["settings.tab.account"] = new("Account", "Аккаунт"),
        ["settings.tab.general"] = new("General", "Общее"),

        ["settings.language"] = new("Language / Язык", "Язык / Language"),
        ["settings.language.en"] = new("English", "English"),
        ["settings.language.ru"] = new("Русский", "Русский"),
        ["settings.language.hint"] = new("The interface switches instantly when you press SAVE.",
                                         "Интерфейс переключится сразу после нажатия СОХРАНИТЬ."),

        ["settings.preset"] = new("Preset", "Пресет"),
        ["settings.preset.custom"] = new("Custom", "Свой"),
        ["settings.fps"] = new("Frame rate: {0} fps", "Частота кадров: {0} fps"),
        ["settings.scale"] = new("Resolution scale: {0}%", "Масштаб разрешения: {0}%"),
        ["settings.jpeg"] = new("Image quality: {0}", "Качество картинки: {0}"),
        ["settings.stats"] = new("Show stream statistics while playing", "Показывать статистику потока в игре"),
        ["settings.quality.hint"] = new("Higher values look better but need more upload bandwidth on the host.",
                                        "Выше значения лучше картинка, но нужен более быстрый интернет у хоста."),

        ["settings.slot"] = new("Player slot", "Слот игрока"),
        ["settings.player"] = new("Player {0}", "Игрок {0}"),
        ["settings.layer.mine"] = new("My keyboard (what I press)", "Моя клавиатура (что я нажимаю)"),
        ["settings.layer.game"] = new("Game keys (what the host injects)", "Клавиши игры (что нажимает хост)"),
        ["settings.defaults"] = new("Defaults", "По умолчанию"),
        ["settings.bind.hint"] = new("Click a button, then press the key you want. Esc clears the binding.",
                                     "Нажмите кнопку, затем нужную клавишу. Esc очищает привязку."),
        ["settings.bind.press"] = new("press a key...", "нажмите клавишу..."),
        ["settings.bind.updated"] = new("* Binding updated (remember to press SAVE)", "* Привязка изменена (не забудьте СОХРАНИТЬ)"),

        ["settings.capture.what"] = new("What the host streams", "Что транслирует хост"),
        ["settings.capture.window"] = new("Game window (recommended)", "Окно игры (рекомендуется)"),
        ["settings.capture.screen"] = new("Whole screen", "Весь экран"),
        ["settings.capture.target"] = new("Selected game process", "Выбранный процесс игры"),
        ["settings.capture.pick"] = new("Select process...", "Выбрать процесс..."),
        ["settings.capture.clear"] = new("Clear", "Сбросить"),
        ["settings.capture.nothing"] = new("nothing selected", "процесс не выбран"),
        ["settings.capture.hint"] = new("Pick the running game.",
                                        "Выберите запущенную игру."),

        ["settings.themes.installed"] = new("Installed themes (.ddntheme)", "Установленные темы (.ddntheme)"),
        ["settings.themes.apply"] = new("Apply", "Применить"),
        ["settings.themes.import"] = new("Import file...", "Импорт файла..."),
        ["settings.themes.folder"] = new("Open themes folder", "Папка тем"),
        ["settings.themes.builtin"] = new("Built-in theme", "Встроенная тема"),
        ["settings.themes.music"] = new("Play theme background music", "Играть фоновую музыку темы"),
        ["settings.themes.volume"] = new("Music volume: {0}%", "Громкость музыки: {0}%"),
        ["settings.themes.select"] = new("* Select a theme", "* Выберите тему"),
        ["settings.themes.applied"] = new("* Theme applied", "* Тема применена"),
        ["settings.themes.imported"] = new("* Theme imported and applied", "* Тема импортирована и применена"),
        ["settings.themes.restored"] = new("* Built-in theme restored", "* Возвращена встроенная тема"),
        ["settings.themes.invalid"] = new("{0}  (invalid)", "{0}  (повреждена)"),

        ["settings.account.notsigned"] = new("Not signed in.", "Вы не авторизованы."),
        ["settings.account.info"] = new("Signed in as {0} (role: {1}). Settings file: {2}",
                                        "Вы вошли как {0} (роль: {1}). Файл настроек: {2}"),
        ["settings.saved"] = new("* Saved", "* Сохранено"),
        ["settings.saved.status"] = new("Settings saved.", "Настройки сохранены."),

        // ---------------- process picker ----------------
        ["picker.title"] = new("Process List", "Список процессов"),
        ["picker.tab.apps"] = new("Applications", "Приложения"),
        ["picker.tab.processes"] = new("Processes", "Процессы"),
        ["picker.tab.windows"] = new("Windows", "Окна"),
        ["picker.filter"] = new("Filter", "Фильтр"),
        ["picker.attach"] = new("Attach", "Выбрать"),
        ["picker.hint"] = new("Double-click an entry to select it.", "Двойной клик по строке выбрать."),

        // ---------------- admin ----------------
        ["admin.title"] = new("* Admin panel", "* Панель администратора"),
        ["admin.tab.users"] = new("Users", "Пользователи"),
        ["admin.tab.lobbies"] = new("Lobbies", "Лобби"),
        ["admin.tab.server"] = new("Server", "Сервер"),
        ["admin.search"] = new("Search", "Поиск"),
        ["admin.nouser"] = new("No user selected", "Пользователь не выбран"),
        ["admin.rainbow"] = new("Rainbow nickname", "Переливающийся ник"),
        ["admin.color"] = new("Static name color (#RRGGBB)", "Постоянный цвет ника (#RRGGBB)"),
        ["admin.badge"] = new("Badge (e.g. dev, adm)", "Бейдж (например dev, adm)"),
        ["admin.role"] = new("Role", "Роль"),
        ["admin.rename"] = new("Rename user...", "Переименовать..."),
        ["admin.ban"] = new("Ban account...", "Забанить аккаунт..."),
        ["admin.unban"] = new("Unban account", "Разбанить аккаунт"),
        ["admin.delete"] = new("Delete account", "Удалить аккаунт"),
        ["admin.forceclose"] = new("Force close selected", "Принудительно закрыть"),
        ["admin.broadcast.label"] = new("Broadcast message to everyone online", "Сообщение всем, кто онлайн"),
        ["admin.broadcast.button"] = new("SEND BROADCAST", "РАЗОСЛАТЬ"),
        ["admin.motd.label"] = new("Message of the day", "Сообщение дня"),
        ["admin.motd.button"] = new("SAVE MOTD", "СОХРАНИТЬ MOTD"),
        ["admin.stats.refresh"] = new("Refresh stats", "Обновить статистику"),
        ["admin.stats"] = new("online: {0} lobbies: {1} playing: {2} accounts: {3} uptime: {4} min",
                              "онлайн: {0} лобби: {1} играют: {2} аккаунтов: {3} аптайм: {4} мин"),
        ["admin.online"] = new("Online now: {0}", "Сейчас онлайн: {0}"),
        ["admin.newname"] = new("New username:", "Новый логин:"),
        ["admin.banreason"] = new("Ban reason:", "Причина бана:"),
        ["admin.confirmdelete"] = new("Delete the account {0} permanently?", "Удалить аккаунт {0} навсегда?"),
        ["admin.done"] = new("* Done", "* Готово"),
    };
}
