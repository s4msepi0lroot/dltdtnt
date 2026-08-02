using System;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using DeltaDotNet.Client.Core;

namespace DeltaDotNet.Client.Views
{
    /// <summary>Server browser + "create lobby" form.</summary>
    public partial class LobbyListView : UserControl
    {
        public LobbyListView()
        {
            InitializeComponent();
            NameBox.Text = Session.Display + "'s lobby";
            MotdText.Text = Session.Motd;

            Loaded += (s, e) =>
            {
                Session.Net.Message += OnMessage;
                Refresh();
            };
            Unloaded += (s, e) => { Session.Net.Message -= OnMessage; };
        }

        private void OnMessage(JsonElement msg)
        {
            var t = Json.Str(msg, "t");
            if (t == "lobby.list")
            {
                Dispatcher.Invoke(() => RenderList(msg));
            }
            else if (t == "lobby.joined")
            {
                Session.Lobby = LobbyInfo.Parse(Json.Obj(msg, "lobby"));
                Session.MySlot = Json.Int(msg, "slot", 1);
                Session.IsHost = Json.Bool(msg, "isHost");
                Dispatcher.Invoke(() => MainWindow.Instance.Navigate(new LobbyRoomView()));
            }
        }

        private void Refresh()
        {
            _ = Session.Net.SendAsync(new { t = "lobby.list" });
        }

        private void Refresh_Click(object sender, RoutedEventArgs e) { Refresh(); }

        private void RenderList(JsonElement msg)
        {
            ListPanel.Children.Clear();
            JsonElement arr;
            if (!msg.TryGetProperty("lobbies", out arr) || arr.ValueKind != JsonValueKind.Array) return;

            int count = 0;
            foreach (var l in arr.EnumerateArray())
            {
                count++;
                var id = Json.Str(l, "id");
                var locked = Json.Bool(l, "locked");
                var row = new Border
                {
                    BorderBrush = (System.Windows.Media.Brush)FindResource("DdnBorderBrush"),
                    BorderThickness = new Thickness(2),
                    Padding = new Thickness(10),
                    Margin = new Thickness(0, 0, 0, 8)
                };

                var grid = new Grid();
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var info = new StackPanel();
                info.Children.Add(new TextBlock
                {
                    Text = (locked ? "[LOCKED] " : "") + Json.Str(l, "name"),
                    Style = (Style)FindResource("DdnText"),
                    FontSize = 18
                });
                info.Children.Add(new TextBlock
                {
                    Text = "host: " + Json.Str(l, "host") +
                           "   players: " + Json.Int(l, "players") + "/" + Json.Int(l, "maxPlayers") +
                           "   state: " + Json.Str(l, "state") +
                           "   id: " + id,
                    Style = (Style)FindResource("DdnMuted")
                });
                Grid.SetColumn(info, 0);
                grid.Children.Add(info);

                var joinBtn = new Button
                {
                    Content = "JOIN",
                    Style = (Style)FindResource("DdnButton"),
                    VerticalAlignment = VerticalAlignment.Center,
                    Margin = new Thickness(8, 0, 0, 0)
                };
                joinBtn.Click += (s, e) => Join(id, locked);
                Grid.SetColumn(joinBtn, 1);
                grid.Children.Add(joinBtn);

                row.Child = grid;
                ListPanel.Children.Add(row);
            }

            if (count == 0)
            {
                ListPanel.Children.Add(new TextBlock
                {
                    Text = "No open lobbies yet. Create the first one!",
                    Style = (Style)FindResource("DdnMuted")
                });
            }
        }

        private void Join(string id, bool locked)
        {
            string password = null;
            if (locked)
            {
                password = Prompt.Show("This lobby is closed. Enter the password (leave empty if you are on the guest list):", "");
                if (password == null) return;
            }
            _ = Session.Net.SendAsync(new { t = "lobby.join", id, password });
        }

        private void JoinById_Click(object sender, RoutedEventArgs e)
        {
            var id = JoinIdBox.Text.Trim();
            if (id.Length == 0) return;
            _ = Session.Net.SendAsync(new { t = "lobby.join", id, password = JoinPassBox.Text });
        }

        private void Vis_Changed(object sender, SelectionChangedEventArgs e)
        {
            if (ClosedPanel == null) return;
            ClosedPanel.Visibility = TagOf(VisBox) == "closed" ? Visibility.Visible : Visibility.Collapsed;
        }

        private void Create_Click(object sender, RoutedEventArgs e)
        {
            int max = 2;
            int.TryParse(TagOf(PlayersBox), out max);
            var allow = new System.Collections.Generic.List<string>();
            if (AllowBox != null && !string.IsNullOrWhiteSpace(AllowBox.Text))
                foreach (var part in AllowBox.Text.Split(','))
                    if (part.Trim().Length > 0) allow.Add(part.Trim());

            _ = Session.Net.SendAsync(new
            {
                t = "lobby.create",
                name = NameBox.Text.Trim(),
                maxPlayers = max,
                visibility = TagOf(VisBox),
                password = PassBox == null ? "" : PassBox.Text,
                allowList = allow
            });
        }

        private static string TagOf(ComboBox box)
        {
            var item = box.SelectedItem as ComboBoxItem;
            return item == null ? "" : Convert.ToString(item.Tag);
        }
    }
}
