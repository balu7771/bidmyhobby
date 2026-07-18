#!/bin/bash

# ============================================================
# build-oci-images.sh
# Builds all Docker images for linux/amd64 (OCI server target)
# and packages them into a single tar.gz for transfer.
# Usage: ./build-oci-images.sh
# ============================================================

set -e

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

TARGET_PLATFORM="linux/amd64"
TARBALL="bidmyhobby-oci-images_$(date +%d%b_%H%M).tar.gz"
IMAGES=(
  "bidmyhobby-v2-catalog-service:latest"
  "bidmyhobby-v2-auction-service:latest"
  "bidmyhobby-v2-hobby-ai-service:latest"
  "bidmyhobby-v2-frontend:latest"
  "nginx:alpine"
)

echo "========================================================"
echo "🔨 Step 1: Build backend JARs (Maven)"
echo "========================================================"
./scripts/build-jars.sh

echo ""
echo "========================================================"
echo "🐳 Step 2: Build Docker images for $TARGET_PLATFORM"
echo "========================================================"

# Ensure buildx builder with docker-container driver exists
# (required for cross-platform --load to work on Apple Silicon)
if docker buildx inspect oci-builder &>/dev/null; then
  echo "Removing existing oci-builder to ensure clean state..."
  docker buildx rm oci-builder
fi
echo "Creating buildx builder 'oci-builder'..."
docker buildx create --name oci-builder --driver docker-container --use
docker buildx inspect --bootstrap

echo ""
echo "--- Building catalog-service ---"
docker buildx build \
  --platform "$TARGET_PLATFORM" \
  --load \
  -t bidmyhobby-v2-catalog-service:latest \
  ./services/catalog

echo ""
echo "--- Building auction-service ---"
docker buildx build \
  --platform "$TARGET_PLATFORM" \
  --load \
  -t bidmyhobby-v2-auction-service:latest \
  ./services/auction

echo ""
echo "--- Building hobby-ai-service ---"
docker buildx build \
  --platform "$TARGET_PLATFORM" \
  --load \
  -t bidmyhobby-v2-hobby-ai-service:latest \
  ./services/hobby-ai

echo ""
echo "--- Building frontend ---"
docker buildx build \
  --platform "$TARGET_PLATFORM" \
  --load \
  -t bidmyhobby-v2-frontend:latest \
  ./apps/web

echo ""
echo "--- Pulling nginx:alpine for $TARGET_PLATFORM ---"
docker pull --platform "$TARGET_PLATFORM" nginx:alpine

echo ""
echo "========================================================"
echo "📦 Step 3: Packaging images → $TARBALL"
echo "========================================================"
docker save "${IMAGES[@]}" | gzip > "$TARBALL"

echo ""
echo "========================================================"
echo "✅ Done. Transfer the tarball, docker-compose.oci.yml,"
echo "services/api-gateway/nginx.conf, and your untracked .env to the OCI host."
echo "Then on the OCI instance:"
echo "  docker load -i $TARBALL"
echo "  docker compose -f docker-compose.oci.yml --env-file .env up -d"
echo "========================================================"
