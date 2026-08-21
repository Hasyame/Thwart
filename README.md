# Thwart

An unofficial Android companion for **Marvel Champions: The Card Game**. It
holds the card database, your deck lists, a scenario randomiser that only offers
what you own, and a campaign tracker. It works offline, has no account, no
server behind it and no advertising.

*Thwart* is the app's own name. It is not a Marvel or Fantasy Flight Games
product and uses none of their branding. The game is named here so you know what
the app is for, which is the only reason to name it.

The app is bilingual, and the two languages are set separately: you can read the
app in French with English cards, or the other way round.

## Contents

- [Install it](#install-it)
- [What it does](#what-it-does)
- [What is still to come](#what-is-still-to-come)
- [Versioning](#versioning)
- [Build it yourself](#build-it-yourself)
- [Card data](#card-data)
- [Licence](#licence)
- [Legal](#legal)

## Install it

### On an Android phone

1. Open [the latest release](https://github.com/Hasyame/Thwart/releases/latest)
   on the phone and download the `.apk` file.
2. Open it. Android will say the file came from an unknown source and offer a
   settings screen; allow your browser or file manager to install apps, then go
   back and open the file again.
3. That is all. There is no account to create and nothing to sign in to.

Android 9 or newer. If you already have Thwart installed from a GitHub release,
a new APK installs straight over it and keeps your decks, campaigns and history.

### Keeping it up to date with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) watches a project's releases
and installs updates for you, without a shop in the middle. It is the easiest
way to stay current with this app.

1. Install Obtainium from
   [its own releases page](https://github.com/ImranR98/Obtainium/releases) or
   from F-Droid.
2. In Obtainium, tap **Add App**.
3. Paste `https://github.com/Hasyame/Thwart` into the URL box and tap **Add**.
4. Obtainium finds the latest release and installs it. From then on it tells you
   when there is a new one.

### From F-Droid

Thwart is going through F-Droid's review. Once it is in, it can be installed and
updated from the F-Droid client like any other app there. The F-Droid build
differs from the GitHub one in a single way, explained under [Legal](#legal):
it arrives without the bundled card snapshot and asks before fetching the cards.

### On a PC

Drag the APK from
[the latest release](https://github.com/Hasyame/Thwart/releases/latest) onto a
[BlueStacks](https://www.bluestacks.com/) window and it installs. BlueStacks
runs Android apps on a PC; it is not a shop, and there is nothing to sign up for.

It happens to be a good fit. The APK carries x86 and x86\_64 builds alongside the
ARM ones, so it runs natively instead of through translation. It asks for
`faketouch` rather than a real touchscreen, so a mouse works. And a BlueStacks
window is wide, which is the width at which the app lays itself out as a
navigation rail with the card list and the card side by side.

now.gg, the cloud version that runs apps in a browser, is not worth it here. It
wants Android 7 as a minimum where this app asks for 9, and streaming an
offline-first app from a server rather defeats the point.

## What it does

In use, and being played with. Current release:
**[v1.22.0](https://github.com/Hasyame/Thwart/releases/latest)**.

Every feature planned for the app is in. The work from here is campaign content
rather than machinery.

- **Card database.** The full MarvelCDB catalogue including encounter cards.
  Every row carries its art, its aspect as a colour, and its resource cost where
  the printed card puts it. Search works offline, ignores accents, matches on
  prefixes, and filters by type, aspect, cost and traits. A card MarvelCDB has
  not translated is shown in English rather than hidden.
- **Favourites.** Star a card and filter the search down to starred ones. Kept
  apart from the card table so they survive a card data refresh.
- **Offline images.** Download the pictures for the packs you own, so cards are
  readable at a table with no signal. Never automatic: it is a large download,
  and asking first is the only decent way to do that on mobile data.
- **Rules reference.** The Rules Reference in its own tab, searchable and
  offline, because the moment somebody needs to know what *retaliate* does is
  the moment the rulebook is in a bag under the table. A search answers with the
  keyword first and the rules that merely mention it after, so "stun" returns
  STUNNED rather than the eleven rules that use the word. Both languages,
  keyword included: in French the entry is *Riposte X*, which is the word
  printed on the card in front of you.

  It is built from [deejimy/mc-reference](https://github.com/deejimy/mc-reference),
  a reference guide for rulebook v1.8 maintained by **deejimy** and released
  under CC0. Every page there carries the French text beside the English it was
  translated from, so one source gives both languages and they cannot drift
  apart.
- **Collection.** Mark the packs you own, and everything the app offers follows
  from it. Owning a pack is not the same as owning everything in it, so tapping
  one opens the scenarios and modular sets inside. Untick whatever is missing
  from a second-hand box or lent out and never returned, and it is never drawn
  and never offered. The same goes for difficulties, which arrive in boxes of
  their own, and the 'Pool aspect that came with Deadpool.
- **Decks.** Import from a MarvelCDB link or a share from the browser, build
  from scratch, edit, check legality as you go, see what a deck is made of, and
  share a decklist as plain text.

  A deck built from scratch opens with the hero's own cards already in it, in
  their printed numbers. They were never a choice, so an empty deck only meant
  failing validation until you added by hand what the rules had already decided.
  Each row carries a mark for its type, a shape as well as a colour so the list
  can be skimmed without relying on colour, and the list sorts by type, cost or
  name. A card with no printed cost sorts last rather than as a zero.

  Legality follows the printed rules: the hero's own cards in their printed
  numbers, copies counted by title rather than by printing, unique cards
  counting the identity card and told apart only by a subtitle both of them
  carry, and the identities that rewrite the rules. Adam Warlock takes four
  balanced aspects and single copies, Spider-Woman two aspects in equal number,
  Gamora six off-aspect events.
- **Randomiser.** Scenario, difficulty, modular sets, heroes and aspects, drawn
  only from what you own, with locking, rerolls and a history. Tap any row to
  choose it yourself instead: that choice locks and the rest rolls around it.
  Each option names the pack it came from, because a scenario is often not named
  after the villain on the box. The Green Goblin contains *Risky Business* and
  *Mutagen Formula*.
- **Versus games.** Civil War and Synthezoid Smackdown are not played against a
  villain but as a leader plus a side, Resistance or Registration, drawing three
  or four modular sets from a pool that is legal only in those games. Both
  halves are read from the card database, which models a leader as a set type of
  its own, so a new versus pack needs no code.
- **Rules the card database cannot express.** Only villain sets that bring a
  scenario of their own: the four Wrecking Crew villains are played inside
  Wrecking Crew, not instead of it. Difficulties are limited to the boxes they
  came in, so Standard I and Expert I with the Core Set, Standard II and Expert
  II with The Hood, Standard III with The Age of Apocalypse.
- **Counters at the table.** Optional, and off unless you ask for them. The
  villain's health and the main scheme's threat, worked out from the printed
  cards for the number of people playing, so Rhino is 14 solo and 56 at four.
  Per-round acceleration is applied by a button so nobody forgets it. The screen
  is held awake while it runs, and a switch on the play screen gives that up for
  one game. Counters only: the app does not adjudicate rules, because a tracker
  that is wrong once stops being trusted for the things it had right.
- **Play log.** Finished games are recorded and timed, whether they came from
  the randomiser, from a campaign, or were set up by hand. The clock can be
  corrected afterwards, a game can be thrown away before it is filed, and at a
  table of two or more the app draws who goes first.
- **Photographs of the table.** Taken during a game and kept with the play, so
  a board worth remembering is still there in the log months later. The picture
  is taken by the phone's own camera app through a content URI, which is why
  the app holds no camera permission and never opens a camera itself, and it
  stays in the app's private storage: a photo of somebody's living room does
  not belong in the shared gallery.
- **Putting a game away.** A game that has to stop mid-play can be filed rather
  than abandoned. The page writes down what nobody remembers a week later: a
  photo of the table, whether you stopped in the player phase or the villain
  phase and which of its five steps, what each hero has left, and the villain's
  life and which stage is face up. All of it optional, and what was left blank
  is left out rather than shown as a gap. The game then waits on the Play tab.
- **Statistics.** Win rate by hero, by scenario, by aspect, by difficulty, by
  hero *with* aspect, and solo against multiplayer, kept apart because a blended
  figure describes neither. Plus total time played, longest game, and current and
  best winning streak.
- **BoardGameGeek.** Optionally send finished plays to your account, with a
  proper player row and the victory points as the score. Credentials are
  encrypted with a key held in the Android keystore.
- **Campaign tracker.** An append-only event log with every bit of state folded
  from it, so a record cannot drift from what was played. Counters, flags, card
  lists, a market, per-scenario questionnaires, and setup steps for one scenario
  that depend on what was recorded in the ones before it. **The Galaxy's Most
  Wanted**, **Age of Apocalypse**, **The Mad Titan's Shadow**, **The Rise of Red
  Skull**, **Fear No Evil**, **Sinister Motives** and **Mutant Genesis** ship
  with the app.

  Fear No Evil is the odd one, because it has no scenario order at all. Two
  environments are drawn before every game and push their scenarios along
  whether or not anybody goes there, the table picks which one to play, and a
  scenario pushed three times is lost without ever being played. Losing a
  scenario does not fail it: you can go straight back and try again, and it is
  choosing to move on instead that turns its environment over to the failed face
  and takes the job out of the campaign. The app also asks, before the campaign
  starts, whether you want the villain order the book recommends for a first
  campaign or a random one.

  Sinister Motives brings its reputation track. The app puts the six printed
  conditions after each scenario, does the adding, and shows only the boxes the
  track has actually reached. Every reward and penalty hangs off the node the
  printed track hangs it off, so a run that reaches the eighth box gets what the
  eighth box gives and nothing else. The deals the track calls for are the app's
  job too, including the three S.H.I.E.L.D. Tech upgrades, which are dealt once
  per player rather than once for the table.

  Mutant Genesis brings roles. Each player takes one of the four at the first
  scenario and keeps it, and the upgrade they start each later scenario with is
  dealt from that role's own set of five rather than from a pile the table
  shares. An upgrade that has been in play is out of the campaign for the
  player who had it, so the five run down one player at a time, which is the
  "use it or lose it" rule the book is after. The log also carries which
  campaign side schemes were beaten, which Future Past cards are still in
  circulation, and who was rescued.

  Where a campaign says each player *chooses* a card, that is compulsory. The
  questionnaire records one answer per player and will not file until everyone
  has picked, because every later scenario assumes the card is in the deck.
- **Setup the app does for you.** Anything a campaign tells you to pick at
  random is drawn by the app: Age of Apocalypse's side missions and overseers,
  the order the Four Horsemen line up in, which Loki you face first, Fear No
  Evil's environments and subordinates, Sinister Motives' Community Service
  scheme, Osborn Tech and S.H.I.E.L.D. Tech, Mutant Genesis' role upgrades and
  the order the Brotherhood arrive in. Each draw is recorded so it cannot
  change while you read it, and kept out of the pool next time.
- **The setup printed on the scenario.** A campaign briefing lists the cards to
  fetch, then the campaign's own steps, then the setup written on the main
  scheme itself, which is the order it is done in at the table. That last part
  is read off the card rather than out of a template, so it arrives in whatever
  language the cards are in, and it covers sixty of the sixty-two scenarios.
  Ebony Maw and Thanos keep theirs in the rules insert and show nothing rather
  than a guess.
- **A setup page for games that are not campaigns.** A random draw, or a setup
  chosen by hand, lands on the same briefing: what to fetch, and then the setup
  printed on the scheme. The clock waits for a Play button rather than starting
  the moment the scenario is known, because laying a game out takes minutes that
  are not play, and they were being counted.
- **Finished campaigns.** Saved runs keep total time, victory points, heroes,
  credits and a per-scenario log of the answers given. A finished run shares as
  plain text, because a campaign is a dozen hours spread over weeks and the end
  of one is the moment somebody wants to show the people they played it with.
- **Backup and restore.** Collection, decks, campaigns, play history and
  favourites to a single readable JSON file, and back again on any device. There
  is no account and no server, so without this the phone is the only copy.
- **Two languages, chosen separately.** The language of the card text and the
  language of the app are different settings, because a French player with an
  English collection is a normal thing to be. Either can be French or English,
  and the app can also just follow the phone.
- **Dark theme**, on by default, with a light one if you prefer. Both are built
  around the game's aspect colours meaning only one thing: aggression red,
  justice gold, leadership blue and protection green belong to the cards, so the
  app's own chrome stays out of that palette.
- **French names the card database gets wrong.** MarvelCDB's French endpoint
  still returns some set names in English. `docs/game_contents.json` records what
  is in every box in both languages, and `tools/build_game_contents.py` derives
  the corrections the app applies.

## What is still to come

- **Card links in the Fear No Evil template.** MarvelCDB has published only the
  hero side of that pack, so its setup steps name cards in words rather than
  linking them. Card names in that campaign stay French for the same reason:
  guessing an English title risks printing something that does not match the
  card in your hand. When the encounter cards appear, the steps gain the usual
  tappable chips.
- **Fear No Evil and Shadowland in the randomiser.** Nine scenarios the card
  database has not got. Civil War looked like the same problem and was not, since
  the card database had it all along under a set type nothing else uses, so
  these would need a curated list.

Campaigns are added after they have been played, so the mechanics in a template
come from experience rather than from a reading of the book. That is the whole
roadmap. No further app features are planned, and anything else will come from
something going wrong at a real table.

## Versioning

Semantic versioning, and the third number carries its weight: **patch releases
are bug fixes and security work only**. A release that adds a campaign or a
feature moves the minor. Nothing here is an API anyone depends on, but a version
that says what kind of change it is means somebody deciding whether to update
does not have to read the diff to find out.

## Build it yourself

You need JDK 21, the Android SDK with platform API 37, and a device or emulator
on API 28 (Android 9) or later.

```bash
./gradlew assembleDebug
```

```bash
./gradlew lintDebug testDebugUnitTest
```

`local.properties` is not committed. Create it with your SDK path, or set
`ANDROID_HOME`.

### Release signing

Without a keystore the release build falls back to the **debug** key and says so
loudly. That is fine for testing on your own device and must never be
distributed. A properly signed build cannot upgrade over a debug-signed one, so
installing the real thing later means uninstalling first, which erases every
campaign, deck and collection setting on the device.

Create a key once:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias mcc
```

Then copy `keystore.properties.example` to `~/.mcc/keystore.properties` and fill
it in, keeping `release.jks` beside it.

It lives outside the repository on purpose. A signing password has to be plain
text for Gradle to use it, so the only real protection is where it sits. Inside
the project folder the key is one zipped folder, one cloud backup or one bad
`.gitignore` edit away from being shared by accident. The build also accepts
`$MCC_KEYSTORE_PROPERTIES` pointing at any path, and still reads
`keystore.properties` in the repo root if you prefer that.

**Back the keystore up somewhere other than this machine.** It cannot be
regenerated or recovered, and losing it means no future build can ever update an
installed copy of the app.

Verify which key a build actually used:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

A debug-signed build shows `CN=Android Debug`. `apksigner` lives in
`$ANDROID_HOME/build-tools/<version>/`.

Do not use `keytool -printcert -jarfile` for this. The APK is signed with scheme
v2 and v3 and carries no v1 JAR signature, so keytool reads it as **unsigned**,
which looks exactly like the failure this command is meant to detect.

## Card data

Card and pack data comes from the [MarvelCDB](https://marvelcdb.com) public API,
maintained by its contributors. The snapshot bundled into the APK is generated at
build time by `./gradlew fetchCardSeed` and is **not** committed to this
repository. See [docs/DATA_SOURCES.md](docs/DATA_SOURCES.md).

## Licence

The source code is released under the [MIT Licence](LICENSE).

That licence covers **this code only**. It grants no rights over Marvel
Champions: The Card Game, its cards, artwork, rules or campaign books, none of
which are mine to license. See below.

### With thanks

The Rules tab is built from
[deejimy/mc-reference](https://github.com/deejimy/mc-reference), a bilingual
reference guide for rulebook v1.8 compiled by **deejimy** and released under CC0.
The French rules in this app are his work, and the English comes from the same
pages, which is why the two always agree. CC0 asks for nothing in return, so
crediting him is the least this can do.

## Legal

This is an unofficial, non-commercial fan project. It is free, not for sale, and
carries no advertising.

Marvel Champions: The Card Game is © Marvel and published by Fantasy Flight
Games. This project is not affiliated with, endorsed by, or sponsored by Marvel,
Fantasy Flight Games or Asmodee. All trademarks and copyrights belong to their
respective owners.

The app is called **Thwart**. The game's name appears in the description and
nowhere in the app's own name, icon or branding. It is there to tell a player
what the app works with, not to suggest it comes from the publisher.

No card images, card text, or campaign book text is stored in this repository.

The release APK is a different matter and worth being plain about. It bundles a
snapshot of the MarvelCDB card data, around 13 MB of card text in both
languages, so the app is usable offline the moment it is installed. That
snapshot is fetched by `./gradlew fetchCardSeed` at packaging time and is
deliberately never committed, but it is inside the APK you download from the
releases page.

A build made without it works too, with one extra step. The app notices the
snapshot is absent, opens on Settings and waits to be told to fetch the cards. It
does not download them on its own, and nothing in this app does. That is what
F-Droid ships, since F-Droid builds from this source and the snapshot is not in
it. So the F-Droid build and the GitHub release differ: the same code, but one
arrives with the cards and the other asks first.

How a new version reaches F-Droid, which is by itself from the git tag, is in
[fdroid/RELEASING.md](fdroid/RELEASING.md). The one-time submission is in
[fdroid/SUBMITTING.md](fdroid/SUBMITTING.md).

Campaign templates in `app/src/main/assets/campaigns/` hold **mechanics only**:
card codes, counters, conditions and effects, plus short labels and a
two-sentence blurb per scenario, all written for this app. They contain no rules
text and reproduce nothing from the campaign book. They are a play aid for
someone who already owns the campaign box and has the book to hand. On their own
they do not explain how to play a campaign, and they are not a substitute for
either the book or the game.

The app collects nothing. There is no account, no analytics and no backend of any
kind, and everything it stores stays on the device. Android's automatic backup is
switched off for the same reason, since it would copy your play history and
campaign log to Google Drive without asking. Settings offers a backup you ask for
and keep yourself instead.

If the app crashes it writes the stack trace to its own private storage and
offers, in Settings, to open a mail draft containing it. That is not crash
reporting. Nothing is sent anywhere unless you press the button, the trace is in
the message body so you can read it first, and it is the only thing the app has
ever offered to send.
