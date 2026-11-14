#!/bin/bash
# Build and verify the signed Android APK.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -n "${JAVA_HOME:-}" ]; then
  JDK_HOME="$JAVA_HOME"
else
  JDK_HOME="$(find "$ROOT/.toolchain/jdk" -maxdepth 3 -path "*/Contents/Home" -type d | head -1)"
fi
SDK_ROOT="${ANDROID_HOME:-$ROOT/.toolchain/android-sdk}"
GRADLE_BIN="${GRADLE_BIN:-$ROOT/.toolchain/gradle/gradle-8.13/bin/gradle}"

if [ ! -x "$JDK_HOME/bin/java" ]; then
  echo "JDK 17 not found at $JDK_HOME" >&2
  exit 1
fi
if [ ! -d "$SDK_ROOT/platforms/android-35" ]; then
  echo "Android SDK platform 35 not found at $SDK_ROOT" >&2
  exit 1
fi
if [ ! -x "$GRADLE_BIN" ]; then
  echo "Gradle not found at $GRADLE_BIN" >&2
  exit 1
fi

export JAVA_HOME="$JDK_HOME"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_USER_HOME="$ROOT/.toolchain/android-home"
export GRADLE_USER_HOME="$ROOT/.toolchain/gradle-home"
export PATH="$JAVA_HOME/bin:$PATH"

"$GRADLE_BIN" -p "$ROOT/android" assembleRelease --no-daemon --console=plain

APK="$ROOT/android/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
  echo "Build failed: $APK was not created." >&2
  exit 1
fi

APKSIGNER="$SDK_ROOT/build-tools/35.0.0/apksigner"
if [ -x "$APKSIGNER" ]; then
  "$APKSIGNER" verify --verbose "$APK" | head -12
fi

mkdir -p "$ROOT/dist"
cp "$APK" "$ROOT/dist/班表-1.3.2-Android.apk"

echo
echo "Android build ready:"
echo "  $ROOT/dist/班表-1.3.2-Android.apk"
