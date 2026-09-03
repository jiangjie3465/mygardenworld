package apiserver

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"path/filepath"
	"strings"
	"testing"
	"time"

	connect "connectrpc.com/connect"
	"golang.org/x/crypto/bcrypt"

	pb "github.com/SilkageNet/mygardenworld/gen/mygardenworld/v1"
	"github.com/SilkageNet/mygardenworld/internal/auth"
	"github.com/SilkageNet/mygardenworld/internal/automation"
	"github.com/SilkageNet/mygardenworld/internal/policycfg"
	"github.com/SilkageNet/mygardenworld/internal/runner"
	"github.com/SilkageNet/mygardenworld/internal/store"
)

func TestLoginUnknownUserAndWrongPasswordUseSameError(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{UserFailures: 100, IPFailures: 100})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "active")

	for _, tc := range []struct {
		name     string
		username string
		password string
	}{
		{name: "unknown", username: "missing", password: "whatever"},
		{name: "wrong password", username: "owner", password: "wrong"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			_, err := svc.Login(ctx, connect.NewRequest(&pb.LoginRequest{Username: tc.username, Password: tc.password}))
			if connect.CodeOf(err) != connect.CodeUnauthenticated {
				t.Fatalf("Login code=%s err=%v, want Unauthenticated", connect.CodeOf(err), err)
			}
			if !strings.Contains(err.Error(), "账号或密码不正确") {
				t.Fatalf("Login error=%q, want localized invalid credentials", err)
			}
		})
	}
}

func TestLoginLimiterReturnsResourceExhausted(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{
		Window:       time.Hour,
		UserFailures: 2,
		IPFailures:   100,
		Lockout:      time.Hour,
		MaxEntries:   16,
	})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "active")

	_, err := svc.Login(ctx, connect.NewRequest(&pb.LoginRequest{Username: "owner", Password: "wrong"}))
	if connect.CodeOf(err) != connect.CodeUnauthenticated {
		t.Fatalf("first Login code=%s err=%v, want Unauthenticated", connect.CodeOf(err), err)
	}
	_, err = svc.Login(ctx, connect.NewRequest(&pb.LoginRequest{Username: "owner", Password: "wrong"}))
	if connect.CodeOf(err) != connect.CodeResourceExhausted {
		t.Fatalf("second Login code=%s err=%v, want ResourceExhausted", connect.CodeOf(err), err)
	}
	_, err = svc.Login(ctx, connect.NewRequest(&pb.LoginRequest{Username: "owner", Password: "ValidPass123!"}))
	if connect.CodeOf(err) != connect.CodeResourceExhausted {
		t.Fatalf("locked Login code=%s err=%v, want ResourceExhausted", connect.CodeOf(err), err)
	}
}

func TestDisabledUserOnlyRevealsStatusAfterCorrectPassword(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{UserFailures: 100, IPFailures: 100})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "disabled")

	_, err := svc.Login(ctx, connect.NewRequest(&pb.LoginRequest{Username: "owner", Password: "wrong"}))
	if connect.CodeOf(err) != connect.CodeUnauthenticated {
		t.Fatalf("wrong password code=%s err=%v, want Unauthenticated", connect.CodeOf(err), err)
	}
	_, err = svc.Login(ctx, connect.NewRequest(&pb.LoginRequest{Username: "owner", Password: "ValidPass123!"}))
	if connect.CodeOf(err) != connect.CodePermissionDenied {
		t.Fatalf("correct disabled code=%s err=%v, want PermissionDenied", connect.CodeOf(err), err)
	}
}

func TestLoginSetsStrictRefreshCookie(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{UserFailures: 100, IPFailures: 100})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "active")

	resp, err := svc.Login(ctx, connect.NewRequest(&pb.LoginRequest{Username: "owner", Password: "ValidPass123!"}))
	if err != nil {
		t.Fatal(err)
	}
	cookie := resp.Header().Get("Set-Cookie")
	if !strings.Contains(cookie, "SameSite=Strict") {
		t.Fatalf("Set-Cookie=%q, want SameSite=Strict", cookie)
	}
}

