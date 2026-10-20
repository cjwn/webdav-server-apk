#!/usr/bin/env bash
# mac-build-install.sh: safe incremental debug build and USB installation.
# Run from an existing git checkout, optionally passing --no-pull or --serial SERIAL.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

usage() {
  printf 'Usage: bash scripts/mac-build-install.sh [--no-pull] [--serial ADB_SERIAL] [--no-launch]\n'
}
PULL=1
LAUNCH=1
SERIAL=""
while (( $# )); do
  case "$1" in
    --no-pull) PULL=0; shift;;
    --no-launch) LAUNCH=0; shift;;
    --serial) if (( $# < 2 )); then usage; exit 2; fi; SERIAL="$2"; shift 2;;
    -h|--help) usage; exit 0;;
    *) usage; echo "Unknown option: $1" >&2; exit 2;;
  esac
done

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This helper is for macOS." >&2; exit 1
fi
if [[ ! -d .git ]] || ! command -v git >/dev/null; then
  echo "Run inside your cloned GitHub repository." >&2; exit 1
fi

if [[ -f .env ]]; then
  # Local project bootstrap variables; sourced only from your own trusted checkout.
  # shellcheck disable=SC1091
  source .env
fi
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  export JAVA_HOME
fi
if [[ -z "${ANDROID_HOME:-}" ]]; then
  for dir in "$HOME/Library/Android/sdk" "$ROOT/tools/android-sdk"; do
    if [[ -d "$dir" ]]; then export ANDROID_HOME="$dir"; break; fi
  done
fi
if [[ -n "${ANDROID_HOME:-}" ]]; then
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi

if (( PULL )); then
  if [[ -n "$(git status --porcelain)" ]]; then
    echo "Local changes detected. Commit/stash them first, or use --no-pull." >&2
    exit 1
  fi
  branch="$(git symbolic-ref --short -q HEAD || true)"
  if [[ -z "$branch" ]]; then echo "Detached HEAD: use --no-pull or switch to a branch." >&2; exit 1; fi
  if [[ -z "$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null || true)" ]]; then
    echo "Branch '$branch' has no upstream. Use --no-pull or set the upstream first." >&2; exit 1
  fi
  echo "==> Fast-forwarding $branch"
  git pull --ff-only
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java 17 not found. Install JDK 17 or configure JAVA_HOME." >&2; exit 1
fi
java_version="$(java -version 2>&1 | head -n 1)"
echo "==> $java_version"
if ! java -version 2>&1 | head -n 1 | grep -Eq '"17([.+"]|$)'; then
  echo "This Android project requires JDK 17 (set JAVA_HOME)." >&2; exit 1
fi
if [[ -z "${ANDROID_HOME:-}" ]] || [[ ! -d "$ANDROID_HOME/platforms/android-36" ]]; then
  echo "Android SDK API 36 missing. Install it via Android Studio SDK Manager." >&2; exit 1
fi
if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  echo "Gradle wrapper JAR missing; run the repository setup instructions first." >&2; exit 1
fi
chmod +x ./gradlew
echo "==> Building APK (incremental)"
./gradlew :app:assembleDebug --build-cache

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo "APK missing: $APK" >&2; exit 1; }
echo "==> Built $APK"
echo "==> SHA-256 $(shasum -a 256 "$APK" | awk '{print $1}')"

if ! command -v adb >/dev/null 2>&1; then
  echo "ADB not found. APK was built; install Android SDK Platform-Tools to deploy." >&2
  exit 0
fi

if [[ -z "$SERIAL" ]]; then
  devices=()
  while IFS= read -r line; do
    [[ -n "$line" ]] && devices+=("$line")
  done < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if (( ${#devices[@]} != 1 )); then
    echo "Expected exactly one authorized ADB device; found ${#devices[@]}." >&2
    echo "Check USB debugging / trust prompt or specify --serial SERIAL." >&2
    adb devices
    exit 1
  fi
  SERIAL="${devices[0]}"
fi
echo "==> Installing to $SERIAL (never uninstalling existing app)"
# Android's debug applicationId includes the .debug suffix from app/build.gradle.kts.
# An INSTALL_FAILED_UPDATE_INCOMPATIBLE error means a different signing key was used.
# Do NOT uninstall automatically: that could delete the app's stored configuration.
adb -s "$SERIAL" install -r "$APK"

if (( LAUNCH )); then
  echo "==> Launching debug app"
  adb -s "$SERIAL" shell monkey -p com.localdev.webdavserver.debug -c android.intent.category.LAUNCHER 1
fi
echo "==> Finished. Use --no-pull to compile uncommitted local edits."
