#!/bin/sh
# Lit les captures de captures/ avec ML Kit sur l'appareil/émulateur connecté
# et copie le résultat dans android/app/src/test/resources/ocr/ pour les tests du parseur.
# (am instrument plutôt que connectedAndroidTest, qui désinstalle l'appli et efface les fichiers.)
set -e
cd "$(dirname "$0")/../android"
./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class be.ombres.conseil.OcrDumpTest be.ombres.conseil.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p app/src/test/resources/ocr
adb pull /sdcard/Android/data/be.ombres.conseil/files/ocr/. app/src/test/resources/ocr/
for f in app/src/test/resources/ocr/*.txt; do echo "== $f"; cat "$f"; echo; done
rm -f app/src/test/resources/ocr/*.txt
