using DeltaDotNet.Client.Input;
using DeltaDotNet.Client.Ui;

namespace DeltaDotNet.Client.Forms;

/// <summary>
/// Редактор управления: для каждого действия назначается одна клавиша.
///
/// Используется дважды:
///  • игрок настраивает СВОИ клавиши (что жать у себя на клавиатуре);
///  • хост настраивает клавиши ИГРЫ для чужого игрока (что ждёт мод).
///
/// Клавиша ловится по аппаратному scan code, поэтому раскладка (русская /
/// английская) не имеет значения — важна только физическая кнопка.
/// </summary>
public sealed class BindingsForm : Form
{
    private const int WM_KEYDOWN = 0x0100;
    private const int WM_SYSKEYDOWN = 0x0104;

    private readonly Bindings _bindings;
    private readonly string _role;
    private readonly Dictionary<string, DeltaButton> _buttons = new(StringComparer.Ordinal);
    private readonly Label _hint;
    private string _capturing;

    /// <summary>Итоговый набор привязок (после DialogResult.OK).</summary>
    public Bindings Result => _bindings;

    public BindingsForm(Bindings source, string role, string caption, string subtitle)
    {
        _bindings = source.Clone();
        _role = role;

        Text = caption;
        ClientSize = new Size(560, 560);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        KeyPreview = true;
        DeltaTheme.ApplyForm(this);

        var title = DeltaTheme.Title(caption);
        title.Location = new Point(28, 22);
        Controls.Add(title);

        var sub = DeltaTheme.Caption(subtitle, DeltaTheme.TextDim, DeltaTheme.FontSmall);
        sub.Location = new Point(30, 62);
        Controls.Add(sub);

        _hint = DeltaTheme.Caption("* Нажмите на действие, затем — нужную клавишу. Esc — отмена.", DeltaTheme.Accent, DeltaTheme.FontSmall);
        _hint.Location = new Point(30, 86);
        Controls.Add(_hint);

        int y = 116;
        foreach (var action in GameAction.All)
        {
            var label = DeltaTheme.Caption(GameAction.Title(action));
            label.Location = new Point(32, y + 10);
            Controls.Add(label);

            var button = new DeltaButton
            {
                Text = KeyMap.Title(_bindings[action]),
                Location = new Point(320, y),
                Size = new Size(200, 36),
                Tag = action,
            };
            button.Click += (s, e) => BeginCapture((string)((Control)s).Tag);
            Controls.Add(button);
            _buttons[action] = button;

            y += 42;
        }

        var btnReset = new DeltaButton
        {
            Text = "СБРОСИТЬ",
            Location = new Point(32, 496),
            Size = new Size(150, 38),
        };
        btnReset.Click += (s, e) =>
        {
            var def = Bindings.Default(_role);
            foreach (var action in GameAction.All) _bindings[action] = def[action];
            RefreshButtons();
            _hint.Text = "* Возвращены значения по умолчанию для " + _role;
        };
        Controls.Add(btnReset);

        var btnCancel = new DeltaButton
        {
            Text = "ОТМЕНА",
            Location = new Point(220, 496),
            Size = new Size(150, 38),
        };
        btnCancel.Click += (s, e) => { DialogResult = DialogResult.Cancel; Close(); };
        Controls.Add(btnCancel);

        var btnOk = new DeltaButton
        {
            Text = "СОХРАНИТЬ",
            Location = new Point(378, 496),
            Size = new Size(150, 38),
        };
        btnOk.Click += (s, e) => { DialogResult = DialogResult.OK; Close(); };
        Controls.Add(btnOk);
    }

    private void BeginCapture(string action)
    {
        _capturing = action;
        _buttons[action].Text = "ЖМИТЕ КЛАВИШУ...";
        _hint.Text = "* Назначаем: " + GameAction.Title(action) + ". Esc — отмена.";
    }

    private void RefreshButtons()
    {
        foreach (var action in GameAction.All)
            _buttons[action].Text = KeyMap.Title(_bindings[action]);
    }

    /// <summary>Перехват нажатия на уровне сообщений окна — нужен scan code, а не символ.</summary>
    protected override bool ProcessKeyPreview(ref Message m)
    {
        if (_capturing != null && (m.Msg == WM_KEYDOWN || m.Msg == WM_SYSKEYDOWN))
        {
            long lParam = m.LParam.ToInt64();
            uint scan = (uint)((lParam >> 16) & 0xFF);
            bool extended = ((lParam >> 24) & 1) != 0;

            // Esc отменяет назначение.
            if (scan == 0x01 && !extended)
            {
                var cancelled = _capturing;
                _capturing = null;
                _buttons[cancelled].Text = KeyMap.Title(_bindings[cancelled]);
                _hint.Text = "* Назначение отменено.";
                return true;
            }

            var keyName = KeyMap.FromScan(scan, extended);
            if (keyName == null)
            {
                _hint.Text = "* Эта клавиша не поддерживается, выберите другую.";
                return true;
            }

            // Если клавиша уже занята другим действием — освобождаем её.
            var busy = _bindings.ActionFor(keyName);
            if (busy != null && busy != _capturing) _bindings[busy] = null;

            _bindings[_capturing] = keyName;
            _hint.Text = "* " + GameAction.Title(_capturing) + " → " + KeyMap.Title(keyName);
            _capturing = null;
            RefreshButtons();
            return true;
        }
        return base.ProcessKeyPreview(ref m);
    }
}
