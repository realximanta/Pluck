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

	AutopayKey       string
	AutopayUPI       string
	AutopayBotName   string
	AutopayVerifyURL string
	AutopayQRURL     string

	TurnstileSecretKey string

	PublicBaseURL string
}

func get(name string) string {
	return strings.TrimSpace(os.Getenv(name))
}

func getOr(name, def string) string {
	v := get(name)
	if v == "" {
		return def
	}
	return v
}

func Load() (*Config, error) {
	var missing []string

	require := func(name string) string {
		v := get(name)
		if v == "" {
			missing = append(missing, name)
		}
		return v
	}

	cfg := &Config{
		Port: getOr("PORT", "8080"),

		CFAccountID:     require("CF_ACCOUNT_ID"),
		CFAPIToken:      require("CF_API_TOKEN"),
		CFKVNamespaceID: require("CF_KV_NAMESPACE_ID"),

		AdminKey: require("ADMIN_KEY"),

		AutopayKey:       require("AUTOPAY_KEY"),
		AutopayUPI:       require("AUTOPAY_UPI"),
		AutopayBotName:   require("AUTOPAY_BOT_NAME"),
		AutopayVerifyURL: getOr("AUTOPAY_VERIFY_URL", "https://auto-payment.ximanta.xyz/api/verify"),
		AutopayQRURL:     getOr("AUTOPAY_QR_URL", "https://qr-api-vercel.vercel.app/qr"),

		TurnstileSecretKey: get("TURNSTILE_SECRET_KEY"),

		PublicBaseURL: getOr("PUBLIC_BASE_URL", ""),
	}

	if len(missing) > 0 {
		return nil, fmt.Errorf("missing required env vars: %s", strings.Join(missing, ", "))
	}
	return cfg, nil
}
