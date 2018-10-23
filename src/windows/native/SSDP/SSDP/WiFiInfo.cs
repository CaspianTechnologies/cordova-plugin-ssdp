using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Windows.Devices.Enumeration;
using Windows.Devices.Radios;
using Windows.Devices.WiFi;
using Windows.Foundation;
using Windows.Networking.Connectivity;
using Windows.UI.Core;

namespace SSDP
{
    public sealed class WiFiInfo
    {
        public event EventHandler<AvailabilityChangedEvent> AvailabilityChanged;
        public event EventHandler<AdapterStatusChangedEvent> AdapterStatusChanged;
        public event EventHandler<ConnectionChangedEvent> ConnectionChanged;

        private readonly ILogger logger;
        private readonly CoreDispatcher dispatcher;
        private readonly DeviceWatcher deviceWatcher;
        private List<Radio> watchedRadios = new List<Radio>();

        private int availableAdaptersCount = 0;
        private int AvailableAdaptersCount
        {
            get
            {
                return availableAdaptersCount;
            }
            set
            {
                logger.WriteLine($"AvailableAdaptersCount = {value}");
                availableAdaptersCount = value;
            }
        }

        private int enabledAdaptersCount = 0;
        private int EnabledAdaptersCount
        {
            get
            {
                return enabledAdaptersCount;
            }
            set
            {
                logger.WriteLine($"EnabledAdaptersCount = {value}");
                enabledAdaptersCount = value;
            }
        }

        private int connectedAdaptersCount = 0;
        private int ConnectedAdaptersCount
        {
            get
            {
                return connectedAdaptersCount;
            }
            set
            {
                logger.WriteLine($"ConnectedAdaptersCount = {value}");
                connectedAdaptersCount = value;
            }
        }

        public WiFiInfo() : this(new SystemLogger()) { }

        public WiFiInfo(ILogger logger)
        {
            this.logger = logger;
            dispatcher = CoreWindow.GetForCurrentThread().Dispatcher;
            deviceWatcher = DeviceInformation.CreateWatcher(WiFiAdapter.GetDeviceSelector());

            initialize();
        }

        private async void initialize()
        {
            deviceWatcher.Added += DeviceWatcher_Added;
            deviceWatcher.Removed += DeviceWatcher_Removed;
            deviceWatcher.Start();

            EnabledAdaptersCount = (await Radio.GetRadiosAsync()).Where(r => r.State == RadioState.On).Count();
            if (EnabledAdaptersCount > 0)
            {
                NotifyAdapterStatusChanged(true);
            }

            ConnectedAdaptersCount = NetworkInformation.GetConnectionProfiles()
                .Where(p => p.IsWlanConnectionProfile && p.GetNetworkConnectivityLevel() != NetworkConnectivityLevel.None)
                .Count();
            if (ConnectedAdaptersCount > 0)
            {
                NotifyConnectionChanged(true);
            }

            NetworkInformation.NetworkStatusChanged += NetworkInformation_NetworkStatusChanged;
        }

        private void NetworkInformation_NetworkStatusChanged(object sender)
        {
            ConnectedAdaptersCount = NetworkInformation.GetConnectionProfiles()
                .Where(p => p.IsWlanConnectionProfile && p.GetNetworkConnectivityLevel() != NetworkConnectivityLevel.None)
                .Count();
            var isConnected = ConnectedAdaptersCount > 0;
            NotifyConnectionChanged(isConnected);
        }

        private async void DeviceWatcher_Added(DeviceWatcher sender, DeviceInformation args)
        {
            AvailableAdaptersCount += 1;

            if (AvailableAdaptersCount > 0)
            {
                NotifyAvailabilityChanged(true);
            }

            await Task.Delay(3000);

            if (await IsEnabled())
            {
                NotifyAdapterStatusChanged(true);
            }

            var radios = (await Radio.GetRadiosAsync()).Where(r => r.Kind == RadioKind.WiFi);
            foreach (var radio in radios)
            {
                if (!watchedRadios.Contains(radio))
                {
                    radio.StateChanged += Radio_StateChanged;
                    watchedRadios.Add(radio);
                }
            }
        }

