// language: Go, file: backend/internal/api/qr.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const (
	fpPrefix     = "fp:"
	fpTTLSeconds = 15 * 60
	fpMaxRetries = 5
)

// Reservation ties a fingerprint amount to a purchasing session.
type Reservation struct {
	FP        string `json:"fp"`
	PlanID    string `json:"plan_id"`
	Name      string `json:"name"`
	CreatedAt int64  `json:"created_at"`
}

// ReserveFingerprint checks KV for an existing reservation on fp.
// If clean, writes it with 15-min TTL. Returns (reservation, conflict, err).
func (a *App) ReserveFingerprint(ctx context.Context, fp string, r Reservation) (*Reservation, bool, error) {
	_, found, err := a.kv.Get(ctx, fpPrefix+fp)
	if err != nil {
		return nil, false, err
	}
	if found {
		return nil, true, nil
	}
	r.FP = fp
	r.CreatedAt = time.Now().Unix()
	data, err := json.Marshal(&r)
	if err != nil {
		return nil, false, err
	}
	if err := a.kv.Put(ctx, fpPrefix+fp, data, fpTTLSeconds); err != nil {
		return nil, false, err
	}
	return &r, false, nil
}

// ReleaseFingerprint removes the reservation. Safe to call on missing key.
func (a *App) ReleaseFingerprint(ctx context.Context, fp string) error {
	return a.kv.Delete(ctx, fpPrefix+fp)
}

// QRResponse is what /api/qr returns to the Android app.
type QRResponse struct {
	PlanID       string `json:"plan_id"`
	PlanName     string `json:"plan_name"`
	BaseAmount   int    `json:"base_amount"`
	FinalAmount  string `json:"final_amount"`
	QRImageBase64 string `json:"qr_image_base64"`
	ExpiresIn    int    `json:"expires_in"`
}

// QRRequest is the body of POST /api/qr.
type QRRequest struct {
	PlanID string `json:"plan_id"`
	Name   string `json:"name"`
}

// HandleQR is POST /api/qr — public, no auth (pre-purchase flow).
func (a *App) HandleQR(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	var req QRRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid json"})
		return
	}
	req.PlanID = strings.TrimSpace(req.PlanID)
	req.Name = strings.TrimSpace(req.Name)
	if req.PlanID == "" || req.Name == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "plan_id and name required"})
		return
	}
	if len(req.Name) > 40 {
		req.Name = req.Name[:40]
	}

	plan, err := GetPlan(req.PlanID)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "unknown plan"})
		return
	}

	var chosenFP string
	var qrBytes []byte
	for attempt := 0; attempt < fpMaxRetries; attempt++ {
		fp, img, err := a.callQRAPI(ctx, plan.Amount)
		if err != nil {
			writeJSON(w, http.StatusBadGateway, map[string]string{"error": "qr api: " + err.Error()})
			return
		}
		res, conflict, err := a.ReserveFingerprint(ctx, fp, Reservation{
			PlanID: plan.ID,
			Name:   req.Name,
		})
		if err != nil {
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "reserve: " + err.Error()})
			return
		}
		if conflict {
			continue
		}
		_ = res
		chosenFP = fp
		qrBytes = img
		break
	}

	if chosenFP == "" {
		writeJSON(w, http.StatusConflict, map[string]string{"error": "fingerprint exhaustion, retry later"})
		return
	}

	writeJSON(w, http.StatusOK, QRResponse{
		PlanID:       plan.ID,
		PlanName:     plan.Name,
		BaseAmount:   plan.Amount,
		FinalAmount:  chosenFP,
		QRImageBase64: encodeBase64(qrBytes),
		ExpiresIn:    fpTTLSeconds,
	})
}

// callQRAPI asks the external QR generator for a PNG and its X-Final-Amount header.
func (a *App) callQRAPI(ctx context.Context, baseAmount int) (string, []byte, error) {
	u, err := url.Parse(a.cfg.AutopayQRURL)
	if err != nil {
		return "", nil, err
	}
	q := u.Query()
	q.Set("upi", a.cfg.AutopayUPI)
	q.Set("amount", strconv.Itoa(baseAmount))
	q.Set("bot_name", a.cfg.AutopayBotName)
	q.Set("remark", randomRemark(10))
	u.RawQuery = q.Encode()

	reqCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(reqCtx, http.MethodGet, u.String(), nil)
	if err != nil {
		return "", nil, err
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return "", nil, fmt.Errorf("%d %s", resp.StatusCode, truncate(string(body), 200))
	}

	fp := resp.Header.Get("X-Final-Amount")
	if fp == "" {
		return "", nil, fmt.Errorf("missing X-Final-Amount header")
	}
	if _, err := strconv.ParseFloat(fp, 64); err != nil {
		return "", nil, fmt.Errorf("bad X-Final-Amount %q", fp)
	}

	img, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", nil, err
	}
	if len(img) == 0 {
		return "", nil, fmt.Errorf("empty qr body")
	}
	return fp, img, nil
}

const remarkAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

func randomRemark(n int) string {
	max := big.NewInt(int64(len(remarkAlphabet)))
	var b strings.Builder
	b.Grow(n)
	for i := 0; i < n; i++ {
		v, err := rand.Int(rand.Reader, max)
		if err != nil {
			return "PLUCK00000"
		}
		b.WriteByte(remarkAlphabet[v.Int64()])
	}
	return b.String()
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}
