#!/bin/bash
# Build the macOS app bundle and a distributable ZIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -x "$ROOT/.toolchain/node/bin/node" ]; then
  export PATH="$ROOT/.toolchain/node/bin:$PATH"
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js 22+ is required to build the macOS app." >&2
  exit 1
fi

cd "$ROOT/macos"
if [ ! -d node_modules/electron ] || [ ! -x node_modules/electron/dist/Electron.app/Contents/MacOS/Electron ]; then
  echo "Electron is not installed. Run: cd macos && npm install" >&2
  echo "If GitHub is slow, set ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/" >&2
  exit 1
fi

rm -rf web
cp -R "$ROOT/web" web
"$ROOT/scripts/build-desktop-widget.sh"

PACKAGER_ARGS=(
  . "班表"
  --platform=darwin
  --arch=arm64
  --out="$ROOT/dist/macos"
  --overwrite
  --icon=build/icon.icns
  --app-bundle-id=com.banbiao.app
  --app-version=1.4.1
  --build-version=5
  --electron-version=45.2.1
  --prune=true
  --app-category-type=public.app-category.productivity
  --ignore='^/(node_modules|build|package-lock\.json|\.DS_Store)'
)

ZIP_PATH="$ROOT/.toolchain/downloads/electron-v45.2.1-darwin-arm64.zip"
if [ -f "$ZIP_PATH" ]; then
  PACKAGER_ARGS+=(--electron-zip-dir="$ROOT/.toolchain/downloads")
fi

npx electron-packager "${PACKAGER_ARGS[@]}"

APP="$ROOT/dist/macos/班表-darwin-arm64/班表.app"
if [ ! -d "$APP" ]; then
  echo "Packaging failed: $APP was not created." >&2
  exit 1
fi

# The mirror build of Electron ships without an icon resource; apply ours.
cp build/icon.icns "$APP/Contents/Resources/electron.icns"

# Embed the native desktop-widget helper outside app.asar so it can be spawned.
rm -rf "$APP/Contents/Resources/BanbiaoDesktopWidget.app"
cp -R "$ROOT/macos/desktop-widget/BanbiaoDesktopWidget.app" "$APP/Contents/Resources/"

plutil -replace LSApplicationCategoryType -string public.app-category.productivity "$APP/Contents/Info.plist"
plutil -remove NSAppTransportSecurity "$APP/Contents/Info.plist" 2>/dev/null || true
for key in NSCameraUsageDescription NSMicrophoneUsageDescription \
  NSAudioCaptureUsageDescription NSBluetoothAlwaysUsageDescription \
  NSBluetoothPeripheralUsageDescription; do
  plutil -remove "$key" "$APP/Contents/Info.plist" 2>/dev/null || true
done
plutil -insert NSHumanReadableCopyright -string "© 2026 班表" "$APP/Contents/Info.plist" 2>/dev/null || \
  plutil -replace NSHumanReadableCopyright -string "© 2026 班表" "$APP/Contents/Info.plist"

codesign --force --deep --sign - --timestamp=none "$APP"
codesign --verify --deep --strict "$APP"

mkdir -p "$ROOT/dist"
rm -f "$ROOT/dist/班表-1.4.1-macOS-arm64.zip"
ditto -c -k --sequesterRsrc --keepParent "$APP" "$ROOT/dist/班表-1.4.1-macOS-arm64.zip"

DMG="$ROOT/dist/班表-1.4.1-macOS-arm64.dmg"
if command -v hdiutil >/dev/null 2>&1; then
  STAGE="$ROOT/.toolchain/dmg-stage"
  rm -rf "$STAGE"
  mkdir -p "$STAGE"
  cp -R "$APP" "$STAGE/班表.app"
  ln -s /Applications "$STAGE/Applications"
  rm -f "$DMG"
  if hdiutil create -volname "班表" -srcfolder "$STAGE" -ov -format UDZO "$DMG" >/dev/null; then
    echo "DMG: $DMG"
  else
    echo "DMG creation skipped (hdiutil failed); ZIP is still available." >&2
  fi
fi

echo
echo "macOS build ready:"
echo "  $APP"
echo "  $ROOT/dist/班表-1.4.1-macOS-arm64.zip"
if [ -f "$DMG" ]; then echo "  $DMG"; fi
