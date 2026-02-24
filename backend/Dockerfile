# ============================================================
# SHE-SHIELD Backend – Dockerfile
# ============================================================
# Multi-stage build for a lightweight production image.
# Runs on Google Cloud Run (PORT env var set automatically).
# ============================================================

# ---------- Stage 1: build / install dependencies ----------
FROM python:3.13-slim AS builder

WORKDIR /app

# Install build-time system deps (for compiled wheels if needed)
RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# ---------- Stage 2: production image ----------
FROM python:3.13-slim

# Security: run as non-root
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Copy installed packages from builder
COPY --from=builder /install /usr/local

# Copy application code
COPY main.py models.py ./

# Cloud Run provides $PORT (default 8080)
ENV PORT=8080

# Expose the port
EXPOSE ${PORT}

# Switch to non-root user
USER appuser

# Start uvicorn – use exec form so signals propagate correctly
CMD exec uvicorn main:app \
    --host 0.0.0.0 \
    --port ${PORT} \
    --workers 1 \
    --log-level info
