using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SSDP
{
    public static class DictionaryExtensions
    {
        /// <summary>
        /// Gets the value of specified key. Simply returns the default value if dic or key are null or specified key does not exists.
        /// </summary>
        public static string GetValueOrDefault(this IDictionary<string, string> dic, string key)
        {
            return (dic != null && key != null && dic.TryGetValue(key, out string value)) ? value : "";
        }

        public static string PopValueOrDefault(this IDictionary<string, string> dic, string key)
        {
            string value = "";
            if (dic != null && key != null)
            {
                var ok = dic.TryGetValue(key, out value);
                dic.Remove(key);
                return value;
            }
            return value;
        }
    }
}
