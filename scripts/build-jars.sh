#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

for service in catalog auction hobby-ai; do
  echo "Building services/$service"
  (
    cd "$ROOT_DIR/services/$service"
    ./mvnw clean package -DskipTests
  )
done
