#!/bin/bash
# Preview the shared UI in a browser at http://127.0.0.1:8765/.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/web"
exec python3 -m http.server 8765 --bind 127.0.0.1
