using System.Windows;

namespace DeltaDotNet.ThemeStudio;

/// <summary>Entry point of the DeltaDotNet Theme Studio.</summary>
public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        DispatcherUnhandledException += (_, args) =>
        {
            MessageBox.Show("Unexpected error:\n\n" + args.Exception.Message,
                "Theme Studio", MessageBoxButton.OK, MessageBoxImage.Error);
            args.Handled = true;
        };
    }
}
