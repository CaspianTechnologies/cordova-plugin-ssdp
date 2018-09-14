using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using Windows.Foundation;
using Windows.Networking.Connectivity;
using Windows.Networking.Sockets;
using Windows.Storage.Streams;
using Windows.UI.Core;

namespace SSDP
{
    public sealed class ControlPoint
    {
        public event EventHandler<Device> DeviceDiscovered;
        public event EventHandler<Device> DeviceGone;
        public string Target { get; set; } = "ssdp:all";

        private DatagramSocket multicastSsdpSocket;
        private DatagramSocket unicastLocalSocket;
        private ILogger logger;
        private bool isStarted = false;
        private readonly CoreDispatcher dispatcher;

        private IList<Device> devices = new List<Device>();

        public ControlPoint() : this(new SystemLogger()) { }

        public ControlPoint(ILogger logger)
        {
            this.dispatcher = CoreWindow.GetForCurrentThread().Dispatcher;
            this.logger = logger;
        }

        public IAsyncOperation<uint> SearchDevices()
        {
            var cancellationTokenSource = new CancellationTokenSource();
            var token = cancellationTokenSource.Token;
            return Task.Run(async () =>
            {
                var searchRequest = new SsdpMessage
                {
                    Type = SsdpMessageType.SearchRequest,
                    Host = Constants.SSDP_ADDRESS,
                    MAN = "ssdp:discover",
                    MX = "1",
                    ST = Target,
                    UserAgent = "Spatium Wallet Device",
                };

                await SendSearchDevicesRequest(searchRequest, token);
                await Task.Delay(1000);
                await SendSearchDevicesRequest(searchRequest, token);
                await Task.Delay(1000);
                return await SendSearchDevicesRequest(searchRequest, token);
            }, token).AsAsyncOperation();
        }

        public IAsyncAction Start()
        {
            return Task.Run(async () =>
            {
                if (isStarted)
                {
                    return;
                }

                //var profiles = NetworkInformation.GetConnectionProfiles();

                multicastSsdpSocket = new DatagramSocket();
                multicastSsdpSocket.MessageReceived += MulticastSsdpSocket_MessageReceived;
                multicastSsdpSocket.Control.MulticastOnly = true;
                await multicastSsdpSocket.BindServiceNameAsync(Constants.SSDP_PORT);
                multicastSsdpSocket.JoinMulticastGroup(Constants.SSDP_HOST);

                unicastLocalSocket = new DatagramSocket();
                unicastLocalSocket.MessageReceived += UnicastLocalSocket_MessageReceived;
                await unicastLocalSocket.BindServiceNameAsync("");
                logger.WriteLine($"ControlPoint: Bind to port :{unicastLocalSocket.Information.LocalPort} for UNICAST search responses.");

                isStarted = true;

                logger.WriteLine("ControlPoint started.");
            }).AsAsyncAction();
        }

        public void Stop()
        {
            if (!isStarted)
            {
                return;
            }
            logger.WriteLine("Stopping ControlPoint...");

            multicastSsdpSocket.Dispose();
            unicastLocalSocket.Dispose();

            isStarted = false;

            logger.WriteLine("ControlPoint stopped.");
        }

        private async Task<uint> SendSearchDevicesRequest(SsdpMessage request, CancellationToken token)
        {
            token.ThrowIfCancellationRequested();
            var outputStream = await unicastLocalSocket.GetOutputStreamAsync(Constants.SSDP_HOST, Constants.SSDP_PORT);
            var writer = new DataWriter(outputStream);
            writer.WriteString(request.ToString());
            return await writer.StoreAsync();
        }

        private async Task<bool> RegisterDevice(Device device)
        {
            if (devices.Contains(device))
            {
                return false;
            }

            if (DeviceDiscovered != null)
            {
                await dispatcher.RunAsync(CoreDispatcherPriority.Normal,
                    new DispatchedHandler(() =>
                    {
                        DeviceDiscovered?.Invoke(this, device);
                    }));
            }
            devices.Add(device);
            return true;
        }

        private async Task<bool> UnregisterDevice(Device device)
        {
            if (!devices.Contains(device))
            {
                return false;
            }

            if (DeviceGone != null)
            {
                await dispatcher.RunAsync(CoreDispatcherPriority.Normal,
                    new DispatchedHandler(() =>
                    {
                        DeviceGone?.Invoke(this, device);
                    }));
            }
            devices.Remove(device);
            return true;
        }

        private async void UnicastLocalSocket_MessageReceived(DatagramSocket sender, DatagramSocketMessageReceivedEventArgs args)
        {
            string address = $"{args.RemoteAddress.CanonicalName}:{args.RemotePort}";
            var reader = args.GetDataReader();
            var data = reader.ReadString(reader.UnconsumedBufferLength);
            logger.WriteLine($"UNICAST ControlPoint [{address}]\n{data}");
            try
            {
                SsdpMessage ssdpMessage = new SsdpMessage(data);
                Device device = Device.ConstructDevice(args.RemoteAddress, ssdpMessage);
                await RegisterDevice(device);
            }
            catch (InvalidMessageException e)
            {
                logger.WriteLine($"Invalid ssdp message:\n{e.ToString()}");
            }
        }

        private async void MulticastSsdpSocket_MessageReceived(DatagramSocket sender, DatagramSocketMessageReceivedEventArgs args)
        {
            string address = $"{args.RemoteAddress.CanonicalName}:{args.RemotePort}";
            var reader = args.GetDataReader();
            var data = reader.ReadString(reader.UnconsumedBufferLength);
            logger.WriteLine($"MULTICAST ControlPoint [{address}]\n{data}");
            try
            {
                var ssdpMessage = new SsdpMessage(data);
                if (Target != "ssdp:all")
                {
                    if (ssdpMessage.NT != Target)
                    {
                        return;
                    }
                }

                if (ssdpMessage.Type == SsdpMessageType.AdvertiseAlive)
                {
                    var device = Device.ConstructDevice(args.RemoteAddress, ssdpMessage);
                    await RegisterDevice(device);
                }
                if (ssdpMessage.Type == SsdpMessageType.AdvertiseByeBye)
                {
                    var device = Device.ConstructDevice(args.RemoteAddress, ssdpMessage);
                    await UnregisterDevice(device);
                }
            }
            catch (InvalidMessageException e)
            {
                logger.WriteLine($"Invalid ssdp message:\n{e.ToString()}");
            }
        }
    }
}
