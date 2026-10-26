// language: Go, file: backend/internal/api/helpers.go, target: Linux (Docker), Go 1.22
package api

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}
