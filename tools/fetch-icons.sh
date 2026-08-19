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
// Anything still without a file on disk (the destination may already
// hold wiki icons restored from the CI cache) goes to the wiki pass.
const missing = [...ids].filter(id =>
  !fs.existsSync(path.join(dest, id + '.png')) &&
  !fs.existsSync(path.join(dest, id + '.webp')));
console.log(`Installed ${copied} icons from the bundle`);
fs.writeFileSync(path.join(dest, '.missing'), missing.join('\n'));
EOF

# Second pass: newer characters absent from the bundle come straight from
# the official wiki. The wiki's Special:FilePath redirect (and anything
# that doesn't look like a real browser) gets HTTP 418 from its bot
# shield, so we compute MediaWiki's hashed image path directly —
# /images/{m1}/{m1m2}/{filename} with m = md5(filename) — and send
# browser-like headers.
md5hex() {
  if command -v md5sum >/dev/null 2>&1; then
    printf '%s' "$1" | md5sum | cut -c1-32
  else
    printf '%s' "$1" | md5 | cut -c1-32
  fi
}
BROWSER_UA="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
MISSING_FILE="$DEST/.missing"
if [ -s "$MISSING_FILE" ]; then
  echo "Fetching remaining icons from the wiki..."
  consecutive_misses=0
  # `|| [ -n "$id" ]` still processes a final line with no trailing
  # newline — exactly how node writes the list (this silently skipped the
  # last missing icon for months).
  while IFS= read -r id || [ -n "$id" ]; do
    [ -z "$id" ] && continue
    got=""
    for candidate in "Icon_${id}.png" "Icon_${id}.webp"; do
      hash="$(md5hex "$candidate")"
      url="https://wiki.bloodontheclocktower.com/images/${hash:0:1}/${hash:0:2}/${candidate}"
      out="$WORK/wiki-$id"
      if curl -sSf --max-time 15 --retry 1 --retry-delay 2 -o "$out" "$url" \
           -H "User-Agent: $BROWSER_UA" \
           -H "Accept: image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8" \
           -H "Accept-Language: en-US,en;q=0.9" \
           -H "Referer: https://wiki.bloodontheclocktower.com/" 2>/dev/null; then
        # Accept only real image payloads (PNG or WEBP magic bytes).
        magic=$(head -c 4 "$out" | od -An -tx1 | tr -d ' \n')
        if [ "${magic:0:8}" = "89504e47" ] || [ "${magic:0:8}" = "52494646" ]; then
          ext=".png"
          [ "${magic:0:8}" = "52494646" ] && ext=".webp"
          cp "$out" "$DEST/${id}${ext}"
          echo "  wiki: $id"
          got=1
          break
        fi
      fi
    done
    if [ -n "$got" ]; then
      consecutive_misses=0
    else
      consecutive_misses=$((consecutive_misses + 1))
      if [ "$consecutive_misses" -ge 4 ]; then
        echo "Wiki looks unreachable ($consecutive_misses straight misses) — stopping this pass." >&2
        echo "Previously cached art (restored by CI) still applies; the rest use monograms." >&2
        break
      fi
    fi
  done < "$MISSING_FILE"
fi
rm -f "$MISSING_FILE"

TOTAL=$(ls "$DEST" | wc -l)
node - "$DEST" "$ROOT/engine/src/main/resources/botc/data/characters.json" <<'EOF'
const fs = require('fs');
const [dest, charactersPath] = process.argv.slice(2);
const ids = JSON.parse(fs.readFileSync(charactersPath, 'utf8')).map(c => c.id);
const have = new Set(fs.readdirSync(dest).map(f => f.replace(/\.(png|webp)$/, '')));
const missing = ids.filter(id => !have.has(id));
console.log(`Total icons installed: ${have.size} of ${ids.length}`);
if (missing.length) {
  console.log(`Still missing (monogram fallback): ${missing.join(', ')}`);
}
EOF
