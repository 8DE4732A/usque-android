#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
USQUE_DIR="$SCRIPT_DIR/../third_party/usque"
OUTPUT="$SCRIPT_DIR/../app/libs/usque.aar"

# Resolve Go toolchain: prefer explicit GOROOT, else find go1.25.5 in module cache
if [ -z "${GOROOT:-}" ]; then
  TOOLCHAIN_DIR=~/go/pkg/mod/golang.org/toolchain@v0.0.1-go1.25.5.darwin-arm64
  if [ -x "$TOOLCHAIN_DIR/bin/go" ]; then
    export GOROOT="$TOOLCHAIN_DIR"
    export PATH="$TOOLCHAIN_DIR/bin:$PATH"
  fi
fi

# Add ~/go/bin (gomobile) to PATH if not already present
export PATH="${PATH}:${HOME}/go/bin"

# NDK: use env var if set, else try default location
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  NDK_DIR=~/Library/Android/sdk/ndk
  if [ -d "$NDK_DIR" ]; then
    ANDROID_NDK_HOME="$(ls -d "$NDK_DIR"/* | sort -V | tail -1)"
    export ANDROID_NDK_HOME
  fi
fi

echo "Using Go:    $(go version)"
echo "Using NDK:   ${ANDROID_NDK_HOME:-NOT SET}"
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
