using System;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using DeltaDotNet.Client.Core;

namespace DeltaDotNet.Client.Views
{
    /// <summary>
    /// The lobby room: player slots, chat, host tools (kick / ban / slots /
    /// access mode / close the lobby) and the button that starts the session.
    /// </summary>
    public partial class LobbyRoomView : UserControl
    {
        public LobbyRoomView()
        {
            InitializeComponent();
            Loaded += (s, e) =>
            {
                Session.Net.Message += OnMessage;
                Render();
            };
            Unloaded += (s, e) => { Session.Net.Message -= OnMessage; };
        }

        private void OnMessage(JsonElement msg)
        {
            var t = Json.Str(msg, "t");
            switch (t)
            {
                case "lobby.state":
                    Session.Lobby = LobbyInfo.Parse(Json.Obj(msg, "lobby"));
                    foreach (var m in Session.Lobby.Members)
                        if (string.Equals(m.Login, Session.Login, StringComparison.OrdinalIgnoreCase))
                        {
                            Session.MySlot = m.Slot;
                            Session.IsHost = m.IsHost;
                        }
                    Dispatcher.Invoke(Render);
                    break;

                case "lobby.chat":
                    Dispatcher.Invoke(() => AddChat(msg));
                    break;

                case "game.started":
                    Dispatcher.Invoke(() => MainWindow.Instance.Navigate(new GameView()));
                    break;

                case "lobby.left":
                    Session.Lobby = null;
                    Dispatcher.Invoke(() => MainWindow.Instance.Navigate(new LobbyListView()));
                    break;
            }
        }

        // ------------------------------------------------------------ render
        private void Render()
        {
            var lobby = Session.Lobby;
            if (lobby == null)
            {
                MainWindow.Instance.Navigate(new LobbyListView());
                return;
            }

            TitleText.Text = "* " + lobby.Name.ToUpperInvariant();
            SubText.Text = "id: " + lobby.Id +
                           "   access: " + (lobby.Visibility == "closed" ? "closed" : "open") +
                           "   players: " + lobby.Members.Count + "/" + lobby.MaxPlayers +
                           "   host: " + lobby.Host +
                           "   your slot: " + Session.MySlot;

            StartBtn.Visibility = Session.IsHost ? Visibility.Visible : Visibility.Collapsed;
            CloseBtn.Visibility = Session.IsHost ? Visibility.Visible : Visibility.Collapsed;
            HostPanel.Visibility = Session.IsHost ? Visibility.Visible : Visibility.Collapsed;
            WatchBtn.Visibility = lobby.State == "playing" ? Visibility.Visible : Visibility.Collapsed;

            MembersPanel.Children.Clear();
            Rainbow.Clear();

            for (int slot = 1; slot <= lobby.MaxPlayers; slot++)
            {
                MemberInfo member = null;
                foreach (var m in lobby.Members) if (m.Slot == slot) member = m;
                MembersPanel.Children.Add(BuildSlotRow(slot, member));
            }

            if (lobby.Bans.Count > 0)
            {
                MembersPanel.Children.Add(new TextBlock
                {
                    Text = "banned in this lobby: " + string.Join(", ", lobby.Bans),
                    Style = (Style)FindResource("DdnMuted"),
                    Margin = new Thickness(0, 10, 0, 0)
                });
            }
        }

        private Border BuildSlotRow(int slot, MemberInfo member)
        {
            var box = new Border
            {
                BorderBrush = (Brush)FindResource("DdnBorderBrush"),
                BorderThickness = new Thickness(2),
                Padding = new Thickness(10),
                Margin = new Thickness(0, 0, 0, 8)
            };

            var grid = new Grid();
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var slotLabel = new TextBlock
            {
                Text = "P" + slot,
                FontSize = 24,
                Width = 54,
                VerticalAlignment = VerticalAlignment.Center,
                Foreground = (Brush)FindResource("DdnAccentBrush"),
                FontFamily = (FontFamily)FindResource("DdnFont")
            };
            Grid.SetColumn(slotLabel, 0);
            grid.Children.Add(slotLabel);

            var info = new StackPanel();
            if (member == null)
            {
                info.Children.Add(new TextBlock
                {
                    Text = "— empty —",
                    Style = (Style)FindResource("DdnMuted"),
                    FontSize = 16
                });
            }
            else
            {
                var name = new TextBlock
                {
                    Text = member.Display + (member.IsHost ? "  (host)" : "") +
                           (string.IsNullOrEmpty(member.Badge) ? "" : "  [" + member.Badge + "]"),
                    Style = (Style)FindResource("DdnText"),
                    FontSize = 18
                };
                if (member.Rainbow) Rainbow.Attach(name);
                else if (!string.IsNullOrEmpty(member.NameColor))
                {
                    try { name.Foreground = new SolidColorBrush(ThemeEngine.ParseColor(member.NameColor, Colors.White)); }
                    catch { }
                }
                info.Children.Add(name);

                info.Children.Add(new TextBlock
                {
                    Text = "keys of this slot in the game: " + DescribeSlotKeys(slot),
                    Style = (Style)FindResource("DdnMuted")
                });
            }
            Grid.SetColumn(info, 1);
            grid.Children.Add(info);

            if (Session.IsHost && member != null && !member.IsHost)
            {
                var tools = new StackPanel { Orientation = Orientation.Horizontal };
                var kick = new Button { Content = "KICK", Style = (Style)FindResource("DdnButton") };
                kick.Click += (s, e) => { _ = Session.Net.SendAsync(new { t = "lobby.kick", login = member.Login, reason = "kicked by the host" }); };
                var ban = new Button { Content = "BAN", Style = (Style)FindResource("DdnDangerButton") };
                ban.Click += (s, e) =>
                {
                    var reason = Prompt.Show("Ban reason for " + member.Display + ":", "no reason");
                    if (reason == null) return;
                    _ = Session.Net.SendAsync(new { t = "lobby.ban", login = member.Login, reason });
                };
                tools.Children.Add(kick);
                tools.Children.Add(ban);
                Grid.SetColumn(tools, 2);
                grid.Children.Add(tools);
            }

            box.Child = grid;
            return box;
        }

