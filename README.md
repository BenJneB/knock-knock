# Conseil de guerre

Lit l'écran de **Knock Knock: Room Defense** et propose un plan de bataille, sans IA ni connexion.
Voir `BRIEF.md` pour le cahier des charges.

```
android/    appli Android (projet Gradle)
web/        version web (conseil-zone-sombre-chefs.html : moteur plan() de référence)
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

## Organisation de l'appli

Le cœur (bulle, capture, lecture du texte) est commun ; chaque mode de jeu a son dossier.

```
android/app/src/main/java/be/ombres/conseil/
  MainActivity.kt         accueil : réglages, bulle, lecture d'une capture enregistrée
  CouncilActivity.kt      écran de conseil du mode actif
  capture/BubbleService   bulle flottante + MediaProjection
  ocr/                    ML Kit, découpe/agrandissement des zones, vote entre lectures
  core/Prefs              réglages communs (nom de la guilde, mode actif)
  core/Analyzer           lecture d'une capture par le mode actif, gardée pour « Signaler une lecture ratée »
  ui/                     thème (couleurs du jeu), en-tête, composants ; icônes dans res/drawable
  mode/GameMode.kt        interface d'un mode + liste des modes
  mode/zonesombre/        Affrontement Zone sombre
    ZsPlan.kt             port fidèle de plan() (web/conseil-zone-sombre-chefs.html)
    ZsReader.kt           lecture du bandeau, du temps restant, du coffre et des salles de la carte
                          (étiquettes nom + DMG, bulle de bouclier par la couleur, « Volé », salles libres)
    ZsMerge.kt            report d'une lecture dans les valeurs mémorisées
    ZoneSombreMode.kt     écran de conseil et réglages du mode
```

Ajouter un mode (ex. Défense de la capitale) : créer `mode/<nom>/` avec un objet qui implémente
`GameMode`, puis l'ajouter à `Modes.all`.

## Tests

- `ZsPlanGoldenTest` : 400 scénarios passés dans le `plan()` JavaScript (`node tools/zs-golden-plan.mjs`)
  doivent donner exactement le même résultat en Kotlin. À régénérer après toute modification du moteur web.
- `ZsReaderTest` : rejoue les lectures ML Kit enregistrées sur les captures de `captures/`
  (les pixels, pour les boucliers, sont lus directement dans les captures).
  ML Kit ne tourne que sur Android : après avoir ajouté une capture ou modifié les zones lues,
  relancer `tools/dump-ocr.sh` avec un émulateur ou un téléphone branché.
- Lecture ratée : sur l'écran de conseil, « Signaler une lecture ratée » partage la capture, le texte lu
  par ML Kit et le résultat. `tools/import-lecture.sh lecture-….png` les range dans `captures/` et dans
  les tests ; il reste à écrire les valeurs attendues dans `ZsReaderTest`.
