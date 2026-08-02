using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace DeltaDotNet.ThemeStudio
{
    public partial class App : Application
    {
        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);
            DispatcherUnhandledException += OnCrash;
        }

        private void OnCrash(object sender, DispatcherUnhandledExceptionEventArgs e)
        {
            try
            {
                var dir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "DeltaDotNet");
                Directory.CreateDirectory(dir);
                File.AppendAllText(Path.Combine(dir, "themestudio-crash.log"),
                    DateTime.Now + "\n" + e.Exception + "\n\n");
            }
            catch { }
            MessageBox.Show("Error: " + e.Exception.Message, "DeltaDotNet Theme Studio");
            e.Handled = true;
        }
    }
}
