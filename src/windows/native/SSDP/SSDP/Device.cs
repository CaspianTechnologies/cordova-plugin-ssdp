using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;
using Windows.Networking;

namespace SSDP
{
    public sealed class Device
    {
        public string IP { get; set; }
        public int Port { get; set; }
        public string Name { get; set; }
        public string USN { get; set; }
        public DateTimeOffset Date { get; set; }
        public string CacheControl { get; set; }
        public Guid NetworkId { get; set; }
        public bool IsExpired
        {
            get
            {
                Match match = maxAgePattern.Match(CacheControl);
                if (int.TryParse(match.Groups[1].Value, out int seconds))
                {
                    var cacheTime = TimeSpan.FromSeconds(seconds);
                    return (Date + cacheTime) < DateTimeOffset.UtcNow;
                }
                return false;
            }
        }

        private Regex maxAgePattern = new Regex(@"max-age\s*=\s*(\d+)");

        public override bool Equals(object obj)
        {
            if (obj == null) return false;
            if (!(obj is Device objAsDevice)) return false;
            else return Equals(objAsDevice);
        }

        private bool Equals(Device other)
        {
            if (other == null) return false;
            return USN == other.USN;
        }

        public static Device ConstructDevice(HostName ip, Guid networkId, SsdpMessage message)
        {
            int port = -1;
            if (message.AdditionalHeaders.TryGetValue("PORT", out string portStr))
            {
                int.TryParse(portStr, out port);
            }
            var device = new Device
            {
                IP = ip.CanonicalName,
                Port = port,
                USN = message.USN ?? "UnknownUSN",
                Name = message.Server ?? "NoName",
                Date = message.Date,
                CacheControl = message.CacheControl,
                NetworkId = networkId,
            };
            return device;
        }

        public override int GetHashCode()
        {
            return 1515427197 + EqualityComparer<string>.Default.GetHashCode(USN);
        }

        public override string ToString()
        {
            return $"[{IP}:{Port}] {Name}";
        }
    }
}
