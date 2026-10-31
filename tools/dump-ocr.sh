#!/bin/sh
# Lit les captures de captures/ avec ML Kit sur l'appareil/émulateur connecté
# et copie le résultat dans android/engine/src/jvmTest/resources/ocr/ pour les tests du parseur.
# (am instrument plutôt que connectedAndroidTest, qui désinstalle l'appli et efface les fichiers.)
set -e
cd "$(dirname "$0")/../android"
./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class be.ombres.conseil.OcrDumpTest be.ombres.conseil.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p engine/src/jvmTest/resources/ocr
adb pull /sdcard/Android/data/be.ombres.conseil/files/ocr/. engine/src/jvmTest/resources/ocr/
for f in engine/src/jvmTest/resources/ocr/*.txt; do echo "== $f"; cat "$f"; echo; done
rm -f engine/src/jvmTest/resources/ocr/*.txt
