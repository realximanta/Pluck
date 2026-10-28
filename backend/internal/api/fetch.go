// language: Go, file: backend/internal/api/fetch.go, target: Linux (Docker), Go 1.22
package api

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"mime"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
)

const (
	fetchTimeout    = 10 * time.Minute
	maxFetchBytes   = 2 * 1024 * 1024 * 1024
	fetchTempPrefix = "pluck-fetch-"
)

var safeFormatRegex = regexp.MustCompile(`^[a-zA-Z0-9+_\-]+$`)

type FetchRequest struct {
	URL         string `json:"url"`
	FormatID    string `json:"format_id,omitempty"`
	DirectURL   string `json:"direct_url,omitempty"`
	MediaType   string `json:"media_type"`
	MergeAudio  bool   `json:"merge_audio,omitempty"`
	Filename    string `json:"filename,omitempty"`
	Title       string `json:"title,omitempty"`
	Platform    string `json:"platform,omitempty"`
	Thumbnail   string `json:"thumbnail,omitempty"`
	Quality     string `json:"quality,omitempty"`
	Ext         string `json:"ext,omitempty"`
	DurationSec int    `json:"duration_sec,omitempty"`
}

type countingWriter struct {
	w io.Writer
	n int64
}

func (c *countingWriter) Write(p []byte) (int, error) {
	n, err := c.w.Write(p)
	c.n += int64(n)
	return n, err
}

func (a *App) HandleFetch(w http.ResponseWriter, r *http.Request) {
	appKey, err := a.requireAppKey(r)
	if err != nil {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": err.Error()})
		return
	}

	var req FetchRequest
	if err := json.NewDecoder(io.LimitReader(r.Body, 64*1024)).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid json"})
		return
	}
	req.URL = strings.TrimSpace(req.URL)
	req.DirectURL = strings.TrimSpace(req.DirectURL)
	req.FormatID = strings.TrimSpace(req.FormatID)
	req.MediaType = strings.ToLower(strings.TrimSpace(req.MediaType))
	req.Ext = strings.TrimPrefix(strings.ToLower(strings.TrimSpace(req.Ext)), ".")

	if req.FormatID != "" && !safeFormatRegex.MatchString(req.FormatID) {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid format_id"})
		return
	}

	if req.URL == "" && req.DirectURL == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "url or direct_url required"})
		return
	}
	if req.MediaType == "" {
		req.MediaType = "other"
	}

	ctx, cancel := context.WithTimeout(r.Context(), fetchTimeout)
	defer cancel()
	if rc := http.NewResponseController(w); rc != nil {
		_ = rc.SetWriteDeadline(time.Now().Add(fetchTimeout))
		_ = rc.SetReadDeadline(time.Now().Add(fetchTimeout))
	}

	cw := &countingWriter{w: w}

	if req.FormatID != "" {
		a.streamYtdlp(ctx, w, cw, &req, appKey)
	} else if req.DirectURL != "" {
		a.streamDirect(ctx, w, cw, &req, appKey)
	} else {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "no format_id or direct_url provided"})
	}
}

// safeDialContext validates the IP at connection time to prevent DNS rebinding.
func safeDialContext(ctx context.Context, network, addr string) (net.Conn, error) {
	host, port, err := net.SplitHostPort(addr)
	if err != nil {
		return nil, err
	}
	ips, err := net.DefaultResolver.LookupIP(ctx, "ip", host)
	if err != nil {
		return nil, err
	}
	for _, ip := range ips {
		// SECURITY: Block unspecified (0.0.0.0), loopback, private, and link-local IPs
		if ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() || ip.IsUnspecified() {
			continue 
		}
		return (&net.Dialer{Timeout: 10 * time.Second}).DialContext(ctx, network, net.JoinHostPort(ip.String(), port))
	}
	return nil, fmt.Errorf("no safe IPs found for %s", host)
}

