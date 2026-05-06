#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
USQUE_DIR="$SCRIPT_DIR/../third_party/usque"
OUTPUT="$SCRIPT_DIR/../app/libs/usque.aar"

echo "Building usque.aar from $USQUE_DIR"
cd "$USQUE_DIR"

gomobile bind \
  -target=android/arm64,android/arm,android/amd64 \
  -androidapi 24 \
  -trimpath \
  -ldflags="-s -w" \
  -o "$OUTPUT" \
  ./mobile

echo "Built: $OUTPUT"
