using System;
using System.Collections.Generic;

namespace SSDP
{
    public sealed class Device
    {
        public string IP { get; set; }
        public string Host { get; set; }
        public string USN { get; set; }
        public string Server { get; set; }
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
            return obj is Device device &&
                   IP == device.IP &&
                   Host == device.Host &&
                   USN == device.USN &&
                   Server == device.Server &&
                   Date.Equals(device.Date) &&
                   CacheControl == device.CacheControl &&
                   IsExpired == device.IsExpired;
        }

        public override int GetHashCode()
        {
            var hashCode = -1754731209;
            hashCode = hashCode * -1521134295 + EqualityComparer<string>.Default.GetHashCode(IP);
            hashCode = hashCode * -1521134295 + EqualityComparer<string>.Default.GetHashCode(Host);
            hashCode = hashCode * -1521134295 + EqualityComparer<string>.Default.GetHashCode(USN);
            hashCode = hashCode * -1521134295 + EqualityComparer<string>.Default.GetHashCode(Server);
            hashCode = hashCode * -1521134295 + EqualityComparer<DateTimeOffset>.Default.GetHashCode(Date);
            hashCode = hashCode * -1521134295 + EqualityComparer<string>.Default.GetHashCode(CacheControl);
            hashCode = hashCode * -1521134295 + IsExpired.GetHashCode();
            return hashCode;
        }
    }
}
