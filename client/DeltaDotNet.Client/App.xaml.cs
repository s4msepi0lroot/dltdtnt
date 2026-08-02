using System;
using System.Collections.Generic;
using System.IO;
using System.Windows;
using System.Windows.Media;
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

            // language: English by default, saved choice otherwise
            Lang.Current = AppConfig.Current.Language;

            // pixel font: drop any .ttf/.otf into Assets\Fonts and it becomes the UI font
            LoadPixelFont();

            if (!string.IsNullOrWhiteSpace(AppConfig.Current.ThemePath) && File.Exists(AppConfig.Current.ThemePath))
            {
                try { ThemeEngine.Apply(AppConfig.Current.ThemePath); }
                catch (Exception ex) { LogCrash(ex); }
            }
        }

        /// <summary>
        /// Looks for a font file in Assets\Fonts and, if found, makes it the
        /// default UI font (the DdnFont resource). Deltarune-style pixel fonts
        /// such as "Determination Mono" or "8bit Operator" work great here.
        /// The app still runs fine with the monospace fallback if none is present.
        /// </summary>
        private void LoadPixelFont()
        {
            try
            {
                var dir = Path.Combine(AppContext.BaseDirectory, "Assets", "Fonts");
                if (!Directory.Exists(dir)) return;

                bool hasFontFile = false;
                foreach (var f in Directory.GetFiles(dir))
                {
                    var ext = Path.GetExtension(f).ToLowerInvariant();
                    if (ext == ".ttf" || ext == ".otf") { hasFontFile = true; break; }
                }
                if (!hasFontFile) return;

                // Enumerate the families that actually live in that folder and use the first.
                var families = new List<FontFamily>(Fonts.GetFontFamilies(dir + Path.DirectorySeparatorChar));
                if (families.Count > 0)
                {
                    Resources["DdnFont"] = families[0];
                }
            }
            catch (Exception ex) { LogCrash(ex); }
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
