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
        x86_64|amd64)   arch="x86_64" ;;
        arm64|aarch64)   arch="aarch64" ;;
        *)
            echo "Error: Unsupported architecture: $arch" >&2
            echo "SpringForge supports x86_64 and aarch64 only." >&2
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
    echo "SpringForge ${version} installed to ${INSTALL_DIR}/${BINARY_NAME}"
    echo "Run 'springforge --help' to get started."
}

main
