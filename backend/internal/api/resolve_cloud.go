// language: Go, file: backend/internal/api/resolve_cloud.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/url"
	"regexp"
	"strings"
)

// resolveCloud dispatches host-matched cloud platforms to their specific resolvers.
// Returns (resp, matched, err). matched=false means "not a cloud host we handle".
func (a *App) resolveCloud(ctx context.Context, u *url.URL, host string) (*ResolveResponse, bool, error) {
	switch {
	case strings.Contains(host, "dropbox.com") || strings.Contains(host, "dropboxusercontent.com"):
		r, err := resolveDropbox(ctx, u)
		return r, true, err
	case strings.Contains(host, "drive.google.com") || strings.Contains(host, "docs.google.com"):
		r, err := resolveGDrive(ctx, u)
		return r, true, err
	case strings.Contains(host, "mediafire.com"):
		r, err := resolveMediaFire(ctx, u)
		return r, true, err
	case strings.Contains(host, "terabox.com") || strings.Contains(host, "1024terabox.com") ||
		strings.Contains(host, "teraboxapp.com") || strings.Contains(host, "4funbox.com"):
		r, err := resolveTerabox(ctx, u)
		return r, true, err
	case strings.Contains(host, "mega.nz") || strings.Contains(host, "mega.co.nz"):
		r, err := resolveMega(ctx, u)
		return r, true, err
	case strings.Contains(host, "onedrive.live.com") || strings.Contains(host, "1drv.ms") ||
		strings.Contains(host, "sharepoint.com"):
		r, err := resolveOneDrive(ctx, u)
		return r, true, err
	case strings.Contains(host, "wetransfer.com"):
		r, err := resolveWeTransfer(ctx, u)
		return r, true, err
	case strings.Contains(host, "box.com") || strings.Contains(host, "app.box.com"):
		r, err := resolveBox(ctx, u)
		return r, true, err
	}
	return nil, false, nil
}

// --- Dropbox --------------------------------------------------------------

func resolveDropbox(ctx context.Context, u *url.URL) (*ResolveResponse, error) {
	// Try yt-dlp first — it understands Dropbox shared links.
	if resp, err := resolveViaYtdlp(ctx, u.String()); err == nil && !emptyResolve(resp) {
		return resp, nil
	}
	// Rewrite to force direct download.
	direct := *u
	q := direct.Query()
	q.Set("dl", "1")
	direct.RawQuery = q.Encode()
	resp, err := resolveDirect(ctx, direct.String())
	if err != nil {
		return nil, fmt.Errorf("dropbox: %w", err)
	}
	resp.Platform = "dropbox"
	resp.Method = "cloud:dropbox"
	return resp, nil
}

// --- Google Drive ---------------------------------------------------------

func resolveGDrive(ctx context.Context, u *url.URL) (*ResolveResponse, error) {
	// yt-dlp handles GDrive including the large-file confirm token dance.
	if resp, err := resolveViaYtdlp(ctx, u.String()); err == nil && !emptyResolve(resp) {
		resp.Platform = "gdrive"
		resp.Method = "cloud:gdrive"
		return resp, nil
	}
	// Extract id → uc?export=download
	id := gdriveID(u)
	if id == "" {
		return nil, fmt.Errorf("gdrive: could not extract file id")
	}
	direct := "https://drive.google.com/uc?export=download&id=" + id
	resp, err := resolveDirect(ctx, direct)
	if err != nil {
		return nil, fmt.Errorf("gdrive: %w", err)
	}
	resp.Platform = "gdrive"
	resp.Method = "cloud:gdrive"
	return resp, nil
}

var gdriveIDPathRE = regexp.MustCompile(`/file/d/([a-zA-Z0-9_-]+)`)

func gdriveID(u *url.URL) string {
	if m := gdriveIDPathRE.FindStringSubmatch(u.Path); len(m) > 1 {
		return m[1]
	}
	if id := u.Query().Get("id"); id != "" {
		return id
	}
	return ""
}

// --- MediaFire ------------------------------------------------------------

