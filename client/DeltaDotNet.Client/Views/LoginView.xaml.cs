using System;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using DeltaDotNet.Client.Core;

namespace DeltaDotNet.Client.Views
{
    /// <summary>Authorization screen: register, sign in and open the WebSocket session.</summary>
    public partial class LoginView : UserControl
    {
        public LoginView()
        {
            InitializeComponent();
            ServerBox.Text = AppConfig.Current.ServerUrl;
            LoginBox.Text = AppConfig.Current.Login;
            RememberBox.IsChecked = AppConfig.Current.RememberMe;

            Loaded += async (s, e) =>
            {
                // auto sign in with the saved token
                if (AppConfig.Current.RememberMe && !string.IsNullOrEmpty(AppConfig.Current.Token))
                {
                    Info("restoring the previous session...");
                    var me = await ApiClient.MeAsync(AppConfig.Current.Token);
                    if (me.Ok)
                    {
                        Session.Token = AppConfig.Current.Token;
                        Session.ApplyProfile(Json.Obj(me.Data, "profile"));
                        await ConnectAsync();
                        return;
                    }
                    AppConfig.Current.Token = "";
                }
                Info("enter your login and password");
            };
        }

        private void Info(string text)
        {
            InfoText.Text = text;
        }

        private void Busy(bool busy)
        {
            LoginBtn.IsEnabled = !busy;
            RegBtn.IsEnabled = !busy;
            PingBtn.IsEnabled = !busy;
        }

        private void StoreServer()
        {
            AppConfig.Current.ServerUrl = ServerBox.Text.Trim();
            AppConfig.Current.RememberMe = RememberBox.IsChecked == true;
            AppConfig.Save();
        }

        private async void Ping_Click(object sender, RoutedEventArgs e)
        {
            StoreServer();
            Busy(true);
            Info("checking " + AppConfig.Current.ServerUrl + " ...");
            var r = await ApiClient.HealthAsync();
            Busy(false);
            if (!r.Ok) { Info("server unavailable: " + r.Error); return; }
            Info("server ok — v" + Json.Str(r.Data, "version") +
                 ", players online: " + Json.Int(r.Data, "online") +
                 ", lobbies: " + Json.Int(r.Data, "lobbies") +
                 "\n" + Json.Str(r.Data, "motd"));
        }

        private async void Login_Click(object sender, RoutedEventArgs e)
        {
            StoreServer();
            Busy(true);
            Info("signing in...");
            var r = await ApiClient.LoginAsync(LoginBox.Text.Trim(), PassBox.Password);
            Busy(false);
            if (!r.Ok) { Info("could not sign in: " + r.Error); return; }
            await AfterAuthAsync(r.Data);
        }

        private async void Register_Click(object sender, RoutedEventArgs e)
        {
            StoreServer();
            Busy(true);
            Info("creating the account...");
            var r = await ApiClient.RegisterAsync(LoginBox.Text.Trim(), PassBox.Password);
            Busy(false);
            if (!r.Ok) { Info("registration failed: " + r.Error); return; }
            await AfterAuthAsync(r.Data);
        }

        private async Task AfterAuthAsync(JsonElement data)
        {
            Session.Token = Json.Str(data, "token");
            Session.ApplyProfile(Json.Obj(data, "profile"));
            AppConfig.Current.Login = Session.Login;
            AppConfig.Current.Token = AppConfig.Current.RememberMe ? Session.Token : "";
            AppConfig.Save();
            await ConnectAsync();
        }

        private async Task ConnectAsync()
        {
            Info("connecting to the relay...");
            var err = await Session.Net.ConnectAsync(AppConfig.Current.ServerUrl, Session.Token);
            if (err != null) { Info(err); return; }
            MainWindow.Instance.ShowLoggedInChrome();
            MainWindow.Instance.Navigate(new LobbyListView());
            MainWindow.Instance.SetStatus("signed in as " + Session.Display);
        }
    }
}
