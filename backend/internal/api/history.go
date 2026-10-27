// language: Go, file: backend/internal/api/history.go, target: Linux (Docker), Go 1.22
package api

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/realximanta/Pluck/backend/internal/kv"
)

const (
	historyPrefix = "history:"
	historyTTL    = 90 * 86400
	historyMax    = 100
)

var ErrMissingAppKey = errors.New("missing app key")

type HistoryEntry struct {
	ID          string `json:"id"`
	URL         string `json:"url"`
	Platform    string `json:"platform"`
	Title       string `json:"title"`
	Thumbnail   string `json:"thumbnail,omitempty"`
	MediaType   string `json:"media_type"`
	Quality     string `json:"quality"`
	FetchedAt   int64  `json:"fetched_at"`
	SizeBytes   int64  `json:"size_bytes,omitempty"`
	DurationSec int    `json:"duration_sec,omitempty"`
}

type HistoryResponse struct {
	Entries []HistoryEntry `json:"entries"`
	Count   int            `json:"count"`
	HasMore bool           `json:"has_more"`
}

func (a *App) requireAppKey(r *http.Request) (string, error) {
	key := r.Header.Get("X-App-Key")
	if key == "" {
		auth := r.Header.Get("Authorization")
		if strings.HasPrefix(auth, "Bearer ") {
			key = strings.TrimPrefix(auth, "Bearer ")
		}
	}
	key = strings.ToUpper(strings.TrimSpace(key))
	if key == "" {
		return "", ErrMissingAppKey
	}
	if _, err := ValidateKey(r.Context(), a.kv, key); err != nil {
		return "", err
	}
	return key, nil
}

func SaveHistoryEntry(ctx context.Context, c *kv.Client, appKey string, e *HistoryEntry) error {
	if e.ID == "" {
		e.ID = randomRemark(8)
	}
	if e.FetchedAt == 0 {
		e.FetchedAt = time.Now().Unix()
	}
	data, err := json.Marshal(e)
	if err != nil {
		return err
	}
	key := historyPrefix + appKey + ":" + strconv.FormatInt(e.FetchedAt, 10) + ":" + e.ID
	return c.Put(ctx, key, data, historyTTL)
}

func ListHistory(ctx context.Context, c *kv.Client, appKey string) ([]HistoryEntry, error) {
	names, err := c.ListPrefix(ctx, historyPrefix+appKey+":")
	if err != nil {
		return nil, err
	}
	out := make([]HistoryEntry, 0, len(names))
	for _, name := range names {
		data, found, err := c.Get(ctx, name)
		if err != nil || !found {
			continue
		}
		var e HistoryEntry
		if json.Unmarshal(data, &e) == nil {
			out = append(out, e)
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].FetchedAt > out[j].FetchedAt })
	if len(out) > historyMax {
		out = out[:historyMax]
	}
	return out, nil
}

func DeleteHistoryEntry(ctx context.Context, c *kv.Client, appKey, entryKey string) error {
	return c.Delete(ctx, entryKey)
}

func (a *App) HandleHistory(w http.ResponseWriter, r *http.Request) {
	key, err := a.requireAppKey(r)
	if err != nil {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": err.Error()})
		return
	}
	entries, err := ListHistory(r.Context(), a.kv, key)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, HistoryResponse{
		Entries: entries,
		Count:   len(entries),
		HasMore: len(entries) == historyMax,
	})
}
