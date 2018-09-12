using System;
using System.Collections.Generic;
using System.Linq;

namespace SSDP
{
    public sealed class SsdpMessage
    {
        public SsdpMessageType Type { get; set; }
        public string Host { get; set; }
        public string CacheControl { get; set; }
        public DateTimeOffset Date { get; set; }
        public string Location { get; set; }
        public string Server { get; set; }
        public string ST { get; set; }
        public string USN { get; set; }
        public string MAN { get; set; }
        public string UserAgent { get; set; }
        public string MX { get; set; }
        public string NT { get; set; }
        public string NTS { get; set; }

        public IDictionary<string, string> AdditionalHeaders { get; set; }

        public SsdpMessage()
        {
            AdditionalHeaders = new Dictionary<string, string>();
        }

        public SsdpMessage(string rawMessage) : this()
        {
            var lines = rawMessage.Split("\r\n".ToCharArray(), StringSplitOptions.RemoveEmptyEntries);
            foreach (var line in lines)
            {
                if (!line.Contains(":"))
                {
                    continue;
                }

                var data = line.Split(":".ToCharArray(), 2).Select(x => x.Trim()).ToArray();
                var key = data[0].ToUpper();
                var value = data[1].Replace("\"", "").Replace("'", "");
                AdditionalHeaders.Add(key, value);
            }

            Type = GetMessageType(rawMessage);
            Host = AdditionalHeaders.PopValueOrDefault("HOST");
            CacheControl = AdditionalHeaders.PopValueOrDefault("CACHE-CONTROL");

            var parsed = DateTimeOffset.TryParse(AdditionalHeaders.GetValueOrDefault("DATE"), out DateTimeOffset date);
            if (parsed) Date = date;
            else Date = DateTimeOffset.UtcNow;
            Location = AdditionalHeaders.PopValueOrDefault("LOCATION");
            Server = AdditionalHeaders.PopValueOrDefault("SERVER");
            ST = AdditionalHeaders.PopValueOrDefault("ST");
            USN = AdditionalHeaders.PopValueOrDefault("USN");
            UserAgent = AdditionalHeaders.PopValueOrDefault("USER-AGENT");
            MX = AdditionalHeaders.PopValueOrDefault("MX");
            NT = AdditionalHeaders.PopValueOrDefault("NT");
            MAN = AdditionalHeaders.PopValueOrDefault("MAN");
            NTS = AdditionalHeaders.PopValueOrDefault("NTS");
        }

        public static SsdpMessageType GetMessageType(string rawMessage)
        {
            string header = rawMessage.Split("\r\n".ToCharArray(), StringSplitOptions.RemoveEmptyEntries)[0];
            switch (header)
            {
                case "NOTIFY * HTTP/1.1":
                    if (rawMessage.Contains("ssdp:alive"))
                    {
                        return SsdpMessageType.AdvertiseAlive;
                    }
                    else if (rawMessage.Contains("ssdp:byebye"))
                    {
                        return SsdpMessageType.AdvertiseByeBye;
                    }
                    else
                    {
                        throw new InvalidMessageException(rawMessage);
                    }
                case "M-SEARCH * HTTP/1.1":
                    return SsdpMessageType.SearchRequest;
                case "HTTP/1.1 200 OK":
                    return SsdpMessageType.SearchResponse;
                default:
                    throw new InvalidMessageException(rawMessage);
            }
        }

        public static string GetMessageHeader(SsdpMessageType type)
        {
            switch (type)
            {
                case SsdpMessageType.AdvertiseAlive:
                case SsdpMessageType.AdvertiseByeBye:
                    return "NOTIFY * HTTP/1.1";
                case SsdpMessageType.SearchRequest:
                    return "M-SEARCH * HTTP/1.1";
                case SsdpMessageType.SearchResponse:
                    return "HTTP/1.1 200 OK";
                default:
                    throw new InvalidMessageException("Invalid message type");
            }
        }

        public IEnumerable<string> DictToList(IDictionary<string, string> dict)
        {
            var list = new List<string>();
            foreach (var x in dict)
            {
                var line = $"{x.Key.ToUpper()}: {x.Value}";
                list.Add(line);
            }
            return list;
        }

        public override string ToString()
        {
            var messageLines = new List<string>();
            
            switch (Type)
            {
                case SsdpMessageType.AdvertiseAlive:
                    messageLines.AddRange(new List<string>
                    {
                        "NOTIFY * HTTP/1.1",
                        $"HOST: {Host}",
                        $"CACHE-CONTROL: {CacheControl}",
                        $"LOCATION: {Location}",
                        $"NT: {NT}",
                        $"NTS: {NTS}",
                        $"USN: {USN}",
                    });
                    break;
                case SsdpMessageType.AdvertiseByeBye:
                    messageLines.AddRange(new List<string>
                    {
                        "NOTIFY * HTTP/1.1",
                        $"HOST: {Host}",
                        $"NT: {NT}",
                        $"NTS: {NTS}",
                        $"USN: {USN}",
                    });
                    break;
                case SsdpMessageType.SearchRequest:
                    messageLines.AddRange(new List<string>
                    {
                        "M-SEARCH * HTTP/1.1",
                        $"HOST: {Host}",
                        $"MAN: {MAN}",
                        $"ST: {ST}",
                        $"MX: {MX}",
                        $"USER-AGENT: {UserAgent}",
                    });
                    break;
                case SsdpMessageType.SearchResponse:
                    messageLines.AddRange(new List<string>
                    {
                        "HTTP/1.1 200 OK",
                        $"CACHE-CONTROL: {CacheControl}",
                        $"DATE: {Date.ToString("r")}",
                        "EXT:",
                        $"LOCATION: {Location}",
                        $"SERVER: {Server}",
                        $"ST: {ST}",
                        $"USN: {USN}",
                    });
                    break;
                default:
                    throw new InvalidMessageException("Invalid ssdp message type");
            }
            messageLines.AddRange(DictToList(AdditionalHeaders));
            messageLines.Add("");
            return string.Join("\r\n", messageLines);
        }
    }
}
