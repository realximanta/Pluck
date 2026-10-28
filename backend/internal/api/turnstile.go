// language: Go, file: backend/internal/api/turnstile.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

const turnstileVerifyURL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"

type turnstileVerifyResponse struct {
	Success     bool     `json:"success"`
	ChallengeTS string   `json:"challenge_ts"`
	Hostname    string   `json:"hostname"`
	ErrorCodes  []string `json:"error-codes"`
}

func (a *App) verifyTurnstile(ctx context.Context, token string) (bool, error) {
	if token == "" {
		return false, fmt.Errorf("missing turnstile token")
	}

	ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	body := strings.NewReader(fmt.Sprintf("secret=%s&response=%s", a.cfg.TurnstileSecretKey, token))
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, turnstileVerifyURL, body)
	if err != nil {
		return false, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return false, err
	}
	defer resp.Body.Close()

	var result turnstileVerifyResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return false, err
	}

	if !result.Success {
		return false, fmt.Errorf("turnstile verification failed: %v", result.ErrorCodes)
	}
	return true, nil
}
