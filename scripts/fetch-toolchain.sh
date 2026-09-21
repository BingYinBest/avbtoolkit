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
# 必须用静态链接变体（debug+static-full）：install_only 系列是动态 musl，
# 在 Android 上缺少 ld-musl loader 无法运行
PY_URL="$(curl -fsSL "https://api.github.com/repos/astral-sh/python-build-standalone/releases/tags/$PYTHON_RELEASE" \
  | grep -o "https://[^\"]*cpython-${PYTHON_VERSION}%2B${PYTHON_RELEASE}-aarch64-unknown-linux-musl[^\"]*debug%2Bstatic-full[^\"]*" \
  | head -1 || true)"
[ -z "$PY_URL" ] && { echo "无法定位 python-build-standalone 静态资产" >&2; exit 1; }
echo "    下载: $PY_URL"
curl -fL --retry 3 -o python.tar.zst "$PY_URL"
zstd -d -f python.tar.zst -o python.tar
mkdir -p "$OUT/python" "$ROOT/build/downloads/pysrc"
tar xf python.tar -C "$ROOT/build/downloads/pysrc" --strip-components=1
# full 变体目录结构不固定（可能嵌套），find 定位可执行文件后重组为 bin/ + lib/ 布局
# 注意不能用 -type f：bin/python3 是指向 python3.10 的符号链接
PYEXE="$(find "$ROOT/build/downloads/pysrc" -maxdepth 4 \( -name 'python3.10' -o -name 'python3' \) | head -1)"
if [ -z "$PYEXE" ]; then
  echo "python 可执行文件缺失，包结构：" >&2
  find "$ROOT/build/downloads/pysrc" -maxdepth 3 | head -40 >&2
  exit 1
fi
PYROOT="$(dirname "$(dirname "$PYEXE")")"
cp -r "$PYROOT/bin" "$OUT/python/bin"
cp -r "$PYROOT/lib" "$OUT/python/lib"
if [ ! -e "$OUT/python/bin/python3.10" ] && [ -e "$OUT/python/bin/python3" ]; then
  ln "$OUT/python/bin/python3" "$OUT/python/bin/python3.10"
fi
[ -x "$OUT/python/bin/python3.10" ] || { echo "python 可执行文件缺失" >&2; exit 1; }
rm -rf "$ROOT/build/downloads/pysrc" python.tar python.tar.zst

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
