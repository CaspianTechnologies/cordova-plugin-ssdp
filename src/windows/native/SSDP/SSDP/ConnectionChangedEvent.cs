namespace SSDP
{
    public sealed class ConnectionChangedEvent
    {
        public bool Connected { get; set; }
        public string WiFiName { get; set; }
        public string AdapterId { get; set; }
    }
}