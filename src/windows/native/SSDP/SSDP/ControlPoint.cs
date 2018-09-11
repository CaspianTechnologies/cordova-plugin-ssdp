using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Windows.Foundation;
using Windows.Networking.Sockets;
using Windows.Storage.Streams;
using Windows.UI.Core;

namespace SSDP
{
    public sealed class ControlPoint
    {
        public event EventHandler<Device> DeviceDiscovered;

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

        public IAsyncOperation<uint> SearchDevices(string target)
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
                    ST = target,
                    UserAgent = "Spatium Wallet Device",
                };

                await SendSearchDevicesRequest(searchRequest, token);
                await Task.Delay(1000);
                await SendSearchDevicesRequest(searchRequest, token);
                await Task.Delay(1000);
                return await SendSearchDevicesRequest(searchRequest, token);
            }, token).AsAsyncOperation();
        }

        private async Task<uint> SendSearchDevicesRequest(SsdpMessage request, CancellationToken token)
        {
            token.ThrowIfCancellationRequested();
            var outputStream = await multicastSsdpSocket.GetOutputStreamAsync(Constants.SSDP_HOST, Constants.SSDP_PORT);
            var writer = new DataWriter(outputStream);
            writer.WriteString(request.ToString());
            return await writer.StoreAsync();
        }
  
        private async void UnicastLocalSocket_MessageReceived(DatagramSocket sender, DatagramSocketMessageReceivedEventArgs args)
        {
            string address = $"{args.RemoteAddress.CanonicalName}:{args.RemotePort}";
            var reader = args.GetDataReader();
            var data = reader.ReadString(reader.UnconsumedBufferLength);
            SsdpMessage ssdpMessage = new SsdpMessage(data);
            if (!devices.Select(d => d.USN = ssdpMessage.USN).Any())
            {
                Device device = new Device
                {
                    IP = args.RemoteAddress.CanonicalName,
                    Host = ssdpMessage.Host,
                    USN = ssdpMessage.USN,
                    Server = ssdpMessage.Server,
                    Date = ssdpMessage.Date,
                    CacheControl = ssdpMessage.CacheControl,
                };
                devices.Add(device);

                if (DeviceDiscovered != null)
                {
                    await dispatcher.RunAsync(CoreDispatcherPriority.Normal,
                        new DispatchedHandler(() =>
                        {
                            DeviceDiscovered?.Invoke(this, device);
                        }));
                }

                //logger.WriteLine($"SSDP MESSAGE:\n{ssdpMessage.ToString()}\n");
                devices.Add(device);
            }
        }

        public IAsyncAction Start()
        {
            return Task.Run(async () =>
            {
                if (isStarted)
                {
                    return;
                }

                multicastSsdpSocket = new DatagramSocket();
                multicastSsdpSocket.MessageReceived += MulticastSsdpSocket_MessageReceived;
                multicastSsdpSocket.Control.MulticastOnly = true;
                await multicastSsdpSocket.BindServiceNameAsync(Constants.SSDP_PORT);
                multicastSsdpSocket.JoinMulticastGroup(Constants.SSDP_HOST);

                unicastLocalSocket = new DatagramSocket();
                unicastLocalSocket.MessageReceived += UnicastLocalSocket_MessageReceived;
                var port = "1901";
                logger.WriteLine($"ControlPoint: listen :{port} for UNICAST requests.");
                await unicastLocalSocket.BindServiceNameAsync(port);

                isStarted = true;

                logger.WriteLine("ControlPoint started.");
            }).AsAsyncAction();
        }

        private void MulticastSsdpSocket_MessageReceived(DatagramSocket sender, DatagramSocketMessageReceivedEventArgs args)
        {
            string address = $"{args.RemoteAddress.CanonicalName}:{args.RemotePort}";
            var reader = args.GetDataReader();
            var data = reader.ReadString(reader.UnconsumedBufferLength);
            if (!data.Contains("spatium"))
            {
                return;
            }
            logger.WriteLine($"MULTICAST [{address}]\n{data}");
        }

        public void Stop()
        {
            if (!isStarted)
            {
                return;
            }
            logger.WriteLine("Stopping ControlPoint...");

            multicastSsdpSocket.Dispose();
            //multicastSsdpSocket.MessageReceived -= UnicastLocalSocket_MessageReceived;
            unicastLocalSocket.Dispose();

            isStarted = false;

            logger.WriteLine("ControlPoint stopped.");
        }
    }
}
