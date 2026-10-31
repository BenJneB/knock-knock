#!/bin/sh
# Range une lecture signalée depuis l'appli (« Signaler une lecture ratée ») dans les données de test :
#   tools/import-lecture.sh chemin/lecture-AAAAMMJJ-HHMMSS.png
# Le .json (texte lu par ML Kit) doit être à côté du .png. La capture va dans captures/, le texte lu dans
# android/engine/src/jvmTest/resources/ocr/ : ajouter ensuite les valeurs attendues dans MapReaderTest.
set -e
png="$1"
[ -f "$png" ] || { echo "usage : $0 lecture-….png" >&2; exit 1; }
json="${png%.png}.json"
[ -f "$json" ] || { echo "fichier manquant : $json" >&2; exit 1; }
root="$(dirname "$0")/.."
name="$(basename "${png%.png}")"
cp "$png" "$root/captures/$name.png"
cp "$json" "$root/android/engine/src/jvmTest/resources/ocr/$name.json"
[ -f "${png%.png}.txt" ] && { echo "Résultat lu par l'appli :"; cat "${png%.png}.txt"; echo; }
echo "Ajouté : captures/$name.png et android/engine/src/jvmTest/resources/ocr/$name.json"
