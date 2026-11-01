# Conseil de guerre

Lit l'écran de **Knock Knock: Room Defense** et propose un plan de bataille, sans IA ni connexion.
Voir `BRIEF.md` pour le cahier des charges.

Modes de jeu : **Affrontement Zone sombre** (cartes d'exorcisme, élimination) et **Défense de la capitale**
(tentatives de défi, classement au total d'Âme ; règles : `captures/regle-capitale*.jpeg`).

```
android/    projet Gradle : appli Android (app/) et moteur commun (engine/)
web/        version web : sources dans web/src/ ; pages assemblées dans web/dist/ (générées, non versionnées :
            une page par mode en un seul fichier, à ouvrir ou à envoyer telle quelle) ; lit aussi une capture
            de la carte (bandeau, temps) avec Tesseract.js
.github/    tests automatiques sur GitHub (GitHub Actions)
captures/   captures du jeu, communes aux deux (données de test)
tools/      scripts : vérification du moteur JavaScript, lecture des captures sur émulateur, clé de signature
```

## Construire l'appli Android

JDK : celui d'Android Studio (`/snap/android-studio/current/jbr`).

```sh
cd android
export JAVA_HOME=/snap/android-studio/current/jbr
./gradlew :app:assembleRelease        # APK à installer : android/app/build/outputs/apk/release/app-arm64-v8a-release.apk
./gradlew :app:assembleDebug          # version de développement (app-universal-debug.apk : tous processeurs)
./gradlew :engine:allTests           # tests du moteur : JVM et JavaScript (Node)
./gradlew ktlintCheck :app:lintDebug  # style du code (ktlint, règles dans android/.editorconfig) et Android Lint
```

Un APK par type de processeur : `arm64-v8a` pour presque tous les téléphones (≈ 14 Mo),
`armeabi-v7a` pour les plus anciens, `x86_64` pour l'émulateur, `universal` pour tous (≈ 44 Mo).

## Distribuer l'appli aux chefs

À chaque push, GitHub construit l'APK et le propose en téléchargement : onglet *Actions*, dernier passage,
artefact « apk-android » (`app-arm64-v8a-release.apk` pour presque tous les téléphones,
`app-armeabi-v7a-release.apk` pour les plus anciens). Le numéro de version suit le numéro du passage, pour que
chaque nouvel APK s'installe par-dessus le précédent.

Signature (une seule fois) : `tools/creer-cle.sh` crée la clé et affiche les 4 secrets à enregistrer dans
GitHub (*Settings > Secrets and variables > Actions*). Garder la clé et ses mots de passe en lieu sûr : sans eux,
plus de mise à jour possible par-dessus la version installée. Tant que les secrets n'existent pas, l'APK est
signé avec une clé de debug (avertissement dans le passage). Un téléphone qui a une version signée autrement
(clé de debug) doit désinstaller l'appli une fois avant d'installer la version signée.

## Moteur commun (android/engine)

Stratégie, lecture des captures et fusion des valeurs lues : un seul code Kotlin, utilisé par l'appli
(JVM) et par la page web (compilé en JavaScript). Après une modification :

```sh
cd android
./gradlew :engine:allTests           # tests du moteur (JVM et Node)
./gradlew :engine:webPage            # recompile le moteur et assemble les pages web (web/dist/)
node ../tools/check-web-engine.mjs   # vérifie la version JavaScript sur les mêmes scénarios
```

## Pages web (web/src)

Les pages à utiliser (`web/dist/*.html`) sont générées par `./gradlew :engine:webPage` à partir des
sources de `web/src/` ; elles ne sont pas versionnées. Elles sont aussi assemblées par GitHub à chaque
push : artefact « pages-web » du passage, dans l'onglet *Actions*. Un dossier par mode, et ce que les pages partagent dans `commun/` ;
chaque page inclut ce dont elle a besoin (`/* inclure:commun/script.js */`, `<!-- inclure:commun/guildes.html -->`…).

```
web/src/
  zone-sombre/   page.html (formulaire, verdict) + script.js   → web/dist/conseil-zone-sombre-chefs.html
  capitale/      page.html + script.js                          → web/dist/defense-capitale.html
  commun/        entete.html  style.css  icones.svg  guildes.html  actions.html
                 script.js : mémorisation, formulaire des guildes, affichage du plan, lecture d'une capture
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
  core/SoulHistory        Âme lue à chaque capture : production mesurée d'une capture à l'autre
  ui/                     thème (couleurs du jeu), en-tête, composants ; icônes dans res/drawable
  mode/GameMode.kt        interface d'un mode + liste des modes
  mode/ModeUi.kt          éléments d'écran communs aux modes (bloc « Lecture », guildes, copie du plan)
  mode/zonesombre/        Affrontement Zone sombre (partie Android) : mémorisation, écran de conseil
  mode/capitale/          Défense de la capitale (partie Android) : mémorisation, écran de conseil

android/engine/src/commonMain/kotlin/be/ombres/conseil/
  ocr/                    modèle du texte lu, zones à relire, vote entre lectures
  game/                   nombres saisis ou lus (724.44K), guildes, étapes de plan, types de salles (lits, production)
  map/                    lecture de la carte du match (MapReader : salles, type d'après la couleur du sol, bonus
                          « 50% »), report du bandeau, production mesurée — communs aux modes
  mode/capitale/          DcPlan (stratégie), DcMerge (report d'une lecture)
  mode/zonesombre/
    ZsPlan.kt             moteur de stratégie (plan), verdict, texte à partager ; valeur d'une salle d'après
                          son type et son bonus quand ils sont connus, sinon moyenne des salles du rival
    ZsMerge.kt            report d'une lecture dans les valeurs mémorisées
    ZsHistory.kt          mesure de la part volée par un voleur entre deux captures
```

Ajouter un mode (ex. Défense de la capitale) : son moteur et sa lecture dans
`engine/…/mode/<nom>/`, son écran dans `app/…/mode/<nom>/` (un objet qui implémente `GameMode`,
ajouté à `Modes.all`).

## Tests et qualité

- Tests communs (`engine/src/commonTest`) : exécutés sur la JVM **et** sous Node, pour vérifier que l'appli et la
  page web lisent et calculent pareil (expressions régulières, arrondis, mise en forme des nombres).
- ktlint (style Kotlin, `./gradlew ktlintFormat` corrige) et Android Lint (tout avertissement bloque) tournent dans la CI.
- Node : version fixée pour le moteur JavaScript (`android/build.gradle.kts`, alignée avec la CI), à mettre à jour
  lors des correctifs de sécurité de Node.js.


- `ZsPlanGoldenTest` : 400 scénarios de référence (non-régression du moteur), vérifiés aussi sur la
  version JavaScript par `tools/check-web-engine.mjs`.
- `MapReaderTest` : rejoue les lectures ML Kit enregistrées sur les captures de `captures/`
  (les pixels, pour les boucliers, sont lus directement dans les captures).
  ML Kit ne tourne que sur Android : après avoir ajouté une capture ou modifié les zones lues,
  relancer `tools/dump-ocr.sh` avec un émulateur ou un téléphone branché.
- Lecture ratée : sur l'écran de conseil, « Signaler une lecture ratée » partage la capture, le texte lu
  par ML Kit et le résultat. `tools/import-lecture.sh lecture-….png` les range dans `captures/` et dans
  les tests ; il reste à écrire les valeurs attendues dans `MapReaderTest`.
