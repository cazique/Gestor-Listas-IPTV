#!/usr/bin/env bash
# Busca en el árbol de archivos (versionados o a punto de serlo) claves de firma, contraseñas
# y datos del proveedor. Sale con error si encuentra algo. Se usa como pre-commit y en CI.
# Uso: tools/comprobar_secretos.sh            (árbol de git)
#      SECRETOS_EXTRA="cadena1|cadena2" tools/comprobar_secretos.sh   (cadenas conocidas, nunca escritas en el repo)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
fallo=0
archivos=$(git ls-files -co --exclude-standard)

# 1. Almacenes de claves y claves privadas
if echo "$archivos" | grep -Ei '\.(jks|keystore|p12|pfx|pem|key)$'; then
  echo "ERROR: hay almacenes de claves o claves en el árbol"; fallo=1
fi
# 2. Contenido sospechoso (se excluyen este script y los binarios)
patron='BEGIN (RSA |EC |)PRIVATE KEY|KEYSTORE_PASSWORD=[^$ ]|KEY_PASSWORD=[^$ ]|storePassword *= *"[^"]+|keyPassword *= *"[^"]+|password=[A-Za-z0-9]{4,}|/live/[A-Za-z0-9._-]{3,}/[A-Za-z0-9._-]{3,}/[0-9]+\.(ts|m3u8)'
if echo "$archivos" | grep -v -e '^tools/comprobar_secretos.sh$' -e '^backup/' | xargs -r -d '\n' grep -IlE "$patron" 2>/dev/null \
   | xargs -r -d '\n' grep -IHnE "$patron" | grep -vE '\{USER\}|\{PASS\}|\{HOST\}|\$\{\{|env:|TU_CONTRASENA|CONTRASENA|jorgeprueba'; then  # se ignoran valores ficticios
  echo "ERROR: posible credencial en los archivos de arriba"; fallo=1
fi
# 3. Cadenas concretas aportadas por variable de entorno (contraseñas reales, host del proveedor)
if [ -n "${SECRETOS_EXTRA:-}" ]; then
  if echo "$archivos" | xargs -r -d '\n' grep -IlF -e "$(echo "$SECRETOS_EXTRA" | tr '|' '\n')" 2>/dev/null; then
    echo "ERROR: aparece una cadena secreta conocida"; fallo=1
  fi
fi
[ $fallo -eq 0 ] && echo "Sin secretos en el árbol."
exit $fallo
