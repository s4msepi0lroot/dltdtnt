using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using DeltaDotNet.Client.Views;

namespace DeltaDotNet.Client;

/// <summary>Shell window: header, navigation host, status bar and theme application.</summary>
public partial class MainWindow : Window
{
    public static MainWindow Instance { get; private set; }

    public MainWindow()
    {
        InitializeComponent();
        Instance = this;

        App.Theme.ThemeChanged += ApplyThemeVisuals;
        ApplyThemeVisuals();

        App.Relay.Announce += text => Dispatcher.Invoke(() =>
            MessageBox.Show(text, "Announcement from the server", MessageBoxButton.OK, MessageBoxImage.Information));
        App.Relay.ErrorReceived += text => Dispatcher.Invoke(() => SetStatus("Server: " + text));

        Navigate(new LoginView());
    }

    // ---------------- navigation ----------------
    public void Navigate(UserControl view)
    {
        ViewHost.Content = view;
        RefreshHeader();
    }

    public void SetStatus(string text) => StatusText.Text = text;

    public void RefreshHeader()
    {
        var user = App.User;
        bool loggedIn = user != null;
        LogoutButton.Visibility = loggedIn ? Visibility.Visible : Visibility.Collapsed;
        AdminButton.Visibility = loggedIn && user.IsOwner ? Visibility.Visible : Visibility.Collapsed;
        UserNameText.Text = loggedIn ? user.Username + (string.IsNullOrEmpty(user.Badge) ? "" : $"  <{user.Badge}>") : "";

        StopRainbow(UserNameText);
        if (loggedIn && user.Rainbow) StartRainbow(UserNameText);
        else if (loggedIn && !string.IsNullOrEmpty(user.NameColor))
            UserNameText.Foreground = new SolidColorBrush(Services.ThemeManager.ParseColor(user.NameColor));
        else UserNameText.SetResourceReference(ForegroundProperty, "TextBrush");

        HeaderStatus.Text = App.Relay.IsConnected ? "connected: " + App.Settings.ServerUrl : "offline";
    }

    // ---------------- theme ----------------
    private void ApplyThemeVisuals()
    {
        var logo = App.Theme.LogoImage;
        LogoImage.Source = logo;
        LogoImage.Visibility = logo != null ? Visibility.Visible : Visibility.Collapsed;
        LogoFallback.Visibility = logo != null ? Visibility.Collapsed : Visibility.Visible;

        BackgroundLayer.Source = App.Theme.BackgroundImage;
        BackgroundLayer.Opacity = App.Theme.Manifest.BackgroundOpacity;
    }

    /// <summary>Animated rainbow nickname — granted from the admin panel.</summary>
    public static void StartRainbow(TextBlock target)
    {
        var brush = new SolidColorBrush(Colors.Red);
        target.Foreground = brush;

        var animation = new ColorAnimationUsingKeyFrames { Duration = TimeSpan.FromSeconds(4), RepeatBehavior = RepeatBehavior.Forever };
        Color[] colors =
        {
            Colors.Red, Colors.Orange, Colors.Yellow, Colors.Lime,
            Colors.Cyan, Colors.Blue, Colors.Magenta, Colors.Red
        };
        for (int i = 0; i < colors.Length; i++)
        {
            animation.KeyFrames.Add(new LinearColorKeyFrame(colors[i],
                KeyTime.FromPercent(i / (double)(colors.Length - 1))));
        }
        brush.BeginAnimation(SolidColorBrush.ColorProperty, animation);
    }

    public static void StopRainbow(TextBlock target)
    {
        if (target.Foreground is SolidColorBrush brush && brush.CanFreeze == false)
            brush.BeginAnimation(SolidColorBrush.ColorProperty, null);
    }

    // ---------------- header buttons ----------------
    private void SettingsButton_Click(object sender, RoutedEventArgs e) => Navigate(new SettingsView());

    private void AdminButton_Click(object sender, RoutedEventArgs e) => Navigate(new AdminView());

    private void LogoutButton_Click(object sender, RoutedEventArgs e)
    {
        App.Settings.Token = "";
        App.Settings.Save();
        App.User = null;
        App.Api.Token = null;
        App.Relay.Dispose();
        Navigate(new LoginView());
        SetStatus("Signed out.");
    }
}
