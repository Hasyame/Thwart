# Architecture

## Shape

Single `:app` module, MVVM with unidirectional data flow, offline first. Package
boundaries are drawn as if they were already Gradle modules, so the split can
happen later as a move rather than a rewrite.

```
ui/  ──────────►  domain/  ◄──────────  data/
     (StateFlow)          (interfaces)
```

- `ui/` holds Compose screens and ViewModels. It talks to repositories, never to
  a DAO or a Retrofit service.
- `domain/` holds pure Kotlin: models, the campaign engine, the randomiser, the
  URL parser. **No Android dependencies.** This is deliberate — it is the part
  that carries real logic, and it should be testable with plain JUnit in
  milliseconds.
- `data/` holds Room, Retrofit, WorkManager, DataStore, and the repositories
  that hide all of them.

## Storage layers

Three, with distinct lifetimes:

| Layer | Contents | Lifetime |
|---|---|---|
| Card database (Room) | cards, packs, FTS index | Cache. Rebuilt per device, excluded from backup, never exported. |
| User state (Room) | collection, decks, campaign runs, randomiser history | Owned by the user. Exported as one versioned JSON bundle. |
| Preferences (DataStore) | UI language, card language, last sync | Owned by the user, included in the bundle. |

The split matters for cross-device use: the bundle travels, the card cache does
not.

## Card search

Room `@Fts4` external-content table over the card table. FTS4 rather than FTS5
because Room's `@Fts4` is first-class and the SQLite bundled with older Android
versions does not reliably carry FTS5.

Accent- and case-insensitivity is handled by storing a **normalised column**
written at insert time — lowercased, `Normalizer.NFD`, combining marks stripped —
and normalising the query the same way. That is what makes `strategie` match
`Stratégie` without a custom tokeniser.

The same write-time idea carries the **synergy** condition: `cards.synergyTraits`
holds the trait keys a card's "Play only if your identity has the X trait" line
names, derived from the English text when the row is stored (empty string for
no condition, null only on a row older than the column, which the start-up
pass fills). The deck editor's "hide cards without synergy" filter is a `LIKE`
on it; the warning is worked out from it on every open and never stored. The
rule and the shared fixture are in `docs/spec/synergie-et-draft.md`.

## Card sync

WorkManager `CoroutineWorker`, triggered manually from Settings only. Downloads
both locales before replacing the catalogue in one Room transaction, so a
failed or cancelled sync leaves the previous database intact. Progress is
reported to the UI; the sync never blocks it.

## Campaign engine

The single most important design decision in the app: a campaign run is an
**append-only event log**, and all state is derived from it.

```kotlin
fold(events: List<CampaignEvent>): CampaignState
```

Events are sealed, each with a stable id and a timestamp. Consequences:

- Undo and history are free.
- Merging two devices' runs is merging two lists by event id.
- Raw questionnaire answers are stored alongside computed effects, so correcting
  a template and replaying an existing run is possible.
- Manual overrides are just another event type, and stay visible as such.

State the engine supports: campaign counters, **per-hero** counters (credits and,
on Expert, hit points capped at the hero's printed health read from the card
database), per-scenario boolean flag sets that later scenarios can *count* over,
card lists both per hero and per scenario, and per-hero status such as
eliminated.

Effects and conditions are sealed hierarchies deserialised from the template.
Arithmetic stays out of the schema: several small steps rather than one formula,
with `min`, `max` and a per-operation cap as the only maths.

`next` is a guarded list evaluated in order, so branching is data. The engine
never assumes the next scenario in the array.

Genuinely bespoke scenario mechanics go through a **handler registry**: a
scenario names a handler id, resolved at runtime against a `Map<String,
ScenarioHandler>` injected by Hilt. This is a last resort. If the same shape
appears twice, it belongs in the declarative schema instead.

### Engine and content are strictly separable

The app ships the schema and the validator. Bundled campaign templates contain mechanics only and live in `assets/campaigns/`.
Personal templates can also be imported from device storage and remain gitignored.
No campaign book prose belongs in committed templates. See the Legal section of the README.

## Navigation

Navigation Compose 2.10.1, type-safe `@Serializable` routes. Navigation 3 is
stable (1.1.5) and its back-stack-as-state model would suit the five independent
tabs well, but it was not chosen for milestone 1: the ecosystem around it is
still thin and the navigation library is not where this project's risk should
sit.

Each tab is a nested graph wrapping a start destination. The extra nesting is
what makes the back stacks independent under `saveState`/`restoreState`.

`NavigationSuiteScaffold` selects a bottom bar or a navigation rail from the
window size class. List/detail panes for cards and decks come with their
milestones.

## Testing

Priority order, reflecting where the logic actually is:

1. Campaign engine — the `fold`, condition evaluation, effect application,
   per-hero scoping, branching.
2. Randomiser — owned-pack filtering, locks and rerolls, mandatory and forbidden
   modular sets.
3. MarvelCDB URL parser — every URL form in F4.
4. Room DAOs, in-memory, especially FTS normalisation.
5. Flows via Turbine.

## Milestones

1. **Skeleton** — Gradle, version catalog, Hilt, Compose, five-tab navigation,
   CI, docs. *(done)*
2. **Data layer** — Room schema, MarvelCDB client, sync, seeding, FTS, pack
   metadata mapping.
3. **F5 card search + F1 collection.**
4. **F2 randomiser** and its curated scenario data file.
5. **F4 decklists** — 5a import and share target, 5b in-app deck builder.
6. **F3 campaign engine** and Galaxy's Most Wanted, once the campaign content is
   supplied.
7. **Polish.**

## Portable backups and account sync

Accounts are optional. Local play is complete without signing in. Opt-in sync
uses the Go server in the separate Thwart Web repository; record bodies and the
backup document remain shared contracts. A durable request in the device-only
sync session keeps the original batch ID and payload until acknowledgement.
Room 27 adds document-level backup metadata so unknown top-level fields survive
restore/export in the same transaction as the restored rows.

OS cloud backup and device transfer are excluded explicitly. The portable backup
is the supported transfer path; it omits credentials, card cache and sync
bookkeeping. Paused games and unfinished drafts remain device-local by design.
Their export/sync scope is unchanged; new shared fields require web coordination.
