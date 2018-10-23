namespace SSDP
{
    public sealed class AvailabilityChangedEvent
    {
        public bool Available { get; set; }
        public string AdapterId { get; set; }
    }
}
