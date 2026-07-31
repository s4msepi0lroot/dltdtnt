using System.Windows;
using System.Windows.Controls;
using DeltaDotNet.Core;

namespace DeltaDotNet.Client.Views;

/// <summary>Sign in / register screen. Also auto-logs in with a stored token.</summary>
public partial class LoginView : UserControl
{
    public LoginView()
    {
        InitializeComponent();
        ServerBox.Text = App.Settings.ServerUrl;
        UsernameBox.Text = App.Settings.Username;
        RememberBox.IsChecked = App.Settings.RememberMe;
        Loaded += async (_, _) => await TryAutoLoginAsync();
    }

    private async Task TryAutoLoginAsync()
    {
        if (string.IsNullOrEmpty(App.Settings.Token)) return;
        App.Api = new ApiClient(App.Settings.ServerUrl) { Token = App.Settings.Token };
        var user = await App.Api.MeAsync();
        if (user != null)
        {
            App.User = user;
            await ConnectRelayAsync();
        }
    }

    private async void Login_Click(object sender, RoutedEventArgs e) => await AuthenticateAsync(false);

    private async void Register_Click(object sender, RoutedEventArgs e) => await AuthenticateAsync(true);

    private async Task AuthenticateAsync(bool register)
    {
        MessageText.Text = "";
        LoginButton.IsEnabled = RegisterButton.IsEnabled = false;
        try
        {
            App.Settings.ServerUrl = ServerBox.Text.Trim().TrimEnd('/');
            App.Api = new ApiClient(App.Settings.ServerUrl);

            var res = register
                ? await App.Api.RegisterAsync(UsernameBox.Text.Trim(), PasswordBox.Password)
                : await App.Api.LoginAsync(UsernameBox.Text.Trim(), PasswordBox.Password);

            App.User = res.User;
            App.Settings.Username = res.User.Username;
            App.Settings.RememberMe = RememberBox.IsChecked == true;
            App.Settings.Token = App.Settings.RememberMe ? res.Token : "";
            App.Settings.Save();

            await ConnectRelayAsync();
        }
        catch (Exception ex)
        {
            MessageText.Text = "* " + ex.Message;
        }
        finally
        {
            LoginButton.IsEnabled = RegisterButton.IsEnabled = true;
        }
    }

    private async Task ConnectRelayAsync()
    {
        try
        {
            await App.Relay.ConnectAsync(App.Api.WebSocketUrl, App.Api.Token);
            MainWindow.Instance.SetStatus("Connected to " + App.Settings.ServerUrl);
            MainWindow.Instance.Navigate(new LobbyBrowserView());
        }
        catch (Exception ex)
        {
            MessageText.Text = "* Cannot reach the realtime server: " + ex.Message;
        }
    }
}
