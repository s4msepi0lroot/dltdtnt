using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;
using DeltaDotNet.Client.Core;

namespace DeltaDotNet.Client
{
    /// <summary>
    /// Application entry point. Loads the configuration and the last used theme
    /// before the main window is shown, and installs a global crash logger.
    /// </summary>
    public partial class App : Application
    {
        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);

            DispatcherUnhandledException += OnUnhandled;
            AppDomain.CurrentDomain.UnhandledException += (s, ev) =>
            {
                LogCrash(ev.ExceptionObject as Exception);
            };

            AppConfig.Load();

            if (!string.IsNullOrWhiteSpace(AppConfig.Current.ThemePath) && File.Exists(AppConfig.Current.ThemePath))
            {
                try { ThemeEngine.Apply(AppConfig.Current.ThemePath); }
                catch (Exception ex) { LogCrash(ex); }
            }
        }

        private void OnUnhandled(object sender, DispatcherUnhandledExceptionEventArgs e)
        {
            LogCrash(e.Exception);
            MessageBox.Show("DeltaDotNet hit an unexpected error:\n\n" + e.Exception.Message +
                            "\n\nDetails were written to " + AppConfig.CrashLogPath,
                            "DeltaDotNet", MessageBoxButton.OK, MessageBoxImage.Error);
            e.Handled = true;
        }

        internal static void LogCrash(Exception ex)
        {
            if (ex == null) return;
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(AppConfig.CrashLogPath));
                File.AppendAllText(AppConfig.CrashLogPath,
                    "[" + DateTime.Now.ToString("s") + "] " + ex + Environment.NewLine + Environment.NewLine);
            }
            catch { /* logging must never throw */ }
        }

        protected override void OnExit(ExitEventArgs e)
        {
            try { AppConfig.Save(); } catch { }
            try { ThemeEngine.StopMusic(); } catch { }
            base.OnExit(e);
        }
    }
}
