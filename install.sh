#!/usr/bin/env bash
set -euo pipefail

REPO="gituser12981u2/javaWhitelist"
ASSET="javawhitelist.jar"

INSTALL_LIB="${HOME}/.local/lib/javaWhitelist"
INSTALL_BIN="${HOME}/.local/bin"
JAR_PATH="${INSTALL_LIB}/javaWhitelist.jar"
BIN_PATH="${INSTALL_BIN}/javaWhitelist"

mkdir -p "$INSTALL_LIB" "$INSTALL_BIN"

echo "Downloading latest ${ASSET} from ${REPO}..."
URL="$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" \
  | grep -Eo '"browser_download_url":[^"]*"' \
  | cut -d'"' -f4 \
  | grep "/download/.*/${ASSET}$" \
  | head -n 1)"

if [ -z "${URL}" ]; then
  echo "Failed to locate release asset ${ASSET}." >&2
  exit 1
fi

curl -fL "$URL" -o "$JAR_PATH"

cat > "$BIN_PATH" <<'EOF'
#!/usr/bin/env bash
exec java -jar "$HOME/.local/lib/javawhitelist/javawhitelist.jar" "$@"
EOF
chmod +x "$BIN_PATH"

echo ""
echo "Installed:"
echo "  Jar:  ${JAR_PATH}"
echo "  Bin:  ${BIN_PATH}"
echo ""

if ! command -v javawhitelist >/dev/null 2>&1; then
  echo "NOTE: '${INSTALL_BIN}' is not on your PATH."
  echo "Add this to your shell config (~/.zshrc or ~/.bashrc):"
  echo "  export PATH=\"${INSTALL_BIN}:\$PATH\""
  echo "Then restart your shell."
else
  echo "You can now run: javawhitelist --help"
fi