func resolveMediaFire(ctx context.Context, u *url.URL) (*ResolveResponse, error) {
	if resp, err := resolveViaYtdlp(ctx, u.String()); err == nil && !emptyResolve(resp) {
		resp.Platform = "mediafire"
		resp.Method = "cloud:mediafire"
		return resp, nil
	}
	// Scrape downloadButton
	resp, err := getWithContext(ctx, u.String())
	if err != nil {
		return nil, fmt.Errorf("mediafire: %w", err)
	}
	defer resp.Body.Close()
	body, _ := readAllLimited(resp.Body, 2<<20)

	re := regexp.MustCompile(`(?is)id=["']downloadButton["'][^>]*?href=["']([^"']+)["']`)
	m := re.FindSubmatch(body)
	if len(m) < 2 {
		re2 := regexp.MustCompile(`(?is)href=["']([^"']*?download[^"']*?)["']`)
		m = re2.FindSubmatch(body)
	}
	if len(m) < 2 {
		return nil, fmt.Errorf("mediafire: download link not found")
	}
	dl := string(m[1])
	dlResp, err := resolveDirect(ctx, dl)
	if err != nil {
		return nil, fmt.Errorf("mediafire direct: %w", err)
	}
	dlResp.Platform = "mediafire"
	dlResp.Method = "cloud:mediafire"
	return dlResp, nil
}

// --- Terabox --------------------------------------------------------------

