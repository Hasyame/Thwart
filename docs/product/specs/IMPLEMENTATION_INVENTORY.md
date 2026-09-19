# Implemented product baseline and instruction audit

Inspected 2026-09-19. Android: `C:/MarvelChampionsCompanion`, branch `dev`, HEAD
`fb9ffd811d060f3c8ac037d48be820262621f519`. Web: `C:/Thwart Web`, branch `main`, HEAD
`94136016112a406f5267b05255a90471b46786d1`. Both working trees were initially clean.
Android reports version 1.56.1/code 90, Room 27. These facts supersede the older
release history in the handover notes; neither branch name proves what is deployed.

Method: repository instruction discovery, documentation and source inspection,
route/component/repository inventory, actual vector and asset comparison, focused
Web achievement and contrast tests. No application source was modified. The apps
were not launched on a browser or Android device in this phase. Feature presence
below is a code-backed inventory, not certification that every interaction is
identical or free of defects. Full visual and runtime parity testing remains after
approved harmonization, following the requested execution order.

## Instruction and documentation inventory

Repository-scoped discovery, including hidden paths while excluding Git/build and
dependency trees, found Android `AGENTS.md` and `CLAUDE.md`, and Web `AGENTS.md`.
No additional nested agent instruction files were found by that search. Personal
global instructions are outside this repository consolidation.

| Location | Material and use |
|---|---|
| Android root | AGENTS.md: extensive architecture, operations, release history and rules; CLAUDE.md: standing rules and toolchain/architecture; README, CONTRIBUTING, RELEASING, SECURITY and privacy documents |
| Android docs | ARCHITECTURE, DATA_SOURCES, REPO_SETUP, REVIEWING, BETA, SCENARIO_COVERAGE, FEAR_NO_EVIL_DESIGN, campaign-template helpers and field report |
| Android docs/spec | Achievements port note, synergy/draft, Fear No Evil one-off |
| Android fdroid and fastlane | F-Droid instructions/recipe and bilingual release metadata; preserve in Android |
| Web root | AGENTS.md: architecture, domain contracts, operational notes and standing rules; README: product and development overview |
| Web docs/design | Data audit, sync protocol, stack decision, roadmap, operations, Android sync briefs and live sync |
| Web docs/spec | Statistics, campaigns, ratings/modular sets, synergy/draft, Fear No Evil and achievement data-model/algorithm/sync/i18n plus vectors |
| Web docs/deployment and deploy | Backend/site promotion, nginx/systemd and operational scripts; preserve in Web |

Shared duplication: bilingual UI, no attribution/owner commit identity, data
minimization, card-content restrictions, offline optional-account operation,
backup/sync wire shapes, campaign event semantics, draft/synergy rules, difficulty
classification and achievement derivation. Put stable common rules in the product
entry point; keep detailed executable contracts/fixtures at identified paths.

Android-only: Compose/Hilt/Room/Gradle, exported schemas, device/emulator, SAF,
signing and F-Droid. Web-only: Svelte/TypeScript/Dexie, browser/PWA/SEO, Node tests,
Go/SQLite API and VPS release handshake. Instructions that are actually historical
notes should move to the platform's maintainer guide, not be duplicated in root.

Important contradictions and their treatment are listed in the workspace plan.
The owner has subsequently confirmed that the latest documented decisions prevail.
Use explicit decision provenance, not a repository-wide assumption of authority.

## Major functionality present in code

Paths under Web refer to `web/src`; Android paths refer to
`app/src/main/java/com/hasyame/marvelchampions` unless stated otherwise.

