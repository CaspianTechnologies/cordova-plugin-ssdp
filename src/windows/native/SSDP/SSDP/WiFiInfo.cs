using System;
using System.Linq;
using System.Threading.Tasks;
using Windows.Devices.Radios;
using Windows.Devices.WiFi;
using Windows.Foundation;
using Windows.Networking.Connectivity;

namespace SSDP
{
    public sealed class WiFiInfo
    {
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
