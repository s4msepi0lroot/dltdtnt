using System;
using System.Collections.Generic;

namespace DeltaDotNet.Client.Core
{
    /// <summary>
    /// ====================================================================
    ///  DeltaDotNet localization.
    ///
    ///  ALL user-facing text lives in this ONE file so both languages can be
    ///  edited side by side. Every phrase is one line:
    ///
    ///      Add("key", "English text", "Русский текст");
    ///
    ///  Add a new language by extending the switch in Get() and adding a third
    ///  string to each Add(...) call (or a parallel dictionary).
    ///
    ///  Usage in code:   Lang.T("login.title")
    ///  Default language: English ("en"). The user can switch it in Settings.
    /// ====================================================================
    /// </summary>
    public static class Lang
    {
        /// <summary>Supported languages, in the order shown in the settings dropdown.</summary>
        public static readonly (string Code, string Title)[] Available = new[]
        {
            ("en", "English"),
            ("ru", "Русский"),
        };

        private static string _current = "en";

        /// <summary>Current language code ("en" or "ru"). Default is "en".</summary>
        public static string Current
        {
            get { return _current; }
            set
            {
                var code = string.IsNullOrWhiteSpace(value) ? "en" : value.Trim().ToLowerInvariant();
                if (code != "en" && code != "ru") code = "en";
                if (code == _current) return;
                _current = code;
                var h = Changed;
                if (h != null) h();
            }
        }

        /// <summary>Raised after the language changes so every open view can re-apply its texts.</summary>
        public static event Action Changed;

        private static readonly Dictionary<string, string> _en = new Dictionary<string, string>();
        private static readonly Dictionary<string, string> _ru = new Dictionary<string, string>();

        private static void Add(string key, string en, string ru)
        {
            _en[key] = en;
            _ru[key] = ru;
        }

        /// <summary>Returns the phrase for <paramref name="key"/> in the current language.</summary>
        public static string T(string key)
        {
            var table = _current == "ru" ? _ru : _en;
            string v;
            if (table.TryGetValue(key, out v)) return v;
            if (_en.TryGetValue(key, out v)) return v; // fall back to English
            return key;                                 // last resort: show the key
        }

        /// <summary>Formats a phrase that contains {0}, {1}, ... placeholders.</summary>
        public static string F(string key, params object[] args)
        {
            try { return string.Format(T(key), args); }
            catch { return T(key); }
        }

