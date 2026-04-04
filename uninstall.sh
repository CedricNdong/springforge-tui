#!/usr/bin/env bash
#
# SpringForge TUI uninstaller
# Usage: curl -L https://github.com/CedricNdong/springforge-tui/releases/latest/download/uninstall.sh | bash
#
set -euo pipefail

INSTALL_DIR="${SPRINGFORGE_INSTALL_DIR:-/usr/local/bin}"
BINARY_NAME="springforge"
BINARY_PATH="${INSTALL_DIR}/${BINARY_NAME}"

main() {
    echo "SpringForge TUI Uninstaller"
    echo "==========================="
    echo ""

    if [ ! -f "$BINARY_PATH" ]; then
        echo "SpringForge is not installed at ${BINARY_PATH}."
        echo "Nothing to remove."
        exit 0
    fi

    local version
    version=$("$BINARY_PATH" --version 2>/dev/null || echo "unknown version")
    echo "Found: ${version} at ${BINARY_PATH}"

    if [ -w "$INSTALL_DIR" ]; then
        rm -f "$BINARY_PATH"
    else
        echo "Removing from ${INSTALL_DIR} requires sudo..."
        sudo rm -f "$BINARY_PATH"
    fi

    echo "SpringForge has been uninstalled."
}

main
