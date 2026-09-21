#!/usr/bin/env bash
# 组装内嵌工具链 toolchain.zip：musl 静态 Python + 静态 openssl + fec + avbtool.py
#
# 产物结构（与 ToolchainManager 期望一致）：
#   toolchain.zip
#   ├── VERSION                    工具链版本号（与 ASSET_VERSION 对齐）
#   ├── python/bin/python3.10     musl 静态 Python 可执行
#   ├── python/lib/...            Python 标准库
#   └── bin/{openssl,fec,avbtool.py}
#
# 用法：bash scripts/fetch-toolchain.sh
# 依赖：curl、tar、zstd、python3（解压/打包）、sha256sum
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/toolchain"
mkdir -p "$OUT/bin" "$OUT/python" "$ROOT/build/downloads"
cd "$ROOT/build/downloads"

PYTHON_RELEASE="20260901"
PYTHON_VERSION="3.10.21"
FEC_VERSION="v1.0"
FEC_SHA256="7467908451cae1dd6bc23c36b994bd0885316d0ddd5c526e133c0ded77fc397b"

# ---------- 1. musl 静态 Python（aarch64） ----------
echo "==> Python"
PY_URL=""
# 注意：GitHub API 的 JSON 中资产 URL 的 "+" 被编码为 %2B，需用 %2B 匹配
for pattern in 'install_only_stripped' 'install_only' 'debug%2Bstatic-full'; do
  PY_URL="$(curl -fsSL "https://api.github.com/repos/astral-sh/python-build-standalone/releases/tags/$PYTHON_RELEASE" \
    | grep -o "https://[^\"]*cpython-${PYTHON_VERSION}%2B${PYTHON_RELEASE}-aarch64-unknown-linux-musl[^\"]*${pattern}[^\"]*" \
    | head -1 || true)"
  [ -n "$PY_URL" ] && break
done
[ -z "$PY_URL" ] && { echo "无法定位 python-build-standalone 资产" >&2; exit 1; }
echo "    下载: $PY_URL"
curl -fL --retry 3 -o python.tar.zst "$PY_URL"
zstd -d -f python.tar.zst -o python.tar
tar xf python.tar -C "$OUT/python" --strip-components=1
PYBIN="$OUT/python/bin"
# 确保 python3.10 存在（install_only 可能只带 python3）
if [ ! -e "$PYBIN/python3.10" ] && [ -e "$PYBIN/python3" ]; then
  ln "$PYBIN/python3" "$PYBIN/python3.10"
fi
[ -x "$PYBIN/python3.10" ] || { echo "python 可执行文件缺失" >&2; ls -la "$PYBIN"; exit 1; }
rm -f python.tar python.tar.zst

# ---------- 2. 静态 openssl（aarch64） ----------
echo "==> OpenSSL"
bash "$ROOT/scripts/build-openssl-static.sh" "$OUT/bin/openssl"

# ---------- 3. fec 静态二进制（用户仓库 release） ----------
echo "==> fec"
curl -fL --retry 3 -o "$OUT/bin/fec" \
  "https://github.com/BingYinBest/fec/releases/download/$FEC_VERSION/fec-aarch64"
echo "$FEC_SHA256  $OUT/bin/fec" | sha256sum -c -
chmod +x "$OUT/bin/fec"

# ---------- 4. avbtool.py（vendor 于 toolchain/） ----------
echo "==> avbtool"
cp "$ROOT/toolchain/avbtool.py" "$OUT/bin/avbtool.py"
chmod +x "$OUT/bin/avbtool.py"

# ---------- 5. 版本与打包 ----------
echo "1" > "$OUT/VERSION"
(cd "$OUT" && python3 -m zipfile -c toolchain.zip VERSION python bin)
echo "==> 完成: $OUT/toolchain.zip"
ls -la "$OUT/toolchain.zip"
