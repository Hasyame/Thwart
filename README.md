# Thwart

An unofficial Android companion for **Marvel Champions: The Card Game**: card
database and search, deck lists, a scenario randomiser driven by the packs you
actually own, and a data-driven campaign tracker. Offline first, no account, no
backend, no advertising.

*Thwart* is the app's own name. It is not a Marvel or Fantasy Flight Games
product and does not use their branding; the game is named here to say what the
app works with, which is the only thing that naming it is for.

Bilingual throughout — the interface and the card text are chosen separately, so
you can read the app in French and the cards in English, or the reverse.

## Status

In use, and being played with. Current release: **[v1.14.0](https://github.com/Hasyame/Thwart/releases/latest)**.

Every feature planned for the app is now in, and the work from here is campaign
content rather than machinery.

### Done

- **Card database.** The full MarvelCDB catalogue including encounter cards,
  each row carrying its art, its aspect as a colour, and its resource cost where
  the printed card puts it. Searchable offline with accent-insensitive prefix
  matching, filters for type,
  aspect, cost and traits, sorting by set, name or cost, and a detail screen
  showing which product a card comes from and which encounter set it belongs to.
  A card MarvelCDB has not translated is shown in English rather than hidden.
- **Favourites.** Star a card and filter the search down to the starred ones.
  Kept separately from the card table, so they survive a card data refresh.
- **Offline images.** Download the pictures for the packs you own, so cards are
  readable at a table with no signal. Never automatic — it is a large download
  and asking first is the only decent way to do that on mobile data.
- **Rules reference.** The Rules Reference in a tab of its own, searchable and
  offline, because the moment somebody needs to know what *retaliate* does is
  the moment the rulebook is in a bag under the table. Searching answers with
  the keyword first and the rules that merely mention it after — "stun" returns
  STUNNED, not the eleven rules that use the word. **In both languages**,
  keyword included: in French the entry is *Riposte X*, which is the word
  printed on the card in front of you. It follows the app language, since the
  rules are the app talking rather than a card.

  Built from [deejimy/mc-reference](https://github.com/deejimy/mc-reference), a
  reference guide for rulebook v1.8 maintained by **deejimy** and released under
  CC0. Every page there carries the French text beside the English it was
  translated from, so one source gives both languages and they cannot drift
  apart. The app credits him under the list.
- **Collection.** Mark the packs you own; everything the app offers follows from
  it. Owning a pack is not owning everything in it, so tapping one opens the
  scenarios and modular sets inside — untick whatever is missing from a
  second-hand box or a set lent out and never returned. What you untick is never
  drawn and never offered when you set a game up by hand. The same rule covers
  difficulties, which arrive in boxes of their own, and the 'Pool aspect, whose
  cards came with Deadpool.
- **Decks.** Import from a MarvelCDB link or a share from the browser, build
  from scratch, edit, check legality as you go, see what a deck is made of, and
  share a decklist as plain text. Legality follows the printed rules: the
  hero's own cards in their printed numbers, copies counted by title rather
  than by printing, unique cards counting the identity card and told apart only
  by a subtitle both of them carry, and the identities that rewrite the rules —
  Adam Warlock's four balanced aspects and single copies, Spider-Woman's two
  aspects in equal number, Gamora's six off-aspect events.
- **Randomiser.** Scenario, difficulty, modular sets, heroes and aspects, drawn
  only from what you own, with locking, rerolls and a history. Tap any row to
  choose it yourself instead — the choice locks, and the rest rolls around it.
  Each option names the pack it came from, because a scenario is often not named
  after the villain on the box: The Green Goblin contains *Risky Business* and
  *Mutagen Formula*.
- **Versus games.** Civil War and Synthezoid Smackdown are not played against a
  villain but as a leader plus a side — Resistance or Registration — drawing
  three or four modular sets from a pool legal only in those games. Both halves
  are read from the card database, which models a leader as a set type of its
  own, so a new versus pack needs no code.
- **Rules the card database cannot express.** Only villain sets that bring a
  scenario of their own — the four Wrecking Crew villains are played inside
  Wrecking Crew, not instead of it. Difficulties limited to the boxes they came
  in: Standard I and Expert I in the Core Set, Standard II and Expert II with
  The Hood, Standard III with The Age of Apocalypse.
- **Play log.** Finished games are recorded and timed, whether they came from
  the randomiser, from a campaign, or were set up by hand with your own modular
  sets. The clock can be corrected afterwards, a game can be thrown away before
  it is filed, and at a table of two or more the app draws who goes first.
- **Statistics.** Win rate by hero, by scenario, by aspect, by difficulty, by
  hero *with* aspect, and solo against multiplayer — kept apart because a
  blended figure describes neither. Plus total time played, longest game, and
  current and best winning streak.
- **BoardGameGeek.** Optionally send finished plays to your account, with a
  proper player row and the victory points as the score. Credentials are
  encrypted with a key held in the Android keystore.
- **Campaign tracker.** An append-only event log with all state folded from it,
  so a record can never drift from what was played. Counters, flags, card lists,
  a market, per-scenario questionnaires, and setup steps for one scenario that
  depend on what was recorded in the ones before it. **The Galaxy's Most Wanted**,
  **Age of Apocalypse**, **The Mad Titan's Shadow** and **The Rise of Red Skull**
  ship with the app. Where a campaign says each player *chooses* a card, that is
  compulsory: the questionnaire records one answer per player and will not file
  until everyone has picked, because every later scenario assumes the card is in
  the deck.
- **Setup the app does for you.** Anything a campaign tells you to pick at
  random — Age of Apocalypse's side missions and overseers, the order the Four
  Horsemen line up in, which Loki you face first — is drawn by the app, recorded
  so it cannot change while you read it, and kept out of the pool next time. A
  mission's own setup and whatever it leaves behind follow it through the rest
  of the campaign.
- **The setup printed on the scenario.** A campaign briefing lists the cards to
  fetch, then the campaign's own steps, then the setup written on the main
  scheme itself — the order it is done in at the table. Read off the card
  rather than out of a template, so it arrives in whatever language the cards
  are in, and covers sixty of the sixty-two scenarios. Ebony Maw and Thanos
  keep theirs in the rules insert and show nothing rather than a guess.
- **A setup page for games that are not campaigns.** A random draw, or a setup
  chosen by hand, lands on the same briefing: what to fetch — the scenario, its
  main scheme, the difficulty, the modular sets that were drawn and who is
  playing which aspect — and then the setup printed on the scheme. The clock
  waits for a Play button rather than starting the moment the scenario is
  known, because laying a game out takes minutes that are not play, and they
  were being counted.
- **Finished campaigns.** Saved runs keep total time, victory points, heroes,
  credits and a per-scenario log of the answers given.
- **Backup and restore.** Collection, decks, campaigns, play history and
  favourites to a single readable JSON file, and back again on any device.
  There is no account and no server, so without this the phone is the only copy.
- **Two languages, chosen separately.** The language of the card text and the
  language of the app itself are different settings, because a French player
  with an English collection is a normal thing to be. Either can be French or
  English, and the app can also just follow the phone. The choice survives a
  restart on every supported version of Android.
- **Dark theme**, on by default, and light if you prefer it. Both are built
  around the game's own aspect colours meaning only one thing: aggression red,
  justice gold, leadership blue, protection green belong to the cards, so the
  app's own chrome stays out of that palette.
- **French names the card database gets wrong.** MarvelCDB's French endpoint
  still returns some set names in English. `docs/game_contents.json` records what
  is in every box, in both languages, and `tools/build_game_contents.py` derives
  the corrections the app applies — so a name typed in one place reaches the
  screen, and a name MarvelCDB fixes upstream is not frozen into a local copy.

### Next

- **The Fear No Evil campaign template**, once MarvelCDB publishes its encounter
  cards. The pack currently holds only the two heroes, so there is nothing to
  build the scenarios against. The engine work the campaign needs — a table that
  chooses its next scenario, a villain drawn into its own deck, choosing one of
  two drawn cards, and a counter fed by how often a card was offered — is
  already done and tested.
- **Fear No Evil and Shadowland in the randomiser.** Nine scenarios the card
  database has not got. Civil War looked like the same problem and was not — the
  card database had it all along, under a set type nothing else uses — so these
  would need a curated list, which is a thing worth adding once rather than
  speculatively.

Campaigns are added after they have been played, so that the mechanics in the
template come from experience rather than from a reading of the book. That is
the whole roadmap — no further app features are planned, and anything else will
come from something going wrong at a real table.

## Running it on a PC

Drag the APK from [the latest release](https://github.com/Hasyame/Thwart/releases/latest)
onto a [BlueStacks](https://www.bluestacks.com/) window and it installs. There is
nothing to publish and nothing to sign up for — BlueStacks runs Android apps, it
is not a shop.

It is a good fit by accident. The APK carries x86 and x86\_64 alongside the ARM
builds, so it runs natively rather than through translation; it asks for
`faketouch` rather than a real touchscreen, so a mouse is fine; and a BlueStacks
window is wide, which is the size at which the app lays itself out as a
navigation rail with the card list and the card side by side.

now.gg, the cloud version where apps run in a browser, is a different thing and
not worth it here: it wants a minimum of Android 7 where this app asks for 9, and
streaming an offline-first app from a server is the opposite of the point.

## Web companion

Players on iPhone cannot install an APK, and an iOS build would mean Kotlin
Multiplatform, a Mac and a yearly fee to reach one more platform. `web/` is a
page instead: **card search and the rules reference**, offline after the first
visit, added to a home screen from any browser.

Deliberately not the whole app. Decks, campaigns, play history and backups stay
on Android, because a browser can evict its own storage without warning and
those are the things a player cannot lose. The web version holds nothing you
would miss if the browser forgot it.

`web/data/` is generated, never committed — it is card text, like the app's
seed. The Pages workflow fetches it before deploying, so every deploy publishes
what MarvelCDB has that day. See [web/SCOPE.md](web/SCOPE.md).

To run it locally:

```
python tools/build_web_data.py
python -m http.server 8765 --directory web
```

## Versioning

Semantic versioning, and the third number carries its weight: **patch releases
are bug fixes and security work only**. A release that adds a campaign or a
feature moves the minor. Nothing here is an API anyone depends on, but a version
that says what kind of change it is means somebody deciding whether to update
does not have to read the diff to find out.

## Requirements

- JDK 21
- Android SDK with platform API 37
- An Android device or emulator on API 28 (Android 9) or later

## Build

```bash
./gradlew assembleDebug
```

```bash
./gradlew lintDebug testDebugUnitTest
```

`local.properties` is not committed; create it with your SDK path, or set
`ANDROID_HOME`.

### Release signing

Without a keystore the release build falls back to the **debug** key and says so
loudly. That is fine for testing on your own device and must never be
distributed: a properly signed build cannot upgrade over a debug-signed one, so
installing the real thing later means uninstalling first, which erases every
campaign, deck and collection setting on the device.

Create a key once:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias mcc
```

Then copy `keystore.properties.example` to `~/.mcc/keystore.properties` and fill
it in, keeping `release.jks` beside it.

**Outside the repository on purpose.** A signing password has to be plain text
for Gradle to use it, so the protection is location: inside the project folder
the key is one zipped folder, one cloud backup or one bad `.gitignore` edit away
from being shared by accident. The build also accepts
`$MCC_KEYSTORE_PROPERTIES` pointing at any path, and still reads
`keystore.properties` in the repo root if you prefer that.

**Back the keystore up somewhere other than this machine.** It cannot be
regenerated or recovered. Losing it means no future build can ever update an
installed copy of the app.

Verify which key a build actually used:

```bash
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

A debug-signed build shows `CN=Android Debug`.

## Card data

Card and pack data comes from the [MarvelCDB](https://marvelcdb.com) public API,
maintained by its contributors. The snapshot bundled into the APK is generated
at build time and is **not** committed to this repository — see
[docs/DATA_SOURCES.md](docs/DATA_SOURCES.md).

## Licence

The source code is released under the [MIT Licence](LICENSE).

That licence covers **this code only**. It grants no rights whatsoever over
Marvel Champions: The Card Game, its cards, artwork, rules or campaign books,
none of which are mine to license — see below.

### With thanks

The Rules tab is built from
[deejimy/mc-reference](https://github.com/deejimy/mc-reference), a bilingual
reference guide for rulebook v1.8 compiled by **deejimy** and released under
CC0. The French rules in this app are his work, and the English comes from the
same pages, which is why the two always agree. CC0 asks for nothing in return;
crediting him is the least this can do.

## Legal

This is an unofficial, non-commercial fan project. It is free, not for sale, and
carries no advertising.

Marvel Champions: The Card Game is © Marvel and published by Fantasy Flight
Games. This project is not affiliated with, endorsed by, or sponsored by Marvel,
Fantasy Flight Games or Asmodee. All trademarks and copyrights belong to their
respective owners.

The app is called **Thwart**. The game's name appears in the description, and
nowhere in the app's own name, icon or branding: it is there to tell a player
what the app is compatible with, not to suggest it comes from the publisher.

No card images, card text, or campaign book text is stored in this repository.

The **release APK is a different matter, and worth being plain about**: it
bundles a snapshot of the MarvelCDB card data — around 15 MB of card text in
both languages — so the app is usable offline the moment it is installed. That
snapshot is fetched by `./gradlew fetchCardSeed` at packaging time and is
deliberately never committed, but it is inside the APK you download from the
releases page.

A build made without it works too: the app detects the snapshot is absent and
downloads the cards on first sync. That is what F-Droid ships, since F-Droid
builds from this source and the snapshot is not in it. So the F-Droid build and
the GitHub release differ — same code, but one arrives with the cards and one
fetches them.

How a new version reaches F-Droid — which is by itself, from the git tag — is in
[fdroid/RELEASING.md](fdroid/RELEASING.md). The one-time submission is in
[fdroid/SUBMITTING.md](fdroid/SUBMITTING.md).

Campaign templates in `app/src/main/assets/campaigns/` hold **mechanics only** —
card codes, counters, conditions and effects, plus short labels and a
two-sentence blurb per scenario, all written for this app. They contain no rules
text and reproduce no text from the campaign book. They are a play aid for
someone who already owns the campaign box and has the book to hand; on their own
they do not explain how to play a campaign, and they are not a substitute for
either the book or the game.

The app collects nothing. There is no account, no analytics and no backend of
any kind; everything it stores stays on the device. Android's automatic backup
is switched off for the same reason — it would copy your play history and
campaign log to Google Drive without asking. Settings offers a backup you ask
for and keep yourself instead.

If the app crashes it writes the stack trace to its own private storage and
offers, in Settings, to open a mail draft containing it. That is not crash
reporting: nothing is sent anywhere unless you press the button, the trace is in
the message body so you can read it first, and it is the only thing the app has
ever offered to send.
