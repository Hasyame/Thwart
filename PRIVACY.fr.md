# Politique de confidentialité

Thwart est maintenu par Benoît Breul. Mise à jour : 19 septembre 2026.

## Utilisation locale

L’application est utilisable sans compte. Decks, collection, campagnes,
historique, notes, photos de table et réglages sont conservés sur votre appareil.
La désinstallation supprime cette copie locale. Les réglages permettent de créer
une sauvegarde portable ; les photos ne sont incluses que si vous le demandez.

## Fonctions réseau

- **MarvelCDB :** mises à jour des cartes, importation de decks et images utilisent
  MarvelCDB et son site français. Ces serveurs reçoivent les informations habituelles
  des requêtes web, notamment votre adresse IP. Votre historique local de parties
  n’est pas envoyé à MarvelCDB.
- **Compte Thwart facultatif :** la connexion puis l’activation de la synchronisation
  envoient les enregistrements pris en charge (collection, decks, campagnes, parties,
  favoris, réglages et évaluations) au serveur choisi, thwart.app par défaut.
  Les notes de ces enregistrements sont transmises avec eux. L’authentification
  transmet les informations saisies pour le compte. Vos autres appareils connectés
  et le client web peuvent accéder aux données du compte. Les fichiers photo ne
  sont pas téléversés par la synchronisation du compte. Consultez la politique
  propre au serveur avant de choisir une autre instance.
- **Évaluations communautaires :** les évaluations facultatives utilisent le service
  Thwart. Leur justification fait référence à une partie ou une campagne enregistrée.
  Une évaluation synchronisée ne doit pas être considérée comme une préférence
  locale anonyme.
- **Envoi facultatif à BoardGameGeek :** après configuration, l’application peut
  envoyer les détails des parties terminées et le lieu saisi à BoardGameGeek.
  Les identifiants sont chiffrés localement avec Android Keystore et utilisés
  pour se connecter à BoardGameGeek.
- **Partage et assistance :** les exports vont vers la destination choisie. Les
  rapports de plantage restent sur l’appareil jusqu’à ce que vous choisissiez
  d’ouvrir puis d’envoyer un message avec votre application de messagerie.
  Aucun service de rapport de plantage ne reçoit d’envoi automatique.

La synchronisation propage les modifications et suppressions ; ce n’est pas une
sauvegarde versionnée. Conservez une sauvegarde indépendante avant une bêta ou
le remplacement de vos données par une restauration.

## Autorisations et transferts

L’application déclare l’accès à Internet. Les bibliothèques Android peuvent ajouter
les autorisations nécessaires aux tâches planifiées ; consultez les informations
Android du paquet installé. Thwart ne demande pas l’accès à la localisation, aux
contacts ou au microphone. La prise de photo utilise votre application photo,
sans demander l’autorisation caméra dans Thwart. Les photos restent dans le
stockage privé de l’application.

La sauvegarde cloud Android et le transfert entre appareils sont exclus. Utilisez
la sauvegarde portable explicite pour déplacer les données prises en charge.
Les identifiants, le suivi technique de synchronisation, les parties en pause et
les drafts inachevés ne figurent pas dans ce document portable.

## Publicité et suivi

L’application ne contient ni réseau publicitaire, ni SDK de mesure d’audience,
ni identifiant publicitaire, ni service automatique de rapport de plantage.

## Enfants

Thwart accompagne un jeu de cartes et ne s’adresse pas aux enfants de moins de 13 ans.

## Contact et modifications

Questions : **marvelchampcompanion@proton.me**. Les changements de comportement
doivent être reflétés ici et dans les notes de version. Code source :
[Hasyame/Thwart](https://github.com/Hasyame/Thwart).

Thwart est un projet de fans non officiel, sans affiliation à Fantasy Flight Games ou Marvel.
