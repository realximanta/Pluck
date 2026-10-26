// language: Go, file: backend/internal/api/admin.go, target: Linux (Docker), Go 1.22
package api

import (
	"crypto/subtle"
	_ "embed"
	"encoding/json"
	"net/http"
	"sort"
	"strings"
	"time"
)

//go:embed admin.html
var adminHTML string

// requireAdmin returns true if the request carries the correct admin key.
func (a *App) requireAdmin(r *http.Request) bool {
	got := r.Header.Get("X-Admin-Key")
	if got == "" {
		if c, err := r.Cookie("admin_key"); err == nil {
			got = c.Value
		}
	}
	if got == "" {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(got), []byte(a.cfg.AdminKey)) == 1
}

// HandleAdminPage is GET /admin — returns the HTML shell.
func (a *App) HandleAdminPage(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(adminHTML))
}

// AdminCreateRequest is the body of POST /admin/keys.
type AdminCreateRequest struct {
	Name         string `json:"name"`
	PlanID       string `json:"plan_id"`
	DurationDays int    `json:"duration_days"`
	ExpiresAt    int64  `json:"expires_at"`
}

// HandleAdminCreate is POST /admin/keys — creates a manual key.
func (a *App) HandleAdminCreate(w http.ResponseWriter, r *http.Request) {
	if !a.requireAdmin(r) {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "unauthorized"})
		return
	}
	var req AdminCreateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid json"})
		return
	}
	req.Name = strings.TrimSpace(req.Name)
	if req.Name == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "name required"})
		return
	}
	if len(req.Name) > 40 {
		req.Name = req.Name[:40]
	}

	plan, err := GetPlan(req.PlanID)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "unknown plan_id"})
		return
	}

	now := time.Now().Unix()
	expiresAt := req.ExpiresAt
	if expiresAt == 0 {
		days := req.DurationDays
		if days <= 0 {
			days = plan.DurationDays
		}
		expiresAt = now + int64(days)*86400
	}
	if expiresAt <= now {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "expires_at in the past"})
		return
	}

	key, err := GenerateKey()
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "keygen failed"})
		return
	}
	rec := &KeyRecord{
		Key:       key,
		Name:      req.Name,
		PlanID:    plan.ID,
		PlanName:  plan.Name,
		CreatedAt: now,
		ExpiresAt: expiresAt,
		Status:    "active",
		Source:    "admin",
	}
	if err := StoreKey(r.Context(), a.kv, rec); err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "store failed: " + err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, rec)
}

// HandleAdminRevoke is POST /admin/revoke — body {"key":"..."}.
func (a *App) HandleAdminRevoke(w http.ResponseWriter, r *http.Request) {
	if !a.requireAdmin(r) {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "unauthorized"})
		return
	}
	var req struct {
		Key string `json:"key"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid json"})
		return
	}
	req.Key = strings.ToUpper(strings.TrimSpace(req.Key))
	if req.Key == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "key required"})
		return
	}
	if err := RevokeKey(r.Context(), a.kv, req.Key); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	rec, _ := GetKey(r.Context(), a.kv, req.Key)
	writeJSON(w, http.StatusOK, rec)
}

// HandleAdminList is GET /admin/keys.json — returns all keys, newest first.
func (a *App) HandleAdminList(w http.ResponseWriter, r *http.Request) {
	if !a.requireAdmin(r) {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "unauthorized"})
		return
	}
	keys, err := ListKeys(r.Context(), a.kv)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	sort.Slice(keys, func(i, j int) bool { return keys[i].CreatedAt > keys[j].CreatedAt })
	writeJSON(w, http.StatusOK, keys)
}