| Area | Web implementation | Android implementation | Initial assessment |
|---|---|---|---|
| Home and news | HomePage, changelog, account and achievement strip | ui/home, release changelogs, account and achievement strip | Both implemented; content/release cadence differs |
| Card catalogue/search | SearchControls, CardWindow/CardDetail, filtering and index data | ui/cards, CardSearchRepository, Room FTS | Both implemented; browser index vs FTS requires query-level comparison |
| Collection | CollectionPage, owned quantities/exclusions | ui/collection, CollectionRepository | Both feed deck legality, randomizer and achievements |
| Card favorites and ratings | Favorite mappings and RatingPanel/RatingRow | FavouriteRepository, RatingRepository, ui/ratings | Both implemented; aggregate API is Web backend |
| Deck library/building | DecksPage, DeckEditor, folders, imported/local decks and deck rules | ui/decks, DeckRepository/DeckBuilderRepository/DeckFolderRepository | Both implemented; hero-rule and reprint fixtures need matched revisions |
| Synergy | lib/synergy and build-derived trait fields | domain/deckbuilder and cached synergy traits | Common warning concept, not legality; storage mechanism differs |
| Draft | DraftPage, lib/draft, resumed session | ui/draft, domain/draft, DraftRepository | Both implemented; old Web 'Android port pending' statement is stale |
| Play hub/randomizer/versus | PlayHub, RandomizerPage, VersusPage | ui/play, ui/randomizer, ui/versus | Both implemented, different navigation entry points |
| Manual game and encounter tracker | PlayPage, Tracker, paused-game/setup helpers | GameSessionScreen/ViewModel, EncounterPanel | Both implemented; compare resume/timer/error behavior later |
| Campaigns | CampaignsPage, CampaignRun, engine/market/briefing/tracker | ui/campaign, domain/campaign, bundled templates | Both event-based; Web fetches Android public templates at build time |
| Fear No Evil one-off | lib/fearNoEvil and campaign/FNE helpers | domain/play/FearNoEvil, FearNoEvilCatalog | Shared one-off code vocabulary; verify subordinate/environment flows later |
| History and replay | HistoryPage, PlayDetail, replay helpers, starred plays | ui/history, play detail and replayId route | Both history/replay; starred plays is a Web capability gap |
| Statistics | StatsPage and shared statistics specification | PlaysScreen/ViewModel, PlayStats | Both implemented; screen/filter/formula equivalence still to audit |
| Achievements | AchievementsPage, derive/store/catalogue, detail targets | ui/achievements, AchievementRepository, pure derivation | Both implemented; actual algorithm and detail capability differ |
| Rules | RulesPage | ui/rules, curated rules_reference asset | Both implemented; source/content and lookup experience require later audit |
| Account | AccountPage, SignInForm, VerifyPage, device/sync controls | ui/settings account routes, data/sync | Both optional; web backend owns auth endpoints |
| Backup/import/export | BackupPanel, lib/backup and archive helpers | data/backup, BackupSection, JSON/photo archive | Shared format 2; Web exports photos empty, Android supports table photos |
| Photos | No equivalent table-photo feature in inspected Web backup flow | ui/photos and data/photos | Platform capability gap; do not discard Android photos |
| Settings/languages/themes | More/account panels, preferences.ts, appsettings | AppPreferences, ui/settings, Android resource locales | Both expose preferences; theme defaults differ |
| Offline/update | Hand-written service worker, cached shell/data, install help | Room cache, first-run initializer, WorkManager refresh | Different platform lifecycle; same offline promise needs scenario-based tests |
| BGG logging | BggPage, BGG helpers and server relay | data/bgg, BggPayload, PlayRepository | Both implemented; browser relay vs native networking |

## Verified identity and achievement findings

1. `Color.kt` and `Theme.kt` are Android's actual color sources. Some old comments
   still describe red/gold secondary selection, but the active Material scheme
   uses graphite/bone secondary and tertiary roles. Copy actual role assignments,
   not the introductory comment. Comic panels and halftone primitives supply the
   styling direction.
2. Web `tokens.css` consciously departed from direct Android transcription for
   contrast and visual hierarchy. Existing text/action and aspect-text variants
   should survive the move toward the requested visual direction. The token tests
   pass today; that does not validate all rendered component combinations.
3. Android launcher foreground and Web `Logo.svelte`/`public/icon.svg` use the
   same tilted card and filled T paths. Web adds an accessible SVG interface, icon
   generation, apple-touch icon and Open Graph preview. Android adds adaptive and
   monochrome icon resources. These are complementary capabilities.
4. Both achievement definition files have 31 entries, definitionsVersion 1,
   and SHA-256 `2fac996e5c36133219fd7c2c8460b291d4b831ddbbb095259033924a2b88dfd0`.
   Android's AchievementsAssetTest pins that snapshot to Web commit a9956972.
5. The vector files each contain 31 cases but are different. Web SHA-256:
   `9f0ccd2042ebd0976bd29d971a8c232df2066402d8322e9600aabbbac99a3c30`;
   Android: `094150a92de0d0215865601d6fb7fbe6236427968d2ff22e86d5a4f0551eb8ce`.
   Each suite reads its own local file. Consequently two green suites would not
   prove shared behavior. Add a pinned vector comparison along with the existing
   definition comparison in the implementation phase.
6. Web derive.ts uses every seat for heroes/aspects; Android AchievementDerivation
   uses `owner` in HeroesWon, AspectsWon and HEROES_PLAYED. This affects progress,
   unlocks and provenance. Latest documented all-seat decision is now confirmed
   by the owner. The Android implementation must catch up.
7. Web detail targets and dialogs show completed/remaining requirements, thresholds,
   dates and contributing plays. Android AchievementRow and AlbumScenario are
   display rows, with no equivalent achievement-detail dialog in the inspected
   screen. Preserve Web's richer behavior and port it appropriately.
8. Named achievement art maps are already aligned between Web art.ts and Android
   AchievementTexts. Both use card references and FNE cover handling; Android
   provides an additional FNE scenario fallback. No speculative asset rewrite or
   new artwork library is needed.

## Shared contracts and domain vocabulary

