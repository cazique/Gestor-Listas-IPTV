#!/usr/bin/env bash
# Genera SHA256SUMS, update.json y las notas del Release (mensajes de commit desde la versión anterior).
# Uso: tools/ci_metadatos.sh <carpeta-con-app-release.apk>
set -euo pipefail
dir=$1
cd "$dir"
sha=$(sha256sum app-release.apk | cut -d' ' -f1)
sha256sum app-release.apk > SHA256SUMS
cd - >/dev/null
anterior=$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)
if [ -n "$anterior" ]; then rango="$anterior..HEAD"; else rango="-20 HEAD"; fi
# shellcheck disable=SC2086
git log --no-merges --pretty='- %s' $rango > "$dir/notas.md"
[ -s "$dir/notas.md" ] || echo "- Recompilación sin cambios" > "$dir/notas.md"
minsdk=$(grep -oE 'minSdk *= *[0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')
APK_URL="https://github.com/${GITHUB_REPOSITORY}/releases/download/v${VERSION_NAME}/app-release.apk" \
SHA="$sha" MINSDK="$minsdk" NOTAS="$dir/notas.md" python3 - > "$dir/update.json" <<'PY'
import json, os, datetime
print(json.dumps({
    "versionCode": int(os.environ["VERSION_CODE"]),
    "versionName": os.environ["VERSION_NAME"],
    "apkUrl": os.environ["APK_URL"],
    "sha256": os.environ["SHA"],
    "minSdk": int(os.environ["MINSDK"]),
    "releaseNotes": open(os.environ["NOTAS"], encoding="utf-8").read().strip(),
    "publishedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
}, ensure_ascii=False, indent=2))
PY
cat "$dir/update.json"
