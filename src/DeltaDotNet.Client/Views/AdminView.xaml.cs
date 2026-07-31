using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using DeltaDotNet.Client.Localization;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Views;

/// <summary>
/// Owner-only control panel (account s4msepi0l): rainbow nicknames, badges, roles,
/// account bans, lobby moderation, broadcasts and the message of the day.
/// </summary>
public partial class AdminView : UserControl
{
    private List<UserInfo> _users = new();

    public AdminView()
    {
        InitializeComponent();
        Loaded += async (_, _) =>
        {
            await RefreshStatsAsync();
            await RefreshUsersAsync("");
            await RefreshLobbiesAsync();
        };
    }

    private UserInfo Selected => UserList.SelectedIndex >= 0 && UserList.SelectedIndex < _users.Count
        ? _users[UserList.SelectedIndex]
        : null;

    // ---------------- loading ----------------
    private async Task RefreshUsersAsync(string query)
    {
        try
        {
            _users = await App.Api.AdminUsersAsync(query);
            UserList.ItemsSource = _users.Select(u =>
                $"{u.Username}  [{u.Role}]" +
                (u.Rainbow ? "  RAINBOW" : "") +
                (string.IsNullOrEmpty(u.Badge) ? "" : $"  <{u.Badge}>") +
                (u.Banned ? "  BANNED" : "")).ToList();
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private async Task RefreshStatsAsync()
    {
        try
        {
            var res = await App.Api.AdminStatsAsync();
            var s = res.Stats;
            StatsText.Text = $"online: {s.Online} · lobbies: {s.Lobbies} (playing: {s.Playing}) · " +
                             $"accounts: {s.Users} · uptime: {TimeSpan.FromSeconds(s.UptimeSec):hh\\:mm\\:ss}";
            OnlineText.Text = Loc.T("admin.online") + " " + (res.Online.Count == 0
                ? Loc.T("none")
                : string.Join(", ", res.Online.Select(u => u.Username)));
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private async Task RefreshLobbiesAsync()
    {
        try { LobbyList.ItemsSource = await App.Api.AdminLobbiesAsync(); }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    // ---------------- user tab ----------------
    private async void Search_Click(object sender, RoutedEventArgs e) => await RefreshUsersAsync(SearchBox.Text.Trim());

    private void UserList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var user = Selected;
        if (user == null) return;
        SelectedUserText.Text = $"{user.Username}  · id {user.Id[..8]}";
        RainbowBox.IsChecked = user.Rainbow;
        ColorBox.Text = user.NameColor ?? "";
        BadgeBox.Text = user.Badge ?? "";
        RoleBox.SelectedIndex = user.Role == "admin" ? 1 : 0;
    }

    private async void Apply_Click(object sender, RoutedEventArgs e)
    {
        var user = Selected;
        if (user == null) { MessageText.Text = "* Select a user"; return; }
        try
        {
            await App.Api.AdminPatchUserAsync(user.Id, new AdminUserPatch
            {
                Rainbow = RainbowBox.IsChecked == true,
                NameColor = ColorBox.Text.Trim(),
                Badge = BadgeBox.Text.Trim(),
                Role = user.IsOwner ? null : ((ComboBoxItem)RoleBox.SelectedItem).Content.ToString()
            });
            MessageText.Text = "* Updated " + user.Username;
            await RefreshUsersAsync(SearchBox.Text.Trim());
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private async void Rename_Click(object sender, RoutedEventArgs e)
    {
        var user = Selected;
        if (user == null) { MessageText.Text = "* Select a user"; return; }
        var dialog = new PromptDialog(Loc.T("admin.rename"), Loc.T("admin.newname"), user.Username)
        {
            Owner = Window.GetWindow(this)
        };
        if (dialog.ShowDialog() != true) return;
        try
        {
            await App.Api.AdminPatchUserAsync(user.Id, new AdminUserPatch { Username = dialog.Value.Trim() });
            await RefreshUsersAsync(SearchBox.Text.Trim());
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private async void Ban_Click(object sender, RoutedEventArgs e)
    {
        var user = Selected;
        if (user == null) { MessageText.Text = "* Select a user"; return; }
        var dialog = new PromptDialog(Loc.T("admin.ban"), Loc.F("admin.banreason", user.Username))
        {
            Owner = Window.GetWindow(this)
        };
        if (dialog.ShowDialog() != true) return;
        try
        {
            await App.Api.AdminPatchUserAsync(user.Id, new AdminUserPatch { Banned = true, Reason = dialog.Value });
            await RefreshUsersAsync(SearchBox.Text.Trim());
            MessageText.Text = "* Banned " + user.Username;
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private async void Unban_Click(object sender, RoutedEventArgs e)
    {
        var user = Selected;
        if (user == null) return;
        try
        {
            await App.Api.AdminPatchUserAsync(user.Id, new AdminUserPatch { Banned = false });
            await RefreshUsersAsync(SearchBox.Text.Trim());
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private async void Delete_Click(object sender, RoutedEventArgs e)
    {
        var user = Selected;
        if (user == null) return;
        if (MessageBox.Show(Loc.F("admin.confirmdelete", user.Username), "DeltaDotNet",
                MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
        try
        {
            await App.Api.AdminDeleteUserAsync(user.Id);
            await RefreshUsersAsync(SearchBox.Text.Trim());
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    // ---------------- lobby tab ----------------
    private async void RefreshLobbies_Click(object sender, RoutedEventArgs e) => await RefreshLobbiesAsync();

    private async void CloseLobby_Click(object sender, RoutedEventArgs e)
    {
        if (LobbyList.SelectedItem is not LobbyInfo lobby) { MessageText.Text = Loc.T("browser.select"); return; }
        try
        {
            await App.Api.AdminDeleteLobbyAsync(lobby.Id);
            await RefreshLobbiesAsync();
            MessageText.Text = "* Lobby #" + lobby.Id + " closed";
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    // ---------------- server tab ----------------
    private async void Broadcast_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            await App.Api.AdminBroadcastAsync(BroadcastBox.Text.Trim());
            MessageText.Text = "* Broadcast sent";
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private async void Motd_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            await App.Api.AdminSetMotdAsync(MotdBox.Text.Trim());
            MessageText.Text = "* MOTD updated";
        }
        catch (Exception ex) { MessageText.Text = "* " + ex.Message; }
    }

    private async void RefreshStats_Click(object sender, RoutedEventArgs e) => await RefreshStatsAsync();

    private void Back_Click(object sender, RoutedEventArgs e) =>
        MainWindow.Instance.Navigate(new LobbyBrowserView());
}