func TestMobileLoginReturnsBodyTokensAndDeviceMetadata(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{UserFailures: 100, IPFailures: 100})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "active")

	resp, err := svc.MobileLogin(ctx, connect.NewRequest(&pb.MobileLoginRequest{
		Username:   "owner",
		Password:   "ValidPass123!",
		DeviceId:   "android-emulator-1",
		DeviceName: "Pixel API 36",
	}))
	if err != nil {
		t.Fatal(err)
	}
	if resp.Msg.GetAccessToken() == "" || resp.Msg.GetRefreshToken() == "" {
		t.Fatal("mobile login did not return both tokens")
	}
	if cookie := resp.Header().Get("Set-Cookie"); cookie != "" {
		t.Fatalf("mobile login unexpectedly set a cookie: %q", cookie)
	}
	deviceID, deviceName, err := svc.DB.RefreshTokenDevice(ctx, resp.Msg.GetRefreshToken())
	if err != nil {
		t.Fatal(err)
	}
	if deviceID != "android-emulator-1" || deviceName != "Pixel API 36" {
		t.Fatalf("stored device=(%q,%q)", deviceID, deviceName)
	}
	if _, err := svc.DB.ValidateRefreshTokenForClient(ctx, resp.Msg.GetRefreshToken(), "web"); !errors.Is(err, store.ErrTokenInvalid) {
		t.Fatalf("mobile token accepted as web token: %v", err)
	}
}

func TestMobileRefreshRotatesTokenAndPreservesDevice(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{UserFailures: 100, IPFailures: 100})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "active")
	login, err := svc.MobileLogin(ctx, connect.NewRequest(&pb.MobileLoginRequest{
		Username: "owner", Password: "ValidPass123!", DeviceId: "device-1", DeviceName: "Emulator",
	}))
	if err != nil {
		t.Fatal(err)
	}
	oldToken := login.Msg.GetRefreshToken()
	refresh, err := svc.MobileRefresh(ctx, connect.NewRequest(&pb.MobileRefreshRequest{RefreshToken: oldToken}))
	if err != nil {
		t.Fatal(err)
	}
	if refresh.Msg.GetRefreshToken() == "" || refresh.Msg.GetRefreshToken() == oldToken {
		t.Fatal("mobile refresh did not rotate refresh token")
	}
	if _, err := svc.DB.ValidateRefreshTokenForClient(ctx, oldToken, "mobile"); !errors.Is(err, store.ErrTokenInvalid) {
		t.Fatalf("old mobile token remained valid: %v", err)
	}
	deviceID, deviceName, err := svc.DB.RefreshTokenDevice(ctx, refresh.Msg.GetRefreshToken())
	if err != nil {
		t.Fatal(err)
	}
	if deviceID != "device-1" || deviceName != "Emulator" {
		t.Fatalf("rotated device=(%q,%q)", deviceID, deviceName)
	}
}

func TestMobileLogoutRevokesOnlyMobileToken(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{UserFailures: 100, IPFailures: 100})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "active")
	login, err := svc.MobileLogin(ctx, connect.NewRequest(&pb.MobileLoginRequest{
		Username: "owner", Password: "ValidPass123!", DeviceId: "device-1",
	}))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := svc.MobileLogout(ctx, connect.NewRequest(&pb.MobileLogoutRequest{RefreshToken: login.Msg.GetRefreshToken()})); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.DB.ValidateRefreshTokenForClient(ctx, login.Msg.GetRefreshToken(), "mobile"); !errors.Is(err, store.ErrTokenInvalid) {
		t.Fatalf("mobile token remained valid after logout: %v", err)
	}
}

func TestMobileLoginValidatesDeviceFields(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "active")
	for _, tc := range []struct {
		name string
		in   *pb.MobileLoginRequest
	}{
		{name: "missing device", in: &pb.MobileLoginRequest{Username: "owner", Password: "ValidPass123!"}},
		{name: "long device id", in: &pb.MobileLoginRequest{Username: "owner", Password: "ValidPass123!", DeviceId: strings.Repeat("x", 129)}},
		{name: "long device name", in: &pb.MobileLoginRequest{Username: "owner", Password: "ValidPass123!", DeviceId: "device", DeviceName: strings.Repeat("x", 129)}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := svc.MobileLogin(ctx, connect.NewRequest(tc.in)); connect.CodeOf(err) != connect.CodeInvalidArgument {
				t.Fatalf("MobileLogin code=%s err=%v, want InvalidArgument", connect.CodeOf(err), err)
			}
		})
	}
}

