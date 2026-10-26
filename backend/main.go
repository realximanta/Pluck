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

	"github.com/realximanta/Pluck/backend/internal/config"
	"github.com/realximanta/Pluck/backend/internal/kv"
)

type App struct {
	cfg *config.Config
	kv  *kv.Client
}

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	app := &App{
		cfg: cfg,
		kv:  kv.New(cfg.CFAccountID, cfg.CFKVNamespaceID, cfg.CFAPIToken),
	}

	mux := http.NewServeMux()

	mux.HandleFunc("HEAD /health", app.handleHealthHead)
	mux.HandleFunc("GET /health", app.handleHealthGet)
	mux.HandleFunc("POST /api/qr", app.HandleQR)
	mux.HandleFunc("POST /api/verify", app.HandleVerify)

	mux.HandleFunc("GET /admin", app.HandleAdminPage)
	mux.HandleFunc("GET /admin/keys.json", app.HandleAdminList)
	mux.HandleFunc("POST /admin/keys", app.HandleAdminCreate)
	mux.HandleFunc("POST /admin/revoke", app.HandleAdminRevoke)

	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           mux,
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

func (a *App) handleHealthHead(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
}

func (a *App) handleHealthGet(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"alive"}`))
}
