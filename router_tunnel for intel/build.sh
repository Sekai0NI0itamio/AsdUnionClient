#!/bin/bash
# Build the RouterTunnel proxy for macOS (Intel)
# Usage: ./build.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/router_tunnel.c"
OUT="$SCRIPT_DIR/router_tunnel"

echo "[build] Compiling router_tunnel.c ..."

# Build for Intel (x86_64). On Apple Silicon, cross-compile with -arch x86_64.
if [[ "$(uname -m)" == "arm64" ]]; then
  echo "[build] Apple Silicon host → cross-compiling for Intel (x86_64)"
  clang -O2 -Wall -Wextra -arch x86_64 -o "$OUT" "$SRC"
else
  echo "[build] Intel host → native build (x86_64)"
  clang -O2 -Wall -Wextra -o "$OUT" "$SRC"
fi

echo "[build] Done → $OUT"
echo ""
echo "Usage:"
echo "  $OUT                    # auto-detect interface, port 25560"
echo "  $OUT --port 25560       # custom port"
echo "  $OUT --http-proxy 25561 # HTTP proxy port for browser routing"
echo "  $OUT --no-http-proxy    # disable HTTP proxy listener"
echo "  $OUT --socks5-proxy 1080 # SOCKS5 proxy port for apps"
echo "  $OUT --no-socks5-proxy  # disable SOCKS5 proxy listener"
echo "  $OUT --socks5-user USER # SOCKS5 username (optional)"
echo "  $OUT --socks5-pass PASS # SOCKS5 password (optional)"
echo "  $OUT --interface en0    # force interface"
echo "  $OUT --always-route     # always install scoped default route"
echo "  $OUT --skip-test        # skip connectivity check"
echo ""
echo "Then enable 'Connect to Router' in the FDPClient multiplayer screen."
