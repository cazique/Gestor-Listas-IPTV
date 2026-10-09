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
# Tolera espacios o saltos de línea al pegar el secreto en GitHub.
printf '%s' "$KEYSTORE_BASE64" | tr -d ' \r\n\t' | base64 -d > "$ks"
mkdir -p "$(dirname "$salida")"
"$bt/zipalign" -p -f 4 "$entrada" "$RUNNER_TEMP/alineado.apk"
"$bt/apksigner" sign --ks "$ks" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD \
  --out "$salida" "$RUNNER_TEMP/alineado.apk"
# Verifica la firma (si falla, el script termina con error) y muestra la huella pública del certificado.
info=$("$bt/apksigner" verify --verbose --print-certs "$salida")
echo "$info" | grep -iE 'verified using|certificate (DN|SHA-256)' || echo "$info" | head -20
rm -f "$salida.idsig"
