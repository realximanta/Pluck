// language: Go, file: backend/internal/api/resolve_ytdlp.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"encoding/json"
	"fmt"
	"os/exec"
	"sort"
	"strconv"
	"strings"
	"time"
)

const ytdlpResolveTimeout = 25 * time.Second

type ytdlpDump struct {
	Title        string        `json:"title"`
	Thumbnail    string        `json:"thumbnail"`
	Duration     float64       `json:"duration"`
	Extractor    string        `json:"extractor"`
	ExtractorKey string        `json:"extractor_key"`
	Uploader     string        `json:"uploader"`
	Formats      []ytdlpFormat `json:"formats"`
	URL          string        `json:"url"`
	Ext          string        `json:"ext"`
	Width        int           `json:"width"`
	Height       int           `json:"height"`
}

type ytdlpFormat struct {
	FormatID       string  `json:"format_id"`
	FormatNote     string  `json:"format_note"`
	Ext            string  `json:"ext"`
	Height         int     `json:"height"`
	Width          int     `json:"width"`
	VCodec         string  `json:"vcodec"`
	ACodec         string  `json:"acodec"`
	Filesize       int64   `json:"filesize"`
	FilesizeApprox int64   `json:"filesize_approx"`
	TBR            float64 `json:"tbr"`
	ABR            float64 `json:"abr"`
}

// resolveViaYtdlp runs yt-dlp and maps the format list into ResolveResponse.
func resolveViaYtdlp(parent context.Context, mediaURL string) (*ResolveResponse, error) {
	ctx, cancel := context.WithTimeout(parent, ytdlpResolveTimeout)
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
			return nil, fmt.Errorf("yt-dlp timeout after %s", ytdlpResolveTimeout)
		}
		if ee, ok := err.(*exec.ExitError); ok {
			return nil, fmt.Errorf("yt-dlp: %s", truncate(string(ee.Stderr), 300))
		}
		return nil, err
	}

	var d ytdlpDump
	if err := json.Unmarshal(out, &d); err != nil {
		return nil, fmt.Errorf("yt-dlp parse: %w", err)
	}
	return buildYtdlpResponse(mediaURL, &d), nil
}

func buildYtdlpResponse(originalURL string, d *ytdlpDump) *ResolveResponse {
	resp := &ResolveResponse{
		URL:         originalURL,
		Platform:    detectPlatformFromHost(originalURL),
		Title:       d.Title,
		Thumbnail:   d.Thumbnail,
		DurationSec: int(d.Duration),
		Uploader:    d.Uploader,
		Method:      "ytdlp",
	}

	if len(d.Formats) == 0 {
		if d.URL != "" {
			ext := strings.TrimPrefix(strings.ToLower(d.Ext), ".")
			f := Format{
				FormatID:  "default",
				Ext:       ext,
				Width:     d.Width,
				Height:    d.Height,
				DirectURL: d.URL,
				Class:     classFromExt(ext),
				Quality:   fmt.Sprintf("%dx%d", d.Width, d.Height),
			}
			appendFormat(resp, f)
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
			Ext:       strings.TrimPrefix(strings.ToLower(f.Ext), "."),
			SizeBytes: size,
			Width:     f.Width,
			Height:    f.Height,
			HasVideo:  hasV,
			HasAudio:  hasA,
			Class:     classFromExt(f.Ext),
		}
		switch {
		case isImageExt(f.Ext) && !hasV && !hasA:
			base.Quality = fmt.Sprintf("%dx%d", f.Width, f.Height)
			base.Class = ClassImage
			resp.Image = append(resp.Image, base)
		case hasV:
			base.Quality = qualityLabel(f.Height, f.FormatNote)
			base.Bitrate = int(f.TBR)
			base.Class = ClassVideo
			if hasA {
				base.Note = "muxed"
			} else {
				base.Note = "video-only (merge with audio)"
			}
			resp.Video = append(resp.Video, base)
		case hasA:
			base.Quality = audioLabel(f.ABR, f.FormatNote)
			base.Bitrate = int(f.ABR)
			base.Class = ClassAudio
			base.Note = "audio-only"
			resp.Audio = append(resp.Audio, base)
		}
	}

	sort.Slice(resp.Video, func(i, j int) bool { return resp.Video[i].Height > resp.Video[j].Height })
	sort.Slice(resp.Audio, func(i, j int) bool { return resp.Audio[i].Bitrate > resp.Audio[j].Bitrate })
	sort.Slice(resp.Image, func(i, j int) bool {
		return (resp.Image[i].Width * resp.Image[i].Height) > (resp.Image[j].Width * resp.Image[j].Height)
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
	switch strings.ToLower(strings.TrimPrefix(ext, ".")) {
	case "jpg", "jpeg", "png", "webp", "gif", "heic", "avif":
		return true
	}
	return false
}
