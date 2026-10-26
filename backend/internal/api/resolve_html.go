// language: Go, file: backend/internal/api/resolve_html.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"regexp"
	"strings"
)

var (
	metaTagRE  = regexp.MustCompile(`(?is)<meta\s+[^>]+?/?>`)
	attrRE     = regexp.MustCompile(`(?is)(\w[\w-]*)\s*=\s*(?:"([^"]*)"|'([^']*)')`)
	titleRE    = regexp.MustCompile(`(?is)<title[^>]*>(.*?)</title>`)
	videoTagRE = regexp.MustCompile(`(?is)<video[^>]+src\s*=\s*(?:"([^"]+)"|'([^']+)')`)
	audioTagRE = regexp.MustCompile(`(?is)<audio[^>]+src\s*=\s*(?:"([^"]+)"|'([^']+)')`)
	sourceRE   = regexp.MustCompile(`(?is)<source[^>]+src\s*=\s*(?:"([^"]+)"|'([^']+)')`)
	imgTagRE   = regexp.MustCompile(`(?is)<img[^>]+src\s*=\s*(?:"([^"]+)"|'([^']+)')`)
	anchorRE   = regexp.MustCompile(`(?is)<a[^>]+href\s*=\s*(?:"([^"]+)"|'([^']+)')`)
	jsonLdRE   = regexp.MustCompile(`(?is)<script[^>]+type=["']application/ld\+json["'][^>]*>(.*?)</script>`)
)

// resolveHTML fetches the page and extracts media links from it.
func resolveHTML(ctx context.Context, rawURL string) (*ResolveResponse, error) {
	u, err := url.Parse(rawURL)
	if err != nil {
		return nil, err
	}

	resp, err := getWithContext(ctx, rawURL)
	if err != nil {
		return nil, fmt.Errorf("html fetch: %w", err)
	}
	defer resp.Body.Close()

	body, _ := readAllLimited(resp.Body, 4<<20)
	html := string(body)

	out := &ResolveResponse{
		URL:      rawURL,
		Platform: detectPlatformFromHost(rawURL),
		Method:   "html",
	}

	// Title
	if m := titleRE.FindStringSubmatch(html); len(m) > 1 {
		out.Title = strings.TrimSpace(html2text(m[1]))
	}
	metas := parseMetaTags(html)
	if t := metas["og:title"]; t != "" && out.Title == "" {
		out.Title = t
	}
	if t := metas["og:site_name"]; t != "" && out.Title == "" {
		out.Title = t
	}

	// og:image / twitter:image as thumbnail
	for _, k := range []string{"og:image", "twitter:image", "og:image:secure_url"} {
		if v := metas[k]; v != "" {
			out.Thumbnail = absURL(u, v)
			break
		}
	}

	// og:video / og:video:secure_url / og:audio
	seen := map[string]bool{}
	addURL := func(rawURL, filename, note string) {
		abs := absURL(u, rawURL)
		if abs == "" || seen[abs] {
			return
		}
		seen[abs] = true
		ext := strings.TrimPrefix(strings.ToLower(pathExt(filename)), ".")
		if ext == "" {
			ext = strings.TrimPrefix(strings.ToLower(pathExt(abs)), ".")
		}
		f := Format{
			FormatID:  abs,
			Filename:  filename,
			DirectURL: abs,
			Ext:       ext,
			Class:     classFromExt(ext),
			Quality:   "",
			Note:      note,
		}
		appendFormat(out, f)
	}

	for _, k := range []string{"og:video", "og:video:secure_url", "og:video:url",
		"twitter:player:stream", "og:audio", "og:audio:secure_url"} {
		if v := metas[k]; v != "" {
			addURL(v, "", "meta:"+k)
		}
	}
	if v := metas["og:image"]; v != "" {
		addURL(v, "", "meta:og:image")
	}

	for _, m := range videoTagRE.FindAllStringSubmatch(html, -1) {
		v := firstNonEmpty(m[1], m[2])
		addURL(v, "", "video tag")
	}
	for _, m := range audioTagRE.FindAllStringSubmatch(html, -1) {
		v := firstNonEmpty(m[1], m[2])
		addURL(v, "", "audio tag")
	}
	for _, m := range sourceRE.FindAllStringSubmatch(html, -1) {
		v := firstNonEmpty(m[1], m[2])
		addURL(v, "", "source tag")
	}
	for _, m := range imgTagRE.FindAllStringSubmatch(html, -1) {
		v := firstNonEmpty(m[1], m[2])
		if v == "" || strings.HasPrefix(v, "data:") {
			continue
		}
		addURL(v, "", "img tag")
	}
	// Anchors to known file extensions only — avoids crawling every link on the page.
	for _, m := range anchorRE.FindAllStringSubmatch(html, -1) {
		v := firstNonEmpty(m[1], m[2])
		if v == "" || strings.HasPrefix(v, "#") || strings.HasPrefix(v, "javascript:") {
			continue
		}
		ext := strings.TrimPrefix(strings.ToLower(pathExt(v)), ".")
		if _, ok := extClassMap[ext]; ok {
			addURL(v, "", "link")
		}
	}
	// JSON-LD contentUrl
	for _, m := range jsonLdRE.FindAllStringSubmatch(html, -1) {
		collectJSONLD(m[1], func(rawURL string) {
			addURL(rawURL, "", "json-ld")
		})
	}

	if len(out.Video) == 0 && len(out.Audio) == 0 && len(out.Image) == 0 &&
		len(out.Archive) == 0 && len(out.Document) == 0 && len(out.Code) == 0 &&
		len(out.Text) == 0 && len(out.Other) == 0 {
		return nil, fmt.Errorf("html: no media found on page")
	}
	return out, nil
}

