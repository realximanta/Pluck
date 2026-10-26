// language: Go, file: backend/internal/config/config.go, target: Linux (Docker), Go 1.22
package config

import (
	"fmt"
	"os"
	"strings"
)

type Config struct {
	Port string

	CFAccountID     string
	CFAPIToken      string
	CFKVNamespaceID string

	AdminKey string

	AutopayKey      string
	AutopayUPI      string
	AutopayBotName  string
	AutopayVerifyURL string
	AutopayQRURL     string

	PublicBaseURL string
}

// Load reads all env vars, returns every missing one at once.
func Load() (*Config, error) {
	var missing []string

	req := func(name string) string {
		v := strings.TrimSpace(os.Getenv(name))
		if v == "" {
			missing = append(missing, name)
		}
		return v
	}
	opt := func(name, def string) string {
		v := strings.TrimSpace(os.Getenv(name))
		if v == "" {
			return def
		}
		return v
	}

	cfg := &Config{
		Port: opt("PORT", "8080"),

		CFAccountID:     req("CF_ACCOUNT_ID"),
		CFAPIToken:      req("CF_API_TOKEN"),
		CFKVNamespaceID: req("CF_KV_NAMESPACE_ID"),

		AdminKey: req("ADMIN_KEY"),

		AutopayKey:      req("AUTOPAY_KEY"),
		AutopayUPI:      req("AUTOPAY_UPI"),
		AutopayBotName:  req("AUTOPAY_BOT_NAME"),
		AutopayVerifyURL: opt("AUTOPAY_VERIFY_URL"),
		AutopayQRURL:     opt("AUTOPAY_QR_URL"),

		PublicBaseURL: opt("PUBLIC_BASE_URL", ""),
	}

	if len(missing) > 0 {
		return nil, fmt.Errorf("missing required env vars: %s", strings.Join(missing, ", "))
	}
	return cfg, nil
}
