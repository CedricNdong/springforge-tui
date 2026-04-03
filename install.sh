#!/usr/bin/env bash
#
# SpringForge TUI installer
# Usage: curl -L https://github.com/CedricNdong/springforge-tui/releases/latest/download/install.sh | bash
#
set -euo pipefail

REPO="CedricNdong/springforge-tui"
INSTALL_DIR="${SPRINGFORGE_INSTALL_DIR:-/usr/local/bin}"
BINARY_NAME="springforge"

detect_platform() {
    local os arch
    os="$(uname -s)"
    arch="$(uname -m)"

    case "$os" in
        Linux)  os="linux" ;;
        Darwin) os="macos" ;;
        *)
            echo "Error: Unsupported operating system: $os" >&2
            echo "SpringForge supports Linux and macOS only." >&2
            exit 1
            ;;
    esac

    case "$arch" in
        x86_64|amd64)
            if [ "$os" = "macos" ]; then
                echo "Error: macOS x86_64 (Intel) is not supported." >&2
                echo "SpringForge supports macOS on Apple Silicon (aarch64) only." >&2
                exit 1
            fi
            arch="x86_64"
            ;;
        arm64|aarch64)   arch="aarch64" ;;
        *)
            echo "Error: Unsupported architecture: $arch" >&2
            exit 1
            ;;
    esac

    echo "${os}-${arch}"
}

get_latest_version() {
    curl -sI "https://github.com/${REPO}/releases/latest" \
        | grep -i '^location:' \
        | sed 's/.*tag\///' \
        | tr -d '\r\n'
}

main() {
    echo "SpringForge TUI Installer"
    echo "========================="
    echo ""

    local platform version download_url tmp_file

    platform="$(detect_platform)"
    echo "Detected platform: ${platform}"

    version="$(get_latest_version)"
    if [ -z "$version" ]; then
        echo "Error: Could not determine latest version." >&2
        exit 1
    fi
    echo "Latest version: ${version}"

    download_url="https://github.com/${REPO}/releases/download/${version}/${BINARY_NAME}-${platform}"
    echo "Downloading: ${download_url}"

    tmp_file="$(mktemp)"
    if ! curl -fSL -o "$tmp_file" "$download_url"; then
        echo "Error: Download failed. No binary available for ${platform}." >&2
        rm -f "$tmp_file"
        exit 1
    fi

    chmod +x "$tmp_file"

    if [ -w "$INSTALL_DIR" ]; then
        mv "$tmp_file" "${INSTALL_DIR}/${BINARY_NAME}"
    else
        echo "Installing to ${INSTALL_DIR} requires sudo..."
        sudo mv "$tmp_file" "${INSTALL_DIR}/${BINARY_NAME}"
    fi

    echo ""

    # Post-install verification
    local expected_version="${version#v}"
    local actual_version
    if ! actual_version=$("${INSTALL_DIR}/${BINARY_NAME}" --version 2>&1); then
        echo "Warning: Binary installed but failed to execute." >&2
        echo "This may indicate a missing library or incompatible platform." >&2
        echo "Try running: ${INSTALL_DIR}/${BINARY_NAME} --version" >&2
        exit 1
    fi

    if echo "$actual_version" | grep -q "$expected_version"; then
        echo "SpringForge ${version} installed to ${INSTALL_DIR}/${BINARY_NAME}"
        echo "Verified: ${actual_version}"
    else
        echo "SpringForge installed to ${INSTALL_DIR}/${BINARY_NAME}"
        echo "Warning: Version mismatch — expected ${expected_version}, got: ${actual_version}" >&2
    fi

    echo "Run 'springforge --help' to get started."
}

main
