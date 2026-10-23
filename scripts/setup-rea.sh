#!/usr/bin/env bash
# Run on your developer computer, not on the Android phone.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v node >/dev/null
command -v npm >/dev/null
command -v java >/dev/null
command -v curl >/dev/null
tool_dir="$PWD/.tools/rea"
mkdir -p "$tool_dir/android"
npm install --prefix "$tool_dir" rea-agents@6.7.0 --no-audit --no-fund
jar="$tool_dir/android/jadx-headless-mcp-0.7.1-all.jar"
curl -fL -o "$jar" https://github.com/1013503897/jadx-headless-mcp/releases/download/v0.7.1/jadx-headless-mcp-0.7.1-all.jar
python3 - "$jar" <<'PY'
import hashlib, pathlib, sys
p=pathlib.Path(sys.argv[1]); expected='6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75'
if hashlib.sha256(p.read_bytes()).hexdigest()!=expected: raise SystemExit('JADX checksum mismatch: refusing to continue')
print('JADX checksum verified')
PY
export REA_JADX_MCP_JAR="$jar"
"$tool_dir/node_modules/.bin/rea" setup --client codex --skill=false --dry-run --json
"$tool_dir/node_modules/.bin/rea" setup --client codex --skill=false --yes
printf '%s\n' 'Restart the Codex client after setup. The new MCP registration is not added to an already running session.'
