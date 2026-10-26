// language: Go, file: backend/internal/api/resolve.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"strings"
)

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

type ResolveRequest struct {
	URL string `json:"url"`
}

var urlSchemeRE = regexp.MustCompile(`^https?://`)

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

func (a *App) resolveURL(ctx context.Context, u *url.URL) (*ResolveResponse, error) {
	host := strings.ToLower(u.Hostname())

	if resp, matched, err := a.resolveCloud(ctx, u, host); matched {
		if err == nil && !emptyResolve(resp) {
			return resp, nil
		}
	}

	if isMediaSiteHost(host) {
		if resp, err := resolveViaYtdlp(ctx, u.String()); err == nil && !emptyResolve(resp) {
			return resp, nil
		}
	}

	if looksLikeFileURL(u) {
		if resp, err := resolveDirect(ctx, u.String()); err == nil {
			return resp, nil
		}
	}

	if resp, err := resolveViaYtdlp(ctx, u.String()); err == nil && !emptyResolve(resp) {
		return resp, nil
	}

	if resp, err := resolveHTML(ctx, u.String()); err == nil && !emptyResolve(resp) {
		return resp, nil
	}

	if resp, err := resolveDirect(ctx, u.String()); err == nil {
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
