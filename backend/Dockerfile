# ---- Stage 1: build the Go binary ----
FROM golang:1.22-bookworm AS builder

WORKDIR /build

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /build/pluck .

# ---- Stage 2: runtime image ----
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    python3 \
    python3-pip \
    ffmpeg \
    unzip \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Deno (JS runtime for yt-dlp YouTube bypass)
RUN curl -fsSL https://deno.land/install.sh | sh
ENV DENO_INSTALL="/root/.deno"
ENV PATH="$DENO_INSTALL/bin:$PATH"

# Install yt-dlp with default extras (includes curl_cffi for Cloudflare bypass)
RUN pip3 install --break-system-packages --no-cache-dir "yt-dlp[default]"

WORKDIR /app
COPY --from=builder /build/pluck /app/pluck

EXPOSE 8080
CMD ["/app/pluck"]
