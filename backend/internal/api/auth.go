// language: Go, file: backend/internal/api/auth.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"strings"
	"time"

	"github.com/realximanta/Pluck/backend/internal/kv"
)

const (
	keyPrefix = "key:"

	// 32 chars: A-Z minus O and I, plus 2-9. No 0/1 to avoid OCR confusion.
	keyAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
	keyLength   = 10
)

var (
	ErrKeyNotFound = errors.New("key not found")
	ErrKeyExpired  = errors.New("key expired")
	ErrKeyRevoked  = errors.New("key revoked")
)

// KeyRecord is what lives at kv["key:<KEY>"].
type KeyRecord struct {
	Key       string `json:"key"`
	Name      string `json:"name"`
	PlanID    string `json:"plan_id"`
	PlanName  string `json:"plan_name"`
	CreatedAt int64  `json:"created_at"`
	ExpiresAt int64  `json:"expires_at"` // unix seconds; 0 = never
	Status    string `json:"status"`     // active | revoked
	Source    string `json:"source"`     // admin | purchase
	PaymentFP string `json:"payment_fp,omitempty"`
}

// GenerateKey returns a 10-char key from a 32-char alphabet.
func GenerateKey() (string, error) {
	max := big.NewInt(int64(len(keyAlphabet)))
	var b strings.Builder
	b.Grow(keyLength)
	for i := 0; i < keyLength; i++ {
		n, err := rand.Int(rand.Reader, max)
		if err != nil {
			return "", err
		}
		b.WriteByte(keyAlphabet[n.Int64()])
	}
	return b.String(), nil
}

// StoreKey writes the record to KV and sets TTL to match ExpiresAt (0 = no TTL).
func StoreKey(ctx context.Context, c *kv.Client, rec *KeyRecord) error {
	data, err := json.Marshal(rec)
	if err != nil {
		return err
	}
	ttl := 0
	if rec.ExpiresAt > 0 {
		remaining := rec.ExpiresAt - time.Now().Unix()
		if remaining <= 0 {
			return ErrKeyExpired
		}
		ttl = int(remaining)
	}
	return c.Put(ctx, keyPrefix+rec.Key, data, ttl)
}

// GetKey reads and parses the record. Returns ErrKeyNotFound on miss.
func GetKey(ctx context.Context, c *kv.Client, key string) (*KeyRecord, error) {
	key = strings.ToUpper(strings.TrimSpace(key))
	data, found, err := c.Get(ctx, keyPrefix+key)
	if err != nil {
		return nil, err
	}
	if !found {
		return nil, ErrKeyNotFound
	}
	var rec KeyRecord
	if err := json.Unmarshal(data, &rec); err != nil {
		return nil, fmt.Errorf("corrupt key record %s: %w", key, err)
	}
	return &rec, nil
}

// ValidateKey enforces status + expiry. Returns the record on success.
func ValidateKey(ctx context.Context, c *kv.Client, key string) (*KeyRecord, error) {
	rec, err := GetKey(ctx, c, key)
	if err != nil {
		return nil, err
	}
	if rec.Status == "revoked" {
		return nil, ErrKeyRevoked
	}
	if rec.ExpiresAt > 0 && time.Now().Unix() >= rec.ExpiresAt {
		return nil, ErrKeyExpired
	}
	return rec, nil
}

// RevokeKey marks the key revoked but keeps the record (for audit + history).
func RevokeKey(ctx context.Context, c *kv.Client, key string) error {
	rec, err := GetKey(ctx, c, key)
	if err != nil {
		return err
	}
	rec.Status = "revoked"
	return StoreKey(ctx, c, rec)
}

// ListKeys returns all key records for the admin panel.
func ListKeys(ctx context.Context, c *kv.Client) ([]*KeyRecord, error) {
	names, err := c.ListPrefix(ctx, keyPrefix)
	if err != nil {
		return nil, err
	}
	out := make([]*KeyRecord, 0, len(names))
	for _, name := range names {
		data, found, err := c.Get(ctx, name)
		if err != nil || !found {
			continue
		}
		var rec KeyRecord
		if json.Unmarshal(data, &rec) == nil {
			out = append(out, &rec)
		}
	}
	return out, nil
}
