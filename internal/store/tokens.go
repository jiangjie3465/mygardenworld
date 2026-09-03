package store

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"time"
)

var ErrTokenInvalid = errors.New("refresh token invalid or expired")

func hashToken(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}

func (d *DB) SaveRefreshToken(ctx context.Context, userID int64, token string, expiresAt time.Time) error {
	return d.SaveRefreshTokenSession(ctx, userID, token, expiresAt, "web", "", "")
}

func (d *DB) SaveRefreshTokenSession(ctx context.Context, userID int64, token string, expiresAt time.Time, clientType, deviceID, deviceName string) error {
	if clientType != "web" && clientType != "mobile" {
		return fmt.Errorf("invalid refresh token client type %q", clientType)
	}
	_, err := d.ExecContext(ctx,
		`INSERT INTO refresh_tokens(user_id, token_hash, expires_at, client_type, device_id, device_name, last_used_at)
		 VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)`,
		userID, hashToken(token), expiresAt.UTC(), clientType, deviceID, deviceName,
	)
	return err
}

func (d *DB) ValidateRefreshToken(ctx context.Context, token string) (int64, error) {
	return d.validateRefreshToken(ctx, token, "")
}

// ValidateRefreshTokenForClient validates a refresh token and requires it to
// belong to the requested transport client. This prevents browser and mobile
// refresh tokens from being exchanged across authentication flows.
func (d *DB) ValidateRefreshTokenForClient(ctx context.Context, token, clientType string) (int64, error) {
	return d.validateRefreshToken(ctx, token, clientType)
}

func (d *DB) validateRefreshToken(ctx context.Context, token, clientType string) (int64, error) {
	var userID int64
	var expiresAt time.Time
	var storedClientType string
	err := d.QueryRowContext(ctx,
		`SELECT user_id, expires_at, client_type FROM refresh_tokens WHERE token_hash = ?`,
		hashToken(token),
	).Scan(&userID, &expiresAt, &storedClientType)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, ErrTokenInvalid
	}
	if err != nil {
		return 0, err
	}
	if time.Now().After(expiresAt) {
		_ = d.revokeTokenHash(ctx, hashToken(token))
		return 0, ErrTokenInvalid
	}
	if clientType != "" && storedClientType != clientType {
		return 0, ErrTokenInvalid
	}
	if _, err := d.ExecContext(ctx,
		`UPDATE refresh_tokens SET last_used_at = CURRENT_TIMESTAMP WHERE token_hash = ?`,
		hashToken(token),
	); err != nil {
		return 0, err
	}
	return userID, nil
}

func (d *DB) RevokeRefreshToken(ctx context.Context, token string) error {
	return d.revokeTokenHash(ctx, hashToken(token))
}

func (d *DB) RevokeRefreshTokenForClient(ctx context.Context, token, clientType string) error {
	if clientType != "web" && clientType != "mobile" {
		return fmt.Errorf("invalid refresh token client type %q", clientType)
	}
	_, err := d.ExecContext(ctx,
		`DELETE FROM refresh_tokens WHERE token_hash = ? AND client_type = ?`,
		hashToken(token), clientType,
	)
	return err
}

func (d *DB) RefreshTokenDevice(ctx context.Context, token string) (string, string, error) {
	var deviceID, deviceName string
	err := d.QueryRowContext(ctx,
		`SELECT device_id, device_name FROM refresh_tokens WHERE token_hash = ? AND client_type = 'mobile'`,
		hashToken(token),
	).Scan(&deviceID, &deviceName)
	if errors.Is(err, sql.ErrNoRows) {
		return "", "", ErrTokenInvalid
	}
	if err != nil {
		return "", "", err
	}
	return deviceID, deviceName, nil
}

func (d *DB) RevokeAllRefreshTokens(ctx context.Context, userID int64) error {
	_, err := d.ExecContext(ctx, `DELETE FROM refresh_tokens WHERE user_id = ?`, userID)
	return err
}

// RotateRefreshToken atomically consumes one refresh token and stores its
// replacement. If any part of the rotation fails, the old token remains
// valid; concurrent replays of the same token can only succeed once.
func (d *DB) RotateRefreshToken(ctx context.Context, oldToken, newToken string, expiresAt time.Time) error {
	return d.RotateRefreshTokenSession(ctx, oldToken, newToken, expiresAt, "web", "", "")
}

