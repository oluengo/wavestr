#!/usr/bin/env bash
# Prueba de arranque en un emulador (CI): instala el APK, abre la app, espera y busca cierres.
# Deja el resultado como anotaciones de GitHub Actions.
set -u
apk=$(ls dist/*.apk | head -1)
pkg=$(aapt2 dump packagename "$apk" 2>/dev/null || true)
[ -z "$pkg" ] && pkg=$(/usr/local/lib/android/sdk/build-tools/*/aapt2 dump packagename "$apk" 2>/dev/null | head -1)
echo "APK: $apk · paquete: $pkg"
adb install -r -g "$apk" || { echo "::error::Android: no se ha podido instalar el APK"; exit 1; }
adb logcat -c
adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
sleep 20
crash=$(adb logcat -d | grep -A30 -E "FATAL EXCEPTION|AndroidRuntime: Process: $pkg" | head -40)
if [ -n "$crash" ]; then
  echo "::error::Android: la app se cierra al arrancar%0A$(echo "$crash" | sed 's/^.*AndroidRuntime: //' | head -25 | sed ':a;N;$!ba;s/\n/%0A/g')"
  exit 1
fi
if adb shell pidof "$pkg" > /dev/null; then
  echo "::notice::Android: la app arranca y sigue abierta a los 20 s"
else
  echo "::error::Android: la app no sigue abierta a los 20 s (sin excepción en el log)"
  adb logcat -d | tail -30 | sed 's/^/::notice::log /'
  exit 1
fi
