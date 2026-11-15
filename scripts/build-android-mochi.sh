#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-$ROOT/.toolchain/jdk/jdk-17.0.2.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$ROOT/.toolchain/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$ROOT/.toolchain/android-home}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.toolchain/gradle-home}"
export PATH="$JAVA_HOME/bin:$PATH"
"$ROOT/scripts/build-rust-core.sh"
cd "$ROOT/android-mochi"
"$ROOT/.toolchain/gradle/gradle-8.13/bin/gradle" :app:assembleRelease --no-daemon --console=plain
mkdir -p "$ROOT/dist"
cp app/build/outputs/apk/release/app-release.apk "$ROOT/dist/Cadence-2.5.0-Android.apk"
echo "$ROOT/dist/Cadence-2.5.0-Android.apk"
