using System;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using DeltaDotNet.Client.Localization;
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
        TitleText.Text = Loc.F("room.title", _lobby.Name);

        var access = _lobby.AccessMode switch
        {
            "password" => Loc.T("room.access.password"),
            "whitelist" => Loc.T("room.access.whitelist"),
            _ => Loc.T("room.access.open")
        };
        InfoText.Text = Loc.F("room.info",
            _lobby.Id, _lobby.Players, _lobby.MaxPlayers, access, _lobby.HostName,
            _lobby.Quality.Fps, _lobby.Quality.Scale, _lobby.Quality.JpegQuality, _slot + 1);

        MemberList.ItemsSource = null;
        MemberList.ItemsSource = _lobby.Members;

        var hostOnly = _isHost ? Visibility.Visible : Visibility.Collapsed;
        StartButton.Visibility = hostOnly;
        CloseButton.Visibility = hostOnly;
        KickButton.Visibility = hostOnly;
        BanButton.Visibility = hostOnly;
        UnbanButton.Visibility = hostOnly;
        ReadyButton.Visibility = _isHost ? Visibility.Collapsed : Visibility.Visible;
        ReadyButton.Content = _ready ? Loc.T("room.ready.on") : Loc.T("room.ready");

        MainWindow.Instance.SetStatus(Loc.F("room.share", _lobby.Id));
    }

    private LobbyMember SelectedMember => MemberList.SelectedItem as LobbyMember;

    /// <summary>Appends one line to the chat panel and scrolls to the bottom.</summary>
    private void AddChatLine(string text, bool system)
    {
        var block = new TextBlock { Text = text, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 2) };
        if (system) block.SetResourceReference(ForegroundProperty, "AccentBrush");
        ChatPanel.Children.Add(block);
        ChatScroll.ScrollToEnd();
    }

    private void Warn(string key) => AddChatLine(Loc.T(key), true);

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

    private void OnChat(string from, string text, bool rainbow) =>
        Dispatcher.Invoke(() => AddChatLine($"{from}: {text}", false));

    private void OnAnnounce(string message) => Dispatcher.Invoke(() =>
    {
        AddChatLine("* " + message, true);
        MainWindow.Instance.SetStatus(message);
    });

    private void OnKicked(bool banned, string reason) => Dispatcher.Invoke(() =>
    {
        MainWindow.Instance.SetStatus(banned
            ? Loc.F("room.wasbanned", reason)
            : Loc.F("room.waskicked", reason));
        MainWindow.Instance.Navigate(new LobbyBrowserView());
    });

    private void OnLobbyClosed(string message) => Dispatcher.Invoke(() =>
    {
        MainWindow.Instance.SetStatus(string.IsNullOrEmpty(message) ? Loc.T("room.wasclosed") : message);
        MainWindow.Instance.Navigate(new LobbyBrowserView());
    });

    private void OnError(string message) => Dispatcher.Invoke(() => AddChatLine("* " + message, true));

    // ---------------- buttons ----------------
    private async void Start_Click(object sender, RoutedEventArgs e)
    {
        if (_lobby.Members.Count < 2)
        {
            Warn("room.needmore");
            return;
        }
        await App.Relay.StartAsync();
    }

    private async void Ready_Click(object sender, RoutedEventArgs e)
    {
        _ready = !_ready;
        ReadyButton.Content = _ready ? Loc.T("room.ready.on") : Loc.T("room.ready");
        await App.Relay.ReadyAsync(_ready);
    }

    private async void Leave_Click(object sender, RoutedEventArgs e)
    {
        await App.Relay.LeaveAsync();
        MainWindow.Instance.Navigate(new LobbyBrowserView());
    }

    private async void Close_Click(object sender, RoutedEventArgs e)
    {
        if (MessageBox.Show(Loc.T("room.close.confirm"), "DeltaDotNet",
                MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
        await App.Relay.CloseLobbyAsync();
        MainWindow.Instance.Navigate(new LobbyBrowserView());
    }

    private async void Kick_Click(object sender, RoutedEventArgs e)
    {
        var member = SelectedMember;
        if (member == null) { Warn("room.selectplayer"); return; }
        if (member.Id == App.User?.Id) { Warn("room.notyourself"); return; }
        await App.Relay.KickAsync(member.Id);
    }

    private async void Ban_Click(object sender, RoutedEventArgs e)
    {
        var member = SelectedMember;
        if (member == null) { Warn("room.selectplayer"); return; }
        if (member.Id == App.User?.Id) { Warn("room.notyourself"); return; }
        var dialog = new PromptDialog(Loc.T("room.ban"), Loc.F("room.banreason", member.Username), "-")
        {
            Owner = Window.GetWindow(this)
        };
        if (dialog.ShowDialog() != true) return;
        await App.Relay.BanAsync(member.Id, dialog.Value);
    }

    private async void Unban_Click(object sender, RoutedEventArgs e)
    {
        if (_lobby.Bans.Count == 0) { Warn("room.nobans"); return; }
        var list = string.Join(", ", _lobby.Bans.Select(b => b.Username));
        var dialog = new PromptDialog(Loc.T("room.unban"), Loc.F("room.banned.list", list))
        {
            Owner = Window.GetWindow(this)
        };
        if (dialog.ShowDialog() != true) return;
        var ban = _lobby.Bans.FirstOrDefault(b =>
            string.Equals(b.Username, dialog.Value.Trim(), StringComparison.OrdinalIgnoreCase));
        if (ban == null) { Warn("room.nosuchban"); return; }
        await App.Relay.UnbanAsync(ban.Id);
    }

    // ---------------- chat ----------------
    private async void Send_Click(object sender, RoutedEventArgs e) => await SendChatAsync();

    private async void ChatInput_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) { e.Handled = true; await SendChatAsync(); }
    }

    private async Task SendChatAsync()
    {
        var text = ChatInput.Text.Trim();
        if (text.Length == 0) return;
        ChatInput.Text = "";
        await App.Relay.ChatAsync(text);
    }
}
