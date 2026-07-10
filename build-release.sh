#!/usr/bin/env bash
#
# build-release.sh — assemble a SIGNED release APK and copy it to dist/.
#
# Required environment variables (set them before running, or in your shell):
#   KEYSTORE_PATH  absolute path to your .jks/.keystore
#   KEYSTORE_PASS  keystore password
#   KEY_ALIAS      signing key alias
#   KEY_PASS       key password
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

: "${KEYSTORE_PATH:?Set KEYSTORE_PATH to your keystore file}"
: "${KEYSTORE_PASS:?Set KEYSTORE_PASS}"
: "${KEY_ALIAS:?Set KEY_ALIAS}"
: "${KEY_PASS:?Set KEY_PASS}"

if [ ! -f "${KEYSTORE_PATH}" ]; then
  echo "x Keystore not found at: ${KEYSTORE_PATH}" >&2
  exit 1
fi

export KEYSTORE_PATH KEYSTORE_PASS KEY_ALIAS KEY_PASS

echo "==> Building signed release APK…"
./gradlew --no-daemon assembleRelease

APK="$(find app/build/outputs/apk/release -name '*.apk' | head -1)"
if [ -z "${APK}" ]; then
  echo "x No release APK produced." >&2
  exit 1
fi

mkdir -p dist
cp "${APK}" dist/webdav-release.apk

SIZE="$(du -h dist/webdav-release.apk | cut -f1)"
if command -v sha256sum >/dev/null 2>&1; then
  SHA="$(sha256sum dist/webdav-release.apk | cut -d' ' -f1)"
else
  SHA="$(shasum -a 256 dist/webdav-release.apk | cut -d' ' -f1)"
fi

echo
echo "=================== RELEASE BUILD OK ==================="
echo "  APK    : dist/webdav-release.apk"
echo "  Size   : ${SIZE}"
echo "  SHA-256: ${SHA}"
echo "======================================================="
