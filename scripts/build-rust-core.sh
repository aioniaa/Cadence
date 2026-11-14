#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CARGO_HOME="${CARGO_HOME:-$ROOT/.toolchain/cargo}"
RUSTUP_HOME="${RUSTUP_HOME:-$ROOT/.toolchain/rustup}"
export CARGO_HOME RUSTUP_HOME
export PATH="$CARGO_HOME/bin:$PATH"
export RUSTUP_DIST_SERVER="${RUSTUP_DIST_SERVER:-https://rsproxy.cn}"
NDK="${ANDROID_NDK_HOME:-$ROOT/.toolchain/android-sdk/ndk/28.2.13676358}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/darwin-x86_64"
export CC_aarch64_linux_android="$TOOLCHAIN/bin/aarch64-linux-android26-clang"
export AR_aarch64_linux_android="$TOOLCHAIN/bin/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$CC_aarch64_linux_android"
export CC_x86_64_linux_android="$TOOLCHAIN/bin/x86_64-linux-android26-clang"
export AR_x86_64_linux_android="$TOOLCHAIN/bin/llvm-ar"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$CC_x86_64_linux_android"

cd "$ROOT/android-mochi/rust/banbiao_core"
"$CARGO_HOME/bin/rustup" target add aarch64-linux-android x86_64-linux-android >/dev/null 2>&1 || true
"$CARGO_HOME/bin/cargo" build --release --target aarch64-linux-android
"$CARGO_HOME/bin/cargo" build --release --target x86_64-linux-android
cp target/aarch64-linux-android/release/libbanbiao_core.so "$ROOT/android-mochi/app/src/main/jniLibs/arm64-v8a/"
cp target/x86_64-linux-android/release/libbanbiao_core.so "$ROOT/android-mochi/app/src/main/jniLibs/x86_64/"
echo "Rust core built."
