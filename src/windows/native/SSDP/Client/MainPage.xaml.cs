using System;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using SSDP;
using Windows.Networking.Sockets;
using Windows.Storage.Streams;
using Windows.Networking;
using Windows.Networking.Connectivity;
using System.Collections.Generic;
using System.Linq;

// The Blank Page item template is documented at https://go.microsoft.com/fwlink/?LinkId=402352&clcid=0x409

namespace Client
{
    /// <summary>
    /// An empty page that can be used on its own or navigated to within a Frame.
    /// </summary>
    public sealed partial class MainPage : Page
    {
        private ControlPoint controlPoint;
        private RootDevice rootDevice;
        private EventTimer eventTimer;
        private ILogger logger;
        private DatagramSocket socket;

        public MainPage()
        {
            InitializeComponent();

            logger = new TextBoxLogger(svLog, TbLog);

            controlPoint = new ControlPoint(logger) { Target = "spatium" };
            controlPoint.DeviceDiscovered += DiscoveredCallback;
            controlPoint.DeviceGone += GoneCallback;
            rootDevice = new RootDevice(logger)
            {
                Target = "spatium",
                Name = "Spatium device",
                USN = "123-321",
                Port = 12345
            };
            eventTimer = new EventTimer(logger);
            eventTimer.Tick += (s, t) =>
            {
                logger.WriteLine(t.ToString());
            };

            Loaded += (s, e) =>
            {
                try
                {
                    HostName localIp = GetLocalIp();
                    LocalIp.Text = (localIp != null) ? localIp.CanonicalName : "Unknown";
                }
                catch (Exception ex)
                {
                    LocalIp.Text = "Error";
                    logger.WriteLine(ex.ToString());
                }
            };
        }

        private async void ControlPointMode_Click(object sender, RoutedEventArgs e)
        {
            rootDevice.Stop();
            AdvertiseAlive.IsEnabled = AdvertiseByeBye.IsEnabled = false;
            await controlPoint.Start();
            SearchDevices.IsEnabled = true;
        }

        private async void SearchDevices_Click(object sender, RoutedEventArgs e)
        {
            await controlPoint.SearchDevices();
        }

        private async void RootDeviceMode_Click(object sender, RoutedEventArgs e)
        {
            controlPoint.Stop();
            SearchDevices.IsEnabled = false;
            await rootDevice.Start();
            AdvertiseAlive.IsEnabled = AdvertiseByeBye.IsEnabled = true;
        }

        private async void AdvertiseAlive_Click(object sender, RoutedEventArgs e)
        {
            await rootDevice.AdvertiseAlive();
        }

        private async void AdvertiseByeBye_Click(object sender, RoutedEventArgs e)
        {
            await rootDevice.AdvertiseByeBye();
        }

        private async void Send_Click(object sender, RoutedEventArgs e)
        {
            Send.IsEnabled = false;
            var socket = new DatagramSocket();
            try
            {
                var address = Address.Text.Split(':');
                string host = address[0], port = address[1];
                await socket.BindServiceNameAsync("1902");
                var outputStream = await socket.GetOutputStreamAsync(new HostName(host), port);
                var writer = new DataWriter(outputStream);
                writer.WriteString(Message.Text);
                await writer.StoreAsync();
                socket.Dispose();
            }
            catch (Exception ex)
            {
                logger.WriteLine(ex.ToString());
            }
            finally
            {
                socket.Dispose();
                Send.IsEnabled = true;
            }
        }

        public HostName GetLocalIp()
        {
            var icp = NetworkInformation.GetInternetConnectionProfile();

            if (icp?.NetworkAdapter == null) return null;
            IReadOnlyList<HostName> hostNames = NetworkInformation.GetHostNames();
            var hostname =
                NetworkInformation.GetHostNames()
                    .SingleOrDefault(
                        hn =>
                            hn.IPInformation?.NetworkAdapter != null && hn.IPInformation.NetworkAdapter.NetworkAdapterId
                            == icp.NetworkAdapter.NetworkAdapterId);

            // the ip address
            return hostname;
        }

        private void Clear_Click(object sender, RoutedEventArgs e)
        {
            TbLog.Text = "";
        }

        private void DiscoveredCallback(object sender, Device device)
        {
            Devices.Items.Add(device.ToString());
        }

        private void GoneCallback(object sender, Device device)
        {
            Devices.Items.Remove(device.ToString());
        }

        private void Tick_Click(object sender, RoutedEventArgs e)
        {
            eventTimer.Start();
        }

        private async void ListenSSDP_Click(object sender, RoutedEventArgs e)
        {
            socket = new DatagramSocket();
            socket.MessageReceived += (s, args) =>
            {
                var reader = args.GetDataReader();
                var data = reader.ReadString(reader.UnconsumedBufferLength);
                string address = $"{args.RemoteAddress.CanonicalName}:{args.RemotePort}";
                logger.WriteLine($"MULTICAST [{address}]\n{data}");
            };
            socket.Control.MulticastOnly = true;
            await socket.BindServiceNameAsync(Constants.SSDP_PORT);
            socket.JoinMulticastGroup(Constants.SSDP_HOST);
            NetworkInformation.NetworkStatusChanged += (s) => socket.JoinMulticastGroup(Constants.SSDP_HOST);
            logger.WriteLine($"Start listening multicast {Constants.SSDP_ADDRESS}");
        }
    }
}
