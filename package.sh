#!/usr/bin/env bash
#
# package.sh — full bootstrap + debug build + zip up a distributable bundle.
#
# Produces webdav-server-android.zip containing all sources, build scripts,
# README and the built debug APK. Excludes heavy/regenerable dirs.
#
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT_DIR}"

# 1) Ensure environment exists.
if [ ! -f "${ROOT_DIR}/.env" ]; then
  echo "==> .env missing — running setup-env.sh"
  chmod +x ./setup-env.sh
  ./setup-env.sh
fi

# 2) Build the debug APK.
chmod +x ./build-debug.sh
./build-debug.sh

# 3) Zip everything (excluding caches / tools / build output dirs).
ZIP_NAME="webdav-server-android.zip"
rm -f "${ZIP_NAME}"

echo "==> Creating ${ZIP_NAME}…"
if command -v zip >/dev/null 2>&1; then
  zip -r -q "${ZIP_NAME}" . \
    -x "./.git/*" \
    -x "./.gradle/*" \
    -x "./build/*" \
    -x "./app/build/*" \
    -x "./tools/*" \
    -x "./.env" \
    -x "./*.zip" \
    -x "**/.DS_Store"
else
  # Fallback to tar if zip is unavailable, then note it.
  echo "!  'zip' not available; producing a .tar.gz instead."
  tar --exclude='./.git' --exclude='./.gradle' --exclude='./build' --exclude='./app/build' \
      --exclude='./tools' --exclude='./.env' --exclude='./*.zip' \
      -czf "webdav-server-android.tar.gz" .
  ZIP_NAME="webdav-server-android.tar.gz"
fi

SIZE="$(du -h "${ZIP_NAME}" | cut -f1)"
echo
echo "==================== PACKAGE READY ===================="
echo "  Bundle : ${ZIP_NAME}  (${SIZE})"
echo "  Note   : tools/ and build/ are excluded — recipients run"
echo "           ./setup-env.sh once to rebuild the environment."
echo "======================================================="
