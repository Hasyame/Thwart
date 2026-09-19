# Succès (achievements)

La spécification partagée vit dans le dépôt web, `docs/spec/achievements/`
(data-model, algorithm, sync, i18n, test-vectors). Ce fichier ne la répète
pas : il note ce que le port Android en fait, et les choix qui lui sont
propres. Décidé avec l'auteur le 2026-09-18, implémenté ici le 2026-09-19.

## Ce qui est partagé, tel quel

* `assets/achievements.json` est une **copie** de `web/public/achievements.json`
  (dépôt web, copie maître), prise à la release, jamais téléchargée à
  l'exécution. `AchievementsAssetTest` en épingle le SHA-256 avec le commit
  web d'origine, et compare au checkout web voisin quand il existe.
* La dérivation (`domain/achievements/AchievementDerivation.kt`) suit
  `algorithm.md` pas à pas ; `app/src/test/resources/achievements/test-vectors.json`
  est la copie inchangée des 31 vecteurs, et `AchievementVectorsTest` exige
  l'égalité champ pour champ. Les valeurs attendues sont le contrat : une
  implémentation qui diverge a tort, pas le vecteur.
* Identifiants : héros par code de carte (`01001a`), scénarios par clé
  (`rhino`, `fne_s1_musee`, code de set du méchant pour une partie de
  campagne, `campaign:<id>` quand rien ne résout), extensions par code
  MarvelCDB, succès par slug.

## Enregistrement (format de sauvegarde 2)

* `PlayHero.isOwner` : vrai sur la place du joueur qui enregistre. Ici,
  **la première place** de la table (mise en place et campagne). Absent sur
  les parties d'avant : la première place est alors le propriétaire.
* `PlayEntity.mode` : `draft` quand le deck de la première place porte le
  tag `draft`, que le draft pose désormais sur les decks qu'il construit
  (`DeckRepository.DRAFT_TAG`). Nul sinon, et jamais écrit quand nul.
* Difficulté : lue par préfixe (`standard*`, `expert*`), sinon `unknown`.
  Jamais Standard par défaut, pour qu'une vieille partie ne satisfasse pas
  un succès de difficulté par erreur.
* **Champs inconnus conservés.** Une clé inconnue sur le document, sur une
  partie ou sur une place atterrit dans `extra` (colonne JSON sur `plays`,
  objet sur `PlayHero` et `Backup`) et ressort telle quelle à l'export et
  dans le corps de synchronisation ; un champ connu l'emporte toujours sur
  une clé `extra` du même nom (`WithExtrasSerializer`). Un fichier d'un
  format plus récent se lit, il n'est plus refusé.
* `BackupWebFixtureTest` importe l'export web `backup-web-v2.json` et le
  ressort entier ; il écrit aussi `app/build/fixtures/backup-android-v2.json`
  pour que le dépôt web garde l'export de cette build comme fixture.
* Base Room 26 : `plays.mode` et `plays.extra` (auto-migration).

## Écrans

* Route à part, `AchievementsRoute`, jamais un onglet des statistiques :
  atteinte depuis l'accueil (panneau sous le nom du compte, à la manière
  d'une boutique : compte, dernier succès, rangée de badges gagnés puis
  verrouillés), depuis le hub Jouer (une entrée discrète en chiffres,
  `won / cells` et une barre), depuis les statistiques (un lien) et depuis
  la page de résultat d'une partie.
* La page : le taux sur la collection en grand avec ses comptes absolus, le
  total sur tout le jeu, les derniers succès, la grille héros × scénarios
  (filtres extension du héros et du scénario indépendants, affinité,
  difficulté minimale, toutes les places ou la mienne, défaites comme
  jouées ; les cases sont dessinées sur un canvas, quatre mille boîtes
  composées prenaient des secondes), puis les succès nommés par catégorie
  avec progression, paliers, date ou « absent de votre collection ».
* Résultat d'une partie (mise en place et campagne) : ce que **cette**
  partie a débloqué, état avant contre état après, trois au plus puis un
  compte, avec un lien vers la page. Rien à l'import d'une sauvegarde.
* Les badges suivent les choix du web (`AchievementTexts.heroById`,
  `scenarioById`) : art du héros ou du méchant sur MarvelCDB, couverture de
  Peur de Rien pour sa boîte ; gris et estompés tant qu'ils ne sont pas
  gagnés.
