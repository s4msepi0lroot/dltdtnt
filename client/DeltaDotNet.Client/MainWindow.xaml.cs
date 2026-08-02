using System;
using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using DeltaDotNet.Client.Core;
using DeltaDotNet.Client.Views;

namespace DeltaDotNet.Client
{
    /// <summary>
    /// Application shell: top bar, navigation and the global connection events.
    /// Every screen is a UserControl placed into <c>PageHost</c>.
    /// </summary>
    public partial class MainWindow : Window
    {
        public static MainWindow Instance;

        public MainWindow()
        {
            InitializeComponent();
            Instance = this;

            Width = Math.Max(800, AppConfig.Current.WindowWidth);
            Height = Math.Max(480, AppConfig.Current.WindowHeight);

            LoadBranding();
            ThemeEngine.ThemeChanged += LoadBranding;

            Session.Net.Message += OnNetMessage;
            Session.Net.Disconnected += OnNetDisconnected;

            ShowLoggedOutChrome();
            Navigate(new LoginView());
        }

        // ------------------------------------------------------------ branding
        /// <summary>Loads Assets\logo.png / icon.png if the user added them.</summary>
        public void LoadBranding()
        {
            try
            {
                var themeLogo = Application.Current.Resources["DdnLogoImage"] as BitmapSource;
                if (themeLogo != null)
                {
                    LogoImage.Source = themeLogo;
                    LogoImage.Visibility = Visibility.Visible;
                    LogoText.Visibility = Visibility.Collapsed;
                }
                else
                {
                    var path = Path.Combine(AppContext.BaseDirectory, "Assets", "logo.png");
                    if (File.Exists(path))
                    {
                        LogoImage.Source = ThemeEngine.LoadImage(path);
                        LogoImage.Visibility = Visibility.Visible;
                        LogoText.Visibility = Visibility.Collapsed;
                    }
                    else
                    {
                        LogoImage.Visibility = Visibility.Collapsed;
                        LogoText.Visibility = Visibility.Visible;
                    }
                }

                var iconPath = Path.Combine(AppContext.BaseDirectory, "Assets", "icon.png");
                if (File.Exists(iconPath)) Icon = ThemeEngine.LoadImage(iconPath);
            }
            catch { }
        }

        // ------------------------------------------------------------ navigation
        public void Navigate(UserControl view)
        {
            Rainbow.Clear();
            PageHost.Content = view;
        }

        public void SetStatus(string text)
        {
            Dispatcher.Invoke(() => { StatusText.Text = "* " + text; });
        }

        public void ShowLoggedInChrome()
        {
            NavPanel.Visibility = Visibility.Visible;
            NavLogout.Visibility = Visibility.Visible;
            NavAdmin.Visibility = Session.IsAdmin ? Visibility.Visible : Visibility.Collapsed;
            UserLabel.Text = Session.Display + (string.IsNullOrEmpty(Session.Rank) ? "" : "  [" + Session.Rank + "]");
            if (Session.Rainbow) Rainbow.Attach(UserLabel);
        }

        public void ShowLoggedOutChrome()
        {
            NavPanel.Visibility = Visibility.Collapsed;
            NavLogout.Visibility = Visibility.Collapsed;
            NavAdmin.Visibility = Visibility.Collapsed;
            UserLabel.Text = "";
        }

        private void NavLobbies_Click(object sender, RoutedEventArgs e)
        {
            if (Session.Lobby != null) Navigate(new LobbyRoomView());
            else Navigate(new LobbyListView());
        }

        private void NavSettings_Click(object sender, RoutedEventArgs e)
        {
            Navigate(new SettingsView());
        }

        private void NavAdmin_Click(object sender, RoutedEventArgs e)
        {
            if (!Session.IsAdmin)
            {
                MessageBox.Show("The admin panel is only available for the owner account.", "DeltaDotNet");
                return;
            }
            Navigate(new AdminView());
        }

        private async void NavLogout_Click(object sender, RoutedEventArgs e)
        {
            await Session.Net.DisconnectAsync();
            Session.Reset();
            AppConfig.Current.Token = "";
            AppConfig.Save();
            ShowLoggedOutChrome();
            Navigate(new LoginView());
            SetStatus("signed out");
        }

        // ------------------------------------------------------------ global events
        private void OnNetMessage(JsonElement msg)
        {
            var type = Json.Str(msg, "t");
            switch (type)
            {
                case "hello":
                    Session.Motd = Json.Str(msg, "motd");
                    Session.ApplyProfile(Json.Obj(msg, "you"));
                    Dispatcher.Invoke(ShowLoggedInChrome);
                    SetStatus("connected — " + Session.Motd);
                    break;

                case "profile.updated":
                    Session.ApplyProfile(Json.Obj(msg, "profile"));
                    Dispatcher.Invoke(ShowLoggedInChrome);
                    break;

                case "announce":
                    Dispatcher.Invoke(() => MessageBox.Show(Json.Str(msg, "text"),
                        "Announcement from " + Json.Str(msg, "from")));
                    break;

                case "kicked":
                    Session.Lobby = null;
                    Dispatcher.Invoke(() =>
                    {
                        MessageBox.Show(Json.Str(msg, "reason"), "DeltaDotNet");
                        Navigate(new LobbyListView());
                    });
                    break;

                case "lobby.closed":
                    Session.Lobby = null;
                    Session.IsHost = false;
                    Dispatcher.Invoke(() =>
                    {
                        SetStatus(Json.Str(msg, "reason", "lobby closed"));
                        Navigate(new LobbyListView());
                    });
                    break;

                case "error":
                    SetStatus("error: " + Json.Str(msg, "message"));
                    break;
            }
        }

        private void OnNetDisconnected(string reason)
        {
            Session.Lobby = null;
            Dispatcher.Invoke(() =>
            {
                SetStatus("disconnected: " + reason);
                ShowLoggedOutChrome();
                Navigate(new LoginView());
            });
        }

        protected override void OnClosing(System.ComponentModel.CancelEventArgs e)
        {
            AppConfig.Current.WindowWidth = Width;
            AppConfig.Current.WindowHeight = Height;
            AppConfig.Save();
            try { InputInjector.ReleaseAll(); } catch { }
            try { Session.Net.DisconnectAsync().Wait(500); } catch { }
            base.OnClosing(e);
        }
    }
}
