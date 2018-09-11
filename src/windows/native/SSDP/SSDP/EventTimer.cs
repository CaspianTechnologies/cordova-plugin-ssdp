using System;
using System.Threading;
using System.Threading.Tasks;
using Windows.Foundation;
using Windows.UI.Core;

namespace SSDP
{
    public sealed class EventTimer
    {
        public event EventHandler<int> Tick;

        private ILogger logger;
        private readonly CoreDispatcher dispatcher;
        private int ticked = 0;
        private Timer timer;

        public EventTimer() : this(new SystemLogger()) { }

        public EventTimer(ILogger logger)
        {
            this.dispatcher = CoreWindow.GetForCurrentThread().Dispatcher;
            this.logger = logger;
        }

        public void Start()
        {
            timer = new Timer(callback, "State", TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(1));
        }

        private async void callback(object state)
        {
            if (Tick != null)
            {
                await dispatcher.RunAsync(CoreDispatcherPriority.Normal,
                    new DispatchedHandler(() =>
                    {
                        Tick?.Invoke(this, ++ticked);
                    }));
            }
        }
    }
}
