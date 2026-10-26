// language: Go, file: backend/main.go, target: Linux (Docker), Go 1.22
package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/realximanta/Pluck/backend/internal/api"
	"github.com/realximanta/Pluck/backend/internal/config"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	app := api.NewApp(cfg)

	mux := http.NewServeMux()

	mux.HandleFunc("HEAD /health", handleHealthHead)
	mux.HandleFunc("GET /health", handleHealthGet)

	mux.HandleFunc("POST /api/qr", app.HandleQR)
	mux.HandleFunc("POST /api/verify", app.HandleVerify)
	mux.HandleFunc("GET /api/history", app.HandleHistory)
	mux.HandleFunc("POST /api/resolve", app.HandleResolve)
	mux.HandleFunc("POST /api/fetch", app.HandleFetch)
	mux.HandleFunc("GET /api/plans", app.HandlePublicPlans)

	mux.HandleFunc("GET /admin", app.HandleAdminPage)
	mux.HandleFunc("GET /admin/keys.json", app.HandleAdminList)
	mux.HandleFunc("POST /admin/keys", app.HandleAdminCreate)
	mux.HandleFunc("POST /admin/revoke", app.HandleAdminRevoke)

	mux.HandleFunc("GET /{$}", app.HandleLandingPage)

	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           api.Wrap(mux, cfg),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      120 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	go func() {
		log.Printf("pluck-server listening on :%s", cfg.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("listen: %v", err)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop

	log.Println("shutting down...")
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Printf("shutdown error: %v", err)
	}
	log.Println("stopped")
}

func handleHealthHead(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
}

func handleHealthGet(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"alive"}`))
}