func (a *App) streamDirect(ctx context.Context, w http.ResponseWriter, cw *countingWriter, req *FetchRequest, appKey string) {
	// SECURITY: TLS verification is enabled (InsecureSkipVerify removed) to prevent MITM.
	safeClient := &http.Client{
		Timeout: 30 * time.Second,
		Transport: &http.Transport{
			DialContext: safeDialContext,
		},
	}

	reqHTTP, err := http.NewRequestWithContext(ctx, http.MethodGet, req.DirectURL, nil)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	reqHTTP.Header.Set("User-Agent", browserUA)
	if req.URL != "" {
		reqHTTP.Header.Set("Referer", req.URL)
	}

	resp, err := safeClient.Do(reqHTTP)
	if err != nil {
		writeJSON(w, http.StatusBadGateway, map[string]string{"error": "upstream: " + err.Error()})
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		writeJSON(w, http.StatusBadGateway, map[string]string{"error": fmt.Sprintf("upstream http %d", resp.StatusCode)})
		return
	}

	ct := resp.Header.Get("Content-Type")
	if strings.Contains(strings.ToLower(ct), "text/html") && req.MediaType != "text" && req.MediaType != "code" {
		writeJSON(w, http.StatusBadGateway, map[string]string{"error": "upstream returned html page, not a file"})
		return
	}

	filename := chooseFilename(req, resp.Header.Get("Content-Disposition"), req.DirectURL)
	contentType := ct
	if contentType == "" {
		contentType = mimeForExt(extFor(req, filename))
	}

	size := resp.ContentLength
	if size <= 0 || size > maxFetchBytes {
		size = -1
	}

	setDownloadHeaders(w, filename, contentType, size)
	w.WriteHeader(http.StatusOK)

	if err := copyBounded(ctx, cw, resp.Body, maxFetchBytes); err != nil {
		log.Printf("fetch direct copy error: %v", err)
		return
	}

	saveFetchHistory(a, appKey, req, cw.n, filename)
}

func (a *App) streamYtdlp(ctx context.Context, w http.ResponseWriter, cw *countingWriter, req *FetchRequest, appKey string) {
	format := req.FormatID
	useMerge := req.MergeAudio || strings.Contains(strings.ToLower(req.FormatID), "+")

	filename := chooseFilename(req, "", req.URL)
	if filename == "download" || filepath.Ext(filename) == "" {
		ext := req.Ext
		if ext == "" {
			ext = extForMediaType(req.MediaType)
		}
		if ext != "" {
			base := filename
			if base == "" || base == "download" {
				base = "media"
			}
			filename = base + "." + ext
		}
	}
	contentType := mimeForExt(extFor(req, filename))
	if contentType == "" {
		contentType = "application/octet-stream"
	}

	if useMerge {
		a.streamYtdlpMerged(ctx, w, cw, req, appKey, format, filename, contentType)
		return
	}

	args := []string{
		"--no-warnings", "--no-playlist", "--no-check-certificates",
		"--socket-timeout", "10", "--no-progress",
		"--extractor-args", "youtube:player_client=default,tv,web",
		"-f", format,
		"-o", "-",
		"--", req.URL,
	}

	cmd := exec.CommandContext(ctx, "yt-dlp", args...)
	var stderr bytes.Buffer
	cmd.Stderr = &stderr

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "yt-dlp pipe: " + err.Error()})
		return
	}
	if err := cmd.Start(); err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "yt-dlp start: " + err.Error()})
		return
	}

	setDownloadHeaders(w, filename, contentType, -1)
	w.WriteHeader(http.StatusOK)

	copyErr := copyBounded(ctx, cw, stdout, maxFetchBytes)
	waitErr := cmd.Wait()

	if copyErr != nil {
		log.Printf("fetch ytdlp copy error: %v (stderr: %s)", copyErr, truncate(stderr.String(), 300))
		return
	}
	if waitErr != nil {
		log.Printf("fetch ytdlp exit: %v (stderr: %s)", waitErr, truncate(stderr.String(), 300))
		return
	}

	saveFetchHistory(a, appKey, req, cw.n, filename)
}

func (a *App) streamYtdlpMerged(ctx context.Context, w http.ResponseWriter, cw *countingWriter, req *FetchRequest, appKey, format, filename, contentType string) {
	if !strings.Contains(format, "+") {
		format = format + "+bestaudio/best"
	}

	tmp, err := os.CreateTemp("", fetchTempPrefix+"*.mp4")
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "temp: " + err.Error()})
		return
	}
	tmpName := tmp.Name()
	tmp.Close()
	defer os.Remove(tmpName)

	args := []string{
		"--no-warnings", "--no-playlist", "--no-check-certificates",
		"--socket-timeout", "10", "--no-progress",
		"--extractor-args", "youtube:player_client=default,tv,web",
		"--merge-output-format", "mp4",
		"-f", format,
		"-o", tmpName,
		"--", req.URL,
	}
	cmd := exec.CommandContext(ctx, "yt-dlp", args...)
	var stderr bytes.Buffer
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		writeJSON(w, http.StatusBadGateway, map[string]string{
			"error": "yt-dlp merge: " + truncate(stderr.String(), 300),
		})
		return
	}

	f, err := os.Open(tmpName)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "open temp: " + err.Error()})
		return
	}
	defer f.Close()

	st, err := f.Stat()
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "stat temp: " + err.Error()})
		return
	}
	size := st.Size()
	if size == 0 {
		writeJSON(w, http.StatusBadGateway, map[string]string{"error": "yt-dlp produced 0 byte file"})
		return
	}
	if size > maxFetchBytes {
		writeJSON(w, http.StatusRequestEntityTooLarge, map[string]string{"error": "file exceeds 2 GB limit"})
		return
	}
	if filepath.Ext(filename) == "" {
		filename = strings.TrimSuffix(filename, ".") + ".mp4"
		contentType = "video/mp4"
	}

	setDownloadHeaders(w, filename, contentType, size)
	w.WriteHeader(http.StatusOK)

	if err := copyBounded(ctx, cw, f, maxFetchBytes); err != nil {
		log.Printf("fetch merged copy error: %v", err)
		return
	}

	saveFetchHistory(a, appKey, req, cw.n, filename)
}

