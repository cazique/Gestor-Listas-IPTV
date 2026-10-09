#!/usr/bin/env bash
# Alinea y firma el APK con la clave de los secretos. Nunca imprime los secretos.
# Uso: tools/ci_firmar.sh <apk-sin-firmar> <apk-firmado>
set -euo pipefail
entrada=$1; salida=$2
umask 077
bt="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
echo "build-tools: $bt"
ks="$RUNNER_TEMP/firma.jks"
trap 'rm -f "$ks"' EXIT
printf '%s' "$KEYSTORE_BASE64" | base64 -d > "$ks"
mkdir -p "$(dirname "$salida")"
"$bt/zipalign" -p -f 4 "$entrada" "$RUNNER_TEMP/alineado.apk"
"$bt/apksigner" sign --ks "$ks" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD \
  --out "$salida" "$RUNNER_TEMP/alineado.apk"
"$bt/apksigner" verify --print-certs "$salida" | grep -E 'Signer #1 certificate (DN|SHA-256)'
rm -f "$salida.idsig"
