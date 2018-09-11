using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Windows.Foundation;
using Windows.Networking;
using Windows.Networking.Sockets;
using Windows.Storage.Streams;

namespace SSDP
{
    public sealed class RootDevice
    {
        private DatagramSocket multicastSsdpSocket;
        private ILogger logger;
        private string target;
        private string usn;
        private bool isStarted = false;

        public RootDevice(string target, string usn) : this(target, usn, new SystemLogger()) { }

        public RootDevice(string target, string usn, ILogger logger)
        {
            this.target = target;
            this.usn = usn;
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
                    CacheControl = "max-age",
                    Location = "http://192.168.255.255:65535/fake.xml",
                    NT = target,
                    NTS = "ssdp:alive",
                    USN = usn,
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
                    NT = target,
                    NTS = "ssdp:byebye",
                    USN = usn,
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
                CacheControl = "max-age",
                Date = DateTimeOffset.UtcNow,
                Location = "http://192.168.255.255:65535/fake.xml",
                Server = "Spatium Confirmation Device",
                ST = target,
                USN = usn,
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
