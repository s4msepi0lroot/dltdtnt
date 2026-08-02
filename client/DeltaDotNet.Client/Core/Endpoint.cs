using System;

namespace DeltaDotNet.Client.Core
{
    /// <summary>
    /// Normalizes the server address the user typed.
    ///
    /// The most common local-server mistake is entering the address the server
    /// BINDS to (0.0.0.0 / :: / [::]) as the address to CONNECT to. Those are
    /// "any interface" wildcards and cannot be dialed - Windows throws
    /// "unspecified addresses cannot be used as a target address". We silently
    /// rewrite them to the loopback address so "it just works" on one machine.
    /// </summary>
    public static class Endpoint
    {
        /// <summary>Returns true if the host part is a non-routable wildcard.</summary>
        public static bool IsWildcardHost(string host)
        {
            if (string.IsNullOrWhiteSpace(host)) return true;
            host = host.Trim().Trim('[', ']');
            return host == "0.0.0.0" || host == "::" || host == "::0" || host == "0:0:0:0:0:0:0:0";
        }

        /// <summary>
        /// Cleans up a base URL: adds http:// if the scheme is missing and
        /// rewrites wildcard hosts to 127.0.0.1. Never throws.
        /// </summary>
        public static string Normalize(string raw)
        {
            var url = (raw ?? "").Trim();
            if (url.Length == 0) return "http://127.0.0.1:8080";

            // remember ws/wss so we can rebuild the same scheme family
            bool ws = url.StartsWith("ws://", StringComparison.OrdinalIgnoreCase)
                   || url.StartsWith("wss://", StringComparison.OrdinalIgnoreCase);

            var probe = url;
            if (probe.IndexOf("://", StringComparison.Ordinal) < 0)
                probe = "http://" + probe;
            // let http parsing work even for ws urls
            var httpProbe = probe
                .Replace("ws://", "http://")
                .Replace("wss://", "https://")
                .Replace("WS://", "http://")
                .Replace("WSS://", "https://");

            try
            {
                var u = new Uri(httpProbe);
                if (IsWildcardHost(u.Host))
                {
                    var b = new UriBuilder(u) { Host = "127.0.0.1" };
                    var rebuilt = b.Uri.GetLeftPart(UriPartial.Authority) + b.Uri.AbsolutePath.TrimEnd('/');
                    if (ws) rebuilt = rebuilt.Replace("http://", "ws://").Replace("https://", "wss://");
                    return rebuilt.TrimEnd('/');
                }
            }
            catch { /* fall through and return the cleaned string */ }

            if (url.IndexOf("://", StringComparison.Ordinal) < 0)
                url = "http://" + url;
            return url.TrimEnd('/');
        }
    }
}
