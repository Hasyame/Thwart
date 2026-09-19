# Privacy policy

Thwart is maintained by Benoît Breul. Last updated: 19 September 2026.

## Local use

You can use the app without an account. Decks, collection choices, campaigns,
play history, notes, table photographs and settings are stored on your device.
Uninstalling the app removes that local copy. A portable backup is available in
Settings; include photographs only when you want them in the exported archive.

## Network features

- **MarvelCDB:** card updates, deck imports and card images use MarvelCDB and its
  French endpoint. Its servers receive ordinary web-request information such as
  your IP address. The app does not send your local play history to MarvelCDB.
- **Optional Thwart account:** signing in and enabling synchronization sends
  supported collection, deck, campaign, play, favourite, setting and rating
  records to the selected Thwart server (thwart.app by default). Notes included
  in those records travel with them. Authentication sends the information you
  enter for the account. Account data can be accessed by your other signed-in
  devices and the web client. Photograph image files are not uploaded by account
  sync. The server has its own account and data-handling policy; consult it before
  using an alternative instance.
- **Community ratings:** optional ratings use the Thwart service. Rating evidence
  refers to a saved game or campaign; do not assume a rating is an anonymous local
  preference when enabling account synchronization.
- **Optional BoardGameGeek reporting:** after you configure it, the app can send
  finished-game details and the location text you enter to BoardGameGeek. Its
  login credentials are encrypted in local storage using Android Keystore and
  used to authenticate to BoardGameGeek.
- **Sharing and support:** exports go to the destination you select. Crash reports
  stay on the device until you choose to open a support message and send it with
  your email application. Nothing is automatically sent to a crash-report service.

Synchronization propagates edits and deletions; it is not a versioned backup.
Keep an independent backup before testing a beta or restoring another dataset.

## Permissions and transfers

The app declares internet access. Android libraries can contribute permissions
needed for scheduled background work; consult Android's app information for the
installed package. Thwart does not request location, contacts or microphone access.
Taking a table photograph uses your chosen camera application, without requesting
camera access in Thwart. Photographs are held in private app storage.

Android cloud backup and device transfer are excluded. Use the explicit portable
backup to move supported data. Credentials, sync bookkeeping, paused games and
unfinished drafts are not included in that portable document.

## Advertising and tracking

There are no advertising networks, analytics SDKs, advertising identifiers or
automatic crash-reporting services in the app.

## Children

Thwart is a companion for a card game and is not directed at children under 13.

## Contact and changes

Questions: **marvelchampcompanion@proton.me**. Changes to these behaviors should be
reflected here and in release notes. Source: [Hasyame/Thwart](https://github.com/Hasyame/Thwart).

Thwart is an unofficial fan project, not affiliated with Fantasy Flight Games or Marvel.
