#!/bin/bash
# Build the small AppKit/SwiftUI process that renders the desktop widget.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/macos/desktop-widget"
APP="$SRC/BanbiaoDesktopWidget.app"
MACOS="$APP/Contents/MacOS"
RES="$APP/Contents/Resources"

rm -rf "$APP"
mkdir -p "$MACOS" "$RES"
cp "$SRC/Info.plist" "$APP/Contents/Info.plist"

SDK="/Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk"
if [ ! -d "$SDK" ]; then
  SDK="$(xcrun --show-sdk-path)"
fi
MODCACHE="$ROOT/.toolchain/swift-module-cache"
mkdir -p "$MODCACHE"

swiftc \
  -O \
  -target arm64-apple-macosx13.0 \
  -sdk "$SDK" \
  -module-cache-path "$MODCACHE" \
  -framework AppKit \
  -framework SwiftUI \
  -o "$MACOS/BanbiaoDesktopWidget" \
  "$SRC/main.swift"

chmod +x "$MACOS/BanbiaoDesktopWidget"
codesign --force --sign - --timestamp=none "$APP"
codesign --verify --strict "$APP"
echo "Desktop widget: $APP"
