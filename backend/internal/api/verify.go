// language: Go, file: backend/internal/api/verify.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const (
	paymentPrefix = "payment:"
	paymentTTL    = 90 * 86400 // 90 days — audit trail for verified payments
)

// PaymentRecord persists after a successful verify. Presence = key already issued.
type PaymentRecord struct {
	FP         string `json:"fp"`
	Key        string `json:"key"`
	PlanID     string `json:"plan_id"`
	Name       string `json:"name"`
	VerifiedAt int64  `json:"verified_at"`
	Received   string `json:"received"`
}

// AutopayVerifyResponse mirrors the verify API's JSON.
type AutopayVerifyResponse struct {
	Verified          bool    `json:"verified"`
	Received          float64 `json:"received"`
	Expected          float64 `json:"expected,omitempty"`
	Status            string  `json:"status"`
	Remaining         int     `json:"remaining,omitempty"`
	Note              string  `json:"note,omitempty"`
	Error             string  `json:"error,omitempty"`
	RetryAfterSeconds int     `json:"retry_after_seconds,omitempty"`
}

// VerifyRequest is the app's POST body.
type VerifyRequest struct {
	FinalAmount string `json:"final_amount"` // e.g. "149.07", string to preserve paise
}

// VerifyResponse is what the app receives.
type VerifyResponse struct {
	Verified          bool   `json:"verified"`
	Status            string `json:"status"`
	Key               string `json:"key,omitempty"`
	PlanName          string `json:"plan_name,omitempty"`
	ExpiresAt         int64  `json:"expires_at,omitempty"`
	Received          string `json:"received,omitempty"`
	Expected          string `json:"expected,omitempty"`
	Message           string `json:"message,omitempty"`
	RetryAfterSeconds int    `json:"retry_after_seconds,omitempty"`
}

// HandleVerify is POST /api/verify.
func (a *App) HandleVerify(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	var req VerifyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, VerifyResponse{
			Status: "invalid_request", Message: "invalid json",
		})
		return
	}
	fp := strings.TrimSpace(req.FinalAmount)
	if fp == "" {
		writeJSON(w, http.StatusBadRequest, VerifyResponse{
			Status: "invalid_request", Message: "final_amount required",
		})
		return
	}
	fpFloat, err := strconv.ParseFloat(fp, 64)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, VerifyResponse{
			Status: "invalid_request", Message: "bad final_amount",
		})
		return
	}

	// Idempotency: if a payment record exists, the key was already issued.
	if existing, err := a.loadPayment(ctx, fp); err != nil {
		writeJSON(w, http.StatusInternalServerError, VerifyResponse{
			Status: "error", Message: "payment lookup failed",
		})
		return
	} else if existing != nil {
		resp := VerifyResponse{
			Verified: true, Status: "exact", Key: existing.Key, Message: "already_verified",
		}
		if keyRec, err := GetKey(ctx, a.kv, existing.Key); err == nil {
			resp.PlanName = keyRec.PlanName
			resp.ExpiresAt = keyRec.ExpiresAt
		}
		writeJSON(w, http.StatusOK, resp)
		return
	}

	// Reservation must exist (15-min TTL enforced by KV itself).
	resRaw, found, err := a.kv.Get(ctx, fpPrefix+fp)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, VerifyResponse{
			Status: "error", Message: "kv read failed",
		})
		return
	}
	if !found {
		writeJSON(w, http.StatusOK, VerifyResponse{
			Status: "expired", Message: "QR expired, generate a new one",
		})
		return
	}
	var res Reservation
	if err := json.Unmarshal(resRaw, &res); err != nil {
		writeJSON(w, http.StatusInternalServerError, VerifyResponse{
			Status: "error", Message: "corrupt reservation",
		})
		return
	}
	plan, err := GetPlan(res.PlanID)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, VerifyResponse{
			Status: "error", Message: "unknown plan on reservation",
		})
		return
	}

	// Ask the auto-payment API.
	av, err := a.callVerifyAPI(ctx, fp)
	if err != nil {
		writeJSON(w, http.StatusBadGateway, VerifyResponse{
			Status: "error", Message: "verify api: " + err.Error(),
		})
		return
	}

	// Terminal errors — no retry, surface to user.
	switch av.Status {
	case "invalid_key", "revoked", "blacklisted", "expired",
		"invalid_request", "rate_limited":
		writeJSON(w, http.StatusOK, VerifyResponse{
			Status: av.Status, Message: av.Error,
			RetryAfterSeconds: av.RetryAfterSeconds,
		})
		return
	}

	// Not exact — return status for the app to show.
	if av.Status != "exact" || !av.Verified {
		writeJSON(w, http.StatusOK, VerifyResponse{
			Verified: false,
			Status:   av.Status,
			Received: formatMoney(av.Received),
			Expected: formatMoney(av.Expected),
			Message:  messageFor(av.Status),
		})
		return
	}

	// Rule 21: exact match AND received matches OUR fingerprint within 0.01.
	if math.Abs(av.Received-fpFloat) >= 0.01 {
		writeJSON(w, http.StatusOK, VerifyResponse{
			Verified: false,
			Status:   "mismatch",
			Received: formatMoney(av.Received),
			Expected: fp,
			Message:  "received amount does not match this deposit",
		})
		return
	}

	// Issue the key.
	key, err := GenerateKey()
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, VerifyResponse{
			Status: "error", Message: "key generation failed",
		})
		return
	}
	now := time.Now().Unix()
	expiresAt := now + int64(plan.DurationDays)*86400

	keyRec := &KeyRecord{
		Key:       key,
		Name:      res.Name,
		PlanID:    plan.ID,
		PlanName:  plan.Name,
		CreatedAt: now,
		ExpiresAt: expiresAt,
		Status:    "active",
		Source:    "purchase",
		PaymentFP: fp,
	}
	// Write key record first. If payment record write fails, the key is
	// orphaned (expires silently) and a retry generates a fresh key.
	if err := StoreKey(ctx, a.kv, keyRec); err != nil {
		writeJSON(w, http.StatusInternalServerError, VerifyResponse{
			Status: "error", Message: "store key failed",
		})
		return
	}
	payRec := &PaymentRecord{
		FP: fp, Key: key, PlanID: plan.ID, Name: res.Name,
		VerifiedAt: now, Received: fp,
	}
	if err := a.savePayment(ctx, payRec); err != nil {
		writeJSON(w, http.StatusInternalServerError, VerifyResponse{
			Status: "error", Message: "payment record failed",
		})
		return
	}
	_ = a.ReleaseFingerprint(ctx, fp)

	writeJSON(w, http.StatusOK, VerifyResponse{
		Verified:  true,
		Status:    "exact",
		Key:       key,
		PlanName:  plan.Name,
		ExpiresAt: expiresAt,
		Received:  fp,
	})
}

