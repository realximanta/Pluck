// language: Go, file: backend/internal/api/json.go, target: Linux (Docker), Go 1.22
package api

import (
	"encoding/base64"
	"encoding/json"
	"log"
	"net/http"
)

// writeJSON sets the content type, status, and encodes v as JSON.
func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if v == nil {
		return
	}
	if err := json.NewEncoder(w).Encode(v); err != nil {
		log.Printf("writeJSON encode: %v", err)
	}
}

// encodeBase64 wraps StdEncoding so qr.go stays readable.
func encodeBase64(b []byte) string {
	return base64.StdEncoding.EncodeToString(b)
}