func tempSuffix(req *FetchRequest) string {
	ext := req.Ext
	if ext == "" {
		ext = "mp4"
	}
	return "." + ext
}

func copyBounded(ctx context.Context, dst io.Writer, src io.Reader, max int64) error {
	limited := io.LimitReader(src, max+1)
	n, err := io.Copy(dst, limited)
	if err != nil {
		return err
	}
	if n > max {
		return fmt.Errorf("download exceeded %d byte cap", max)
	}
	return ctx.Err()
}

func setDownloadHeaders(w http.ResponseWriter, filename, contentType string, size int64) {
	if contentType == "" {
		contentType = "application/octet-stream"
	}
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Content-Disposition", dispositionHeader(filename))
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Cache-Control", "no-store")
	if size > 0 {
		w.Header().Set("Content-Length", strconv.FormatInt(size, 10))
	}
}

func dispositionHeader(filename string) string {
	safe := sanitizeFilename(filename)
	escaped := url.PathEscape(safe)
	return fmt.Sprintf(`attachment; filename="%s"; filename*=UTF-8''%s`, safe, escaped)
}

func sanitizeFilename(s string) string {
	s = strings.ReplaceAll(s, `"`, "")
	s = strings.ReplaceAll(s, "\r", "")
	s = strings.ReplaceAll(s, "\n", "")
	s = strings.ReplaceAll(s, "\\", "_")
	s = strings.TrimSpace(s)
	if s == "" {
		s = "download"
	}
	if len(s) > 200 {
		ext := filepath.Ext(s)
		s = s[:200-len(ext)] + ext
	}
	return s
}

func chooseFilename(req *FetchRequest, cd, fallbackURL string) string {
	if req.Filename != "" {
		return sanitizeFilename(req.Filename)
	}
	if cd != "" {
		if name := filenameFromDisposition(cd); name != "" {
			return sanitizeFilename(name)
		}
	}
	if name := filenameFromURLPath(fallbackURL); name != "" {
		return sanitizeFilename(name)
	}
	if req.Title != "" {
		return sanitizeFilename(req.Title)
	}
	return "download"
}

func extFor(req *FetchRequest, filename string) string {
	if req.Ext != "" {
		return req.Ext
	}
	ext := strings.TrimPrefix(strings.ToLower(filepath.Ext(filename)), ".")
	if ext != "" {
		return ext
	}
	return extForMediaType(req.MediaType)
}

func extForMediaType(mediaType string) string {
	switch mediaType {
	case "video":
		return "mp4"
	case "audio":
		return "m4a"
	case "image":
		return "jpg"
	case "archive":
		return "zip"
	case "document":
		return "pdf"
	case "code":
		return "txt"
	case "text":
		return "txt"
	}
	return "bin"
}

func mimeForExt(ext string) string {
	if ext == "" {
		return ""
	}
	return mime.TypeByExtension("." + strings.TrimPrefix(ext, "."))
}

func saveFetchHistory(a *App, appKey string, req *FetchRequest, size int64, filename string) {
	if size <= 0 {
		return
	}
	entry := &HistoryEntry{
		URL:         req.URL,
		Platform:    req.Platform,
		Title:       firstNonEmpty(req.Title, filename),
		Thumbnail:   req.Thumbnail,
		MediaType:   req.MediaType,
		Quality:     req.Quality,
		SizeBytes:   size,
		DurationSec: req.DurationSec,
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := SaveHistoryEntry(ctx, a.kv, appKey, entry); err != nil {
			log.Printf("save history: %v", err)
		}
	}()
}
