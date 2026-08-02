using System;
using System.Collections.Generic;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using DeltaDotNet.Client.Core;

namespace DeltaDotNet.Client.Views
{
    /// <summary>
    /// Owner-only control room. The server refuses every admin.* message from
    /// anyone except DDN_ADMIN_USERNAME (s4msepi0l by default), so this screen
    /// is useless even if somebody patches the client.
    /// </summary>
    public partial class AdminView : UserControl
    {
        private readonly List<JsonElement> _users = new List<JsonElement>();

        public AdminView()
        {
            InitializeComponent();
            ApplyLang();
            Lang.Changed += ApplyLang;
            Loaded += (s, e) =>
            {
                Session.Net.Message += OnMessage;
                Refresh();
            };
            Unloaded += (s, e) =>
            {
                Session.Net.Message -= OnMessage;
                Lang.Changed -= ApplyLang;
            };
        }

        /// <summary>Localizes the static captions of the owner control room.</summary>
        private void ApplyLang()
        {
            UsersTitle.Text = Lang.T("admin.usersTitle");
            RefreshBtn.Content = Lang.T("admin.refresh");
            ControlTitle.Text = Lang.T("admin.controlRoom");
            BroadcastBtn.Content = Lang.T("admin.broadcast");
            MotdBtn.Content = Lang.T("admin.setMotd");
            MaintenanceBtn.Content = Lang.T("admin.maintenance");
            StatsBtn.Content = Lang.T("admin.refreshStats");
            LiveLobbiesTitle.Text = Lang.T("admin.liveLobbies");
        }

        private void Refresh()
        {
            _ = Session.Net.SendAsync(new { t = "admin.stats" });
            _ = Session.Net.SendAsync(new { t = "admin.users" });
            _ = Session.Net.SendAsync(new { t = "admin.lobbies" });
        }

        private void OnMessage(JsonElement msg)
        {
            switch (Json.Str(msg, "t"))
            {
                case "admin.stats":
                    Dispatcher.Invoke(() =>
                    {
                        StatsText.Text =
                            "accounts: " + Json.Int(msg, "users") +
                            "\nonline: " + Json.Int(msg, "online") +
                            "\nlobbies: " + Json.Int(msg, "lobbies") + " (playing: " + Json.Int(msg, "playing") + ")" +
                            "\nuptime: " + Json.Int(msg, "uptimeSec") + " s" +
                            "\nmemory: " + Json.Int(msg, "memoryMb") + " MB" +
                            "\nmaintenance: " + (Json.Bool(msg, "maintenance") ? "ON" : "off") +
                            "\nmotd: " + Json.Str(msg, "motd");
                    });
                    break;

                case "admin.users":
                    _users.Clear();
                    JsonElement arr;
                    if (msg.TryGetProperty("users", out arr) && arr.ValueKind == JsonValueKind.Array)
                        foreach (var u in arr.EnumerateArray()) _users.Add(u.Clone());
                    Dispatcher.Invoke(RenderUsers);
                    break;

                case "admin.lobbies":
                    Dispatcher.Invoke(() => RenderLobbies(msg));
                    break;

                case "admin.ok":
                    MainWindow.Instance.SetStatus("done: " + Json.Str(msg, "action"));
                    Refresh();
                    break;
            }
        }

