#!/bin/sh
# Contrôle de sécurité : l'APK ne doit demander que les autorisations prévues. En particulier, aucune autorisation
# réseau : l'appli fonctionne sans connexion (brief), et une bibliothèque qui en ajouterait une ferait échouer la CI.
#   tools/verifier-autorisations.sh [chemin/vers/app.apk]
set -e
cd "$(dirname "$0")/.."
apk="${1:-android/app/build/outputs/apk/release/app-arm64-v8a-release.apk}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
aapt2="$(ls -d "$sdk"/build-tools/*/aapt2 | sort -V | tail -1)"
attendues="android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
android.permission.POST_NOTIFICATIONS
android.permission.SYSTEM_ALERT_WINDOW
be.ombres.conseil.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
trouvees="$("$aapt2" dump permissions "$apk" | sed -n "s/.*name='\([^']*\)'.*/\1/p" | sort -u)"
if [ "$trouvees" != "$attendues" ]; then
  echo "Autorisations inattendues dans $apk :"
  printf '%s\n' "$trouvees" | grep -vxF "$attendues" | sed 's/^/  + /' || true
  printf '%s\n' "$attendues" | grep -vxF "$trouvees" | sed 's/^/  - /' || true
  exit 1
fi
echo "Autorisations conformes ($(printf '%s\n' "$trouvees" | wc -l)), aucune autorisation réseau."
