package babigame

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"
)

// TestHeartbeatClosesSilentConnection covers a half-open game socket: the
// write side still succeeds but nothing ever comes back. Without a liveness
// verdict the runner keeps a "connected" client whose every RPC times out.
func TestHeartbeatClosesSilentConnection(t *testing.T) {
	tests := []struct {
		name       string
		replies    bool
		wantClosed bool
	}{
		{name: "silent server closes client", replies: false, wantClosed: true},
		{name: "server traffic keeps client open", replies: true, wantClosed: false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				conn, err := websocket.Accept(w, r, nil)
				if err != nil {
					return
				}
				defer func() { _ = conn.CloseNow() }()
				for {
					if _, _, err := conn.Read(r.Context()); err != nil {
						return
					}
					if tt.replies {
						if err := conn.Write(r.Context(), websocket.MessageText, []byte(`"connectionEnabled"`)); err != nil {
							return
						}
					}
				}
			}))
			defer server.Close()

			cfg, err := ConfigForChannel(ChannelIOS)
			if err != nil {
				t.Fatalf("ConfigForChannel: %v", err)
			}
			c := NewClient(&Session{Cfg: cfg, RouteToken: "route-token", GsIdx: 1})
			c.HeartbeatInterval = 20 * time.Millisecond
			c.HeartbeatTimeout = 40 * time.Millisecond
			c.HeartbeatMaxMisses = 2
			defer func() { _ = c.Close() }()

			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			conn, _, err := websocket.Dial(ctx, "ws"+strings.TrimPrefix(server.URL, "http"), nil)
			if err != nil {
				t.Fatalf("dial: %v", err)
			}
			c.mu.Lock()
			c.conn = conn
			c.mu.Unlock()
			c.markReceived()
			go c.reader()
			go c.heartbeat()

			select {
			case <-c.Done():
				if !tt.wantClosed {
					t.Fatal("client closed although the server kept replying")
				}
			case <-time.After(600 * time.Millisecond):
				if tt.wantClosed {
					t.Fatal("silent connection was never closed")
				}
			}
		})
	}
}