        // ------------------------------------------------------------ users
        private void RenderUsers()
        {
            UsersPanel.Children.Clear();
            Rainbow.Clear();
            var filter = (FilterBox.Text ?? "").Trim().ToLowerInvariant();

            foreach (var u in _users)
            {
                var login = Json.Str(u, "login");
                if (filter.Length > 0 && login.ToLowerInvariant().IndexOf(filter, StringComparison.Ordinal) < 0) continue;

                var box = new Border
                {
                    BorderBrush = (Brush)FindResource("DdnBorderBrush"),
                    BorderThickness = new Thickness(2),
                    Padding = new Thickness(10),
                    Margin = new Thickness(0, 0, 0, 8)
                };

                var grid = new Grid();
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var info = new StackPanel();
                var nameLine = new TextBlock
                {
                    Text = Json.Str(u, "display") + (Json.Bool(u, "online") ? "  ● online" : ""),
                    Style = (Style)FindResource("DdnText"),
                    FontSize = 18
                };
                if (Json.Bool(u, "rainbow")) Rainbow.Attach(nameLine);
                info.Children.Add(nameLine);
                info.Children.Add(new TextBlock
                {
                    Text = "login: " + login +
                           "   rank: " + Json.Str(u, "rank") +
                           "   badge: " + (string.IsNullOrEmpty(Json.Str(u, "badge")) ? "-" : Json.Str(u, "badge")) +
                           "   rainbow: " + (Json.Bool(u, "rainbow") ? "yes" : "no"),
                    Style = (Style)FindResource("DdnMuted")
                });
                Grid.SetColumn(info, 0);
                grid.Children.Add(info);

                var tools = new WrapPanel { Width = 330 };
                tools.Children.Add(Btn("RAINBOW", () => Send(new { t = "admin.setRainbow", login, value = !Json.Bool(u, "rainbow") })));
                tools.Children.Add(Btn("COLOR", () =>
                {
                    var c = Prompt.Show("Nickname color in #RRGGBB (empty = default):", Json.Str(u, "nameColor"));
                    if (c == null) return;
                    Send(new { t = "admin.setNameColor", login, color = c });
                }));
                tools.Children.Add(Btn("BADGE", () =>
                {
                    var b = Prompt.Show("Badge text, max 12 chars (empty = remove):", Json.Str(u, "badge"));
                    if (b == null) return;
                    Send(new { t = "admin.setBadge", login, badge = b });
                }));
                tools.Children.Add(Btn("RANK", () =>
                {
                    var r = Prompt.Show("Rank: player / vip / moderator / admin", Json.Str(u, "rank"));
                    if (r == null) return;
                    Send(new { t = "admin.setRank", login, rank = r.Trim().ToLowerInvariant() });
                }));
                tools.Children.Add(Btn("PASSWORD", () =>
                {
                    var p = Prompt.Show("New password for " + login + ":", "");
                    if (string.IsNullOrEmpty(p)) return;
                    Send(new { t = "admin.resetPassword", login, password = p });
                }));
                tools.Children.Add(Btn("BAN", () =>
                {
                    var reason = Prompt.Show("Global ban reason for " + login + ":", "cheating");
                    if (reason == null) return;
                    Send(new { t = "admin.globalBan", login, value = true, reason });
                }, true));
                tools.Children.Add(Btn("UNBAN", () => Send(new { t = "admin.globalBan", login, value = false })));
                tools.Children.Add(Btn("DELETE", () =>
                {
                    if (MessageBox.Show("Delete the account " + login + "?", "DeltaDotNet",
                            MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
                    Send(new { t = "admin.deleteUser", login });
                }, true));

                Grid.SetColumn(tools, 1);
                grid.Children.Add(tools);

                box.Child = grid;
                UsersPanel.Children.Add(box);
            }

            if (UsersPanel.Children.Count == 0)
                UsersPanel.Children.Add(new TextBlock { Text = "nothing found", Style = (Style)FindResource("DdnMuted") });
        }

        private void RenderLobbies(JsonElement msg)
        {
            LobbiesPanel.Children.Clear();
            JsonElement arr;
            if (!msg.TryGetProperty("lobbies", out arr) || arr.ValueKind != JsonValueKind.Array) return;
            foreach (var l in arr.EnumerateArray())
            {
                var id = Json.Str(l, "id");
                var sp = new StackPanel { Margin = new Thickness(0, 0, 0, 8) };
                sp.Children.Add(new TextBlock
                {
                    Text = Json.Str(l, "name") + "  [" + id + "]",
                    Style = (Style)FindResource("DdnText")
                });
                sp.Children.Add(new TextBlock
                {
                    Text = "host: " + Json.Str(l, "host") + "   " + Json.Int(l, "players") + "/" + Json.Int(l, "maxPlayers") +
                           "   " + Json.Str(l, "state") + "   " + Json.Str(l, "visibility"),
                    Style = (Style)FindResource("DdnMuted")
                });
                sp.Children.Add(Btn("CLOSE THIS LOBBY", () => Send(new { t = "admin.killLobby", id }), true));
                LobbiesPanel.Children.Add(sp);
            }
            if (LobbiesPanel.Children.Count == 0)
                LobbiesPanel.Children.Add(new TextBlock { Text = "no live lobbies", Style = (Style)FindResource("DdnMuted") });
        }

        // ------------------------------------------------------------ actions
        private void Broadcast_Click(object sender, RoutedEventArgs e)
        {
            var text = Prompt.Show("Message for every connected player:", "");
            if (string.IsNullOrWhiteSpace(text)) return;
            Send(new { t = "admin.broadcast", text });
        }

        private void Motd_Click(object sender, RoutedEventArgs e)
        {
            var text = Prompt.Show("New message of the day:", Session.Motd);
            if (text == null) return;
            Send(new { t = "admin.setMotd", text });
        }

        private void Maintenance_Click(object sender, RoutedEventArgs e)
        {
            var answer = MessageBox.Show("Turn maintenance mode ON?\nYes = on, No = off", "DeltaDotNet",
                MessageBoxButton.YesNoCancel);
            if (answer == MessageBoxResult.Cancel) return;
            Send(new { t = "admin.setMaintenance", value = answer == MessageBoxResult.Yes });
        }

        private void Stats_Click(object sender, RoutedEventArgs e) { Refresh(); }
        private void Refresh_Click(object sender, RoutedEventArgs e) { Refresh(); }
        private void Filter_Changed(object sender, TextChangedEventArgs e) { RenderUsers(); }

        private void Send(object payload) { _ = Session.Net.SendAsync(payload); }

        private Button Btn(string text, Action onClick, bool danger = false)
        {
            var b = new Button
            {
                Content = text,
                Style = (Style)FindResource(danger ? "DdnDangerButton" : "DdnButton"),
                FontSize = 12,
                Padding = new Thickness(8, 4, 8, 4)
            };
            b.Click += (s, e) => onClick();
            return b;
        }
    }
}
