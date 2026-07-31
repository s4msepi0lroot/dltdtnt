using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using DeltaDotNet.Client.Services;

namespace DeltaDotNet.Client.Views;

// Cheat Engine style target picker.
//   Applications - visible top-level windows with a title (what you normally want)
//   Processes    - every running process that owns a main window
//   Windows      - every window, including tool windows without a taskbar entry
// The result is a CaptureTarget which the host uses to grab frames.
public partial class ProcessPickerWindow : Window
{
    private List<CaptureTarget> _all = new();

    /// <summary>The chosen target, or null when the dialog was cancelled.</summary>
    public CaptureTarget Selected { get; private set; }

    public ProcessPickerWindow()
    {
        InitializeComponent();
        Tabs.SelectedIndex = 0;
        Reload();
    }

    private void Reload()
    {
        _all = Tabs.SelectedIndex switch
        {
            1 => ScreenCapture.ListProcesses(),
            2 => ScreenCapture.ListWindows(true),
            _ => ScreenCapture.ListWindows(false)
        };
        ApplyFilter();
    }

    private void ApplyFilter()
    {
        if (ItemList == null) return;
        var filter = FilterBox == null ? "" : FilterBox.Text.Trim();
        ItemList.ItemsSource = filter.Length == 0
            ? _all
            : _all.Where(t => t.Display.IndexOf(filter, StringComparison.OrdinalIgnoreCase) >= 0).ToList();
    }

    private void Tabs_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded) return;
        Reload();
    }

    private void Filter_Changed(object sender, TextChangedEventArgs e) => ApplyFilter();

    private void Refresh_Click(object sender, RoutedEventArgs e) => Reload();

    private void Attach_Click(object sender, RoutedEventArgs e)
    {
        if (ItemList.SelectedItem is not CaptureTarget target) return;
        Selected = target;
        DialogResult = true;
        Close();
    }
}
