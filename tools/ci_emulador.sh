#!/usr/bin/env bash
# Instala el APK en el emulador, abre la app y comprueba que sigue viva a los 25 s.
# Guarda el registro de fallos y una captura de pantalla en salida-emulador/.
# Uso: tools/ci_emulador.sh <apk> <nombre>
set -uo pipefail
apk=$1; nombre=$2
mkdir -p salida-emulador
adb wait-for-device
adb shell getprop ro.build.version.release
adb install -r -g "$apk" || exit 1
adb logcat -c || true
adb shell am start -W -n es.cazique.iptvgestor/.ui.MainActivity
sleep 25
adb logcat -d -b crash > "salida-emulador/fallos-$nombre.txt" || true
adb logcat -d > "salida-emulador/logcat-$nombre.txt" || true
adb exec-out screencap -p > "salida-emulador/captura-$nombre.png" || true
if adb shell pidof es.cazique.iptvgestor >/dev/null; then vivo=1; else vivo=0; fi
echo "== Fallos registrados ($nombre):"
cat "salida-emulador/fallos-$nombre.txt"
# Errores que la app capturó (tareas de fondo) sin llegar a cerrarse: también cuentan como fallo.
grep -A30 "IptvGestorFallo" "salida-emulador/logcat-$nombre.txt" > "salida-emulador/errores-app-$nombre.txt" || true
cat "salida-emulador/errores-app-$nombre.txt"
if [ "$vivo" = 1 ] && ! grep -q "es.cazique.iptvgestor" "salida-emulador/fallos-$nombre.txt" \
   && [ ! -s "salida-emulador/errores-app-$nombre.txt" ]; then
  echo "La app sigue abierta tras 25 s: OK"
else
  echo "::error::La app se ha cerrado en el emulador ($nombre)"
  grep -A40 "FATAL EXCEPTION" "salida-emulador/logcat-$nombre.txt" | head -80 || true
  exit 1
fi
