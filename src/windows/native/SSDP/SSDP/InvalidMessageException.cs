using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SSDP
{
    class InvalidMessageException : Exception
    {
        public InvalidMessageException(string message) : base(message)
        {
        }
    }
}