func TestCreateUserRejectsWeakPassword(t *testing.T) {
	ctx := auth.ContextWithIdentity(context.Background(), &auth.Identity{UserID: 1, Role: "admin"})
	svc := newAuthTestService(t, LoginLimiterConfig{})

	_, err := svc.CreateUser(ctx, connect.NewRequest(&pb.CreateUserRequest{
		Username: "weak",
		Email:    "weak@example.test",
		Password: "change-me-first",
	}))
	if connect.CodeOf(err) != connect.CodeInvalidArgument {
		t.Fatalf("CreateUser code=%s err=%v, want InvalidArgument", connect.CodeOf(err), err)
	}
}

func TestCreateUserValidatesOptionsBeforeInsert(t *testing.T) {
	ctx := auth.ContextWithIdentity(context.Background(), &auth.Identity{UserID: 1, Role: "admin"})
	svc := newAuthTestService(t, LoginLimiterConfig{})

	_, err := svc.CreateUser(ctx, connect.NewRequest(&pb.CreateUserRequest{
		Username: " partial ",
		Email:    "partial@example.test",
		Password: "ValidPass123!",
		Role:     userRolePtr(pb.UserRole(99)),
	}))
	if connect.CodeOf(err) != connect.CodeInvalidArgument {
		t.Fatalf("CreateUser code=%s err=%v, want InvalidArgument", connect.CodeOf(err), err)
	}
	if _, err := svc.DB.GetUserByUsername(context.Background(), "partial"); !errors.Is(err, store.ErrUserNotFound) {
		t.Fatalf("invalid CreateUser left a user behind: %v", err)
	}
}

func TestDisableUserStopsRestoreAndRevokesRefreshTokens(t *testing.T) {
	ctx := context.Background()
	adminCtx := auth.ContextWithIdentity(ctx, &auth.Identity{UserID: 1, Role: "admin"})
	svc := newAuthTestService(t, LoginLimiterConfig{})
	svc.Manager = runner.NewManager(svc.DB, runner.NewBus(), svc.Log)
	user, err := svc.DB.CreateUser(ctx, "owner", "owner@example.test", "hash")
	if err != nil {
		t.Fatal(err)
	}
	account, err := svc.DB.CreateAccount(ctx, user.ID, "main", "ios", "game", "password")
	if err != nil {
		t.Fatal(err)
	}
	policy := automation.DefaultPolicy()
	policy.AutomationEnabled = true
	if err := svc.persistPolicy(ctx, account.ID, policy); err != nil {
		t.Fatal(err)
	}
	if err := svc.DB.SaveRefreshToken(ctx, user.ID, "refresh-token", time.Now().Add(time.Hour)); err != nil {
		t.Fatal(err)
	}

	status := pb.UserStatus_USER_STATUS_DISABLED
	if _, err := svc.UpdateUser(adminCtx, connect.NewRequest(&pb.UpdateUserRequest{UserId: user.ID, Status: &status})); err != nil {
		t.Fatal(err)
	}
	storedPolicy, err := svc.DB.LoadPolicyJSON(ctx, account.ID)
	if err != nil {
		t.Fatal(err)
	}
	effective, err := policycfg.FromJSON(storedPolicy)
	if err != nil {
		t.Fatal(err)
	}
	if effective.GetAutomationEnabled() {
		t.Fatal("disabled user's persisted automation remains enabled")
	}
	if _, err := svc.DB.ValidateRefreshToken(ctx, "refresh-token"); !errors.Is(err, store.ErrTokenInvalid) {
		t.Fatalf("disabled user's refresh token error = %v, want ErrTokenInvalid", err)
	}
}

func userRolePtr(value pb.UserRole) *pb.UserRole { return &value }