        static Lang()
        {
            //            key                         English                                    Русский
            // ---- generic ----
            Add("app.ready",                    "* ready",                                 "* готов");
            Add("common.ok",                    "OK",                                      "ОК");
            Add("common.cancel",                "CANCEL",                                  "ОТМЕНА");
            Add("common.close",                 "CLOSE",                                   "ЗАКРЫТЬ");
            Add("common.yes",                   "YES",                                     "ДА");
            Add("common.no",                    "NO",                                      "НЕТ");
            Add("common.back",                  "BACK",                                    "НАЗАД");
            Add("common.save",                  "SAVE",                                    "СОХРАНИТЬ");
            Add("common.host",                  "host",                                    "хост");
            Add("common.guest",                 "guest",                                   "гость");
            Add("common.player",                "player",                                  "игрок");

            // ---- top bar / navigation ----
            Add("nav.lobbies",                  "LOBBIES",                                 "ЛОББИ");
            Add("nav.settings",                 "SETTINGS",                                "НАСТРОЙКИ");
            Add("nav.admin",                    "ADMIN",                                   "АДМИНКА");
            Add("nav.exit",                     "EXIT",                                    "ВЫЙТИ");
            Add("status.signedInAs",            "signed in as {0}",                        "вход выполнен как {0}");

            // ---- login ----
            Add("login.title",                  "* SIGN IN",                               "* ВХОД");
            Add("login.server",                 "Server address",                          "Адрес сервера");
            Add("login.login",                  "Login",                                   "Логин");
            Add("login.password",               "Password",                                "Пароль");
            Add("login.remember",               "Remember me on this PC",                  "Запомнить меня на этом ПК");
            Add("login.enter",                  "ENTER",                                   "ВОЙТИ");
            Add("login.register",               "CREATE ACCOUNT",                          "СОЗДАТЬ АККАУНТ");
            Add("login.check",                  "CHECK SERVER",                            "ПРОВЕРИТЬ СЕРВЕР");
            Add("login.hint",                   "enter your login and password",           "введите логин и пароль");
            Add("login.localHint",              "Server on this PC? Use http://127.0.0.1:8080", "Сервер на этом ПК? Используйте http://127.0.0.1:8080");
            Add("login.restoring",              "restoring the previous session...",       "восстановление прошлой сессии...");
            Add("login.signingIn",              "signing in...",                           "выполняется вход...");
            Add("login.registering",            "creating the account...",                 "создание аккаунта...");
            Add("login.connecting",             "connecting to the relay...",              "подключение к серверу...");
            Add("login.checking",               "checking {0} ...",                        "проверка {0} ...");
            Add("login.signInFail",             "could not sign in: {0}",                  "не удалось войти: {0}");
            Add("login.regFail",                "registration failed: {0}",                "не удалось зарегистрироваться: {0}");
            Add("login.serverDown",             "server unavailable: {0}",                 "сервер недоступен: {0}");
            Add("login.serverOk",               "server ok - v{0}, players online: {1}, lobbies: {2}", "сервер в порядке - v{0}, игроков онлайн: {1}, лобби: {2}");

            // ---- lobby list ----
            Add("lobbies.title",                "* LOBBIES",                               "* ЛОББИ");
            Add("lobbies.refresh",              "REFRESH",                                 "ОБНОВИТЬ");
            Add("lobbies.joinById",             "Join by ID:",                             "Войти по ID:");
            Add("lobbies.passwordShort",        "password:",                               "пароль:");
            Add("lobbies.join",                 "JOIN",                                    "ВОЙТИ");
            Add("lobbies.empty",                "* no open lobbies. Create your own on the right!", "* нет открытых лобби. Создай своё справа!");
            Add("lobbies.players",              "players",                                 "игроков");
            Add("lobbies.closedTag",            "CLOSED",                                  "ЗАКРЫТОЕ");
            Add("lobbies.openTag",              "OPEN",                                    "ОТКРЫТОЕ");
            Add("lobbies.create",               "* CREATE A LOBBY",                        "* СОЗДАТЬ ЛОББИ");
            Add("lobbies.name",                 "Lobby name",                              "Название лобби");
            Add("lobbies.count",                "Number of players",                       "Количество игроков");
            Add("lobbies.access",               "Access",                                  "Доступ");
            Add("lobbies.playersN",             "{0} players",                             "{0} игроков");
            Add("lobbies.accessOpen",           "Open - anybody can join",                 "Открытое - может зайти любой");
            Add("lobbies.accessClosed",         "Closed - password or guest list",         "Закрытое - пароль или список гостей");
            Add("lobbies.passHint",             "Password (leave empty to use the guest list only)", "Пароль (оставьте пустым, чтобы пускать только по списку гостей)");
            Add("lobbies.allowHint",            "Guest list: logins separated by a comma", "Список гостей: логины через запятую");
            Add("lobbies.createBtn",            "CREATE",                                  "СОЗДАТЬ");
            Add("lobbies.hostNote",             "You become the host: your PC runs the game and streams it. Everyone else only needs the client.", "Вы становитесь хостом: игра запускается на вашем ПК и транслируется. Остальным нужен только клиент.");

            // ---- lobby room ----
            Add("room.title",                   "* LOBBY",                                 "* ЛОББИ");
            Add("room.players",                 "* PLAYERS",                               "* ИГРОКИ");
            Add("room.chat",                    "* CHAT",                                  "* ЧАТ");
            Add("room.start",                   "START THE GAME",                          "НАЧАТЬ ИГРУ");
            Add("room.leave",                   "LEAVE",                                   "ПОКИНУТЬ");
            Add("room.close",                   "CLOSE THE LOBBY",                         "УДАЛИТЬ ЛОББИ");
            Add("room.kick",                    "KICK",                                    "КИКНУТЬ");
            Add("room.ban",                     "BAN",                                     "ЗАБАНИТЬ");
            Add("room.send",                    "SEND",                                    "ОТПРАВИТЬ");
            Add("room.slot",                    "slot {0}",                                "слот {0}");
            Add("room.hostTools",               "* HOST TOOLS",                            "* ИНСТРУМЕНТЫ ХОСТА");
            Add("room.rename",                  "RENAME",                                  "ПЕРЕИМЕНОВАТЬ");
            Add("room.access",                  "ACCESS",                                  "ДОСТУП");
            Add("room.slots",                   "SLOTS",                                   "СЛОТЫ");
            Add("room.unban",                   "UNBAN",                                   "РАЗБАНИТЬ");
            Add("room.watch",                   "OPEN THE STREAM",                         "ОТКРЫТЬ ТРАНСЛЯЦИЮ");
            Add("room.waitingHost",             "* waiting for the host to start...",      "* ждём, пока хост начнёт игру...");
            Add("room.closeConfirm",            "Delete this lobby for everyone?",         "Удалить это лобби для всех?");

            // ---- game view ----
            Add("game.hostTitle",               "* YOU ARE THE HOST",                      "* ВЫ ХОСТ");
            Add("game.guestTitle",              "* LIVE",                                  "* ТРАНСЛЯЦИЯ");
            Add("game.hostSub",                 "Your screen is being streamed. Keep the game window focused.", "Ваш экран транслируется. Держите окно игры в фокусе.");
            Add("game.guestSub",                "Grab the keyboard to play. Press keys as usual.", "Захватите клавиатуру, чтобы играть. Нажимайте клавиши как обычно.");
            Add("game.waiting",                 "* waiting for the first frame...",        "* ждём первый кадр...");
            Add("game.grab",                    "GRAB THE KEYBOARD",                       "ЗАХВАТИТЬ КЛАВИАТУРУ");
            Add("game.release",                 "RELEASE THE KEYBOARD",                    "ОТПУСТИТЬ КЛАВИАТУРУ");
            Add("game.focus",                   "FOCUS THE GAME",                          "ФОКУС НА ИГРУ");
            Add("game.stop",                    "STOP THE GAME",                           "ОСТАНОВИТЬ ИГРУ");
            Add("game.back",                    "BACK TO THE LOBBY",                       "НАЗАД В ЛОББИ");

            // ---- settings ----
            Add("set.tabQuality",               "QUALITY",                                 "КАЧЕСТВО");
            Add("set.tabMyKeys",                "MY KEYS",                                 "МОИ КЛАВИШИ");
            Add("set.tabModKeys",               "MOD KEYS",                                "КЛАВИШИ МОДА");
            Add("set.tabTheme",                 "THEME",                                   "ТЕМА");
            Add("set.tabAccount",               "ACCOUNT",                                 "АККАУНТ");
            Add("set.quality.title",            "* STREAM QUALITY (host only)",            "* КАЧЕСТВО ТРАНСЛЯЦИИ (только хост)");
            Add("set.preset",                   "Preset",                                  "Пресет");
            Add("set.fps",                      "Frames per second: {0}",                  "Кадров в секунду: {0}");
            Add("set.scale",                    "Resolution scale: {0}%",                  "Масштаб разрешения: {0}%");
            Add("set.jpeg",                     "JPEG quality: {0}",                       "Качество JPEG: {0}");
            Add("set.drawCursor",               "Draw the mouse cursor into the stream",   "Рисовать курсор мыши в трансляции");
            Add("set.skipIdentical",            "Do not resend identical frames (saves traffic)", "Не пересылать одинаковые кадры (экономит трафик)");
            Add("set.showStats",                "Show the stream stats overlay",           "Показывать статистику трансляции");
            Add("set.focusGame",                "Focus the game window when the session starts", "Фокусировать окно игры при старте сессии");
            Add("set.capture.title",            "* WHAT TO CAPTURE",                       "* ЧТО ЗАХВАТЫВАТЬ");
            Add("set.capWindow",                "Game window (by title)",                  "Окно игры (по заголовку)");
            Add("set.capScreen",                "Whole screen",                            "Весь экран");
            Add("set.capRegion",                "Screen region",                           "Область экрана");
            Add("set.pickWindow",               "PICK A WINDOW",                           "ВЫБРАТЬ ОКНО");
            Add("set.testCapture",              "TEST THE CAPTURE",                        "ПРОВЕРИТЬ ЗАХВАТ");
            Add("set.myKeys.title",             "* YOUR OWN KEYBOARD",                     "* ВАША КЛАВИАТУРА");
            Add("set.myKeys.note",              "These are the keys YOU press on YOUR keyboard. They are translated into actions and the host converts them into the keys the mod expects for your player slot - so every player can use whatever layout they like.", "Это клавиши, которые нажимаете ВЫ на СВОЕЙ клавиатуре. Они превращаются в действия, а хост переводит их в клавиши, которые ждёт мод для вашего слота - так каждый может выбрать удобную раскладку.");
            Add("set.resetDefaults",            "RESET TO DEFAULTS",                       "СБРОСИТЬ ПО УМОЛЧАНИЮ");
            Add("set.modKeys.title",            "* KEYS THE MOD LISTENS TO",               "* КЛАВИШИ, КОТОРЫЕ СЛУШАЕТ МОД");
            Add("set.theme.title",              "* THEME",                                 "* ТЕМА");
            Add("set.theme.load",               "LOAD A .ddntheme",                        "ЗАГРУЗИТЬ .ddntheme");
            Add("set.theme.reset",              "RESET TO DEFAULT THEME",                  "СБРОСИТЬ ТЕМУ");
            Add("set.music.volume",             "Music volume: {0}%",                      "Громкость музыки: {0}%");
            Add("set.music.enabled",            "Play theme music",                        "Играть музыку темы");
            Add("set.scaleUi",                  "Scale the interface to the window",       "Масштабировать интерфейс под окно");
            Add("set.language",                 "Language / Язык",                         "Язык / Language");
            Add("set.account.title",            "* ACCOUNT",                               "* АККАУНТ");
            Add("set.account.info",             "You are signed in as {0}   rank: {1}",    "Вы вошли как {0}   ранг: {1}");
            Add("set.account.admin",            "(administrator)",                         "(администратор)");
            Add("set.pressKey",                 "press a key...",                          "нажмите клавишу...");
            Add("set.slot",                     "Player slot {0}",                         "Слот игрока {0}");

            // ---- admin ----
            Add("admin.title",                  "* ADMIN PANEL",                           "* ПАНЕЛЬ АДМИНА");
            Add("admin.onlyOwner",              "This panel is available only from the owner account.", "Эта панель доступна только с аккаунта владельца.");
            Add("admin.users",                  "USERS",                                   "ПОЛЬЗОВАТЕЛИ");
            Add("admin.lobbies",                "LOBBIES",                                 "ЛОББИ");
            Add("admin.broadcast",              "BROADCAST",                               "ОБЪЯВЛЕНИЕ");
            Add("admin.rainbow",                "RAINBOW NICK",                            "РАДУЖНЫЙ НИК");
            Add("admin.setRank",                "SET RANK",                                "ВЫДАТЬ РАНГ");
            Add("admin.ban",                    "GLOBAL BAN",                              "ГЛОБАЛЬНЫЙ БАН");
            Add("admin.kickLobby",              "KILL LOBBY",                              "УБИТЬ ЛОББИ");
            Add("admin.usersTitle",             "* USERS",                                 "* ПОЛЬЗОВАТЕЛИ");
            Add("admin.controlRoom",            "* CONTROL ROOM",                          "* ПУЛЬТ УПРАВЛЕНИЯ");
            Add("admin.refresh",                "REFRESH",                                 "ОБНОВИТЬ");
            Add("admin.setMotd",                "SET MOTD",                                "ЗАДАТЬ MOTD");
            Add("admin.maintenance",            "MAINTENANCE ON/OFF",                      "ТЕХРАБОТЫ ВКЛ/ВЫКЛ");
            Add("admin.refreshStats",           "REFRESH STATS",                           "ОБНОВИТЬ СТАТИСТИКУ");
            Add("admin.liveLobbies",            "* LIVE LOBBIES",                          "* АКТИВНЫЕ ЛОББИ");
            Add("admin.filter",                 "filter by login...",                      "фильтр по логину...");

            // ---- errors / net ----
            Add("net.badHost",                  "0.0.0.0 is not a real address. Use 127.0.0.1 for a server on this PC.", "0.0.0.0 - это не адрес для подключения. Для сервера на этом ПК используйте 127.0.0.1.");
            Add("net.disconnected",             "* disconnected: {0}",                     "* соединение потеряно: {0}");
        }
    }
}