        private void Radio_StateChanged(Radio sender, object args)
        {
            if (sender.State == RadioState.On)
            {
                EnabledAdaptersCount++;
            }
            else
            {
                EnabledAdaptersCount--;
            }

            var isEnabled = EnabledAdaptersCount > 0;
            NotifyAdapterStatusChanged(isEnabled);
        }

        private async void DeviceWatcher_Removed(DeviceWatcher sender, DeviceInformationUpdate args)
        {
            AvailableAdaptersCount -= 1;

            if (AvailableAdaptersCount == 0)
            {
                NotifyAvailabilityChanged(false);
            }

            await Task.Delay(1000);
            var radios = (await Radio.GetRadiosAsync()).Where(r => r.Kind == RadioKind.WiFi);
            var watchedRadiosToRemove = new List<Radio>();
            foreach (var watchedRadio in watchedRadios)
            {
                if (!radios.Contains(watchedRadio))
                {
                    watchedRadio.StateChanged -= Radio_StateChanged;
                    watchedRadiosToRemove.Add(watchedRadio);
                }
            }

            foreach (var radioToRemove in watchedRadiosToRemove)
            {
                logger.WriteLine($"Remove radio: {radioToRemove.Name}");
                watchedRadios.Remove(radioToRemove);
            }
        }

        private async void NotifyAvailabilityChanged(bool available)
        {
            logger.WriteLine($"available: {available}");

            if (!available)
            {
                NotifyAdapterStatusChanged(false);
            }

            if (AvailabilityChanged != null)
            {
                var data = new AvailabilityChangedEvent { Available = available, AdapterId = "adapterId" };
                await dispatcher.RunAsync(CoreDispatcherPriority.Normal,
                        new DispatchedHandler(() =>
                        {
                            AvailabilityChanged?.Invoke(this, data);
                        }));
            }
        }

        private async void NotifyAdapterStatusChanged(bool enabled)
        {
            logger.WriteLine($"enabled: {enabled}");

            if (!enabled)
            {
                NotifyConnectionChanged(false);
            }

            if (AdapterStatusChanged != null)
            {
                var data = new AdapterStatusChangedEvent { Enabled = enabled, AdapterId = "adapterId" };
                await dispatcher.RunAsync(CoreDispatcherPriority.Normal,
                        new DispatchedHandler(() =>
                        {
                            AdapterStatusChanged?.Invoke(this, data);
                        }));
            }
        }

        private async void NotifyConnectionChanged(bool connected)
        {
            logger.WriteLine($"connected: {connected}");
            if (ConnectionChanged != null)
            {
                var data = new ConnectionChangedEvent
                {
                    Connected = connected,
                    AdapterId = "adapterId",
                    WiFiName = "wiFiName"
                };
                await dispatcher.RunAsync(CoreDispatcherPriority.Normal,
                        new DispatchedHandler(() =>
                        {
                            ConnectionChanged?.Invoke(this, data);
                        }));
            }
        }

        public IAsyncOperation<bool> IsAvailable()
        {
            return Task.Run(async () =>
            {
                var adapters = await WiFiAdapter.FindAllAdaptersAsync();
                return adapters.Any();
            }).AsAsyncOperation();
        }

        public IAsyncOperation<bool> IsEnabled()
        {
            return Task.Run(async () =>
            {
                bool isEnabled = false;
                var radios = await Radio.GetRadiosAsync();
                var wiFiRadios = radios.Where(radio => radio.Kind == RadioKind.WiFi);
                if (wiFiRadios.Any())
                {
                    isEnabled = wiFiRadios.Where(radio => radio.State == RadioState.On).Any();
                }
                return isEnabled;
            }).AsAsyncOperation();
        }

        public IAsyncOperation<bool> IsConnected()
        {
            return Task.Run(() =>
            {
                var profiles = NetworkInformation.GetConnectionProfiles().Where(p => p.IsWlanConnectionProfile);
                foreach (ConnectionProfile profile in profiles)
                {
                    var connected = profile.GetNetworkConnectivityLevel() != NetworkConnectivityLevel.None;
                    if (connected)
                    {
                        return true;
                    }
                }
                return false;
            }).AsAsyncOperation();
        }
    }
}