func resolveTerabox(ctx context.Context, u *url.URL) (*ResolveResponse, error) {
	if resp, err := resolveViaYtdlp(ctx, u.String()); err == nil && !emptyResolve(resp) {
		resp.Platform = "terabox"
		resp.Method = "cloud:terabox"
		return resp, nil
	}
	surl := teraboxSURL(u)
	if surl == "" {
		return nil, fmt.Errorf("terabox: no surl found in URL")
	}
	apiURL := "https://terabox.com/api/shorturlinfo?app_id=250528&shorturl=" + url.QueryEscape(surl) + "&root=1"
	resp, err := getWithContext(ctx, apiURL)
	if err != nil {
		return nil, fmt.Errorf("terabox api: %w", err)
	}
	defer resp.Body.Close()
	body, _ := readAllLimited(resp.Body, 4<<20)

	var parsed struct {
		Errno int `json:"errno"`
		List  []struct {
			Filename string `json:"server_filename"`
			Size     int64  `json:"size"`
			Dlink    string `json:"dlink"`
			Isdir    int    `json:"isdir"`
		} `json:"list"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, fmt.Errorf("terabox json: %w", err)
	}
	if parsed.Errno != 0 {
		return nil, fmt.Errorf("terabox: errno %d", parsed.Errno)
	}

	out := &ResolveResponse{
		URL:      u.String(),
		Platform: "terabox",
		Title:    surl,
		Method:   "cloud:terabox",
	}
	for _, item := range parsed.List {
		if item.Isdir == 1 || item.Dlink == "" {
			continue
		}
		f := Format{
			FormatID:  item.Filename,
			Filename:  item.Filename,
			SizeBytes: item.Size,
			Quality:   humanBytes(item.Size),
			DirectURL: item.Dlink,
			Ext:       strings.TrimPrefix(strings.ToLower(pathExt(item.Filename)), "."),
			Class:     classFromExt(pathExt(item.Filename)),
		}
		appendFormat(out, f)
	}
	if emptyResolve(out) {
		return nil, fmt.Errorf("terabox: no downloadable files in shared folder")
	}
	return out, nil
}

var teraboxPathRE = regexp.MustCompile(`^/s/([a-zA-Z0-9_-]+)`)

func teraboxSURL(u *url.URL) string {
	if m := teraboxPathRE.FindStringSubmatch(u.Path); len(m) > 1 {
		return m[1]
	}
	if s := u.Query().Get("surl"); s != "" {
		return strings.TrimPrefix(s, "/")
	}
	return ""
}

func pathExt(name string) string {
	if i := strings.LastIndex(name, "."); i >= 0 {
		return name[i:]
	}
	return ""
}

// --- MEGA -----------------------------------------------------------------

func resolveMega(ctx context.Context, u *url.URL) (*ResolveResponse, error) {
	// MEGA files are AES-encrypted client-side with a key embedded in the URL
	// fragment (#<key>) or the user's browser. yt-dlp does not support MEGA.
	// Full support requires AES-128-CTR + CBC-MAC decryption (~500 LOC) or
	// mega-cmd in the image. Return a clear message the app can surface.
	return nil, fmt.Errorf("mega.nz: files are AES-encrypted client-side and are not supported by the server-side resolver. Open the link in a browser and use MEGA's official download (or the MEGAsync app) — the backend cannot decrypt without the on-page session key")
}

// --- OneDrive -------------------------------------------------------------

func resolveOneDrive(ctx context.Context, u *url.URL) (*ResolveResponse, error) {
	if resp, err := resolveViaYtdlp(ctx, u.String()); err == nil && !emptyResolve(resp) {
		resp.Platform = "onedrive"
		resp.Method = "cloud:onedrive"
		return resp, nil
	}
	// Follow redirect (1drv.ms → onedrive.live.com) then probe.
	resp, err := getWithContext(ctx, u.String())
	if err != nil {
		return nil, fmt.Errorf("onedrive: %w", err)
	}
	finalURL := u.String()
	if resp.Request != nil && resp.Request.URL != nil {
		finalURL = resp.Request.URL.String()
	}
	resp.Body.Close()

	// Append ?download=1 on the final URL, or use the /download endpoint.
	dlURL := finalURL
	if strings.Contains(dlURL, "?") {
		dlURL += "&download=1"
	} else {
		dlURL += "?download=1"
	}

	direct, err := resolveDirect(ctx, dlURL)
	if err != nil {
		return nil, fmt.Errorf("onedrive direct: %w", err)
	}
	direct.Platform = "onedrive"
	direct.Method = "cloud:onedrive"
	return direct, nil
}

// --- WeTransfer -----------------------------------------------------------

func resolveWeTransfer(ctx context.Context, u *url.URL) (*ResolveResponse, error) {
	// WeTransfer's public links need an API dance. Best effort — parse id and hit
	// the download endpoint. Falls back to a clear message if it fails.
	parts := strings.Split(strings.Trim(u.Path, "/"), "/")
	if len(parts) < 2 || parts[0] != "downloads" {
		return nil, fmt.Errorf("wetransfer: unsupported URL format")
	}
	id := parts[1]
	apiURL := "https://wetransfer.com/api/v4/transfers/" + id + "/download"
	req, err := newRequest(ctx, "POST", apiURL)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := resolveClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("wetransfer api: %w", err)
	}
	defer resp.Body.Close()
	body, _ := readAllLimited(resp.Body, 2<<20)

	var parsed struct {
		DirectLink string `json:"direct_link"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil || parsed.DirectLink == "" {
		return nil, fmt.Errorf("wetransfer: no direct link returned (link may be expired or require a password)")
	}

	direct, err := resolveDirect(ctx, parsed.DirectLink)
	if err != nil {
		return nil, fmt.Errorf("wetransfer direct: %w", err)
	}
	direct.Platform = "wetransfer"
	direct.Method = "cloud:wetransfer"
	return direct, nil
}

// --- Box ------------------------------------------------------------------

func resolveBox(ctx context.Context, u *url.URL) (*ResolveResponse, error) {
	// /s/<share_id> → direct download via /index.php?rm=box_download_shared_file&shared_name=<id>
	// Also try yt-dlp.
	if resp, err := resolveViaYtdlp(ctx, u.String()); err == nil && !emptyResolve(resp) {
		resp.Platform = "box"
		resp.Method = "cloud:box"
		return resp, nil
	}
	m := regexp.MustCompile(`^/s/([a-zA-Z0-9]+)`).FindStringSubmatch(u.Path)
	if len(m) < 2 {
		return nil, fmt.Errorf("box: could not extract share id")
	}
	dlURL := "https://app.box.com/index.php?rm=box_download_shared_file&shared_name=" + m[1]
	direct, err := resolveDirect(ctx, dlURL)
	if err != nil {
		return nil, fmt.Errorf("box direct: %w", err)
	}
	direct.Platform = "box"
	direct.Method = "cloud:box"
	return direct, nil
}

// referenced import marker — keeps io used even if future edits remove a read.
var _ = io.Discard
