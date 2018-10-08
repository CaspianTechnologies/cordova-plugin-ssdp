using System;
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

        public WiFiInfo() : this(new SystemLogger()) { }

        public WiFiInfo(ILogger logger)
        {
            this.logger = logger;
            dispatcher = CoreWindow.GetForCurrentThread().Dispatcher;
            deviceWatcher = DeviceInformation.CreateWatcher(WiFiAdapter.GetDeviceSelector());
            deviceWatcher.Added += DeviceWatcher_Added;
            deviceWatcher.Removed += DeviceWatcher_Removed;
            deviceWatcher.Start();

            NetworkInformation.NetworkStatusChanged += NetworkInformation_NetworkStatusChanged;
        }

        private async void NetworkInformation_NetworkStatusChanged(object sender)
        {
            var profiles = NetworkInformation.GetConnectionProfiles().Where(p => p.IsWlanConnectionProfile);
            foreach (ConnectionProfile profile in profiles)
            {
                if (ConnectionChanged != null)
                {
                    var data = new ConnectionChangedEvent {
                        Connected = profile.GetNetworkConnectivityLevel() != NetworkConnectivityLevel.None,
                        AdapterId = profile.NetworkAdapter.NetworkAdapterId.ToString(),
                        WiFiName = profile.ProfileName
                    };
                    await dispatcher.RunAsync(CoreDispatcherPriority.Normal,
                            new DispatchedHandler(() =>
                            {
                                ConnectionChanged?.Invoke(this, data);
                            }));
                }
            }
        }

        private void DeviceWatcher_Added(DeviceWatcher sender, DeviceInformation args)
        {
            NotifyAvailabilityChanged(true, args.Id);
        }

        private void DeviceWatcher_Removed(DeviceWatcher sender, DeviceInformationUpdate args)
        {
            NotifyAvailabilityChanged(false, args.Id);
        }

        private async void NotifyAvailabilityChanged(bool available, string adapterId)
        {
            if (AvailabilityChanged != null)
            {
                var data = new AvailabilityChangedEvent { Available = available, AdapterId = adapterId };
                await dispatcher.RunAsync(CoreDispatcherPriority.Normal,
                        new DispatchedHandler(() =>
                        {
                            AvailabilityChanged?.Invoke(this, data);
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
            return Task.Run(async () =>
            {
                var connection = NetworkInformation.GetInternetConnectionProfile();
                bool connectedOverWiFi = (connection != null) ? connection.IsWlanConnectionProfile : false;
                return connectedOverWiFi;
            }).AsAsyncOperation();
        }
    }
}
