// language: Go, file: backend/internal/api/resolve.go, target: Linux (Docker), Go 1.22
package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"strings"
)

// FileClass categorizes the file kind for the app's UI grouping.
type FileClass string

const (
	ClassVideo    FileClass = "video"
	ClassAudio    FileClass = "audio"
	ClassImage    FileClass = "image"
	ClassArchive  FileClass = "archive"
	ClassDocument FileClass = "document"
	ClassCode     FileClass = "code"
	ClassText     FileClass = "text"
	ClassOther    FileClass = "other"
)

// Format is one selectable option — matches the app's picker model.
type Format struct {
	FormatID    string    `json:"format_id"`
	Quality     string    `json:"quality,omitempty"`
	Ext         string    `json:"ext,omitempty"`
	SizeBytes   int64     `json:"size_bytes,omitempty"`
	Width       int       `json:"width,omitempty"`
	Height      int       `json:"height,omitempty"`
	Bitrate     int       `json:"bitrate,omitempty"`
	HasVideo    bool      `json:"has_video"`
	HasAudio    bool      `json:"has_audio"`
	Class       FileClass `json:"class"`
	Filename    string    `json:"filename,omitempty"`
	DirectURL   string    `json:"direct_url,omitempty"`
	ContentType string    `json:"content_type,omitempty"`
	Note        string    `json:"note,omitempty"`
}

// ResolveResponse is the unified shape for every kind of target.
type ResolveResponse struct {
	URL         string   `json:"url"`
	Platform    string   `json:"platform"`
	Title       string   `json:"title"`
	Thumbnail   string   `json:"thumbnail,omitempty"`
	DurationSec int      `json:"duration_sec,omitempty"`
	Uploader    string   `json:"uploader,omitempty"`
	Method      string   `json:"method"`
	Video       []Format `json:"video,omitempty"`
	Audio       []Format `json:"audio,omitempty"`
	Image       []Format `json:"image,omitempty"`
	Archive     []Format `json:"archive,omitempty"`
	Document    []Format `json:"document,omitempty"`
	Code        []Format `json:"code,omitempty"`
	Text        []Format `json:"text,omitempty"`
	Other       []Format `json:"other,omitempty"`
}

// ResolveRequest is the body of POST /api/resolve.
type ResolveRequest struct {
	URL string `json:"url"`
}

var urlSchemeRE = regexp.MustCompile(`^https?://`)

// HandleResolve is POST /api/resolve. Universal — any URL, any file.
func (a *App) HandleResolve(w http.ResponseWriter, r *http.Request) {
	if _, err := a.requireAppKey(r); err != nil {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": err.Error()})
		return
	}
	var req ResolveRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid json"})
		return
	}
	req.URL = strings.TrimSpace(req.URL)
	if req.URL == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "url required"})
		return
	}
	if !urlSchemeRE.MatchString(req.URL) {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "url must start with http:// or https://"})
		return
	}

	u, err := url.Parse(req.URL)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "bad url"})
		return
	}

	resp, err := a.resolveURL(r.Context(), u)
	if err != nil {
		writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
		return
	}
	if resp == nil || emptyResolve(resp) {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "no downloadable content found"})
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

// resolveURL routes the URL through the resolver chain.
func (a *App) resolveURL(ctx interface {
	Deadline() (deadline interface{ IsZero() bool }, ok bool)
	Done() <-chan struct{}
	Err() error
	Value(key interface{}) interface{}
}, u *url.URL) (*ResolveResponse, error) {
	// real ctx — typed shim to allow interface{} pass without import cycle
	c := asContext(ctx)
	host := strings.ToLower(u.Hostname())

	// 1. Cloud platforms — host-matched, specific resolvers first
	if resp, matched, err := a.resolveCloud(c, u, host); matched {
		if err == nil && !emptyResolve(resp) {
			return resp, nil
		}
		// Cloud resolver failed — try yt-dlp as fallback below
	}

	// 2. Media sites yt-dlp definitely handles
	if isMediaSiteHost(host) {
		if resp, err := resolveViaYtdlp(c, u.String()); err == nil && !emptyResolve(resp) {
			return resp, nil
		}
		// fall through to next methods
	}

	// 3. Direct file URL (has known extension in path)
	if looksLikeFileURL(u) {
		if resp, err := resolveDirect(c, u.String()); err == nil {
			return resp, nil
		}
	}

	// 4. yt-dlp fallback — catches platforms yt-dlp knows even if not in our host list
	if resp, err := resolveViaYtdlp(c, u.String()); err == nil && !emptyResolve(resp) {
		return resp, nil
	}

	// 5. HTML page scrape for embedded/linked media
	if resp, err := resolveHTML(c, u.String()); err == nil && !emptyResolve(resp) {
		return resp, nil
	}

	// 6. Last resort — direct probe (works for direct file links without known ext)
	if resp, err := resolveDirect(c, u.String()); err == nil {
		return resp, nil
	}

	return nil, fmt.Errorf("could not resolve %s by any method", u.String())
}

func emptyResolve(r *ResolveResponse) bool {
	if r == nil {
		return true
	}
	total := len(r.Video) + len(r.Audio) + len(r.Image) + len(r.Archive) +
		len(r.Document) + len(r.Code) + len(r.Text) + len(r.Other)
	return total == 0
}

// isMediaSiteHost flags hosts yt-dlp handles natively.
func isMediaSiteHost(host string) bool {
	for _, s := range []string{
		"youtube.com", "youtu.be", "youtube-nocookie.com", "m.youtube.com",
		"instagram.com", "tiktok.com", "vt.tiktok.com", "vm.tiktok.com",
		"facebook.com", "fb.watch", "fb.com", "m.facebook.com",
		"twitter.com", "x.com", "t.co",
		"reddit.com", "redd.it", "v.redd.it",
		"vimeo.com", "dailymotion.com", "dai.ly",
		"twitch.tv", "clips.twitch.tv",
		"soundcloud.com", "bandcamp.com",
		"pinterest.com", "pin.it",
		"snapchat.com", "linkedin.com", "bilibili.com", "b23.tv",
		"rumble.com", "odysee.com", "streamable.com", "imgur.com",
		"tumblr.com", "vk.com", "ok.ru", "weibo.com", "ixigua.com",
		"douyin.com", "kuaishou.com", "xiaohongshu.com", "xhslink.com",
	} {
		if host == s || strings.HasSuffix(host, "."+s) {
			return true
		}
	}
	return false
}
