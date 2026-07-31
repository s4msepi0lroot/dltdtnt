using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Views;

/// <summary>
/// The lobby room: member list, chat, host tools (kick / ban / unban / close lobby)
/// and the START GAME button.
/// </summary>
public partial class LobbyRoomView : UserControl
{
    private LobbyInfo _lobby;
    private int _slot;
    private bool _isHost;
    private bool _ready;

    public LobbyRoomView(LobbyInfo lobby, int slot, bool isHost)
    {
        InitializeComponent();
        _lobby = lobby;
        _slot = slot;
        _isHost = isHost;

        App.Relay.LobbyUpdated += OnLobbyUpdated;
        App.Relay.GameStarted += OnGameStarted;
        App.Relay.Chat += OnChat;
        App.Relay.Announce += OnAnnounce;
        App.Relay.Kicked += OnKicked;
        App.Relay.LobbyClosed += OnLobbyClosed;
        App.Relay.ErrorReceived += OnError;

        Unloaded += (_, _) =>
        {
            App.Relay.LobbyUpdated -= OnLobbyUpdated;
            App.Relay.GameStarted -= OnGameStarted;
            App.Relay.Chat -= OnChat;
            App.Relay.Announce -= OnAnnounce;
            App.Relay.Kicked -= OnKicked;
            App.Relay.LobbyClosed -= OnLobbyClosed;
            App.Relay.ErrorReceived -= OnError;
        };

        Render();
    }

    // ---------------- rendering ----------------
    private void Render()
    {
        TitleTextBlock.Text = $"* {_lobby.Name}";

        var access = _lobby.AccessMode switch
        {
            "password" => "closed · password",
            "whitelist" => "closed · allow list",
            _ => "open"
        };
        InfoText.Text = $"code #{_lobby.Id} · {_lobby.Players}/{_lobby.MaxPlayers} players · {access} · " +
                        $"host: {_lobby.HostName} · quality {_lobby.Quality.Fps}fps/{_lobby.Quality.Scale}%/q{_lobby.Quality.JpegQuality} · " +
                        $"you are P{_slot + 1}";

        MemberList.ItemsSource = null;
        MemberList.ItemsSource = _lobby.Members;

        StartButton.Visibility = _isHost ? Visibility.Visible : Visibility.Collapsed;
        CloseLobbyButton.Visibility = _isHost ? Visibility.Visible : Visibility.Collapsed;
        HostTools.Visibility = _isHost ? Visibility.Visible : Visibility.Collapsed;
        ReadyButton.Visibility = _isHost ? Visibility.Collapsed : Visibility.Visible;
        ReadyButton.Content = _ready ? "READY ✓" : "READY";

        MainWindow.Instance.SetStatus($"Lobby #{_lobby.Id} — share this code with your friends.");
    }

    private LobbyMember SelectedMember => MemberList.SelectedItem as LobbyMember;

    // ---------------- relay events ----------------
    private void OnLobbyUpdated(LobbyInfo lobby) => Dispatcher.Invoke(() =>
    {
        if (lobby == null || lobby.Id != _lobby.Id) return;
        _lobby = lobby;
        var me = lobby.Members.FirstOrDefault(m => m.Id == App.User?.Id);
        if (me != null)
        {
            _slot = me.Slot;
            _isHost = me.IsHost;
            _ready = me.Ready;
        }
        Render();
    });

    private void OnGameStarted(QualitySettings quality, LobbyInfo lobby) => Dispatcher.Invoke(() =>
    {
        if (lobby != null) _lobby = lobby;
        MainWindow.Instance.Navigate(new GameView(_lobby, _slot, _isHost, quality ?? _lobby.Quality));
    });

    private void OnChat(string from, string text, bool rainbow) => Dispatcher.Invoke(() =>
    {
        ChatList.Items.Add($"{from}: {text}");
        if (ChatList.Items.Count > 0) ChatList.ScrollIntoView(ChatList.Items[^1]);
    });

