// language: Go, file: backend/internal/kv/kv.go, target: Linux (Docker), Go 1.22
package kv

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"time"
)

type Client struct {
	accountID   string
	namespaceID string
	apiToken    string
	baseURL     string
	http        *http.Client
}

func New(accountID, namespaceID, apiToken string) *Client {
	return &Client{
		accountID:   accountID,
		namespaceID: namespaceID,
		apiToken:    apiToken,
		baseURL: fmt.Sprintf(
			"https://api.cloudflare.com/client/v4/accounts/%s/storage/kv/namespaces/%s",
			accountID, namespaceID,
		),
		http: &http.Client{Timeout: 15 * time.Second},
	}
}

// Get returns (value, found, error). found=false on 404.
func (c *Client) Get(ctx context.Context, key string) ([]byte, bool, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		c.baseURL+"/values/"+url.PathEscape(key), nil)
	if err != nil {
		return nil, false, err
	}
	req.Header.Set("Authorization", "Bearer "+c.apiToken)

	resp, err := c.http.Do(req)
	if err != nil {
		return nil, false, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return nil, false, nil
	}
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, false, err
	}
	if resp.StatusCode != http.StatusOK {
		return nil, false, fmt.Errorf("kv get %s: %d %s", key, resp.StatusCode, snippet(body))
	}
	return body, true, nil
}

// Put writes value. ttlSeconds <= 0 means no expiration.
func (c *Client) Put(ctx context.Context, key string, value []byte, ttlSeconds int) error {
	u := c.baseURL + "/values/" + url.PathEscape(key)
	if ttlSeconds > 0 {
		u += "?expiration_ttl=" + strconv.Itoa(ttlSeconds)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, u, bytes.NewReader(value))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.apiToken)
	req.Header.Set("Content-Type", "text/plain")

	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("kv put %s: %d %s", key, resp.StatusCode, snippet(body))
	}
	return nil
}

// Delete removes a key. Missing keys are not an error.
func (c *Client) Delete(ctx context.Context, key string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodDelete,
		c.baseURL+"/values/"+url.PathEscape(key), nil)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.apiToken)

	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNotFound {
		return fmt.Errorf("kv delete %s: %d %s", key, resp.StatusCode, snippet(body))
	}
	return nil
}

// ListPrefix returns all keys starting with prefix, following pagination.
func (c *Client) ListPrefix(ctx context.Context, prefix string) ([]string, error) {
	var keys []string
	cursor := ""
	for {
		u := c.baseURL + "/keys?prefix=" + url.QueryEscape(prefix)
		if cursor != "" {
			u += "&cursor=" + url.QueryEscape(cursor)
		}
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
		if err != nil {
			return nil, err
		}
		req.Header.Set("Authorization", "Bearer "+c.apiToken)

		resp, err := c.http.Do(req)
		if err != nil {
			return nil, err
		}
		body, err := io.ReadAll(resp.Body)
		resp.Body.Close()
		if err != nil {
			return nil, err
		}
		if resp.StatusCode != http.StatusOK {
			return nil, fmt.Errorf("kv list %s: %d %s", prefix, resp.StatusCode, snippet(body))
		}

		var page struct {
			Result []struct {
				Name string `json:"name"`
			} `json:"result"`
			ResultInfo struct {
				Cursor string `json:"cursor"`
			} `json:"result_info"`
		}
		if err := json.Unmarshal(body, &page); err != nil {
			return nil, err
		}
		for _, k := range page.Result {
			keys = append(keys, k.Name)
		}
		if page.ResultInfo.Cursor == "" {
			break
		}
		cursor = page.ResultInfo.Cursor
	}
	return keys, nil
}

func snippet(b []byte) string {
	if len(b) > 200 {
		b = b[:200]
	}
	return string(b)
}
