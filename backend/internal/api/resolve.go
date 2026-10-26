// language: Go, file: backend/internal/api/resolve.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os/exec"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

const resolveTimeout = 20 * time.Second

var urlSchemeRE = regexp.MustCompile(`^https?://`)

// ResolveRequest is the body of POST /api/resolve.
type ResolveRequest struct {
	URL string `json:"url"`
}

// Format is one selectable option returned to the app.
type Format struct {
	FormatID  string `json:"format_id"`
	Quality   string `json:"quality"`
	Ext       string `json:"ext"`
	SizeBytes int64  `json:"size_bytes,omitempty"`
	Width     int    `json:"width,omitempty"`
	Height    int    `json:"height,omitempty"`
	Bitrate   int    `json:"bitrate,omitempty"`
	HasVideo  bool   `json:"has_video"`
	HasAudio  bool   `json:"has_audio"`
	Note      string `json:"note,omitempty"`
}

// ResolveResponse is what the app receives.
type ResolveResponse struct {
	URL         string   `json:"url"`
	Platform    string   `json:"platform"`
	Title       string   `json:"title"`
	Thumbnail   string   `json:"thumbnail,omitempty"`
	DurationSec int      `json:"duration_sec,omitempty"`
	Uploader    string   `json:"uploader,omitempty"`
	Video       []Format `json:"video,omitempty"`
	Audio       []Format `json:"audio,omitempty"`
	Image       []Format `json:"image,omitempty"`
}

// ytdlpDump is the subset of yt-dlp's --dump-single-json we actually use.
type ytdlpDump struct {
	Title       string        `json:"title"`
	Thumbnail   string        `json:"thumbnail"`
	Duration    float64       `json:"duration"`
	Extractor   string        `json:"extractor"`
	ExtractorKey string       `json:"extractor_key"`
	Uploader    string        `json:"uploader"`
	Formats     []ytdlpFormat `json:"formats"`
	URL         string        `json:"url"`
	Ext         string        `json:"ext"`
	Width       int           `json:"width"`
	Height      int           `json:"height"`
}

type ytdlpFormat struct {
	FormatID   string  `json:"format_id"`
	FormatNote string  `json:"format_note"`
	Ext        string  `json:"ext"`
	Height     int     `json:"height"`
	Width      int     `json:"width"`
	VCodec     string  `json:"vcodec"`
	ACodec     string  `json:"acodec"`
	Filesize   int64   `json:"filesize"`
	FilesizeApprox int64 `json:"filesize_approx"`
	TBR        float64 `json:"tbr"`
	ABR        float64 `json:"abr"`
	Protocol   string  `json:"protocol"`
}

// HandleResolve is POST /api/resolve.
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

	dump, err := runYtdlpDump(r.Context(), req.URL)
	if err != nil {
		writeJSON(w, http.StatusBadGateway, map[string]string{"error": "resolve failed: " + err.Error()})
		return
	}

	resp := buildResolveResponse(req.URL, dump)
	writeJSON(w, http.StatusOK, resp)
}

// runYtdlpDump runs yt-dlp --dump-single-json with a hard timeout.
func runYtdlpDump(parent context.Context, mediaURL string) (*ytdlpDump, error) {
	ctx, cancel := context.WithTimeout(parent, resolveTimeout)
	defer cancel()

	cmd := exec.CommandContext(ctx, "yt-dlp",
		"--dump-single-json",
		"--no-warnings",
		"--no-playlist",
		"--no-check-certificates",
		"--socket-timeout", "10",
		"--",
		mediaURL,
	)

	out, err := cmd.Output()
	if err != nil {
		if ctx.Err() == context.DeadlineExceeded {
			return nil, fmt.Errorf("timeout after %s", resolveTimeout)
		}
		if ee, ok := err.(*exec.ExitError); ok {
			return nil, fmt.Errorf("yt-dlp: %s", truncate(string(ee.Stderr), 300))
		}
		return nil, err
	}

	var dump ytdlpDump
	if err := json.Unmarshal(out, &dump); err != nil {
		return nil, fmt.Errorf("parse json: %w", err)
	}
	return &dump, nil
}

