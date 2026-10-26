# Pluck - Media Downloader App

Pull any media from anywhere. Open source Android app with self-hostable backend.

## Features
- 50+ platforms (YouTube, Instagram, TikTok, cloud drives)
- Multiple qualities (144p → 4K)
- All file types (video, audio, image, PDF, zip)
- Offline history tracking

## Quick Start

### Backend (Docker)
```bash
cd backend
docker build -t pluck-server .
docker run -p 8080:8080 \
  -e CF_ACCOUNT_ID=xxx \
  -e CF_API_TOKEN=xxx \
  -e CF_KV_NAMESPACE_ID=xxx \
  -e ADMIN_KEY=xxx \
  pluck-server
```
### Android App
Download APK from [Releases](https://github.com/realximanta/Pluck/releases)

## Architecture
```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Android App │────▶│ Go Backend  │────▶│ Cloudflare  │
│  (Kotlin)   │     │  (Docker)   │     │     KV      │
└─────────────┘     └─────────────┘     └─────────────┘
                           │
                    ┌──────┴──────┐
                    ▼             ▼
              ┌─────────┐   ┌─────────┐
              │ yt-dlp  │   │ Direct  │
              │ resolver│   │  HTTP   │
              └─────────┘   └─────────┘
```

## Environment Variables
| Variable | Required | Description |
|----------|----------|-------------|
| `CF_ACCOUNT_ID` | ✅ | Cloudflare account ID |
| `CF_API_TOKEN` | ✅ | KV API token |
| `ADMIN_KEY` | ✅ | Admin panel password |
| ... | ... | ... |

## License
MIT
