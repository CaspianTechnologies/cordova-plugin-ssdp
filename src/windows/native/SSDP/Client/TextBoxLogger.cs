using SSDP;
using System;
using Windows.UI.Core;
using Windows.UI.Xaml.Controls;
using Windows.ApplicationModel.Core;

namespace Client
{
    class TextBoxLogger : ILogger
    {
        private ScrollViewer scrollViewer;
        private TextBox textBox;

        public TextBoxLogger(ScrollViewer scrollViewer, TextBox textBox)
        {
            this.scrollViewer = scrollViewer;
            this.textBox = textBox;
        }

        public async void WriteLine(string message)
        {
            await CoreApplication.MainView.CoreWindow.Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                var now = DateTime.Now.ToString("HH:mm:ss");
                textBox.Text += $"[{now}] {message}\r\n";
                scrollViewer.ChangeView(0, 10000, 1);
            });
        }
    }
}
