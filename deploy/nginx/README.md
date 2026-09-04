# Nginx HTTPS edge

`gardend` stays bound to `127.0.0.1:50051`; Nginx terminates TLS and forwards
Connect RPC, the embedded Web UI, and the `/api/workspace` WebSocket.

1. Build a binary with the Web UI embedded (the release workflow does this):
   `NEXT_PUBLIC_API_URL= pnpm --dir web build`, copy `web/out` into
   `internal/webui/static`, then `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ./cmd/gardend`.
2. Install the binary to `/opt/mygardenworld/bin/gardend`, create the `gardend`
   system user and `/opt/mygardenworld/data`, and put `GARDEND_JWT_SECRET` and
   `GARDEND_ADMIN_PASSWORD` in `/etc/mygardenworld/gardend.env` (mode 600).
3. Install `gardend.service.example` as `/etc/systemd/system/gardend.service`,
   then `systemctl enable --now gardend`.
4. Copy `mygardenworld.conf.example` to `/etc/nginx/sites-available/mygardenworld`,
   replace the hostname and certificate paths, enable it, `nginx -t`, `systemctl reload nginx`.

The WebSocket location needs HTTP/1.1 upgrade headers and a long read timeout;
account creation performs the game login inline, so the RPC location keeps a
300s read timeout. `X-Forwarded-Proto` must be forwarded for secure cookies.
