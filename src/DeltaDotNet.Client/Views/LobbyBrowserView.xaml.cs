using System.Linq;
using System.Windows;
using System.Windows.Controls;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Views;

/// <summary>Lists lobbies and creates new ones (player count, open/closed, quality).</summary>
public partial class LobbyBrowserView : UserControl
{
    public LobbyBrowserView()
    {
        InitializeComponent();
        NameBox.Text = (App.User?.Username ?? "Player") + "'s lobby";
        Loaded += async (_, _) => await RefreshAsync();

        App.Relay.Joined += OnJoined;
        Unloaded += (_, _) => App.Relay.Joined -= OnJoined;
    }

    private void OnJoined(LobbyInfo lobby, int slot, bool isHost)
    {
        Dispatcher.Invoke(() => MainWindow.Instance.Navigate(new LobbyRoomView(lobby, slot, isHost)));
    }

    private async Task RefreshAsync()
    {
        try
        {
            MessageText.Text = "";
            var lobbies = await App.Api.ListLobbiesAsync();
            LobbyList.ItemsSource = lobbies;
            MainWindow.Instance.SetStatus($"{lobbies.Count} lobbies online.");
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e) => await RefreshAsync();

    private async void Join_Click(object sender, RoutedEventArgs e)
    {
        if (LobbyList.SelectedItem is not LobbyInfo lobby) { MessageText.Text = "* Select a lobby first"; return; }
        await JoinAsync(lobby.Id, lobby.AccessMode == "password");
    }

    private async void JoinByCode_Click(object sender, RoutedEventArgs e)
    {
        var code = CodeBox.Text.Trim().ToUpperInvariant();
        if (code.Length == 0) { MessageText.Text = "* Enter a lobby code"; return; }
        await JoinAsync(code, true);
    }

    private async Task JoinAsync(string lobbyId, bool mayNeedPassword)
    {
        string password = null;
        if (mayNeedPassword)
        {
            var dialog = new PromptDialog("Lobby password", "Leave empty if the lobby has no password:");
            if (dialog.ShowDialog() == true) password = dialog.Value;
        }
        try { await App.Relay.JoinAsync(lobbyId, password); }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private void Visibility_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (ClosedPanel == null) return;
        ClosedPanel.Visibility = VisibilityBox.SelectedIndex == 1 ? Visibility.Visible : Visibility.Collapsed;
    }

    private void Access_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (PasswordPanel == null) return;
        bool byPassword = AccessBox.SelectedIndex == 0;
        PasswordPanel.Visibility = byPassword ? Visibility.Visible : Visibility.Collapsed;
        WhitelistPanel.Visibility = byPassword ? Visibility.Collapsed : Visibility.Visible;
    }

    private async void Create_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var quality = QualityBox.SelectedIndex >= 5
                ? App.Settings.Quality.Clone()
                : QualitySettings.Preset(((ComboBoxItem)QualityBox.SelectedItem).Content.ToString());

            var request = new CreateLobbyRequest
            {
                Name = NameBox.Text.Trim(),
                MaxPlayers = PlayersBox.SelectedIndex + 2,
                Visibility = VisibilityBox.SelectedIndex == 1 ? "closed" : "open",
                AccessMode = VisibilityBox.SelectedIndex == 1
                    ? (AccessBox.SelectedIndex == 0 ? "password" : "whitelist")
                    : "none",
                Password = LobbyPasswordBox.Text,
                Whitelist = WhitelistBox.Text
                    .Split(new[] { ',', ';', ' ' }, StringSplitOptions.RemoveEmptyEntries)
                    .ToList(),
                Quality = quality
            };

            var lobby = await App.Api.CreateLobbyAsync(request);
            await App.Relay.JoinAsync(lobby.Id, request.Password);
            MainWindow.Instance.SetStatus("Lobby created: #" + lobby.Id);
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }
}
