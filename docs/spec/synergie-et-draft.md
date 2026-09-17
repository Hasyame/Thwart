# Synergie et draft

Spécification fonctionnelle commune à Thwart (Android) et Thwart Web. Les règles
métier ci-dessous doivent donner exactement les mêmes résultats des deux côtés,
et les decks doivent circuler entre les deux via le serveur sans perte. La
fixture de référence des tests, identique des deux côtés, est
`app/src/test/resources/synergy-fixture.json` (Android) et sa copie sur le Web.

Vocabulaire : identité = héros, affinité = aspect (Justice, Protection,
Agressivité, Commandement, 'Pool), basique = cartes neutres.

## Phase 1 : synergie entre le deck et l'identité

**Définition.** Certaines cartes d'affinité ou basiques portent une condition de
jeu liée aux traits de l'identité. Exemple : Rocket Raccoon (The Galaxy's Most
Wanted #19) indique « Jouez cette carte uniquement si votre identité a le trait
GARDIEN ». Adam Warlock (The Mad Titan's Shadow #31) a les traits Gardien et
Mystique, il peut donc la jouer. Magik (Age of Apocalypse #30) a les traits
Mystique et X-Men, elle ne peut que l'utiliser comme ressource. Une telle carte
reste légale dans un deck : la synergie est un avertissement, jamais un critère
de légalité.

**Données.**

* Extraire les traits de chaque identité depuis le fichier d'inventaire des
  identités (ou depuis les données de cartes s'il ne les contient pas encore, et
  dans ce cas l'enrichir). Stocker des clés de traits indépendantes de la langue
  (par exemple la clé anglaise normalisée `guardian`), l'affichage passe par la
  traduction.
* Une identité a au moins deux faces (héros et alter ego) dont les traits
  diffèrent souvent. Conserver les traits par face. Pour l'avertissement de
  construction, une carte est compatible si au moins une face de l'identité
  remplit la condition. Les identités à plusieurs formes héros prennent en
  compte toutes leurs faces.
* Pour les conditions des cartes, choisis la méthode la plus fiable et la plus
  performante. Ma recommandation : une passe de dérivation exécutée à l'import
  ou à la mise à jour des données (jamais à l'affichage) qui calcule un champ
  par carte, par exemple `synergy: { anyOfTraits: ["guardian"] }` ou `null` si
  la carte n'a pas de condition. La passe s'appuie sur le texte anglais
  (`real_text` s'il existe, sinon `text`), plus régulier que le français, où les
  traits sont normalement balisés `[[Trait]]` (à vérifier dans les données
  réelles). Gérer au minimum les formes « has the X trait » et « has the X or Y
  trait ».
* La passe doit produire un rapport (log ou test) listant toutes les cartes
  joueur contenant « Play only if » dont la condition n'a pas été reconnue, pour
  qu'aucun cas ne passe à la trappe. Les conditions qui ne portent pas sur les
  traits (forme héros, nom d'identité, Team-Up, etc.) sont hors périmètre pour
  l'instant : liste-les dans le rapport et je trancherai.
* Créer une fixture JSON de référence, strictement identique sur Web et
  Android, utilisée par les tests des deux côtés : Rocket Raccoon + Adam Warlock
  = compatible, Rocket Raccoon + Magik = incompatible, carte sans condition =
  compatible, plus quelques cas « X or Y » et un cas où seul l'alter ego (ou
  seul le héros) a le trait.

**Comportement.**

* Éditeur de deck : dès qu'une carte incompatible est ajoutée, afficher un
  avertissement non bloquant. FR : « Problème de synergie : la carte X (et Y,
  Z…) n'a pas de synergie avec l'identité ». EN : « Synergy issue: X (and Y,
  Z…) has no synergy with this identity ». La liste couvre toutes les cartes
  concernées et se met à jour en direct (l'avertissement disparaît quand on les
  retire).
* Ouverture d'un deck sauvegardé, importé ou synchronisé : même avertissement,
  recalculé à l'ouverture et jamais stocké dans le deck.
* Dans la liste des cartes disponibles de l'éditeur, une case à cocher
  « Masquer les cartes sans synergie avec l'identité » (EN « Hide cards without
  synergy with this identity »), décochée par défaut. Dis-moi si tu recommandes
  de mémoriser ce choix ou de le réinitialiser à chaque ouverture.

## Phase 2 : mode draft

**Principe.** Draft à la manière de Magic: The Gathering, de 1 à 4 joueurs sur
le même appareil, chacun son tour. Toutes les identités et toutes les cartes
proviennent de la collection de l'utilisateur.

**Page 1, paramètres**

* Nombre de joueurs : 1 à 4.
* Exclure les cartes sans synergie avec l'identité : oui ou non (non par
  défaut). Ce filtre réutilise la logique de la phase 1.
* Sélection des identités : « Aléatoire », « Aléatoire parmi 5 » ou « Au
  choix ».
* Nombre de cartes proposées à chaque choix (X) : bornes 2 à 10, valeur par
  défaut 3.

**Page 2, identités et affinités** (en solo un seul passage, en multijoueur
chaque joueur à son tour)

* Identité selon le mode : « Aléatoire » tire au sort une identité possédée.
  « Aléatoire parmi 5 » tire 5 identités possédées et le joueur en choisit une.
  « Au choix » affiche toutes les identités possédées. Deux joueurs ne peuvent
  pas avoir la même identité.
* Affinité : Justice, Protection, Agressivité, Commandement, 'Pool, plus un
  bouton « Aléatoire ». 'Pool n'est proposée (y compris dans le tirage
  aléatoire) que si le pack Deadpool est dans la collection.
* Les identités dont les règles de construction imposent leurs propres
  affinités (Adam Warlock, et à vérifier pour d'autres comme Spider-Woman) ne
  proposent pas de choix d'affinité et appliquent leur règle. Déduis ces règles
  des données (`deck_requirements`, `deck_options` ou équivalent) ou de la
  validation de deck existante plutôt que d'une liste en dur. Si une liste en
  dur est inévitable, centralise-la dans un seul fichier de configuration et
  montre-la-moi.
* Les cartes spécifiques à l'identité (cartes signature) sont ajoutées
  automatiquement et comptent dans le total. Obligation et némésis sont exclues
  comme en construction normale.
* Taille du deck : 40 à 50 cartes, par joueur. Nombre de choix de draft =
  taille demandée moins le nombre de cartes signature.
* Avant de lancer le draft, vérifier que le stock disponible permet
  d'atteindre les tailles demandées (en tenant compte des autres joueurs).
  Sinon, message explicite et proposition de réduire la taille.

**Page 3, draft**

* Pool d'un joueur : cartes joueur de la collection appartenant à son ou ses
  affinités, plus les basiques. Jamais de cartes signature d'autres identités
  ni de cartes rencontre ou campagne. Filtre synergie appliqué si l'option est
  active.
* À chaque tour, X cartes tirées au hasard dans le pool du joueur actif. Il en
  choisit une, les autres retournent dans le pool. S'il reste moins de X
  cartes, proposer ce qui reste.
* Légalité garantie à chaque tirage : une carte n'est jamais proposée si
  l'ajouter rendait le deck illégal (limite d'exemplaires `deck_limit`, règles
  propres à l'identité comme un seul exemplaire par carte pour Adam Warlock,
  réimpressions comptées comme une même carte).
* Quantités possédées : un exemplaire physique ne peut être drafté qu'une
  seule fois, tous joueurs confondus, puisque la collection est partagée sur
  l'appareil. Le pool est donc un stock décrémenté à chaque choix, et les
  réimpressions présentes dans plusieurs packs possédés s'additionnent.
* Multijoueur : tour par tour (J1, J2, J3, J4, J1…). Un joueur qui a atteint
  sa taille est sauté. Un écran de transition « Au tour de Joueur N » permet de
  passer l'appareil sans voir le choix du précédent.
* Pour le joueur actif, afficher son identité, son affinité, sa progression
  (ex. 23/45) et un aperçu de ses cartes déjà choisies.
* Le tirage aléatoire est injectable (seed) pour rendre les tests
  déterministes.
* L'état du draft est sauvegardé localement pour reprendre après une
  fermeture, avec un bouton « Abandonner le draft » soumis à confirmation.

**Page 4, fin du draft**

* Nom par défaut : `DRAFT-NOMIDENTITE-AFFINITE-01`, par exemple
  `DRAFT-SPIDERMAN-AGGRESSION-01`. Nom d'identité en majuscules, sans espaces,
  tirets, accents ni ponctuation. Affinité en code anglais stable, identique
  quelle que soit la langue : JUSTICE, PROTECTION, AGGRESSION, LEADERSHIP, POOL.
  Pour une identité sans choix d'affinité, propose-moi une valeur (par exemple
  le code de l'identité ou MULTI). Suffixe incrémenté si le nom existe déjà
  (02, 03…), y compris entre les decks d'un même draft.
* Chaque joueur, à son tour, peut modifier le nom ou garder celui par défaut.
  Le bouton « Fin du draft » sauvegarde tous les decks d'un coup.
* Avant sauvegarde, chaque deck repasse par la validation standard. Un deck
  illégal à ce stade est un bug : log détaillé, message clair à l'utilisateur,
  aucune sauvegarde silencieuse. Un deck contenant des cartes sans synergie
  (option désactivée) reste légal et affichera simplement l'avertissement de la
  phase 1.
* Les decks créés passent par le circuit normal de sauvegarde et déclenchent
  la synchronisation serveur pour apparaître dans les assets partagés du compte
  (mise en file si hors ligne, comportement habituel si aucun compte). Pas de
  nouveau champ obligatoire dans le modèle de deck. Si un marqueur « issu d'un
  draft » te semble utile, il doit être optionnel et rétrocompatible, et tu
  dois me le soumettre avant, car l'autre plateforme devra le gérer aussi.

## Décisions prises pendant l'implémentation

Arrêtées avec l'auteur le 2026-09-16, à appliquer à l'identique sur le Web.

* **Clés de traits.** Anglais, minuscules, point final retiré, tirets et points
  internes conservés : `guardian`, `x-men`, `s.h.i.e.l.d`, `deadpool corps`.
  Les traits imprimés se découpent sur « point espace » et non sur le point
  seul, sinon « S.H.I.E.L.D. » éclate en lettres.
* **Faces d'une identité.** Toutes les cartes `hero` et `alter_ego` du
  `card_set_code` de l'identité (Ant-Man et Wasp ont deux formes héros, Angel
  et Archangel, Ironheart trois versions).
* **Formes reconnues** dans `real_text` (sinon `text`), insensibles à la casse
  des balises :
  * « Play only if your identity has the [[X]] trait » et « … the [[X]] or
    [[Y]] trait » : compatible si une face quelconque porte X (ou Y).
  * « Play only if you have the [[X]] trait » : même sens, identité.
  * « Play only if your hero has the [[X]] trait » : seules les faces `hero`
    comptent (Psi-Bow Attack, Psi-Flail Strike, Telekinesis). Une identité dont
    seul l'alter ego a le trait est incompatible.
* **Hors périmètre pour l'instant**, listées par le rapport et sans
  avertissement : contrôle d'un personnage ou d'une carte à trait (Spy,
  Web-Warrior, Martial Artist), forme héros (Giant, Tiny, masses de Vision,
  Element Gun), points de vie imprimés (Limitless Stamina, Unshakable), plan
  annexe dans la zone de victoire, joueur nommé (Captain America de Bucky ou
  Sam), « Max 1 per deck » (déjà une limite d'exemplaires).
* **Case « Masquer les cartes sans synergie ».** Non mémorisée : décochée à
  chaque ouverture de l'éditeur.
* **'Pool.** Proposée à toute identité dès que le pack Deadpool est dans la
  collection, conformément au livre de règles (« l'un des cinq aspects »).
* **Pool du draft.** Affinités choisies + basiques, plus les cartes que les
  `deck_options` de l'identité admettent (les trois supports S.H.I.E.L.D. de
  Maria Hill, par exemple), la légalité en bornant le nombre.
* **Taille du deck.** Réglée par joueur, sur la page 2.
* **Cartes proposées.** Cinq par défaut (et non trois), bornes 2 à 10 inchangées.
* **Pas d'écran de transition.** En multijoueur, la table passe directement d'un
  joueur au suivant ; l'en-tête de la page nomme le joueur et son identité.
* **Prendre une carte.** Un appui sur la carte la prend, sans bouton ni
  confirmation ; un appui long ouvre la fiche de la carte. Sur téléphone, les
  cartes proposées sont en grille de deux colonnes, le deck en cours (cartes
  signature comprises, par type, avec le coût) en dessous ; sur tablette, le
  deck est à côté.
* **Vérification du stock.** Comptée en exemplaires qu'un deck peut vraiment
  contenir (min du stock et de la limite d'exemplaires par titre, 1 pour une
  carte unique ou pour Adam Warlock), par joueur puis tous ensemble. Un seul
  Core Set ne suffit pas à deux decks Justice de 40 : 47 choix possibles pour
  50 demandés, dit avant le premier tirage.
* **Nom par défaut sans choix d'affinité.** `MULTI` pour une identité dont les
  règles imposent ses affinités (Adam Warlock : `DRAFT-ADAMWARLOCK-MULTI-01`).
  Une identité qui en choisit deux (Spider-Woman) les écrit toutes les deux,
  par ordre alphabétique : `DRAFT-SPIDERWOMAN-JUSTICE-PROTECTION-01`.
