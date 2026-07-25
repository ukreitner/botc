#!/usr/bin/env bash
# Fetches the official Blood on the Clocktower character icons (as bundled
# by the community townsquare project) and installs them as app assets,
# named by our normalized character ids. Run before assembling the APK.
#
# The art is (c) Steven Medway / The Pandemonium Institute. It is fetched
# at build time for personal, non-commercial storyteller use and is
# deliberately NOT committed to this repository.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/icons"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Downloading icon bundle..."
curl -sSL -o "$WORK/townsquare.tar.gz" \
  "https://github.com/bra1n/townsquare/archive/refs/heads/main.tar.gz"
tar -xzf "$WORK/townsquare.tar.gz" -C "$WORK"

ICON_DIR="$(find "$WORK" -type d -path '*/src/assets/icons' | head -1)"
if [ -z "$ICON_DIR" ]; then
  echo "Icon directory not found in bundle" >&2
  exit 1
fi

mkdir -p "$DEST"
node - "$ICON_DIR" "$DEST" "$ROOT/engine/src/main/resources/botc/data/characters.json" <<'EOF'
const fs = require('fs');
const path = require('path');
const [iconDir, dest, charactersPath] = process.argv.slice(2);
const ids = new Set(JSON.parse(fs.readFileSync(charactersPath, 'utf8')).map(c => c.id));
const normalize = s => s.toLowerCase().replace(/[^a-z0-9]/g, '');
let copied = 0;
const found = new Set();
for (const file of fs.readdirSync(iconDir)) {
  const ext = path.extname(file).toLowerCase();
  if (!['.png', '.webp'].includes(ext)) continue;
  const id = normalize(path.basename(file, ext));
  if (!ids.has(id)) continue;
  fs.copyFileSync(path.join(iconDir, file), path.join(dest, id + ext));
  found.add(id); copied++;
}
const missing = [...ids].filter(id => !found.has(id));
console.log(`Installed ${copied} icons to ${dest}`);
if (missing.length) {
  console.log(`No icon for ${missing.length} ids (emoji fallback will be used): ${missing.join(', ')}`);
}
EOF
