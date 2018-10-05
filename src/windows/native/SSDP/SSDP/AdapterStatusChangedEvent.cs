namespace SSDP
{
    public sealed class AdapterStatusChangedEvent
    {
        public bool Enabled { get; set; }
        public string AdapterId { get; set; }
    }
}