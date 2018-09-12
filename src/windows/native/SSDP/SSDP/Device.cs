using System;
using System.Collections.Generic;
using Windows.Networking;

namespace SSDP
{
    public sealed class Device
    {
        public string IP { get; set; }
        public string Host { get; set; }
        public string USN { get; set; }
        public DateTimeOffset Date { get; set; }
        public string CacheControl { get; set; }
        public bool IsExpired
        {
            get
            {
                return false;
            }
        }

        public override bool Equals(object obj)
        {
            if (obj == null) return false;
            if (!(obj is Device objAsDevice)) return false;
            else return Equals(objAsDevice);
        }

        private bool Equals(Device other)
        {
            if (other == null) return false;
            return USN == other.USN 
                && IP == other.IP;
        }

        public static Device ConstructDevice(HostName ip, SsdpMessage message)
        {
            var device = new Device
            {
                IP = ip.CanonicalName,
                Host = message.Host,
                USN = message.USN,
                Date = message.Date,
                CacheControl = message.CacheControl,
            };
            if (device.USN == null) device.USN = "UnknownUSN";
            return device;
        }

        public override int GetHashCode()
        {
            var hashCode = -195774287;
            hashCode = hashCode * -1521134295 + EqualityComparer<string>.Default.GetHashCode(IP);
            hashCode = hashCode * -1521134295 + EqualityComparer<string>.Default.GetHashCode(USN);
            return hashCode;
        }
    }
}
