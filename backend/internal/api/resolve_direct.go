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
		if resp != nil {
			resp.Body.Close()
		}
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

func rangeGet(ctx context.Context, rawURL string) (*http.Response, error) {
	req, err := newRequest(ctx, http.MethodGet, rawURL)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Range", "bytes=0-0")
	return resolveClient.Do(req)
}

func appendFormat(r *ResolveResponse, f Format) {
	switch f.Class {
	case ClassVideo:
		r.Video = append(r.Video, f)
	case ClassAudio:
		r.Audio = append(r.Audio, f)
	case ClassImage:
		r.Image = append(r.Image, f)
	case ClassArchive:
		r.Archive = append(r.Archive, f)
	case ClassDocument:
		r.Document = append(r.Document, f)
	case ClassCode:
		r.Code = append(r.Code, f)
	case ClassText:
		r.Text = append(r.Text, f)
	default:
		r.Other = append(r.Other, f)
	}
}

func looksLikeFileURL(u *url.URL) bool {
	ext := strings.TrimPrefix(strings.ToLower(filepath.Ext(u.Path)), ".")
	if ext == "" {
		return false
	}
	_, ok := extClassMap[ext]
	return ok
}

var extClassMap = map[string]FileClass{
	"mp4": ClassVideo, "mkv": ClassVideo, "avi": ClassVideo, "mov": ClassVideo,
	"webm": ClassVideo, "flv": ClassVideo, "wmv": ClassVideo, "m4v": ClassVideo,
	"3gp": ClassVideo, "mpg": ClassVideo, "mpeg": ClassVideo, "ts": ClassVideo,
	"m2ts": ClassVideo, "mts": ClassVideo, "ogv": ClassVideo, "rmvb": ClassVideo,
	"rm": ClassVideo, "divx": ClassVideo, "asf": ClassVideo,

	"mp3": ClassAudio, "m4a": ClassAudio, "wav": ClassAudio, "flac": ClassAudio,
	"ogg": ClassAudio, "opus": ClassAudio, "aac": ClassAudio, "wma": ClassAudio,
	"m4b": ClassAudio, "aiff": ClassAudio, "alac": ClassAudio, "ape": ClassAudio,

	"jpg": ClassImage, "jpeg": ClassImage, "png": ClassImage, "gif": ClassImage,
	"webp": ClassImage, "bmp": ClassImage, "svg": ClassImage, "ico": ClassImage,
	"heic": ClassImage, "avif": ClassImage, "tiff": ClassImage, "tif": ClassImage,

	"zip": ClassArchive, "tar": ClassArchive, "gz": ClassArchive, "tgz": ClassArchive,
	"bz2": ClassArchive, "xz": ClassArchive, "7z": ClassArchive, "rar": ClassArchive,
	"zst": ClassArchive, "lz4": ClassArchive, "zipx": ClassArchive, "cab": ClassArchive,
	"iso": ClassArchive, "dmg": ClassArchive, "jar": ClassArchive, "apk": ClassArchive,
	"xapk": ClassArchive, "ipa": ClassArchive, "deb": ClassArchive, "rpm": ClassArchive,

	"pdf": ClassDocument, "doc": ClassDocument, "docx": ClassDocument,
	"xls": ClassDocument, "xlsx": ClassDocument, "ppt": ClassDocument,
	"pptx": ClassDocument, "odt": ClassDocument, "ods": ClassDocument,
	"odp": ClassDocument, "rtf": ClassDocument, "epub": ClassDocument,
	"mobi": ClassDocument, "azw3": ClassDocument, "djvu": ClassDocument,

	"py": ClassCode, "js": ClassCode, "ts": ClassCode, "go": ClassCode,
	"rs": ClassCode, "c": ClassCode, "cpp": ClassCode, "h": ClassCode,
	"hpp": ClassCode, "java": ClassCode, "kt": ClassCode, "swift": ClassCode,
	"rb": ClassCode, "php": ClassCode, "cs": ClassCode, "sh": ClassCode,
	"sql": ClassCode, "html": ClassCode, "css": ClassCode, "scss": ClassCode,
	"jsx": ClassCode, "tsx": ClassCode, "vue": ClassCode, "lua": ClassCode,
	"pl": ClassCode, "r": ClassCode, "dart": ClassCode, "ex": ClassCode,

	"txt": ClassText, "md": ClassText, "json": ClassText, "xml": ClassText,
	"yaml": ClassText, "yml": ClassText, "toml": ClassText, "ini": ClassText,
	"csv": ClassText, "tsv": ClassText, "log": ClassText, "conf": ClassText,
	"cfg": ClassText, "env": ClassText, "srt": ClassText, "vtt": ClassText,
}

