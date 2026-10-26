// language: Go, file: backend/internal/api/resolve_http.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"crypto/tls"
	"io"
	"net/http"
	"net/http/cookiejar"
	"time"
)

// resolveClient is shared across all resolve paths. Browser UA + cookie jar
// so cloud platforms don't block the request as a bot.
var resolveClient *http.Client

func init() {
	jar, _ := cookiejar.New(nil)
	resolveClient = &http.Client{
		Timeout: 30 * time.Second,
		Jar:     jar,
		Transport: &http.Transport{
			TLSClientConfig:     &tls.Config{InsecureSkipVerify: true},
			MaxIdleConns:        20,
			MaxIdleConnsPerHost: 4,
			IdleConnTimeout:     60 * time.Second,
		},
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 10 {
				return http.ErrUseLastResponse
			}
			return nil
		},
	}
}

const browserUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

func newRequest(ctx context.Context, method, url string) (*http.Request, error) {
	req, err := http.NewRequestWithContext(ctx, method, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", browserUA)
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	return req, nil
}

// getWithContext does a GET and returns resp — caller must close body.
func getWithContext(ctx context.Context, url string) (*http.Response, error) {
	req, err := newRequest(ctx, http.MethodGet, url)
	if err != nil {
		return nil, err
	}
	return resolveClient.Do(req)
}

// headWithContext does a HEAD request.
func headWithContext(ctx context.Context, url string) (*http.Response, error) {
	req, err := newRequest(ctx, http.MethodHead, url)
	if err != nil {
		return nil, err
	}
	return resolveClient.Do(req)
}

// readAllLimited reads at most n bytes — protects against giant HTML pages.
func readAllLimited(r io.Reader, n int64) ([]byte, error) {
	return io.ReadAll(io.LimitReader(r, n))
}

// asContext converts the interface{} shim back to context.Context.
// (Needed because resolveURL declares a local type to avoid duplicate import.)
func asContext(v interface {
	Deadline() (deadline interface{ IsZero() bool }, ok bool)
	Done() <-chan struct{}
	Err() error
	Value(key interface{}) interface{}
}) context.Context {
	if c, ok := v.(context.Context); ok {
		return c
	}
	return context.Background()
}
