# Thwart Web — scope

A companion for the players who are not on Android. It is not the app; it is the
half of the app that answers questions at a table, in a browser, added to a home
screen.

## Why a web app and not an iOS port

The Android app is 21,390 lines of Kotlin. Porting it means Kotlin Multiplatform,
a Mac to build on, 99 EUR a year to keep it installable, and Hilt torn out of
thirty files. A web app costs a static host, works on iPhone, Android, a laptop
and a Steam Deck, and needs nobody's permission to install.

The trade is real and worth stating plainly: **no shared code with the Android
app**. The two will drift unless the data they share stays in one place, which is
why this lives in the same repository and eats the same generated files.

## What it covers

- **Card search.** All 4,375 cards, offline, accent-insensitive, the same
  prefix-first matching as the app. Filters for type, aspect, cost and set.
- **Card detail.** The full card: text, traits, stats where the printed card puts
  them, the pack it comes from, the encounter set it belongs to, and the picture.
- **Rules reference.** The same 143 entries the app gained in v1.11.0, from the
  same `rules_reference.json`, with the same term-before-body search.
- **Both languages.** English and French card text, chosen in the page, from the
  same MarvelCDB endpoints the app uses.

## What it does not cover, and why

- **Decks.** Building and editing a deck is the app's largest surface, and a deck
  is worth nothing without somewhere to keep it. That means accounts or local
  storage that a cleared browser cache destroys. MarvelCDB already builds decks
  in a browser, and does it well.
- **Campaigns.** An append-only event log that must never be lost, on a platform
  where storage can be evicted without warning. This one is a promise the web
  cannot keep.
- **Play log, statistics, BoardGameGeek, backup.** All of them are records the
  user cannot afford to lose, for the same reason.

The line is not arbitrary: **the web companion holds nothing you would miss if
the browser forgot it.** Everything it shows can be rebuilt from MarvelCDB in a
second. Everything the app holds cannot.

## How it is built

Static files, no framework, no build step for the site itself. The data is
generated the way `game_contents.json` already is:

```
tools/build_web_data.py      ->  web/data/cards.en.json
                                 web/data/cards.fr.json
                                 web/data/rules.json   (copied from the app's asset)
```

4,375 cards trim to 2.27 MB, which is **0.31 MB gzipped** — the whole catalogue
ships with the page. A service worker caches it, so the second visit is offline
and instant. Card pictures are fetched from MarvelCDB on demand and cached as
they are seen, rather than downloaded in bulk.

Hosted on GitHub Pages from `web/`, deployed by Actions on push.

`web/data/` is **not in the repository**, for the same reason
`app/src/main/assets/seed/` is not: it is Fantasy Flight's card text. The Pages
workflow runs the generator before it uploads, so the published site carries the
cards and the repository does not.

## Refreshing the data

The same problem the app has: MarvelCDB adds cards. The app has an update button
the player presses. Here it happens on its own — every deploy fetches the
catalogue fresh, so the site is never older than its last push.

Running it locally, to see the site before pushing it:

```
python tools/build_web_data.py
python -m http.server 8765 --directory web
```
