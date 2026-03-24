#!/bin/sh
set -eu

# Check for JDK 8
java_found=false
if command -v java >/dev/null 2>&1; then
    java_version_output=$(java -version 2>&1)
    if echo "$java_version_output" | grep -qE '"1\.8\.|version "1\.8'; then
        java_found=true
    fi
fi

if [ "$java_found" = false ]; then
    echo "Error: JDK 8 is required but was not found."
    echo "Please install JDK 8 and make sure 'java' is available in your PATH."
    exit 1
fi

LINKMIND_DIR="$HOME/LinkMind"
JAR_NAME="LinkMind.jar"
DOWNLOAD_URL="https://downloads.landingbj.com/lagi/installer/LinkMind.jar"
#DOWNLOAD_URL="http://localhost:8000/LinkMind.jar"
JAR_PATH="$LINKMIND_DIR/$JAR_NAME"

# 1. Ensure LinkMind directory exists
if [ ! -d "$LINKMIND_DIR" ]; then
    mkdir -p "$LINKMIND_DIR"
    echo "Created directory: $LINKMIND_DIR"
else
    echo "Directory already exists: $LINKMIND_DIR"
fi

# 2-3. Download jar to a temp file with progress, then move to target
TEMP_FILE="$(mktemp "${TMPDIR:-/tmp}/LinkMind_XXXXXXXXXX.jar")"
cleanup() { rm -f "$TEMP_FILE"; }
trap cleanup EXIT

echo "Downloading $DOWNLOAD_URL ..."

if command -v curl >/dev/null 2>&1; then
    if ! curl -fL --progress-bar -o "$TEMP_FILE" "$DOWNLOAD_URL"; then
        echo "Error: Failed to download $DOWNLOAD_URL"
        exit 1
    fi
elif command -v wget >/dev/null 2>&1; then
    if ! wget --show-progress -q -O "$TEMP_FILE" "$DOWNLOAD_URL"; then
        echo "Error: Failed to download $DOWNLOAD_URL"
        exit 1
    fi
else
    echo "Error: Neither curl nor wget is available. Please install one of them."
    exit 1
fi

cp -f "$TEMP_FILE" "$JAR_PATH"
echo "Download complete: $JAR_PATH"

# 4-5. Ask user questions and run InstallerUtil
read_yes_no() {
    prompt="$1"
    printf "%s (yes/no) [no]: " "$prompt"
    read -r answer < /dev/tty
    answer=$(echo "$answer" | tr '[:upper:]' '[:lower:]' | xargs)
    if [ "$answer" = "yes" ] || [ "$answer" = "y" ]; then
        return 0
    else
        return 1
    fi
}

export_val="false"
import_val="false"

if read_yes_no "Would you like to inject LinkMind into OpenClaw?"; then
    export_val="true"
fi

if read_yes_no "Would you like to import OpenClaw configurations into LinkMind?"; then
    import_val="true"
fi

echo "Running installer..."
java -cp "$JAR_PATH" ai.starter.InstallerUtil \
    "--export-to-openclaw=$export_val" \
    "--import-from-openclaw=$import_val" || {
    rc=$?
    echo "Error: Installer exited with code $rc"
    exit $rc
}

# 6. Success message
echo ""
echo "LinkMind installed successfully!"
echo ""

# 7. Optionally start LinkMind
if read_yes_no "Would you like to start LinkMind now?"; then
    cd "$LINKMIND_DIR"
    java -jar "$JAR_NAME"
else
    echo "You can start LinkMind later by running:"
    echo "  cd $LINKMIND_DIR && java -jar $JAR_NAME"
fi
