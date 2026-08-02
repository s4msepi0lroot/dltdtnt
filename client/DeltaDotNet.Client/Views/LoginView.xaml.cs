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

            ApplyLang();
            Lang.Changed += ApplyLang;
            Unloaded += (s, e) => { Lang.Changed -= ApplyLang; };

            Loaded += async (s, e) =>
            {
                // auto sign in with the saved token
                if (AppConfig.Current.RememberMe && !string.IsNullOrEmpty(AppConfig.Current.Token))
                {
                    Info(Lang.T("login.restoring"));
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
                Info(Lang.T("login.hint"));
            };
        }

        /// <summary>Re-applies every static caption in the current language.</summary>
        private void ApplyLang()
        {
            TitleLabel.Text = Lang.T("login.title");
            ServerLabel.Text = Lang.T("login.server");
            LocalHintLabel.Text = Lang.T("login.localHint");
            LoginLabel.Text = Lang.T("login.login");
            PassLabel.Text = Lang.T("login.password");
            RememberBox.Content = Lang.T("login.remember");
            LoginBtn.Content = Lang.T("login.enter");
            RegBtn.Content = Lang.T("login.register");
            PingBtn.Content = Lang.T("login.check");
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
            Info(Lang.F("login.checking", AppConfig.Current.ServerUrl));
            var r = await ApiClient.HealthAsync();
            Busy(false);
            if (!r.Ok) { Info(Lang.F("login.serverDown", r.Error)); return; }
            Info(Lang.F("login.serverOk", Json.Str(r.Data, "version"), Json.Int(r.Data, "online"), Json.Int(r.Data, "lobbies")) +
                 "\n" + Json.Str(r.Data, "motd"));
        }

        private async void Login_Click(object sender, RoutedEventArgs e)
        {
            StoreServer();
            Busy(true);
            Info(Lang.T("login.signingIn"));
            var r = await ApiClient.LoginAsync(LoginBox.Text.Trim(), PassBox.Password);
            Busy(false);
            if (!r.Ok) { Info(Lang.F("login.signInFail", r.Error)); return; }
            await AfterAuthAsync(r.Data);
        }

        private async void Register_Click(object sender, RoutedEventArgs e)
        {
            StoreServer();
            Busy(true);
            Info(Lang.T("login.registering"));
            var r = await ApiClient.RegisterAsync(LoginBox.Text.Trim(), PassBox.Password);
            Busy(false);
            if (!r.Ok) { Info(Lang.F("login.regFail", r.Error)); return; }
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
            Info(Lang.T("login.connecting"));
            var err = await Session.Net.ConnectAsync(AppConfig.Current.ServerUrl, Session.Token);
            if (err != null) { Info(err); return; }
            MainWindow.Instance.ShowLoggedInChrome();
            MainWindow.Instance.Navigate(new LobbyListView());
            MainWindow.Instance.SetStatus(Lang.F("status.signedInAs", Session.Display));
        }
    }
}
