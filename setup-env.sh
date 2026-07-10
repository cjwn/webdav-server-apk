#!/usr/bin/env bash
#
# setup-env.sh — portable JDK 17 + Android SDK + Gradle wrapper bootstrap.
#
# Downloads everything into ./tools (and gradle/wrapper) so NOTHING needs to be
# pre-installed system-wide. Safe to re-run; it skips steps already completed.
#
set -euo pipefail

# --------------------------------------------------------------------------- #
# Configuration
# --------------------------------------------------------------------------- #
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="${ROOT_DIR}/tools"
JDK_DIR="${TOOLS_DIR}/jdk"
SDK_DIR="${TOOLS_DIR}/android-sdk"
GRADLE_WRAPPER_DIR="${ROOT_DIR}/gradle/wrapper"

GRADLE_VERSION="8.9"
GRADLE_DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

# Eclipse Temurin JDK 17 (LTS), portable tar.gz
TEMURIN_VERSION="17.0.12+7"
TEMURIN_TAG="jdk-17.0.12%2B7"

# Android command-line tools (latest stable at time of writing)
CMDLINE_TOOLS_VERSION="11076708"

ANDROID_PLATFORM="platforms;android-36"
ANDROID_BUILD_TOOLS="build-tools;36.0.0"
ANDROID_PLATFORM_TOOLS="platform-tools"

# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m  ✔\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m  !\033[0m %s\n' "$*"; }
err()  { printf '\033[1;31m  x\033[0m %s\n' "$*" >&2; }

detect_os() {
  case "$(uname -s)" in
    Linux*)  echo "linux" ;;
    Darwin*) echo "mac"   ;;
    *) err "Unsupported OS: $(uname -s)"; exit 1 ;;
  esac
}

detect_arch() {
  case "$(uname -m)" in
    x86_64|amd64) echo "x64" ;;
    arm64|aarch64) echo "aarch64" ;;
    *) err "Unsupported arch: $(uname -m)"; exit 1 ;;
  esac
}

download() {
  # download <url> <dest>
  local url="$1" dest="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --progress-bar -o "$dest" "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget -q --show-progress -O "$dest" "$url"
  else
    err "Neither curl nor wget is available."; exit 1
  fi
}

OS="$(detect_os)"
ARCH="$(detect_arch)"

mkdir -p "${TOOLS_DIR}" "${GRADLE_WRAPPER_DIR}"