        private static string DescribeSlotKeys(int slot)
        {
            System.Collections.Generic.Dictionary<string, string> map;
            if (AppConfig.Current.SlotGameKeys == null ||
                !AppConfig.Current.SlotGameKeys.TryGetValue(slot.ToString(), out map))
                return "not configured";
            var parts = new System.Collections.Generic.List<string>();
            foreach (var a in Keybinds.Actions)
            {
                string v;
                if (map.TryGetValue(a, out v) && !string.IsNullOrWhiteSpace(v))
                    parts.Add(Keybinds.Pretty(v));
            }
            return parts.Count == 0 ? "not configured" : string.Join(", ", parts);
        }

        // ------------------------------------------------------------ chat
        private void AddChat(JsonElement msg)
        {
            var text = Json.Str(msg, "text");
            var line = new TextBlock
            {
                TextWrapping = TextWrapping.Wrap,
                Style = (Style)FindResource("DdnText"),
                FontSize = 14,
                Margin = new Thickness(0, 0, 0, 3)
            };
            if (Json.Bool(msg, "system"))
            {
                line.Text = "* " + text;
                line.Foreground = (Brush)FindResource("DdnMutedBrush");
            }
            else
            {
                line.Text = Json.Str(msg, "from") + ": " + text;
                if (Json.Bool(msg, "rainbow")) Rainbow.Attach(line);
            }
            ChatPanel.Children.Add(line);
            ChatScroll.ScrollToEnd();
        }

        private void Send_Click(object sender, RoutedEventArgs e) { SendChat(); }

        private void ChatBox_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter) SendChat();
        }

        private void SendChat()
        {
            var text = ChatBox.Text.Trim();
            if (text.Length == 0) return;
            ChatBox.Text = "";
            _ = Session.Net.SendAsync(new { t = "lobby.chat", text });
        }

        // ------------------------------------------------------------ buttons
        private void Start_Click(object sender, RoutedEventArgs e)
        {
            _ = Session.Net.SendAsync(new { t = "lobby.start" });
        }

        private void Watch_Click(object sender, RoutedEventArgs e)
        {
            MainWindow.Instance.Navigate(new GameView());
        }

        private void Leave_Click(object sender, RoutedEventArgs e)
        {
            _ = Session.Net.SendAsync(new { t = "lobby.leave" });
            Session.Lobby = null;
            MainWindow.Instance.Navigate(new LobbyListView());
        }

        private void CloseLobby_Click(object sender, RoutedEventArgs e)
        {
            if (MessageBox.Show("Close the lobby and disconnect everybody?", "DeltaDotNet",
                    MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
            _ = Session.Net.SendAsync(new { t = "lobby.close" });
            Session.Lobby = null;
            MainWindow.Instance.Navigate(new LobbyListView());
        }

        private void Rename_Click(object sender, RoutedEventArgs e)
        {
            var name = Prompt.Show("New lobby name:", Session.Lobby.Name);
            if (name == null) return;
            _ = Session.Net.SendAsync(new { t = "lobby.update", name });
        }

        private void Access_Click(object sender, RoutedEventArgs e)
        {
            var mode = Prompt.Show("Access mode: type \"open\" or \"closed\"", Session.Lobby.Visibility);
            if (mode == null) return;
            mode = mode.Trim().ToLowerInvariant();
            if (mode != "open" && mode != "closed") { MessageBox.Show("Type open or closed."); return; }

            if (mode == "open")
            {
                _ = Session.Net.SendAsync(new { t = "lobby.update", visibility = "open", password = "" });
                return;
            }

            var pass = Prompt.Show("Password for the closed lobby (empty = guest list only):", "");
            if (pass == null) return;
            var allow = Prompt.Show("Guest list, logins separated by a comma:",
                string.Join(", ", Session.Lobby.AllowList));
            if (allow == null) allow = "";
            var list = new System.Collections.Generic.List<string>();
            foreach (var p in allow.Split(',')) if (p.Trim().Length > 0) list.Add(p.Trim());

            _ = Session.Net.SendAsync(new { t = "lobby.update", visibility = "closed", password = pass, allowList = list });
        }

        private void Slots_Click(object sender, RoutedEventArgs e)
        {
            var login = Prompt.Show("Whose slot should be changed? Type the login:", "");
            if (string.IsNullOrWhiteSpace(login)) return;
            var slotText = Prompt.Show("New slot number (1.." + Session.Lobby.MaxPlayers + "):", "2");
            int slot;
            if (!int.TryParse(slotText, out slot)) return;
            _ = Session.Net.SendAsync(new { t = "lobby.setSlot", login, slot });
        }

        private void Unban_Click(object sender, RoutedEventArgs e)
        {
            var login = Prompt.Show("Un-ban which login?", "");
            if (string.IsNullOrWhiteSpace(login)) return;
            _ = Session.Net.SendAsync(new { t = "lobby.unban", login });
        }
    }
}