// callVerifyAPI hits the auto-payment verify endpoint.
func (a *App) callVerifyAPI(ctx context.Context, amount string) (*AutopayVerifyResponse, error) {
	u, err := url.Parse(a.cfg.AutopayVerifyURL)
	if err != nil {
		return nil, err
	}
	q := u.Query()
	q.Set("key", a.cfg.AutopayKey)
	q.Set("amount", amount)
	u.RawQuery = q.Encode()

	reqCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(reqCtx, http.MethodGet, u.String(), nil)
	if err != nil {
		return nil, err
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("%d %s", resp.StatusCode, truncate(string(body), 200))
	}

	var out AutopayVerifyResponse
	if err := json.Unmarshal(body, &out); err != nil {
		return nil, err
	}
	return &out, nil
}

func (a *App) savePayment(ctx context.Context, p *PaymentRecord) error {
	data, err := json.Marshal(p)
	if err != nil {
		return err
	}
	return a.kv.Put(ctx, paymentPrefix+p.FP, data, paymentTTL)
}

func (a *App) loadPayment(ctx context.Context, fp string) (*PaymentRecord, error) {
	data, found, err := a.kv.Get(ctx, paymentPrefix+fp)
	if err != nil || !found {
		return nil, err
	}
	var p PaymentRecord
	if err := json.Unmarshal(data, &p); err != nil {
		return nil, err
	}
	return &p, nil
}

func formatMoney(f float64) string {
	return strconv.FormatFloat(f, 'f', 2, 64)
}

func messageFor(status string) string {
	switch status {
	case "not_found":
		return "Payment not detected yet. Wait 30 seconds and check again."
	case "partial":
		return "You paid less than the QR amount."
	case "overpaid":
		return "You paid more than the QR amount."
	case "rate_limited":
		return "Too many checks. Wait before trying again."
	default:
		return ""
	}
}
