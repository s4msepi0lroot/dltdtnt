using System.Windows;

namespace DeltaDotNet.Client.Views;

/// <summary>Small modal used for passwords, kick/ban reasons and admin inputs.</summary>
public partial class PromptDialog : Window
{
    public string Value => InputBox.Text;

    public PromptDialog(string title, string prompt, string initialValue = "")
    {
        InitializeComponent();
        Title = title;
        PromptText.Text = prompt;
        InputBox.Text = initialValue;
        Owner = MainWindow.Instance;
        Loaded += (_, _) => InputBox.Focus();
    }

    private void Ok_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = true;
        Close();
    }
}
