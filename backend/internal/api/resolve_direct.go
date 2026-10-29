// language: Go, file: backend/internal/api/resolve_direct.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"fmt"
	"net/http"
	"net/url"
	"path/filepath"
	"strconv"
	"strings"
)

func resolveDirect(ctx context.Context, rawURL string) (*ResolveResponse, error) {
	resp, err := headWithContext(ctx, rawURL)
	if err != nil || resp.StatusCode >= 400 {
		if resp != nil { resp.Body.Close() }
		resp, err = rangeGet(ctx, rawURL)
		if err != nil {
			return nil, fmt.Errorf("direct probe: %w", err)
		}
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("http %d", resp.StatusCode)
	}

	finalURL := rawURL
	if resp.Request != nil && resp.Request.URL != nil {
		finalURL = resp.Request.URL.String()
	}

	ct := resp.Header.Get("Content-Type")
	cd := resp.Header.Get("Content-Disposition")
	cl := resp.Header.Get("Content-Length")

	// Reject HTML directly
	if strings.Contains(strings.ToLower(ct), "text/html") {
		return nil, fmt.Errorf("html page, not a direct file")
	}

	size, _ := strconv.ParseInt(cl, 10, 64)
	filename := filenameFromDisposition(cd)
	if filename == "" {
		filename = filenameFromURLPath(finalURL)
	}
	if filename == "" {
		filename = "download"
	}

	class := classFromExt(filepath.Ext(filename))
	if class == ClassOther {
		class = classFromContentType(ct)
	}

	ext := strings.TrimPrefix(strings.ToLower(filepath.Ext(filename)), ".")
	if ext == "" {
		ext = extFromContentType(ct)
	}

	f := Format{
		FormatID:    "direct",
		Ext:         ext,
		SizeBytes:   size,
		Class:       class,
		Filename:    filename,
		DirectURL:   finalURL,
		ContentType: ct,
		Quality:     humanBytes(size),
	}

	out := &ResolveResponse{
		URL:      rawURL,
		Platform: detectPlatformFromHost(rawURL),
		Title:    filename,
		Method:   "direct",
	}
	appendFormat(out, f)
	return out, nil
}

// ... rest of the file remains identical
