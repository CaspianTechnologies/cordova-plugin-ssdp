using Windows.Networking;

namespace SSDP
{
    public static class Constants
    {
        public static HostName SSDP_HOST { get { return new HostName("239.255.255.250"); } }
        public static string SSDP_PORT { get{ return "1900"; } }
        public static string SSDP_ADDRESS { get { return $"{SSDP_HOST.CanonicalName}:{SSDP_PORT}"; } }
    }
}
