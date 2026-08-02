using System.Windows;
using System.Windows.Controls;

namespace DeltaDotNet.Client.Views
{
    /// <summary>Tiny themed input dialog (WPF has no built in InputBox).</summary>
    public static class Prompt
    {
        /// <summary>Returns the typed text, or null when the user cancels.</summary>
        public static string Show(string message, string initial = "", string title = "DeltaDotNet")
        {
            var win = new Window
            {
                Title = title,
                Width = 520,
                SizeToContent = SizeToContent.Height,
                WindowStartupLocation = WindowStartupLocation.CenterScreen,
                ResizeMode = ResizeMode.NoResize,
                Background = (System.Windows.Media.Brush)Application.Current.Resources["DdnPanelBrush"],
                Owner = Application.Current.MainWindow
            };

            var panel = new StackPanel { Margin = new Thickness(16) };
            panel.Children.Add(new TextBlock
            {
                Text = message,
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 10),
                Style = (Style)Application.Current.Resources["DdnText"]
            });

            var box = new TextBox
            {
                Text = initial ?? "",
                Style = (Style)Application.Current.Resources["DdnInput"]
            };
            panel.Children.Add(box);

            var buttons = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right
            };
            var ok = new Button
            {
                Content = "OK",
                IsDefault = true,
                Style = (Style)Application.Current.Resources["DdnButton"]
            };
            var cancel = new Button
            {
                Content = "CANCEL",
                IsCancel = true,
                Style = (Style)Application.Current.Resources["DdnButton"]
            };
            buttons.Children.Add(ok);
            buttons.Children.Add(cancel);
            panel.Children.Add(buttons);

            win.Content = panel;

            string result = null;
            ok.Click += (s, e) => { result = box.Text; win.DialogResult = true; };
            box.Focus();
            box.SelectAll();

            var dr = win.ShowDialog();
            return dr == true ? result : null;
        }
    }
}
