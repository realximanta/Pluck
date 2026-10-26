// language: Go, file: backend/internal/api/landing.go, target: Linux (Docker), Go 1.22
package api

import (
	_ "embed"
	"net/http"
)

//go:embed landing.html
var landingHTML string

// HandleLandingPage is GET /{$} — the product page.
func (a *App) HandleLandingPage(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "public, max-age=300")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(landingHTML))
}

// HandlePublicPlans is GET /api/plans — public catalog for the landing page.
func (a *App) HandlePublicPlans(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, Plans)
}
