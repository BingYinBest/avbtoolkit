#!/usr/bin/env bash
# 组装内嵌工具链：
#   jni/arm64-v8a/{python3.10,openssl,fec}   可执行文件 → APK lib/（nativeLibraryDir，系统赋执行权限）
#   assets/toolchain_pylib.zip                python 标准库（运行时解压，仅读取）
#   assets/avbtool.py                         脚本（由 python 解释执行）
#
# 用法：bash scripts/fetch-toolchain.sh
# 依赖：curl、tar、zstd、python3、sha256sum
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/final"
mkdir -p "$OUT/jni/arm64-v8a" "$OUT/assets" "$ROOT/build/downloads"
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
rm -rf pysrc && mkdir pysrc
tar xf python.tar -C pysrc --strip-components=1
# full 变体结构不固定，定位 install 目录
PYROOT="$(find pysrc -maxdepth 3 -type d -name install | head -1)"
[ -n "$PYROOT" ] || { echo "python 结构异常" >&2; find pysrc -maxdepth 2 | head -30 >&2; exit 1; }
if [ ! -e "$PYROOT/bin/python3.10" ] && [ -e "$PYROOT/bin/python3" ]; then
  ln "$PYROOT/bin/python3" "$PYROOT/bin/python3.10"
fi
[ -x "$PYROOT/bin/python3.10" ] || { echo "python 可执行文件缺失" >&2; exit 1; }
cp "$PYROOT/bin/python3.10" "$OUT/jni/arm64-v8a/libvbm_python.so"
# 标准库打包为 python/lib/python3.10 结构（PYTHONHOME 指向 python/ 根）
rm -rf pylib && mkdir -p pylib/python
cp -r "$PYROOT/lib" pylib/python/lib
(cd pylib && python3 -m zipfile -c "$OUT/assets/toolchain_pylib.zip" python)
rm -rf pysrc pylib python.tar python.tar.zst

# ---------- 2. 静态 openssl（aarch64，NDK） ----------
echo "==> OpenSSL"
bash "$ROOT/scripts/build-openssl-static.sh" "$OUT/jni/arm64-v8a/libvbm_openssl.so"

# ---------- 3. fec 静态二进制（用户仓库 release） ----------
echo "==> fec"
curl -fL --retry 3 -o "$OUT/jni/arm64-v8a/libvbm_fec.so" \
  "https://github.com/BingYinBest/fec/releases/download/$FEC_VERSION/fec-aarch64"
echo "$FEC_SHA256  $OUT/jni/arm64-v8a/libvbm_fec.so" | sha256sum -c -
chmod +x "$OUT/jni/arm64-v8a/libvbm_fec.so"

# ---------- 4. avbtool.py（脚本 → assets，运行时复制到 filesDir） ----------
echo "==> avbtool"
cp "$ROOT/toolchain/avbtool.py" "$OUT/assets/avbtool.py"

# ---------- 5. 打包 ----------
(cd "$OUT" && python3 -m zipfile -c toolchain.zip jni assets)
echo "==> 完成: $OUT/toolchain.zip"
ls -la "$OUT/jni/arm64-v8a/" "$OUT/assets/"