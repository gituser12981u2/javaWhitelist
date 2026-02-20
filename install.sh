#!/usr/bin/env bash
set -euo pipefail

REPO="gituser12981u2/javaWhitelist"
ASSET="javaWhitelist.jar"

INSTALL_LIB="${HOME}/.local/lib/javaWhitelist"
INSTALL_BIN="${HOME}/.local/bin"
JAR_PATH="${INSTALL_LIB}/${ASSET}"
BIN_PATH="${INSTALL_BIN}/javaWhitelist"

mkdir -p "$INSTALL_LIB" "$INSTALL_BIN"

URL="https://github.com/${REPO}/releases/latest/download/${ASSET}"

echo "Downloading ${ASSET} from ${URL}..."
curl -fL "$URL" -o "$JAR_PATH"

cat > "$BIN_PATH" <<'EOF'
#!/usr/bin/env bash
exec java -jar "$HOME/.local/lib/javaWhitelist/javaWhitelist.jar" "$@"
EOF

chmod +x "$BIN_PATH"

echo ""
echo "Installed:"
echo "  Jar:  ${JAR_PATH}"
echo "  Bin:  ${BIN_PATH}"
echo ""

if ! command -v javaWhitelist >/dev/null 2>&1; then
  echo "NOTE: '${INSTALL_BIN}' is not on your PATH."
  echo "Add this to your shell config (~/.zshrc or ~/.bashrc):"
  echo "  export PATH=\"${INSTALL_BIN}:\$PATH\""
  echo "Then restart your shell."
else
  echo "You can now run: javaWhitelist --help"
fi
