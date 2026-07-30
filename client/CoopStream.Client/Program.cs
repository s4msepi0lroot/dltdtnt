using CoopStream.Client.Forms;

namespace CoopStream.Client;

/// <summary>Точка входа клиента.</summary>
internal static class Program
{
    [STAThread]
    private static void Main()
    {
        Application.SetHighDpiMode(HighDpiMode.PerMonitorV2);
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);

        Application.ThreadException += (_, e) =>
            MessageBox.Show(e.Exception.ToString(), "Ошибка", MessageBoxButtons.OK, MessageBoxIcon.Error);

        var config = AppConfig.Load();
        var form = new MainForm(config);
        form.Shown += async (_, _) => await form.TryAutoLoginAsync();
        Application.Run(form);
    }
}