func (d *DB) RotateRefreshTokenSession(ctx context.Context, oldToken, newToken string, expiresAt time.Time, clientType, deviceID, deviceName string) error {
	if oldToken == "" || newToken == "" || oldToken == newToken {
		return ErrTokenInvalid
	}
	if clientType != "web" && clientType != "mobile" {
		return fmt.Errorf("invalid refresh token client type %q", clientType)
	}
	tx, err := d.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin refresh token rotation: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	oldHash := hashToken(oldToken)
	var userID int64
	var storedExpiry time.Time
	var storedClientType string
	if err := tx.QueryRowContext(ctx,
		`DELETE FROM refresh_tokens WHERE token_hash = ? RETURNING user_id, expires_at, client_type`,
		oldHash,
	).Scan(&userID, &storedExpiry, &storedClientType); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ErrTokenInvalid
		}
		return fmt.Errorf("consume refresh token: %w", err)
	}
	if !time.Now().Before(storedExpiry) {
		if err := tx.Commit(); err != nil {
			return fmt.Errorf("commit expired refresh token cleanup: %w", err)
		}
		return ErrTokenInvalid
	}
	if storedClientType != clientType {
		return ErrTokenInvalid
	}

	if _, err := tx.ExecContext(ctx,
		`INSERT INTO refresh_tokens(user_id, token_hash, expires_at, client_type, device_id, device_name, last_used_at)
		 VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)`,
		userID, hashToken(newToken), expiresAt.UTC(), clientType, deviceID, deviceName,
	); err != nil {
		return fmt.Errorf("store rotated refresh token: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit refresh token rotation: %w", err)
	}
	return nil
}

func (d *DB) revokeTokenHash(ctx context.Context, hash string) error {
	_, err := d.ExecContext(ctx, `DELETE FROM refresh_tokens WHERE token_hash = ?`, hash)
	return err
}

func (d *DB) CleanExpiredTokens(ctx context.Context) error {
	_, err := d.ExecContext(ctx, `DELETE FROM refresh_tokens WHERE expires_at < ?`, time.Now().UTC())
	return err
}

var ErrMobileSessionNotFound = errors.New("mobile session not found")

// MobileSession is one device-scoped mobile refresh-token session. The token
// hash is intentionally not exposed.
type MobileSession struct {
	ID         int64
	UserID     int64
	DeviceID   string
	DeviceName string
	CreatedAt  time.Time
	LastUsedAt time.Time
	ExpiresAt  time.Time
}

// ListMobileSessions returns the caller's unexpired mobile sessions, most
// recently used first.
func (d *DB) ListMobileSessions(ctx context.Context, userID int64) ([]MobileSession, error) {
	rows, err := d.QueryContext(ctx,
		`SELECT id, user_id, device_id, device_name, created_at, last_used_at, expires_at
		 FROM refresh_tokens
		 WHERE user_id = ? AND client_type = 'mobile' AND expires_at > ?
		 ORDER BY COALESCE(last_used_at, created_at) DESC, id DESC`,
		userID, time.Now().UTC(),
	)
	if err != nil {
		return nil, err
	}
	defer func() { _ = rows.Close() }()
	var sessions []MobileSession
	for rows.Next() {
		var s MobileSession
		var lastUsed sql.NullTime
		if err := rows.Scan(&s.ID, &s.UserID, &s.DeviceID, &s.DeviceName, &s.CreatedAt, &lastUsed, &s.ExpiresAt); err != nil {
			return nil, err
		}
		if lastUsed.Valid {
			s.LastUsedAt = lastUsed.Time
		}
		sessions = append(sessions, s)
	}
	return sessions, rows.Err()
}

// RevokeMobileSession deletes one of the caller's own mobile sessions. Sessions
// owned by other users are reported as not found rather than revealed.
func (d *DB) RevokeMobileSession(ctx context.Context, userID, sessionID int64) error {
	res, err := d.ExecContext(ctx,
		`DELETE FROM refresh_tokens WHERE id = ? AND user_id = ? AND client_type = 'mobile'`,
		sessionID, userID,
	)
	if err != nil {
		return err
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		return ErrMobileSessionNotFound
	}
	return nil
}
