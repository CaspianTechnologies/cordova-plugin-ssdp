using System.Net;
using Windows.Networking.Connectivity;

namespace SSDP
{
    internal class NetworkAdapterInfo
    {
        public NetworkAdapter NetworkAdapter { get; set; }
        public IPAddress IPAddress { get; set; }
        public IPAddress SubnetMask { get; set; }
    }
}
