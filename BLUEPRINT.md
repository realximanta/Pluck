# Project Architectural Blueprint & Memory

## 🎯 Core Concept
This document serves as the definitive architectural memory for building a secure, API-driven mobile application. The system consists of a native Android client, a containerized Go backend, a web-based landing/admin portal, and a payment-gateway integration. Access to the backend's heavy-processing endpoints is strictly gated behind paid, time-limited API keys.

## 🏗️ Tech Stack & Infrastructure
- **Mobile Client:** Android (Kotlin, Jetpack Compose, Material 3, OkHttp, Kotlinx-Serialization).
- **Backend:** Go (Standard Library `net/http`, Dockerized).
- **Database:** Cloudflare Workers KV (Serverless, eventually consistent key-value store).
- **Hosting:** Render (Docker Web Service on the free/paid tier, auto-deploying from `main`).
- **Web Portal:** Go-served static HTML using Tailwind CSS via CDN, protected by Cloudflare Turnstile.
- **CI/CD:** GitHub Actions.

## 🔄 The Authentication & Payment Flow
1. **Web Portal:** User selects a plan. A Cloudflare Turnstile CAPTCHA validates the request.
2. **QR Generation:** The Go backend calls an external Payment API to generate a UPI QR code. The API returns a randomized decimal "fingerprint" (e.g., ₹10.07 instead of ₹10) to uniquely identify the transaction.
3. **Verification:** The user pays. The frontend polls the backend. The backend calls the Payment API to verify the exact decimal fingerprint.
4. **Key Issuance:** Upon success, the backend generates a 10-character alphanumeric API key (excluding `O/0/I/1` for readability), stores it in Cloudflare KV with an expiration timestamp, and returns it to the user.
5. **App Access:** The user logs into the Android app with this key. The app stores it securely in DataStore and attaches it as an `X-App-Key` header to all API requests.

## 🛡️ Security Hardening Rules
- **SSRF Protection:** Custom `DialContext` in Go that resolves domains and blocks connections to Loopback, Private, Link-Local, and Unspecified (`0.0.0.0`) IPs to prevent DNS rebinding.
- **Command Injection Prevention:** Strict regex validation (`^[a-zA-Z0-9+_\-]+$`) on any user-supplied arguments passed to subprocesses (e.g., `exec.Command`).
- **CORS & CSP:** Strict Content-Security-Policy allowing only required CDNs (Tailwind, Google Fonts, Cloudflare) and exact CORS origin matching.
- **Admin Auth:** Admin panel uses an HttpOnly, Secure, SameSite=Strict cookie. Admin keys are never exposed to frontend JS.
- **App Bypass:** Web CAPTCHAs are bypassed for the mobile app via a custom `X-Client-Type: app` header.

## ⚙️ Backend Infrastructure (Go + Docker)
- **Dockerfile (Multi-stage):** 
  - Stage 1: `golang:1.22-bookworm` compiles the static binary.
  - Stage 2: `debian:bookworm-slim` installs `ffmpeg`, `python3-pip`, `deno` (crucial for modern `yt-dlp` extraction), and `yt-dlp[default]`.
- **API Client:** A shared `ApiClient` struct in Go that wraps `http.Client`. Uses a `downloadClient` with a 15-minute timeout for streaming heavy files, and a standard `client` with a 60-second timeout for JSON APIs.
- **Streaming:** Heavy media streams directly from the Go backend to the Android app using `io.Copy` with a `countingWriter` to track bytes and enforce maximum size limits.

## 🚀 CI/CD & Release Automation
- **Release Trigger:** Publishing a GitHub Release (e.g., `v1.0.0`) triggers the workflow.
- **Version Bumping:** A script extracts the tag, increments the `versionCode` in `app/build.gradle.kts`, updates the `versionName`, and pushes a commit back to `main` as `github-actions[bot]`. (Idempotency check: if the `versionName` already matches the tag, skip the bump).
- **Keystore Vault:** A separate private GitHub repo acts as a secret vault. A `workflow_dispatch` action decodes the base64 keystore and outputs it as a downloadable artifact for manual injection into the app's secrets.
- **APK Build:** The workflow restores the keystore from secrets, injects the production `API_BASE_URL` via Gradle properties, builds the signed release APK, and attaches it to the GitHub Release.
- **Metadata Sync:** The workflow generates a `latest.json` file and commits it to the repo, which the web landing page fetches via `raw.githubusercontent.com` to display the latest download link.

## 📱 Android Client Rules
- **Storage:** Do not save files to `Android/data/`. Use the `MediaStore` API to write to public directories (`Movies/`, `Music/`, `Pictures/`).
- **Cleartext Traffic:** Disabled globally. Exceptions made only for `10.0.2.2` (emulator localhost).
- **File Handling:** When receiving a download, stream directly to a temp file in `context.cacheDir`, then copy to the `MediaStore` using `FileOutputStream`. Clean up temp files immediately.
- **UI/UX:** Do not use the default Android ActionBar. Use `Theme.Material.NoActionBar` and `enableEdgeToEdge()` for a seamless, custom Compose UI. 

## 📝 General Instructions for the AI
- When writing Go code, prefer the standard library. Avoid heavy frameworks like Gin or Echo unless strictly necessary.
- When writing Android code, prefer Compose and StateFlow. Keep ViewModels clean of Android UI dependencies.
- Always assume an API-first architecture. Every action should have a corresponding JSON endpoint.
- Never hardcode secrets. Always assume they are injected via environment variables (Render) or GitHub Secrets.
