#!/usr/bin/env bash
# 用 musl.cc 交叉工具链静态编译 openssl（aarch64-linux-musl）
#
# 用法：bash scripts/build-openssl-static.sh <输出路径>
# 输出：完全静态链接的 openssl 可执行文件
set -euo pipefail

OUT="${1:?用法: build-openssl-static.sh <输出路径>}"
OPENSSL_VERSION="${OPENSSL_VERSION:-3.0.16}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "    下载交叉工具链"
CROSS_URL="https://more.musl.cc/11/aarch64-linux-musl-cross.tgz"
curl -fL --retry 3 -o "$WORK/cross.tgz" "$CROSS_URL"
tar xzf "$WORK/cross.tgz" -C "$WORK"
TOOLCHAIN="$(find "$WORK" -maxdepth 2 -type d -name 'aarch64-linux-musl' | head -1)"
[ -n "$TOOLCHAIN" ] || { echo "交叉工具链解压失败" >&2; exit 1; }
export PATH="$TOOLCHAIN/bin:$PATH"

echo "    下载 openssl $OPENSSL_VERSION"
curl -fL --retry 3 -o "$WORK/openssl.tar.gz" \
  "https://www.openssl.org/source/openssl-$OPENSSL_VERSION.tar.gz"
tar xzf "$WORK/openssl.tar.gz" -C "$WORK"
cd "$WORK/openssl-$OPENSSL_VERSION"

echo "    配置并编译"
./Configure -static no-shared no-tests no-docs no-ui-console \
  --cross-compile-prefix=aarch64-linux-musl- linux-aarch64
make -j"$(nproc)" build_libs apps/openssl >/dev/null

mkdir -p "$(dirname "$OUT")"
cp apps/openssl "$OUT"
chmod +x "$OUT"
echo "    完成: $OUT ($(file -b "$OUT" | cut -d, -f1-2))"