# --------------------------------------------------------------------------- #
# 1) Portable JDK 17 (Eclipse Temurin)
# --------------------------------------------------------------------------- #
install_jdk() {
  if [ -x "${JDK_DIR}/bin/javac" ] || ls "${JDK_DIR}"/*/bin/javac >/dev/null 2>&1; then
    ok "JDK already present in ${JDK_DIR}"
    return
  fi
  log "Downloading Eclipse Temurin JDK ${TEMURIN_VERSION} (${OS}/${ARCH})…"

  local temurin_os="$OS"
  [ "$OS" = "mac" ] && temurin_os="mac"
  local temurin_arch="$ARCH"

  local file="OpenJDK17U-jdk_${temurin_arch}_${temurin_os}_hotspot_17.0.12_7.tar.gz"
  local url="https://github.com/adoptium/temurin17-binaries/releases/download/${TEMURIN_TAG}/${file}"

  local tmp="${TOOLS_DIR}/jdk.tar.gz"
  download "$url" "$tmp"

  log "Extracting JDK to ${JDK_DIR}…"
  rm -rf "${JDK_DIR}"; mkdir -p "${JDK_DIR}"
  tar -xzf "$tmp" -C "${JDK_DIR}" --strip-components=1 2>/dev/null || \
    tar -xzf "$tmp" -C "${JDK_DIR}"
  rm -f "$tmp"

  # On macOS the layout is Contents/Home; normalise JAVA_HOME later.
  ok "JDK installed."
}

resolve_java_home() {
  if [ -x "${JDK_DIR}/bin/javac" ]; then
    echo "${JDK_DIR}"
  elif [ -x "${JDK_DIR}/Contents/Home/bin/javac" ]; then
    echo "${JDK_DIR}/Contents/Home"
  else
    # strip-components fallback: find first javac
    local jc; jc="$(find "${JDK_DIR}" -name javac -type f 2>/dev/null | head -1)"
    [ -n "$jc" ] && dirname "$(dirname "$jc")"
  fi
}

# --------------------------------------------------------------------------- #
# 2) Gradle wrapper JAR
# --------------------------------------------------------------------------- #
install_gradle_wrapper() {
  if [ -f "${GRADLE_WRAPPER_DIR}/gradle-wrapper.jar" ]; then
    ok "gradle-wrapper.jar already present"
    return
  fi
  log "Fetching Gradle ${GRADLE_VERSION} to extract gradle-wrapper.jar…"
  local tmpzip="${TOOLS_DIR}/gradle.zip"
  local tmpdir="${TOOLS_DIR}/gradle-extract"
  download "$GRADLE_DIST_URL" "$tmpzip"
  rm -rf "$tmpdir"; mkdir -p "$tmpdir"
  unzip -q "$tmpzip" -d "$tmpdir"
  local wjar
  wjar="$(find "$tmpdir" -name 'gradle-wrapper.jar' | head -1)"
  if [ -z "$wjar" ]; then
    # Some distributions ship the wrapper only as plugin classes; generate it.
    warn "gradle-wrapper.jar not found in distribution; generating via gradle wrapper task."
    local gbin; gbin="$(find "$tmpdir" -name gradle -type f -path '*/bin/*' | head -1)"
    chmod +x "$gbin"
    ( cd "${ROOT_DIR}" && JAVA_HOME="$(resolve_java_home)" "$gbin" wrapper --gradle-version "${GRADLE_VERSION}" )
  else
    cp "$wjar" "${GRADLE_WRAPPER_DIR}/gradle-wrapper.jar"
  fi
  rm -rf "$tmpzip" "$tmpdir"
  ok "gradle-wrapper.jar ready."
}

ensure_gradlew() {
  if [ ! -f "${ROOT_DIR}/gradlew" ]; then
    log "Creating gradlew launcher scripts…"
    local gbin tmpzip tmpdir
    tmpzip="${TOOLS_DIR}/gradle.zip"; tmpdir="${TOOLS_DIR}/gradle-extract"
    if [ ! -d "$tmpdir" ]; then
      download "$GRADLE_DIST_URL" "$tmpzip"
      mkdir -p "$tmpdir"; unzip -q "$tmpzip" -d "$tmpdir"
    fi
    gbin="$(find "$tmpdir" -name gradle -type f -path '*/bin/*' | head -1)"
    chmod +x "$gbin"
    ( cd "${ROOT_DIR}" && JAVA_HOME="$(resolve_java_home)" "$gbin" wrapper --gradle-version "${GRADLE_VERSION}" >/dev/null 2>&1 || true )
    rm -rf "$tmpzip" "$tmpdir"
  fi
  chmod +x "${ROOT_DIR}/gradlew" 2>/dev/null || true
  ok "gradlew available."
}

# --------------------------------------------------------------------------- #
# 3) Android command-line tools + SDK packages
# --------------------------------------------------------------------------- #
install_android_sdk() {
  local sdkmanager
  sdkmanager="$(find "${SDK_DIR}/cmdline-tools" -name sdkmanager -type f 2>/dev/null | head -1 || true)"

  if [ -z "$sdkmanager" ]; then
    log "Downloading Android command-line tools…"
    local cmd_os="$OS"
    [ "$OS" = "mac" ] && cmd_os="mac"
    local file="commandlinetools-${cmd_os}-${CMDLINE_TOOLS_VERSION}_latest.zip"
    local url="https://dl.google.com/android/repository/${file}"
    local tmp="${TOOLS_DIR}/cmdline-tools.zip"
    download "$url" "$tmp"
    mkdir -p "${SDK_DIR}/cmdline-tools"
    rm -rf "${SDK_DIR}/cmdline-tools/latest"
    unzip -q "$tmp" -d "${SDK_DIR}/cmdline-tools"
    # The zip extracts to cmdline-tools/cmdline-tools; relocate to .../latest
    if [ -d "${SDK_DIR}/cmdline-tools/cmdline-tools" ]; then
      mv "${SDK_DIR}/cmdline-tools/cmdline-tools" "${SDK_DIR}/cmdline-tools/latest"
    fi
    rm -f "$tmp"
    sdkmanager="${SDK_DIR}/cmdline-tools/latest/bin/sdkmanager"
  else
    ok "Android cmdline-tools already present"
  fi

  export ANDROID_HOME="${SDK_DIR}"
  export ANDROID_SDK_ROOT="${SDK_DIR}"
  export JAVA_HOME="$(resolve_java_home)"
  export PATH="${JAVA_HOME}/bin:${PATH}"

  log "Accepting SDK licenses…"
  yes | "$sdkmanager" --sdk_root="${SDK_DIR}" --licenses >/dev/null 2>&1 || true

  log "Installing SDK packages: ${ANDROID_PLATFORM}, ${ANDROID_BUILD_TOOLS}, ${ANDROID_PLATFORM_TOOLS}…"
  "$sdkmanager" --sdk_root="${SDK_DIR}" \
    "${ANDROID_PLATFORM}" "${ANDROID_BUILD_TOOLS}" "${ANDROID_PLATFORM_TOOLS}"
  ok "Android SDK packages installed."
}

# --------------------------------------------------------------------------- #
# 4) Write .env
# --------------------------------------------------------------------------- #
write_env() {
  local java_home; java_home="$(resolve_java_home)"
  log "Writing .env…"
  cat > "${ROOT_DIR}/.env" <<EOF
# Auto-generated by setup-env.sh — DO NOT COMMIT.
export JAVA_HOME="${java_home}"
export ANDROID_HOME="${SDK_DIR}"
export ANDROID_SDK_ROOT="${SDK_DIR}"
export PATH="\${JAVA_HOME}/bin:${SDK_DIR}/platform-tools:${SDK_DIR}/cmdline-tools/latest/bin:\${PATH}"
EOF
  ok ".env written."
}

# --------------------------------------------------------------------------- #
# Run
# --------------------------------------------------------------------------- #
main() {
  log "WebDAV Server — environment setup (OS=${OS}, ARCH=${ARCH})"
  install_jdk
  install_gradle_wrapper
  ensure_gradlew
  install_android_sdk
  write_env

  # shellcheck disable=SC1091
  source "${ROOT_DIR}/.env"

  echo
  printf '\033[1;32m==================== SETUP COMPLETE ====================\033[0m\n'
  local jv av gv
  jv="$("${JAVA_HOME}/bin/java" -version 2>&1 | head -1)"
  printf '  JAVA_HOME    : %s\n' "${JAVA_HOME}"
  printf '  Java         : %s\n' "${jv}"
  printf '  ANDROID_HOME : %s\n' "${ANDROID_HOME}"
  if [ -f "${ROOT_DIR}/gradlew" ]; then
    gv="$(cd "${ROOT_DIR}" && ./gradlew --version 2>/dev/null | awk '/Gradle/{print $2; exit}')"
    printf '  Gradle       : %s\n' "${gv:-unknown}"
  fi
  printf '  Platform     : %s\n' "${ANDROID_PLATFORM}"
  printf '  Build-tools  : %s\n' "${ANDROID_BUILD_TOOLS}"
  printf '\033[1;32m========================================================\033[0m\n'
  echo
  echo "Next:  ./build-debug.sh"
}

main "$@"