    private void OnAnnounce(string message) => Dispatcher.Invoke(() =>
    {
        ChatList.Items.Add($"* {message}");
        MainWindow.Instance.SetStatus(message);
    });

    private void OnKicked(bool banned, string reason) => Dispatcher.Invoke(() =>
    {
        MainWindow.Instance.SetStatus(banned
            ? $"You were banned from the lobby. {reason}"
            : $"You were kicked from the lobby. {reason}");
        MainWindow.Instance.Navigate(new LobbyBrowserView());
    });

    private void OnLobbyClosed(string message) => Dispatcher.Invoke(() =>
    {
        MainWindow.Instance.SetStatus(string.IsNullOrEmpty(message) ? "The lobby was closed." : message);
        MainWindow.Instance.Navigate(new LobbyBrowserView());
    });

    private void OnError(string message) => Dispatcher.Invoke(() => MessageText.Text = "* " + message);

    // ---------------- buttons ----------------
    private async void Start_Click(object sender, RoutedEventArgs e)
    {
        MessageText.Text = "";
        if (_lobby.Members.Count < 2)
        {
            MessageText.Text = "* Wait for at least one more player.";
            return;
        }
        await App.Relay.StartAsync();
    }

    private async void Ready_Click(object sender, RoutedEventArgs e)
    {
        _ready = !_ready;
        ReadyButton.Content = _ready ? "READY ✓" : "READY";
        await App.Relay.ReadyAsync(_ready);
    }

    private async void Leave_Click(object sender, RoutedEventArgs e)
    {
        await App.Relay.LeaveAsync();
        MainWindow.Instance.Navigate(new LobbyBrowserView());
    }

    private async void CloseLobby_Click(object sender, RoutedEventArgs e)
    {
        if (MessageBox.Show("Delete this lobby for everyone?", "DeltaDotNet",
                MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
        await App.Relay.CloseLobbyAsync();
        MainWindow.Instance.Navigate(new LobbyBrowserView());
    }

    private async void Kick_Click(object sender, RoutedEventArgs e)
    {
        var member = SelectedMember;
        if (member == null) { MessageText.Text = "* Select a player first."; return; }
        if (member.Id == App.User?.Id) { MessageText.Text = "* You cannot kick yourself."; return; }
        await App.Relay.KickAsync(member.Id);
    }

    private async void Ban_Click(object sender, RoutedEventArgs e)
    {
        var member = SelectedMember;
        if (member == null) { MessageText.Text = "* Select a player first."; return; }
        if (member.Id == App.User?.Id) { MessageText.Text = "* You cannot ban yourself."; return; }
        var dialog = new PromptDialog("Ban player", $"Reason for banning {member.Username}:", "no reason");
        if (dialog.ShowDialog() != true) return;
        await App.Relay.BanAsync(member.Id, dialog.Value);
    }

    private async void Unban_Click(object sender, RoutedEventArgs e)
    {
        if (_lobby.Bans.Count == 0) { MessageText.Text = "* Nobody is banned here."; return; }
        var list = string.Join(", ", _lobby.Bans.Select(b => b.Username));
        var dialog = new PromptDialog("Unban player", $"Banned: {list}\nType the username to unban:");
        if (dialog.ShowDialog() != true) return;
        var ban = _lobby.Bans.FirstOrDefault(b =>
            string.Equals(b.Username, dialog.Value.Trim(), StringComparison.OrdinalIgnoreCase));
        if (ban == null) { MessageText.Text = "* No such banned player."; return; }
        await App.Relay.UnbanAsync(ban.Id);
    }

    // ---------------- chat ----------------
    private async void SendChat_Click(object sender, RoutedEventArgs e) => await SendChatAsync();

    private async void ChatBox_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) { e.Handled = true; await SendChatAsync(); }
    }

    private async Task SendChatAsync()
    {
        var text = ChatBox.Text.Trim();
        if (text.Length == 0) return;
        ChatBox.Text = "";
        await App.Relay.ChatAsync(text);
    }
}
