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
# Segunda captura: la sección Ajustes (con el mando en TV; tocando la barra inferior en el móvil).
if [ "$nombre" = "tv" ]; then
  for _ in 1 2 3 4 5 6; do adb shell input keyevent KEYCODE_DPAD_LEFT; done
  for _ in 1 2 3 4; do adb shell input keyevent KEYCODE_DPAD_DOWN; done
  adb shell input keyevent KEYCODE_DPAD_CENTER
else
  tam=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); ancho=${tam%x*}; alto=${tam#*x}
  adb shell input tap $((ancho * 9 / 10)) $((alto * 7 / 8))
fi
sleep 4
adb exec-out screencap -p > "salida-emulador/captura-$nombre-ajustes.png" || true
if adb shell pidof es.cazique.iptvgestor >/dev/null; then vivo=1; else vivo=0; fi
# Pruebas instrumentadas (motor con fixtures sobre ICU), solo en el emulador de móvil.
instr=0
if [ "$nombre" = "movil" ]; then
  ./gradlew connectedDebugAndroidTest --stacktrace > "salida-emulador/instrumentadas.txt" 2>&1 || instr=1
  tail -40 "salida-emulador/instrumentadas.txt"
  cp -r app/build/reports/androidTests salida-emulador/ 2>/dev/null || true
fi
echo "== Fallos registrados ($nombre):"
cat "salida-emulador/fallos-$nombre.txt"
# Errores que la app capturó (tareas de fondo) sin llegar a cerrarse: también cuentan como fallo.
grep -A30 "IptvGestorFallo" "salida-emulador/logcat-$nombre.txt" > "salida-emulador/errores-app-$nombre.txt" || true
cat "salida-emulador/errores-app-$nombre.txt"
if [ "$vivo" = 1 ] && ! grep -q "es.cazique.iptvgestor" "salida-emulador/fallos-$nombre.txt" \
   && [ ! -s "salida-emulador/errores-app-$nombre.txt" ] && [ "$instr" = 0 ]; then
  echo "La app sigue abierta tras 25 s: OK"
else
  echo "::error::Fallo en el emulador ($nombre): vivo=$vivo, pruebas instrumentadas=$instr"
  grep -A40 "FATAL EXCEPTION" "salida-emulador/logcat-$nombre.txt" | head -80 || true
  exit 1
fi
