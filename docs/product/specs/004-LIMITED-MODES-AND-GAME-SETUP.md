# Limited formats and game preparation

Status: implemented, 2026-09-20. Applies to Android and Web.

The owner requested these five capabilities after the 1.57.0 release:

- Adjust collection quantities during draft configuration, after selecting heroes
  and aspects. This is a session snapshot and never writes the saved collection.
- Draw only previously unplayed setups. The owner explicitly chose **hero plus
  scenario only**: aspects, modules, difficulty and result do not distinguish a
  pairing. Read completed, non-deleted play history, including campaigns and all
  seats, not saved random draws. Every seat must have a new pairing. Respect locks
  and collection filters; report exhaustion without falling back to played games.
- Prepare a game from an achievement: choose an owned remaining requirement and
  the required difficulty/table size. Campaign and limited-format goals open their
  corresponding setup. Suggestions do not award achievements or guarantee victory.
- After saving draft decks, offer a random game, campaign, own setup or playing
  later. Carry the actual saved deck IDs and every aspect into the selected setup.
- Offer sealed within the existing limited-format entry point. Deal 60 player
  cards per player, excluding signature cards. The owner clarified the booster
  simulation and explicitly confirmed **six boosters of ten**, preserving sixty
  cards (2026-09-19). Open each booster in sequence before deck construction;
  persist opening progress without redrawing cards. Add signatures separately and build
  a legal 40–50-card deck by adding/removing cards from that fixed pool. Reuse draft
  hero rules, canonical reprints, quantities and optional synergy filtering.

Sealed implementation choices: reserve physical copies across all players, deal
round-robin, and cap copies in each pool by the hero's legal per-card limit. Report
an insufficient 60-card pool before starting. Persist pools, selection and the
temporary collection on this device through the existing draft-session storage.
Existing sessions default to draft. Save ordinary decks with the existing optional
`sealed` tag and record the already-defined `sealed` play mode. No new sync
collection, backup field, card database or permanent collection mutation is needed.

Verification covers shared-stock conservation, deterministic resume, legal
selection and removal, old sessions, novelty exhaustion and locks, achievement
targets, saved-deck hand-off, and the affected UI flows on both platforms.

Visual direction confirmed by the owner during implementation: retain Thwart's
Marvel/comics styling, use a red angled title banner without a focus box around
the heading, show card art in sealed, align minus/count/plus controls, keep aspect
labels inside their buttons, and use sealed-specific action labels. Draft and
Sealed are separate selected-state buttons, not a checkbox. Use "Cards per
booster" / "Cartes par booster" for draft configuration. Show opening controls
above and below revealed cards, with an "Open all boosters" shortcut that reveals
the already-dealt pool and starts construction. Card hover previews use translated
rules text even when only English card art is available; touch opens card details.

On 2026-09-20 the owner extended the comic title treatment to all main Web pages.
The same day they requested separate draft and sealed victory milestones at
1, 5, 10 and 50 wins, and encouraging defeat milestones at 1, 5, 10, 50 and 100.
Definitions schema/version 2 and shared boundary vectors implement these on both
clients. Existing game-card portraits illustrate the milestones; no new artwork
is bundled. The first draft achievement retains its existing identifier.

On 2026-09-20 the owner reported that the random limited-game choice incorrectly
opened custom setup. It must open the actual randomizer, with saved deck IDs,
heroes and all aspects fixed while scenario, difficulty and modules remain
randomizable. Playing the draw retains those decks. The common user-facing label
for own setup is now "Custom game" / "Partie personnalisée" throughout both apps.

Web provides a visible back action and keeps the completed limited-deck chooser
in its browser history entry. Returning from any offered game mode, including
browser back and reloading that entry, must not start a fresh draft or save the
same decks twice. A new visit from the Play hub may start a new limited session.
The owner also approved comic title banners on main Android screens, with readable
contrast, wrapping and support for enlarged text; card/deck names remain readable.

The owner additionally requested Android navigation and button icons to follow
the Web artwork on 2026-09-20. Use the same outlined card/search, deck/star,
shield/lightning, home, rules/book, campaign/map, collection, history and trophy
symbols, with a red selected indicator and contrasting light icon. Preserve the
five Android navigation graphs and their labels and independent back stacks.
