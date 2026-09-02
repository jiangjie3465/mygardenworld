# Caddy HTTPS edge

This configuration exposes the independently deployed `gardend` service over
HTTPS while keeping the daemon on its existing `127.0.0.1:50051` listener.
Caddy obtains and renews a trusted certificate for the configured hostname.

1. Install Caddy on the desktop or server that runs `gardend`.
2. Copy [`Caddyfile.example`](./Caddyfile.example) to the Caddy config path.
3. Replace `garden.example.com` with a DNS name pointing to this host.
4. Start `gardend` on `127.0.0.1:50051` and start/reload Caddy.
5. Verify both `https://<host>/mygardenworld.v1.AuthService/MobileLogin` and
   the `/api/workspace` WebSocket endpoint are reachable through the HTTPS
   hostname.

The Android app must use the HTTPS URL and must not trust a self-signed
certificate in production. For emulator development, use a real development
hostname/certificate or a private test CA configured only in the debug build.

The backend port does not change. Caddy terminates TLS and proxies HTTP/1.1
and WebSocket traffic to the local daemon. Do not expose port `50051` directly
to the public network unless a separate firewall policy explicitly requires it.
