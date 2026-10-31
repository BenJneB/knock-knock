# Projet : Conseil de guerre – appli Android (version sans IA)

## Contexte
Jeu mobile **Knock Knock: Room Defense** (Joy Nice Games), mode **Affrontement Zone sombre**.
Je joue dans la guilde **s40-Ombres**. Les chefs et vice-chefs de la guilde jouent les cartes d’exorcisme.
Objectif : une appli Android qui lit l’écran du jeu et affiche le meilleur plan de bataille, **sans IA ni connexion**.

Une version web existe déjà : `conseil-zone-sombre-chefs.html` (dans ce dossier). Son moteur de stratégie
(fonction `plan()`) est la référence à porter en Kotlin. Les captures d’écran du jeu sont dans `captures/`.

## Règles du mode
- Phase éliminatoire : groupes de 3 guildes, matchs de 10:00 à 22:00. **Seul le 1er du groupe passe**
  (jour 1 : 81→27, jour 2 : 27→9, jour 3 : 9→3, puis finale). Finir 2e = éliminé.
- Chaque guilde accumule de l’Âme selon son efficacité (/s), qui dépend des salles qu’elle occupe.
  Le plus d’Âme à la fin gagne.
- Cartes d’exorcisme : 24 au départ (2 Esprit malveillant, 10 Sape d’âme, 6 Garde immaculée,
  6 Sanctuaire sacré), aucune nouvelle carte ensuite, max 15 par jour, conservées d’un match à l’autre,
  supprimées à la fin de la phase. Jouées uniquement par chef et vice-chef.
  - **Esprit malveillant** : vide une salle d’une autre guilde. Impossible sur une salle avec bouclier.
  - **Sape d’âme** : voleur secret dans une salle ennemie, vole une partie de sa production pendant 2 h.
    Impossible sur une salle avec bouclier. (La part volée n’est pas indiquée : 25 % par défaut, réglable.)
  - **Garde immaculée** : bouclier sur une de nos salles ; personne ne peut la défier ni y jouer
    Esprit malveillant ou Sape d’âme.
  - **Sanctuaire sacré** : sur notre salle, élimine le voleur ; sur une salle ennemie, supprime son bouclier.

## Principes de stratégie (déjà dans `plan()`)
1. Protéger d’abord notre salle principale (bouclier), garder 1 Garde en réserve si elle est déjà protégée.
2. Seul le rival qui menace la 1re place compte. Une salle prise au rival compte double.
3. Actions gratuites d’abord (combat sur salle battable sans bouclier, salle libre), puis les cartes
   au meilleur gain par carte, dans la limite du jour.
4. S’arrêter quand la victoire est assurée avec une marge (une salle rivale pendant 1 h),
   pour garder des cartes pour les tours suivants. En finale : tout jouer.

## Ce que l’écran montre (voir `captures/`)
- **Bandeau du haut**, 3 colonnes (une par guilde) : nom (ex. `s18-Noxia`), nombre de salles par type
  (icônes maison verte / violette / rouge + chiffre), `Âme : 733.95K`, `Efficacité : 34/s`.
- Sous le bandeau : `Fin dans : 4 h 27 min`.
- **Carte** : chaque salle occupée a une étiquette (nom de guilde, `DMG : 42.15B`), un boss, des joueurs (+N).
  Bulle bleue = bouclier. Salles grises « Efficacité en mode inactif X/s » = libres. « Volé X » = voleur.
  La salle avec « Moi » est celle du joueur.
- **Coffre de cartes** : `Quantité : N` sous chaque carte, `Disponible aujourd’hui : X/15`.

## Spécification de l’appli
- Kotlin, Android 8+ (minSdk 26), installée hors Play Store (APK).
- **Bulle flottante** par-dessus le jeu (permission « afficher par-dessus les autres applis »).
- Toucher la bulle → **capture d’écran** (MediaProjection, avec l’accord de l’utilisateur).
- **Lecture du texte sur le téléphone** avec ML Kit Text Recognition (hors ligne, gratuit).
- Écran de résultat : scores lus (modifiables si mal lus), plan d’actions numéroté, cartes à garder, menaces.
- Deux types de capture reconnus : carte du match et coffre de cartes ; les cartes lues sont mémorisées.
- Réglages : nom de notre guilde (défaut `s40-Ombres`), part volée par une Sape, prod. d’une salle libre.
- Interface en français.

## Interdits
- Ne jamais lire la mémoire du jeu, intercepter son trafic réseau ni automatiser des touches
  (risque de bannissement). Uniquement la lecture de l’écran.

## Plan de travail
- **Phase 1** : projet Android, bulle, capture, lecture du bandeau du haut + temps restant + coffre,
  salles du rival saisies à la main (bouclier / battable), moteur `plan()` porté, écran de résultat.
  Tester la lecture sur les captures de `captures/` avant tout.
- **Phase 2** : détection automatique des salles sur la carte (étiquettes de guilde, DMG, bulle de bouclier).
- **Phase 3** (plus tard, pour moi seulement) : mode optionnel d’analyse par l’API Claude.

## Plateformes
- Android uniquement pour l’appli (pas d’iPhone).
- Une version web viendra plus tard en faisant évoluer `conseil-zone-sombre-chefs.html`
  (envoi de captures + lecture du texte dans le navigateur). Garder le moteur `plan()` en Kotlin
  simple et fidèle à la version JavaScript, pour que les deux donnent toujours le même plan.
