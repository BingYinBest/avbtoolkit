#!/usr/bin/env bash
# 用 Android NDK 静态编译 openssl（aarch64-linux-android，静态 bionic 链接）
# 与 fec 使用同一工具链（NDK r26d），产物可在 Android 设备上直接执行。
#
# 用法：NDK=<ndk根目录> bash scripts/build-openssl-static.sh <输出路径>
set -euo pipefail

OUT="${1:?用法: build-openssl-static.sh <输出路径>}"
OPENSSL_VERSION="${OPENSSL_VERSION:-3.0.16}"
: "${NDK:?需要 NDK 环境变量（Android NDK 根目录，如 /opt/android-ndk-r26d）}"

HOST_OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
case "$HOST_OS" in
  linux) HOST_OS="linux" ;;
  darwin) HOST_OS="darwin" ;;
  *) echo "不支持的宿主系统: $HOST_OS" >&2; exit 1 ;;
esac
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_OS-x86_64"
[ -d "$TOOLCHAIN" ] || { echo "NDK 工具链不存在: $TOOLCHAIN" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "    下载 openssl $OPENSSL_VERSION"
curl -fL --retry 3 -o "$WORK/openssl.tar.gz" \
  "https://www.openssl.org/source/openssl-$OPENSSL_VERSION.tar.gz"
tar xzf "$WORK/openssl.tar.gz" -C "$WORK"
cd "$WORK/openssl-$OPENSSL_VERSION"

echo "    配置并编译（android-aarch64）"
export ANDROID_NDK_ROOT="$NDK"
export PATH="$TOOLCHAIN/bin:$PATH"
export CC="$TOOLCHAIN/bin/aarch64-linux-android21-clang"
./Configure -static no-shared no-tests no-docs no-ui-console \
  --cross-compile-prefix="$TOOLCHAIN/bin/aarch64-linux-android21-" \
  android-aarch64
make -j"$(nproc)" build_libs apps/openssl >/dev/null

mkdir -p "$(dirname "$OUT")"
cp apps/openssl "$OUT"
chmod +x "$OUT"
echo "    完成: $OUT"
