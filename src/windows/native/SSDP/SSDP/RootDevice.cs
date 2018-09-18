using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Windows.Foundation;
using Windows.Networking;
using Windows.Networking.Connectivity;
using Windows.Networking.Sockets;
using Windows.Storage.Streams;

namespace SSDP
{
    public sealed class RootDevice
    {
        public string Target { get; set; } = "ssdp:all";
        public string USN { get; set; } = "UnknownDevice";
        public string Name { get; set; } = "NoName";
        public int Port { get; set; } = -1;

        private DatagramSocket multicastSsdpSocket;
        private ILogger logger;
        private bool isStarted = false;

        public RootDevice() : this(new SystemLogger()) { }

        public RootDevice(ILogger logger)
        {
            this.logger = logger ?? new SystemLogger();
        }

        public IAsyncOperation<uint> AdvertiseAlive()
        {
            return Task.Run(async () =>
            {
                SsdpMessage request = new SsdpMessage
                {
                    Type = SsdpMessageType.AdvertiseAlive,
                    Host = Constants.SSDP_ADDRESS,
                    CacheControl = "max-age = 30",
                    NT = Target,
                    NTS = "ssdp:alive",
                    Server = Name,
                    USN = USN,
                    AdditionalHeaders = new Dictionary<string, string> {
                        { "PORT", Port.ToString() }
                    },
                };
                
                var outputStream = await multicastSsdpSocket.GetOutputStreamAsync(Constants.SSDP_HOST, Constants.SSDP_PORT);
                var writer = new DataWriter(outputStream);
                writer.WriteString(request.ToString());
                return await writer.StoreAsync();
            }).AsAsyncOperation();
        }

        public IAsyncOperation<uint> AdvertiseByeBye()
        {
            return Task.Run(async () =>
            {
                SsdpMessage request = new SsdpMessage
                {
                    Type = SsdpMessageType.AdvertiseByeBye,
                    Host = Constants.SSDP_ADDRESS,
                    NT = Target,
                    NTS = "ssdp:byebye",
                    Server = Name,
                    USN = USN,
                    AdditionalHeaders = new Dictionary<string, string> {
                        { "PORT", Port.ToString() }
                    },
                };

                var outputStream = await multicastSsdpSocket.GetOutputStreamAsync(Constants.SSDP_HOST, Constants.SSDP_PORT);
                var writer = new DataWriter(outputStream);
                writer.WriteString(request.ToString());
                return await writer.StoreAsync();
            }).AsAsyncOperation();
        }

        private async Task RespondToSearch(HostName host, string port)
        {
            SsdpMessage searchResponse = new SsdpMessage
            {
                Type = SsdpMessageType.SearchResponse,
                CacheControl = "max-age = 30",
                Date = DateTimeOffset.UtcNow,
                Server = Name,
                ST = Target,
                USN = USN,
                AdditionalHeaders = new Dictionary<string, string> {
                    { "PORT", Port.ToString() }
                },
            };

            var unicastSocket = new DatagramSocket();
            var outputStream = await unicastSocket.GetOutputStreamAsync(host, port);
            var writer = new DataWriter(outputStream);
            writer.WriteString(searchResponse.ToString());
            await writer.StoreAsync();
        }

        private async void MulticastSsdpSocket_MessageReceived(DatagramSocket sender, DatagramSocketMessageReceivedEventArgs args)
        {
            var reader = args.GetDataReader();
            var data = reader.ReadString(reader.UnconsumedBufferLength);

            if (data.Contains("Google Chrome"))
            {
                return;
            }

            if (!data.Contains("M-SEARCH") || !data.Contains("ssdp:discover"))
            {
                return;
            }

            string address = $"{args.RemoteAddress.CanonicalName}:{args.RemotePort}";
            logger.WriteLine($"[{address}]\n{data}");
            logger.WriteLine("Send response...");
            await RespondToSearch(args.RemoteAddress, args.RemotePort);
        }

        private void NetworkInformation_NetworkStatusChanged(object sender)
        {
            logger.WriteLine("Network status changed");
            multicastSsdpSocket.JoinMulticastGroup(Constants.SSDP_HOST);
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

                NetworkInformation.NetworkStatusChanged += NetworkInformation_NetworkStatusChanged;

                isStarted = true;

                logger.WriteLine("RootDevice started.");
            }).AsAsyncAction();
        }

        public void Stop()
        {
            if (!isStarted)
            {
                return;
            }
            logger.WriteLine("Stopping RootDevice...");

            //multicastSsdpSocket.MessageReceived -= MulticastSsdpSocket_MessageReceived;
            multicastSsdpSocket.Dispose();

            isStarted = false;

            logger.WriteLine("RootDevice stopped.");
        }
    }
}
