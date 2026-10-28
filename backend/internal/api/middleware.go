// language: Go, file: backend/internal/api/middleware.go, target: Linux (Docker), Go 1.22
package api

import (
	"log"
	"math"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/realximanta/Pluck/backend/internal/config"
)

// --- Rate limiter ---------------------------------------------------------

type rateBucket struct {
	tokens   float64
	lastFill time.Time
}

type rateLimiter struct {
	mu      sync.Mutex
	buckets map[string]*rateBucket
	lastGC  time.Time
}

var globalLimiter = newRateLimiter()

func newRateLimiter() *rateLimiter {
	rl := &rateLimiter{
		buckets: make(map[string]*rateBucket),
		lastGC:  time.Now(),
	}
	go rl.gcLoop()
	return rl
}

func (rl *rateLimiter) gcLoop() {
	t := time.NewTicker(5 * time.Minute)
	defer t.Stop()
	for range t.C {
		rl.mu.Lock()
		cutoff := time.Now().Add(-10 * time.Minute)
		for k, b := range rl.buckets {
			if b.lastFill.Before(cutoff) {
				delete(rl.buckets, k)
			}
		}
		rl.lastGC = time.Now()
		rl.mu.Unlock()
	}
}

func (rl *rateLimiter) allow(key string, limit int) bool {
	if limit <= 0 {
		return true
	}
	rl.mu.Lock()
	defer rl.mu.Unlock()

	now := time.Now()
	b, ok := rl.buckets[key]
	if !ok {
		b = &rateBucket{tokens: float64(limit), lastFill: now}
		rl.buckets[key] = b
	}
	elapsed := now.Sub(b.lastFill).Seconds()
	if elapsed > 0 {
		b.tokens = math.Min(float64(limit), b.tokens+elapsed*float64(limit)/60.0)
		b.lastFill = now
	}
	if b.tokens < 1 {
		return false
	}
	b.tokens -= 1
	return true
}

func ratePolicy(r *http.Request) (int, string) {
	path := r.URL.Path
	ip := clientIP(r)

	switch {
	case path == "/health":
		return 0, ""
	case path == "/api/qr":
		return 10, "ip:" + ip
	case path == "/api/verify":
		return 20, "ip:" + ip
	case path == "/api/resolve", path == "/api/fetch", path == "/api/history":
		if k := appKeyFromRequest(r); k != "" {
			return perKeyLimit(path), "key:" + k
		}
		return perKeyLimit(path), "ip:" + ip
	case strings.HasPrefix(path, "/admin"):
		return 30, "ip:" + ip
	default:
		return 120, "ip:" + ip
	}
}

func perKeyLimit(path string) int {
	switch path {
	case "/api/fetch":
		return 20
	case "/api/resolve":
		return 60
	case "/api/history":
		return 60
	}
	return 60
}

func Wrap(next http.Handler, cfg *config.Config) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()

		// SECURITY: Strict CSP, allowing Cloudflare Turnstile scripts and frames.
		h := w.Header()
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Referrer-Policy", "strict-origin-when-cross-origin")
		h.Set("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com https://unpkg.com https://challenges.cloudflare.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; connect-src 'self' https://api.github.com https://challenges.cloudflare.com; frame-src 'self' https://challenges.cloudflare.com; img-src 'self' data: https:;")

		setCORS(w, r, cfg)
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}

		limit, ident := ratePolicy(r)
		if limit > 0 && !globalLimiter.allow(ident, limit) {
			writeJSON(w, http.StatusTooManyRequests, map[string]string{
				"error": "rate_limited", "retry_after_seconds": "60",
			})
			logRequest(r, http.StatusTooManyRequests, time.Since(start))
			return
		}

		sw := &statusWriter{ResponseWriter: w, status: 200}
		next.ServeHTTP(sw, r)
		logRequest(r, sw.status, time.Since(start))
	})
}

type statusWriter struct {
	http.ResponseWriter
	status int
	wrote  bool
}

func (s *statusWriter) WriteHeader(code int) {
	if !s.wrote {
		s.status = code
		s.wrote = true
	}
	s.ResponseWriter.WriteHeader(code)
}

func (s *statusWriter) Write(b []byte) (int, error) {
	if !s.wrote {
		s.wrote = true
	}
	return s.ResponseWriter.Write(b)
}

func (s *statusWriter) Unwrap() http.ResponseWriter {
	return s.ResponseWriter
}

func logRequest(r *http.Request, status int, dur time.Duration) {
	if r.URL.Path == "/health" {
		return
	}
	key := ""
	if k := appKeyFromRequest(r); k != "" {
		if len(k) > 3 {
			key = " key=" + k[:3] + "…"
		} else {
			key = " key=" + k
		}
	}
	log.Printf("%s %s -> %d %dms ip=%s%s", r.Method, r.URL.Path, status, dur.Milliseconds(), clientIP(r), key)
}

func appKeyFromRequest(r *http.Request) string {
	if k := r.Header.Get("X-App-Key"); k != "" {
		return strings.ToUpper(strings.TrimSpace(k))
	}
	auth := r.Header.Get("Authorization")
	if strings.HasPrefix(auth, "Bearer ") {
		return strings.ToUpper(strings.TrimSpace(strings.TrimPrefix(auth, "Bearer ")))
	}
	return ""
}

func clientIP(r *http.Request) string {
	if ip := r.Header.Get("CF-Connecting-IP"); ip != "" {
		return strings.TrimSpace(ip)
	}
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		return strings.TrimSpace(parts[len(parts)-1])
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

func setCORS(w http.ResponseWriter, r *http.Request, cfg *config.Config) {
	origin := r.Header.Get("Origin")
	if origin == "" {
		return
	}
	allowed := originAllowed(origin, cfg)
	if !allowed {
		return
	}
	h := w.Header()
	h.Set("Access-Control-Allow-Origin", origin)
	h.Set("Vary", "Origin")
	h.Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
	h.Set("Access-Control-Allow-Headers", "Content-Type, X-App-Key, X-Admin-Key, Authorization")
	h.Set("Access-Control-Max-Age", "86400")
}

func originAllowed(origin string, cfg *config.Config) bool {
	if cfg.PublicBaseURL != "" {
		base := strings.TrimRight(cfg.PublicBaseURL, "/")
		if strings.HasPrefix(origin, base) {
			return true
		}
	}
	if strings.HasPrefix(origin, "https://realximanta.github.io") {
		return true
	}
	if strings.HasPrefix(origin, "http://localhost") || strings.HasPrefix(origin, "http://127.0.0.1") {
		return true
	}
	return false
}
