#!/usr/bin/env bash
#
# build-debug.sh — assemble the debug APK and copy it to dist/.
#
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT_DIR}"

if [ ! -f "${ROOT_DIR}/.env" ]; then
  echo "x .env not found. Run ./setup-env.sh first." >&2
  exit 1
fi
# shellcheck disable=SC1091
source "${ROOT_DIR}/.env"

echo "==> Building debug APK…"
./gradlew --no-daemon assembleDebug

APK="$(find app/build/outputs/apk/debug -name '*.apk' | head -1)"
if [ -z "${APK}" ]; then
  echo "x No debug APK produced." >&2
  exit 1
fi

mkdir -p dist
cp "${APK}" dist/webdav-debug.apk

SIZE="$(du -h dist/webdav-debug.apk | cut -f1)"
if command -v sha256sum >/dev/null 2>&1; then
  SHA="$(sha256sum dist/webdav-debug.apk | cut -d' ' -f1)"
else
  SHA="$(shasum -a 256 dist/webdav-debug.apk | cut -d' ' -f1)"
fi

echo
echo "==================== DEBUG BUILD OK ===================="
echo "  APK    : dist/webdav-debug.apk"
echo "  Size   : ${SIZE}"
echo "  SHA-256: ${SHA}"
echo "======================================================="
