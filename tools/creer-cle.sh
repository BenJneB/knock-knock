#!/bin/sh
# Crée la clé de signature des APK à distribuer (à faire une seule fois) et affiche ce qu'il faut
# enregistrer dans GitHub : Settings > Secrets and variables > Actions > New repository secret.
# Garder la clé ET ses mots de passe en lieu sûr : sans eux, impossible de publier une mise à jour
# qui s'installe par-dessus la version déjà installée chez les chefs.
set -e
dest="${1:-$HOME/.android/conseil-de-guerre.jks}"
[ -e "$dest" ] && { echo "La clé existe déjà : $dest" >&2; exit 1; }
keytool="${JAVA_HOME:-/snap/android-studio/current/jbr}/bin/keytool"
mkdir -p "$(dirname "$dest")"
"$keytool" -genkeypair -v -keystore "$dest" -alias conseil -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=Conseil de guerre, O=s40-Ombres"
echo
echo "Clé créée : $dest (alias : conseil). Secrets GitHub à créer :"
echo "  CONSEIL_KEYSTORE_BASE64   = le contenu ci-dessous (une seule ligne)"
echo "  CONSEIL_KEYSTORE_PASSWORD = le mot de passe saisi pour le magasin de clés"
echo "  CONSEIL_KEY_ALIAS         = conseil"
echo "  CONSEIL_KEY_PASSWORD      = le mot de passe de la clé (souvent le même)"
echo
base64 -w0 "$dest"; echo
echo
echo "Pour signer aussi en local, ajouter à ~/.gradle/gradle.properties :"
echo "  conseil.keystore=$dest"
echo "  conseil.keystore_password=…"
echo "  conseil.key_alias=conseil"
echo "  conseil.key_password=…"
