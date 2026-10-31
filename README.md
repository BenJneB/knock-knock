# Conseil de guerre

Lit l'écran de **Knock Knock: Room Defense** et propose un plan de bataille, sans IA ni connexion.
Voir `BRIEF.md` pour le cahier des charges.

```
android/    projet Gradle : appli Android (app/) et moteur commun (engine/)
web/        version web : conseil-zone-sombre-chefs.html, un seul fichier (moteur commun compilé en
            JavaScript et intégré) ; lit aussi une capture de la carte (bandeau, temps) avec Tesseract.js
.github/    tests automatiques sur GitHub (GitHub Actions)
captures/   captures du jeu, communes aux deux (données de test)
tools/      scripts : comparaison des moteurs web/Android, lecture des captures sur émulateur
```

## Construire l'appli Android

JDK : celui d'Android Studio (`/snap/android-studio/current/jbr`).

```sh
cd android
export JAVA_HOME=/snap/android-studio/current/jbr
./gradlew :app:assembleRelease        # APK à installer : android/app/build/outputs/apk/release/app-arm64-v8a-release.apk
./gradlew :app:assembleDebug          # version de développement (app-universal-debug.apk : tous processeurs)
./gradlew :app:testDebugUnitTest      # tests JVM (moteur + lecture)
```

Un APK par type de processeur : `arm64-v8a` pour presque tous les téléphones (≈ 14 Mo),
`armeabi-v7a` pour les plus anciens, `x86_64` pour l'émulateur, `universal` pour tous (≈ 44 Mo).

## Moteur commun (android/engine)

Stratégie, lecture des captures et fusion des valeurs lues : un seul code Kotlin, utilisé par l'appli
(JVM) et par la page web (compilé en JavaScript). Après une modification :

```sh
cd android
./gradlew :engine:jvmTest            # tests du moteur
./gradlew :engine:webPage            # recompile le moteur et l'intègre dans la page web
node ../tools/check-web-engine.mjs   # vérifie la version JavaScript sur les mêmes scénarios
```

## Organisation de l'appli

Le cœur (bulle, capture, lecture du texte) est commun ; chaque mode de jeu a son dossier.

```
android/app/src/main/java/be/ombres/conseil/
  MainActivity.kt         accueil : réglages, bulle, lecture d'une capture enregistrée
  CouncilActivity.kt      écran de conseil du mode actif
  capture/BubbleService   bulle flottante + MediaProjection
  ocr/                    ML Kit, découpe/agrandissement des zones
  core/Prefs              réglages communs (nom de la guilde, mode actif)
  core/Analyzer           lecture d'une capture par le mode actif, gardée pour « Signaler une lecture ratée »
  core/Reminders          rappels locaux (fin d'une Sape d'âme, 2 h après)
  ui/                     thème (couleurs du jeu), en-tête, composants ; icônes dans res/drawable
  mode/GameMode.kt        interface d'un mode + liste des modes
  mode/zonesombre/        Affrontement Zone sombre (partie Android)
    ZsStore.kt            mémorisation des valeurs sur le téléphone
    ZoneSombreMode.kt     écran de conseil et réglages du mode

android/engine/src/commonMain/kotlin/be/ombres/conseil/
  ocr/                    modèle du texte lu, zones à relire, vote entre lectures
  mode/zonesombre/
    ZsPlan.kt             moteur de stratégie (plan), verdict, texte à partager
    ZsReader.kt           lecture du bandeau, du temps restant, du coffre et des salles de la carte
                          (étiquettes nom + DMG, bulle de bouclier par la couleur, « Volé », salles libres)
    ZsMerge.kt            report d'une lecture dans les valeurs mémorisées
    ZsHistory.kt          mesure de la part volée par un voleur entre deux captures
```

Ajouter un mode (ex. Défense de la capitale) : son moteur et sa lecture dans
`engine/…/mode/<nom>/`, son écran dans `app/…/mode/<nom>/` (un objet qui implémente `GameMode`,
ajouté à `Modes.all`).

## Tests

- `ZsPlanGoldenTest` : 400 scénarios de référence (non-régression du moteur), vérifiés aussi sur la
  version JavaScript par `tools/check-web-engine.mjs`.
- `ZsReaderTest` : rejoue les lectures ML Kit enregistrées sur les captures de `captures/`
  (les pixels, pour les boucliers, sont lus directement dans les captures).
  ML Kit ne tourne que sur Android : après avoir ajouté une capture ou modifié les zones lues,
  relancer `tools/dump-ocr.sh` avec un émulateur ou un téléphone branché.
- Lecture ratée : sur l'écran de conseil, « Signaler une lecture ratée » partage la capture, le texte lu
  par ML Kit et le résultat. `tools/import-lecture.sh lecture-….png` les range dans `captures/` et dans
  les tests ; il reste à écrire les valeurs attendues dans `ZsReaderTest`.