// parseMetaTags extracts property/name → content pairs from meta tags.
func parseMetaTags(html string) map[string]string {
	out := map[string]string{}
	for _, tag := range metaTagRE.FindAllString(html, -1) {
		attrs := map[string]string{}
		for _, a := range attrRE.FindAllStringSubmatch(tag, -1) {
			key := strings.ToLower(a[1])
			val := firstNonEmpty(a[2], a[3])
			attrs[key] = val
		}
		prop := attrs["property"]
		if prop == "" {
			prop = attrs["name"]
		}
		if prop != "" && attrs["content"] != "" {
			out[strings.ToLower(prop)] = attrs["content"]
		}
	}
	return out
}

// collectJSONLD walks the JSON-LD blob looking for contentUrl / url keys.
func collectJSONLD(src string, emit func(string)) {
	var v interface{}
	if err := json.Unmarshal([]byte(src), &v); err != nil {
		return
	}
	var walk func(node interface{})
	walk = func(node interface{}) {
		switch n := node.(type) {
		case map[string]interface{}:
			for k, val := range n {
				lk := strings.ToLower(k)
				if lk == "contenturl" || lk == "url" || lk == "embedurl" {
					if s, ok := val.(string); ok && (strings.HasPrefix(s, "http://") || strings.HasPrefix(s, "https://")) {
						emit(s)
					}
				}
				walk(val)
			}
		case []interface{}:
			for _, item := range n {
				walk(item)
			}
		}
	}
	walk(v)
}

// absURL resolves a possibly-relative URL against the page's base URL.
func absURL(base *url.URL, ref string) string {
	if ref == "" {
		return ""
	}
	if strings.HasPrefix(ref, "http://") || strings.HasPrefix(ref, "https://") {
		return ref
	}
	if strings.HasPrefix(ref, "//") {
		return base.Scheme + ":" + ref
	}
	r, err := url.Parse(ref)
	if err != nil {
		return ""
	}
	return base.ResolveReference(r).String()
}

// html2text — strips HTML tags from a fragment.
var tagStripRE = regexp.MustCompile(`<[^>]+>`)

func html2text(s string) string {
	s = tagStripRE.ReplaceAllString(s, "")
	s = strings.ReplaceAll(s, "&amp;", "&")
	s = strings.ReplaceAll(s, "&lt;", "<")
	s = strings.ReplaceAll(s, "&gt;", ">")
	s = strings.ReplaceAll(s, "&quot;", `"`)
	s = strings.ReplaceAll(s, "&#39;", "'")
	s = strings.ReplaceAll(s, "&nbsp;", " ")
	return s
}

func firstNonEmpty(xs ...string) string {
	for _, s := range xs {
		if s != "" {
			return s
		}
	}
	return ""
}