func newAuthTestService(t *testing.T, limiterCfg LoginLimiterConfig) *Services {
	t.Helper()
	ctx := context.Background()
	db, err := store.Open(ctx, filepath.Join(t.TempDir(), "garden.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = db.Close() })
	return &Services{
		DB:           db,
		JWT:          auth.NewJWT("test-secret-test-secret-test-secret"),
		Log:          slog.New(slog.NewTextHandler(io.Discard, nil)),
		LoginLimiter: NewLoginLimiter(limiterCfg),
	}
}

func createTestUser(t *testing.T, ctx context.Context, db *store.DB, username, email, password, status string) {
	t.Helper()
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		t.Fatal(err)
	}
	user, err := db.CreateUser(ctx, username, email, string(hash))
	if err != nil {
		t.Fatal(err)
	}
	if status != "" && status != "active" {
		if _, err := db.UpdateUser(ctx, user.ID, nil, nil, &status); err != nil {
			t.Fatal(err)
		}
	}
}

func TestMobileRefreshRejectsReplayExpiredDisabledAndRevoked(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{UserFailures: 100, IPFailures: 100})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "active")
	mobileLogin := func(t *testing.T, device string) string {
		t.Helper()
		resp, err := svc.MobileLogin(ctx, connect.NewRequest(&pb.MobileLoginRequest{
			Username: "owner", Password: "ValidPass123!", DeviceId: device, DeviceName: device,
		}))
		if err != nil {
			t.Fatal(err)
		}
		return resp.Msg.GetRefreshToken()
	}
	owner, err := svc.DB.GetUserByUsername(ctx, "owner")
	if err != nil {
		t.Fatal(err)
	}

	for _, tc := range []struct {
		name  string
		token func(t *testing.T) string
		want  connect.Code
	}{
		{
			name: "replayed token after rotation",
			token: func(t *testing.T) string {
				old := mobileLogin(t, "replay")
				if _, err := svc.MobileRefresh(ctx, connect.NewRequest(&pb.MobileRefreshRequest{RefreshToken: old})); err != nil {
					t.Fatal(err)
				}
				return old
			},
			want: connect.CodeUnauthenticated,
		},
		{
			name: "expired token",
			token: func(t *testing.T) string {
				if err := svc.DB.SaveRefreshTokenSession(ctx, owner.ID, "expired-mobile", time.Now().Add(-time.Minute), "mobile", "expired", ""); err != nil {
					t.Fatal(err)
				}
				return "expired-mobile"
			},
			want: connect.CodeUnauthenticated,
		},
		{
			name: "web token on mobile refresh",
			token: func(t *testing.T) string {
				if err := svc.DB.SaveRefreshToken(ctx, owner.ID, "web-only", time.Now().Add(time.Hour)); err != nil {
					t.Fatal(err)
				}
				return "web-only"
			},
			want: connect.CodeUnauthenticated,
		},
		{
			name: "revoked device session",
			token: func(t *testing.T) string {
				token := mobileLogin(t, "revoked")
				userCtx := auth.ContextWithIdentity(ctx, &auth.Identity{UserID: owner.ID, Role: "user"})
				list, err := svc.ListMobileSessions(userCtx, connect.NewRequest(&pb.ListMobileSessionsRequest{}))
				if err != nil {
					t.Fatal(err)
				}
				for _, s := range list.Msg.GetSessions() {
					if s.GetDeviceId() == "revoked" {
						if _, err := svc.RevokeMobileSession(userCtx, connect.NewRequest(&pb.RevokeMobileSessionRequest{SessionId: s.GetId()})); err != nil {
							t.Fatal(err)
						}
					}
				}
				return token
			},
			want: connect.CodeUnauthenticated,
		},
		{
			name: "disabled user",
			token: func(t *testing.T) string {
				token := mobileLogin(t, "disabled")
				status := "disabled"
				if _, err := svc.DB.UpdateUser(ctx, owner.ID, nil, nil, &status); err != nil {
					t.Fatal(err)
				}
				return token
			},
			want: connect.CodePermissionDenied,
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			token := tc.token(t)
			_, err := svc.MobileRefresh(ctx, connect.NewRequest(&pb.MobileRefreshRequest{RefreshToken: token}))
			if connect.CodeOf(err) != tc.want {
				t.Fatalf("MobileRefresh code=%s err=%v, want %s", connect.CodeOf(err), err, tc.want)
			}
		})
	}
}

