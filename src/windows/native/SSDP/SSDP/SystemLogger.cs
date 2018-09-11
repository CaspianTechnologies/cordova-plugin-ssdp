using System.Diagnostics;

namespace SSDP
{
    class SystemLogger : ILogger
    {
        public void WriteLine(string message)
        {
            Debug.WriteLine(message);
        }
    }
}