func classFromExt(ext string) FileClass {
	ext = strings.TrimPrefix(strings.ToLower(ext), ".")
	if c, ok := extClassMap[ext]; ok {
		return c
	}
	return ClassOther
}

func classFromContentType(ct string) FileClass {
	ct = strings.ToLower(strings.Split(ct, ";")[0])
	ct = strings.TrimSpace(ct)
	switch {
	case strings.HasPrefix(ct, "video/"):
		return ClassVideo
	case strings.HasPrefix(ct, "audio/"):
		return ClassAudio
	case strings.HasPrefix(ct, "image/"):
		return ClassImage
	case strings.HasPrefix(ct, "text/html"), strings.HasPrefix(ct, "text/plain"),
		strings.HasPrefix(ct, "text/xml"), strings.HasPrefix(ct, "application/json"):
		return ClassText
	case strings.HasPrefix(ct, "text/"):
		return ClassText
	case strings.Contains(ct, "zip"), strings.Contains(ct, "compressed"),
		strings.Contains(ct, "rar"), strings.Contains(ct, "7z"),
		strings.Contains(ct, "x-tar"), strings.Contains(ct, "gzip"),
		strings.Contains(ct, "bzip2"), strings.Contains(ct, "x-xz"):
		return ClassArchive
	case strings.Contains(ct, "pdf"), strings.Contains(ct, "msword"),
		strings.Contains(ct, "officedocument"), strings.Contains(ct, "epub"):
		return ClassDocument
	}
	return ClassOther
}

func extFromContentType(ct string) string {
	ct = strings.ToLower(strings.Split(ct, ";")[0])
	switch ct {
	case "video/mp4":
		return "mp4"
	case "video/webm":
		return "webm"
	case "video/quicktime":
		return "mov"
	case "audio/mpeg":
		return "mp3"
	case "audio/mp4":
		return "m4a"
	case "audio/ogg":
		return "ogg"
	case "image/jpeg":
		return "jpg"
	case "image/png":
		return "png"
	case "image/gif":
		return "gif"
	case "image/webp":
		return "webp"
	case "application/pdf":
		return "pdf"
	case "application/zip":
		return "zip"
	case "application/x-tar":
		return "tar"
	}
	return ""
}

func filenameFromDisposition(cd string) string {
	if cd == "" {
		return ""
	}
	lower := strings.ToLower(cd)
	if i := strings.Index(lower, "filename*=utf-8''"); i >= 0 {
		s := cd[i+len("filename*=utf-8''"):]
		if j := strings.IndexAny(s, ";"); j >= 0 {
			s = s[:j]
		}
		s = strings.TrimSpace(s)
		if dec, err := url.PathUnescape(s); err == nil {
			return dec
		}
		return s
	}
	if i := strings.Index(lower, "filename="); i >= 0 {
		s := cd[i+len("filename="):]
		if j := strings.IndexAny(s, ";"); j >= 0 {
			s = s[:j]
		}
		s = strings.TrimSpace(s)
		s = strings.Trim(s, `"'`)
		return s
	}
	return ""
}

func filenameFromURLPath(rawURL string) string {
	u, err := url.Parse(rawURL)
	if err != nil {
		return ""
	}
	base := filepath.Base(u.Path)
	if base == "." || base == "/" {
		return ""
	}
	return base
}

func humanBytes(n int64) string {
	if n <= 0 {
		return ""
	}
	const unit = 1024
	if n < unit {
		return strconv.FormatInt(n, 10) + " B"
	}
	div, exp := int64(unit), 0
	for m := n / unit; m >= unit; m /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f %cB", float64(n)/float64(div), "KMGTPE"[exp])
}

func detectPlatformFromHost(rawURL string) string {
	u, err := url.Parse(rawURL)
	if err != nil {
		return "web"
	}
	host := strings.ToLower(u.Hostname())
	for _, s := range []string{
		"youtube.com", "youtu.be",
		"instagram.com",
		"tiktok.com",
		"facebook.com", "fb.com",
		"twitter.com", "x.com",
		"pinterest.com",
		"reddit.com",
		"vimeo.com",
		"twitch.tv",
		"dropbox.com",
		"drive.google.com", "docs.google.com",
		"mediafire.com",
		"terabox.com",
		"mega.nz",
		"onedrive.live.com", "1drv.ms",
		"wetransfer.com",
		"box.com",
	} {
		if host == s || strings.HasSuffix(host, "."+s) {
			parts := strings.SplitN(s, ".", 2)
			return parts[0]
		}
	}
	return "web"
}
