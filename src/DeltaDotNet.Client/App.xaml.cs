using System.Windows;
using DeltaDotNet.Core;
using DeltaDotNet.Client.Localization;
using DeltaDotNet.Client.Services;

namespace DeltaDotNet.Client;

/// <summary>Application entry point and global state container.</summary>
public partial class App : Application
{
    /// <summary>Persisted user settings (server URL, quality, key bindings, theme).</summary>
    public static AppSettings Settings { get; private set; } = AppSettings.Load();

    /// <summary>REST client for the DeltaDotNet server.</summary>
    public static ApiClient Api { get; set; } = new(Settings.ServerUrl);

    /// <summary>Realtime relay connection (lobby events, video, input).</summary>
    public static RelayClient Relay { get; } = new();

    /// <summary>Active theme (built-in or loaded from a .ddntheme package).</summary>
    public static ThemeManager Theme { get; } = new();

    /// <summary>The currently signed-in user, or null.</summary>
    public static UserInfo User { get; set; }

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Language must be set before the first window is created so that every
        // {loc:Tr key} markup extension resolves in the right language.
        Loc.SetLanguage(Settings.Language);

        Theme.ApplyStartupTheme();

        DispatcherUnhandledException += (_, args) =>
        {
            MessageBox.Show(Loc.T("unexpected") + "\n\n" + args.Exception.Message,
                "DeltaDotNet", MessageBoxButton.OK, MessageBoxImage.Error);
            args.Handled = true;
        };
    }

    protected override void OnExit(ExitEventArgs e)
    {
        try { Settings.Save(); } catch { }
        try { Relay.Dispose(); } catch { }
        base.OnExit(e);
    }
}