// buildResolveResponse categorizes formats into video/audio/image for the app.
func buildResolveResponse(originalURL string, d *ytdlpDump) ResolveResponse {
	resp := ResolveResponse{
		URL:         originalURL,
		Platform:    detectPlatform(d.Extractor, d.ExtractorKey, originalURL),
		Title:       d.Title,
		Thumbnail:   d.Thumbnail,
		DurationSec: int(d.Duration),
		Uploader:    d.Uploader,
	}

	// Some single-image URLs return no formats array — synthesize from top-level.
	if len(d.Formats) == 0 {
		if d.URL != "" {
			f := Format{
				FormatID:  "default",
				Ext:       d.Ext,
				Width:     d.Width,
				Height:    d.Height,
				HasVideo:  false,
				HasAudio:  false,
				Quality:   fmt.Sprintf("%dx%d", d.Width, d.Height),
			}
			if isImageExt(d.Ext) {
				resp.Image = append(resp.Image, f)
			} else {
				resp.Video = append(resp.Video, f)
			}
		}
		return resp
	}

	for _, f := range d.Formats {
		hasV := f.VCodec != "" && f.VCodec != "none"
		hasA := f.ACodec != "" && f.ACodec != "none"
		size := f.Filesize
		if size == 0 {
			size = f.FilesizeApprox
		}
		base := Format{
			FormatID:  f.FormatID,
			Ext:       f.Ext,
			SizeBytes: size,
			Width:     f.Width,
			Height:    f.Height,
			HasVideo:  hasV,
			HasAudio:  hasA,
		}

		switch {
		case isImageExt(f.Ext) && !hasV && !hasA:
			base.Quality = fmt.Sprintf("%dx%d", f.Width, f.Height)
			resp.Image = append(resp.Image, base)

		case hasV:
			base.Quality = qualityLabel(f.Height, f.FormatNote)
			base.Bitrate = int(f.TBR)
			if hasA {
				base.Note = "muxed"
			} else {
				base.Note = "video-only (merge with audio)"
			}
			resp.Video = append(resp.Video, base)

		case hasA:
			base.Quality = audioLabel(f.ABR, f.FormatNote)
			base.Bitrate = int(f.ABR)
			base.Note = "audio-only"
			resp.Audio = append(resp.Audio, base)
		}
	}

	// Sort video by height descending — highest quality first in the app picker.
	sort.Slice(resp.Video, func(i, j int) bool {
		return resp.Video[i].Height > resp.Video[j].Height
	})
	sort.Slice(resp.Audio, func(i, j int) bool {
		return resp.Audio[i].Bitrate > resp.Audio[j].Bitrate
	})
	sort.Slice(resp.Image, func(i, j int) bool {
		return (resp.Image[i].Width * resp.Image[i].Height) >
			(resp.Image[j].Width * resp.Image[j].Height)
	})

	return resp
}

func qualityLabel(height int, note string) string {
	if height > 0 {
		return strconv.Itoa(height) + "p"
	}
	if note != "" {
		return note
	}
	return "unknown"
}

func audioLabel(abr float64, note string) string {
	if abr > 0 {
		return strconv.Itoa(int(abr)) + "kbps"
	}
	if note != "" {
		return note
	}
	return "audio"
}

func isImageExt(ext string) bool {
	switch strings.ToLower(ext) {
	case "jpg", "jpeg", "png", "webp", "gif", "heic":
		return true
	}
	return false
}

func detectPlatform(extractor, extractorKey, rawURL string) string {
	src := strings.ToLower(extractorKey + " " + extractor)
	switch {
	case strings.Contains(src, "youtube"):
		return "youtube"
	case strings.Contains(src, "instagram"):
		return "instagram"
	case strings.Contains(src, "tiktok"):
		return "tiktok"
	case strings.Contains(src, "facebook"):
		return "facebook"
	case strings.Contains(src, "twitter") || strings.Contains(src, "x.com"):
		return "twitter"
	case strings.Contains(src, "pinterest"):
		return "pinterest"
	case strings.Contains(src, "reddit"):
		return "reddit"
	case strings.Contains(src, "vimeo"):
		return "vimeo"
	case strings.Contains(src, "twitch"):
		return "twitch"
	}
	if strings.Contains(rawURL, "instagram.com") {
		return "instagram"
	}
	if strings.Contains(rawURL, "tiktok.com") {
		return "tiktok"
	}
	return "web"
}
