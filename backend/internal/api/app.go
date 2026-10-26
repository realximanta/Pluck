// language: Go, file: backend/internal/api/app.go, target: Linux (Docker), Go 1.22
package api

import (
	"github.com/realximanta/Pluck/backend/internal/config"
	"github.com/realximanta/Pluck/backend/internal/kv"
)

// App is the shared dependency holder every handler method hangs off.
type App struct {
	cfg *config.Config
	kv  *kv.Client
}

// NewApp constructs the App with a live KV client built from cfg.
func NewApp(cfg *config.Config) *App {
	return &App{
		cfg: cfg,
		kv:  kv.New(cfg.CFAccountID, cfg.CFKVNamespaceID, cfg.CFAPIToken),
	}
}

func (a *App) Config() *config.Config { return a.cfg }
func (a *App) KV() *kv.Client         { return a.kv }
