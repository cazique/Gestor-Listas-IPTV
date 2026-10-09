#!/usr/bin/env bash
# versionCode = 100 + número de ejecución del flujo (estrictamente creciente).
# versionName = 0.<fase>.<número de ejecución>; etiqueta = v<versionName>.
set -euo pipefail
fase=$(grep -E '^fase=' version.properties | cut -d= -f2 | tr -d '[:space:]')
codigo=$((100 + GITHUB_RUN_NUMBER))
nombre="0.${fase}.${GITHUB_RUN_NUMBER}"
echo "VERSION_CODE=$codigo" >> "$GITHUB_ENV"
echo "VERSION_NAME=$nombre" >> "$GITHUB_ENV"
echo "versionCode=$codigo versionName=$nombre"