func TestListMobileSessionsMarksCurrentDeviceAndHidesOtherUsers(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{UserFailures: 100, IPFailures: 100})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "active")
	createTestUser(t, ctx, svc.DB, "other", "other@example.test", "ValidPass123!", "active")
	for _, in := range []*pb.MobileLoginRequest{
		{Username: "owner", Password: "ValidPass123!", DeviceId: "phone", DeviceName: "Redmi"},
		{Username: "owner", Password: "ValidPass123!", DeviceId: "tablet", DeviceName: "Pad"},
		{Username: "other", Password: "ValidPass123!", DeviceId: "phone", DeviceName: "Other phone"},
	} {
		if _, err := svc.MobileLogin(ctx, connect.NewRequest(in)); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := svc.Login(ctx, connect.NewRequest(&pb.LoginRequest{Username: "owner", Password: "ValidPass123!"})); err != nil {
		t.Fatal(err)
	}
	owner, err := svc.DB.GetUserByUsername(ctx, "owner")
	if err != nil {
		t.Fatal(err)
	}

	if _, err := svc.ListMobileSessions(ctx, connect.NewRequest(&pb.ListMobileSessionsRequest{})); connect.CodeOf(err) != connect.CodeUnauthenticated {
		t.Fatalf("anonymous ListMobileSessions code=%s, want Unauthenticated", connect.CodeOf(err))
	}
	userCtx := auth.ContextWithIdentity(ctx, &auth.Identity{UserID: owner.ID, Role: "user"})
	resp, err := svc.ListMobileSessions(userCtx, connect.NewRequest(&pb.ListMobileSessionsRequest{DeviceId: "phone"}))
	if err != nil {
		t.Fatal(err)
	}
	sessions := resp.Msg.GetSessions()
	if len(sessions) != 2 {
		t.Fatalf("sessions=%d, want 2 (web token and other user's session excluded)", len(sessions))
	}
	current := map[string]bool{}
	for _, s := range sessions {
		current[s.GetDeviceId()] = s.GetCurrent()
		if s.GetCreatedAt() == nil || s.GetExpiresAt() == nil || s.GetLastUsedAt() == nil {
			t.Fatalf("session %d is missing timestamps: %v", s.GetId(), s)
		}
	}
	if !current["phone"] || current["tablet"] {
		t.Fatalf("current flags=%v, want only phone", current)
	}
}

func TestRevokeMobileSessionOnlyAffectsOwnSessions(t *testing.T) {
	ctx := context.Background()
	svc := newAuthTestService(t, LoginLimiterConfig{UserFailures: 100, IPFailures: 100})
	createTestUser(t, ctx, svc.DB, "owner", "owner@example.test", "ValidPass123!", "active")
	createTestUser(t, ctx, svc.DB, "other", "other@example.test", "ValidPass123!", "active")
	otherLogin, err := svc.MobileLogin(ctx, connect.NewRequest(&pb.MobileLoginRequest{
		Username: "other", Password: "ValidPass123!", DeviceId: "other-phone",
	}))
	if err != nil {
		t.Fatal(err)
	}
	owner, err := svc.DB.GetUserByUsername(ctx, "owner")
	if err != nil {
		t.Fatal(err)
	}
	other, err := svc.DB.GetUserByUsername(ctx, "other")
	if err != nil {
		t.Fatal(err)
	}
	otherSessions, err := svc.DB.ListMobileSessions(ctx, other.ID)
	if err != nil || len(otherSessions) != 1 {
		t.Fatalf("other sessions=%v err=%v", otherSessions, err)
	}
	ownerCtx := auth.ContextWithIdentity(ctx, &auth.Identity{UserID: owner.ID, Role: "user"})

	if _, err := svc.RevokeMobileSession(ownerCtx, connect.NewRequest(&pb.RevokeMobileSessionRequest{SessionId: 0})); connect.CodeOf(err) != connect.CodeInvalidArgument {
		t.Fatalf("zero session id code=%s, want InvalidArgument", connect.CodeOf(err))
	}
	if _, err := svc.RevokeMobileSession(ownerCtx, connect.NewRequest(&pb.RevokeMobileSessionRequest{SessionId: otherSessions[0].ID})); connect.CodeOf(err) != connect.CodeNotFound {
		t.Fatalf("cross-user revoke code=%s err=%v, want NotFound", connect.CodeOf(err), err)
	}
	if _, err := svc.MobileRefresh(ctx, connect.NewRequest(&pb.MobileRefreshRequest{RefreshToken: otherLogin.Msg.GetRefreshToken()})); err != nil {
		t.Fatalf("other user's session was affected by cross-user revoke: %v", err)
	}
}