| Contract | Evidence to keep coordinated | Implication |
|---|---|---|
| Backup and records | Android BackupModels/PlayEntity/PlayHero/Extras; Web records.ts/playShape.ts/backup.ts; both round-trip fixtures | Preserve fields, defaults, unknown data, format version and export/import behavior |
| Sync | Android SyncEngine/SyncRecordCodec/SyncMerge; Web sync/{collections,engine,merge}; server/sync.go; design/02 | Check server revisions, dirty writes, tombstones, idempotency and collection-specific refinements |
| Account API | Web server/api.go and client auth code; Android data/sync | Shared endpoint and token/device behavior; no new client-only protocol assumptions |
| Achievements | Web docs/spec/achievements plus definitions, Android assets and vectors | Pin inputs, normalization, definitions and semantic expected state, not just ID lists |
| Campaigns | Android assets/campaigns and domain/campaign; Web lib/campaign and fetch-campaigns.mjs | Same declarative template/event meaning; build source revision matters |
| Synergy/draft | Both docs/spec/synergie-et-draft.md and fixtures/engines | Affinity warning is separate from legality; owned physical copies/reprints matter |
| Fear No Evil | Both fear-no-evil-one-off specs and helpers | `fne_<scenario>__<villain>` is a wire/domain key; do not replace with localized labels |
| Statistics/ratings | Web statistics and ratings specs, StatsPage; Android PlayStats and RatingRepository | Compare actual populations, difficulty rules, date/grouping and validation |

English `hero`/identity maps to French héros/identité; `aspect` maps to affinité;
`basic` to basique. Keep scenario, modular set, campaign template, campaign run,
play and deck as different concepts. Technical codes remain stable regardless of
translated display names. Audit visible strings separately from wire names.

## Baseline differences queued for the post-harmonization audit

These are candidate decision items or confirmed capabilities, not silently chosen
reference behaviors. First look for a latest explicit decision, as the owner asked.

| Area | Web now | Android now | Considerations and remaining work |
|---|---|---|---|
| Navigation | Cards, Decks, Play, Progress and More in grouped layout; Home in header/More | Home, Cards, Decks, Play, Rules | Product/UX choice: dedicated Progress vs Rules/Home access. Preserve both until latest decision is established or owner chooses |
| Theme default | CSS follows system without explicit override | ThemeChoice.fromCode defaults dark | Product preference distinct from palette harmonization; inspect latest decision before changing defaults |
| Achievement reading | All-seat album and richer detail dialogs | Owner/any-seat display filter and summary rows | All-seat counting is decided; retain or adapt reading controls without confusing them with unlock criteria |
| Starred plays | favouritePlays table, backup field and sync mapping | No corresponding favourite_plays implementation found | Capability gap; backend registers it opt-in. The mapping's comment about old clients stalling is stale relative to backend protection. Do not claim an actual sync stall |
| Photographs | Export writes photos empty; no native photo feature | Table photos and photo archive | Platform capability/product choice; test whether importing and re-exporting photo-bearing data loses anything before making portability claims |
| Data freshness | Generated card JSON served and cached by service worker | Room cache populated by user-approved fetch/refresh | Intentional implementation difference; compare fresh install, stale data, lost network and catalogue invalidation |
| Settings | IndexedDB shared settings plus browser-local presentation preferences | DataStore shared/local settings | Compare applied theme/card-language values after sync; identical stored fields alone is insufficient |
| Validation/errors | Strict definition parser, browser/network/storage errors | Kotlin parser and repository/ViewModel errors | Audit malformed definitions, unavailable catalogue, expired sessions, backup conflicts and recovery messages |
| Authentication | Web verification route and account UI, backend owned here | Native account UI with same server | Compare adoption confirmation, recovery, verification, deletion and session expiry on a local test server |
| Accessibility | Visible focus, native detail dialogs, reduced-motion styles and safe-area logic | Compose semantics and accessible album rows | Different mechanisms are legitimate. Need keyboard, focus return, TalkBack, large-text and contrast runtime checks |
| Notifications | In-app achievement feedback, PWA update/install guidance | Result unlock presentation and background WorkManager | No general notification-parity conclusion established. Inventory OS permissions/background alerts explicitly in later audit |
| Statistics/progression | Web screen and engine populated from local history | Android PlayStats and plays UI | Use identical fixtures for date boundaries, multiplayer, FNE, campaign and unknown difficulty; do not assume result parity |
| Platform integration | Browser links, SEO, install icons and responsive desktop | Android share/deep links, adaptive navigation, SAF and signing | Preserve platform-specific capabilities; identical screens are not the goal |

The full audit must exercise features, screens, user flows, terminology, models,
API usage, validation, offline behavior, settings, errors, auth, accessibility and
notifications on both clients after the first harmonization. For each unresolved
product choice record Web behavior, Android behavior, technical impact and an
owner decision in the shared feature spec. No later audit has yet been completed.
