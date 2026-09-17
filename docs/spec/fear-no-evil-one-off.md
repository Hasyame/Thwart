# Fear No Evil outside the campaign

Decided with the author on 2026-09-18. The box's scenarios can be played as
one-off games, from the randomiser and from "My own setup", the same way on
Android and on Thwart Web. Android is the reference implementation:
`domain/play/FearNoEvil.kt` (the codes) and `domain/play/FearNoEvilBox.kt`
(everything read from the template), tested by `FearNoEvilBoxTest` against
the bundled `assets/campaigns/fne.json`.

## What the box is

Fear No Evil is on no card database. Its five **jobs** (Art Museum Heist,
The Getaway, Protection Racket, The Raft Breakout, Stop the Presses!) are
each played against one of five **subordinates** (Hammerhead, Bullseye,
Electro, Purple Man, Typhoid Mary), drawn at the table; the **finale**,
Kingpin, has its villain. The campaign template carries all of it: the
scenarios, `villainPool`, `localCardNames`, the `tracker` numbers and the
setup text. A one-off game reads the same template and nothing else.

## Codes

A one-off game names its scenario with a code, where a card set would be:

| code | meaning |
|---|---|
| `fne_s1_musee` | a job, villain still to draw |
| `fne_s1_musee__fne_villain_electro` | the job with its villain |
| `fne_s6_caid` | the finale, whose villain is fixed |

`fne_` + the template's scenario id; `__` + the villain's id from
`villainPool`, as the versus scenarios join their two halves. The code is
what a play stores in `scenarioCode`, what a replay reads, and what the
sync carries: a game recorded on one client must read on the other.

A job with no villain on it is **not a playable scenario**: the session
refuses to start, and the randomiser never leaves one in that state.

## Names

`"<scenario> : <villain>"` in the reader's language: "Art Museum Heist :
Electro", "Cambriolage du Musée d'Art : Electro". The finale is plain
"Kingpin". Scenario names are the template's `name`; villain names its
`localCardNames`.

## Offering

* **Owned**: the box's `packCode` (`fne`) must be in the collection, like
  any scenario's pack. A scenario excluded in the collection is excluded.
* **Randomiser**: the six scenarios enter the pool as six entries (not one
  per job × villain pair, which would make the box come up five times as
  often). When a job is drawn, or chosen by hand, a subordinate is drawn
  with it at random from the five. Rerolling the scenario rerolls both.
* **My own setup**: choosing a job opens a second choice, the villain, at
  once; a "Villain" row under the scenario shows it and lets it be changed.
  The finale asks nothing.
* No modular sets: the box's encounter deck is named in its setup text, and
  the scenarios have no entry in the modular rules.

## The tracker

From `tracker.villains[villainId]` (or `[scenarioId]` for the finale),
keeping the sides whose `onlyOn` matches the difficulty (`standard` for a
Standard set, `expert` for an Expert one), and `tracker.schemes[scenarioId]`.
Nothing from the campaign is added: no pressure, no carried threat. A
scenario in `perPlayerSchemes` (the racket) deals one scheme per player.
Villain sides are named in the reader's language; the tracker's own names
are French.

## The briefing

The scenario's `preSetup` steps, `include` fragments expanded, keeping the
steps whose `when` holds with the difficulty and the villain as the draw
(`drawIs: villain:<id>`), and dropping:

* steps that **draw** (the campaign draws the villain; the game has it);
* the step about the job's **environment card** (`{card:<scenarioId>}`):
  that card is the campaign's pressure board, turned over between jobs,
  and a one-off game has no use for it.

`{villain}` is the villain's name in quotes; `{card:x}` the local card's
name in quotes. No braces are left in the prose.

## Everything else

* **Ratings**: nothing to rate; the server knows no set for it.
* **History**: a one-off Fear No Evil game shows the box's bundled cover,
  as its campaign does.
* **Statistics**: grouped by the full code, so each pairing is its own
  line, as versus games are.
