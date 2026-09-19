# Cross-platform parity audit

Date: 2026-09-19. Scope: the two `feat/thwart-identity` working trees, based on
Web `9413601` and Android `fb9ffd8`. This records the implementation before the subsequent production request.

This is a source-backed audit with automated regression checks and focused Web
runtime inspection. It is not an exhaustive device, accessibility or account
certification. The [inventory](IMPLEMENTATION_INVENTORY.md) records the inspected
feature entry points and the pre-change baseline. Unknowns below remain unknown;
matching feature names or green independent tests do not establish full parity.

## Harmonization delivered

- Warm paper/dark surfaces, red action accents, stronger panel outlines and restrained
  comic shadows now connect Web to Android's active theme. Headers use warm surfaces,
  matching `comicTopBarColors`, rather than its obsolete red-header comment.
- The tilted card and gold T already have identical geometry. Web retains SVG/PWA,
  Apple and social assets; Android retains adaptive/monochrome assets. A sibling
  asset check now compares maintained geometry, definitions and vectors.
- Android now counts all seats for named hero/aspect achievements, distinct heroes
  played and completion. This implements the owner's latest-decision instruction,
  not a new product choice. Owner metadata and owner-only album filtering remain.
- Android now exposes named achievement and album-cell details, completed/remaining
  targets, tier thresholds and play evidence. These are derived from full history;
  filters do not rewrite unlock rules. No database migration or stored unlocks.
- Both clients use the same 31 definitions and the same 31 semantic vectors.
  Android additionally checks checklist/progress agreement and input-order stability.

## Owner decisions applied in follow-up 003

The owner explicitly approved the combined direction on 2026-09-19. The original
comparison is retained here with the accepted outcome, not as pending questions.

| ID / area | Original difference | Accepted outcome |
|---|---|---|
| P1 Navigation / flows | Web foregrounds progression; Android Home/Rules and independent back stacks | Retain each platform's navigation |
| P2 Theme default | Web system; Android implicit dark | System on new installations; preserve existing explicit and implicit Android preferences |
| P3 Starred plays | Web persistence/export/optional sync; absent Android collection | Add Android storage, backup, sync, tombstones, controls and history filter |
| P4 Photographs | Web skipped archive images; Android private photo gallery/archive | Preserve/display imported Web photos and export compatible ZIPs; camera capture remains Android-specific; no photo account sync |
| P5 Achievement album filtering | Web all-seat only; Android owner/all-seat option | Add Web owner-only reading filter; named achievements and completion continue to count every seat |

Implementation and current validation are recorded in [003](003-PARITY-COMPLETION.md).

## Contract defects and test gaps, not product choices

| ID | Evidence | Consequence / follow-up |
|---|---|---|
| C1 Unknown backup envelope/settings fields | Fixed locally in follow-up 002: Web retains unknown envelope/settings fields in local backup metadata; Android preserves envelope extras; follow-up 003 adds settings extras | Tested through upgrade, storage reopen, merge, replace and rollback with the Android fixture. Not a new sync collection; old discarded data cannot be recovered |
| C2 Photo-bearing backup portability | Fixed in approved follow-up 003: Web stores archive images locally, shows them with plays and re-exports ZIPs | Binary round-trip, missing-file disclosure, merge/replace/rollback and local-only storage verified; earlier imports that discarded images cannot recover them |
| C3 Broader business-rule equivalence | Both sides have draft/synergy/campaign/validation/statistics tests, but not one complete cross-client result suite | Existing tests passing independently does not prove matching legacy-row normalization, date boundaries, reprints, campaign/FNE catalogue or malformed-input behavior. Extend common fixtures rather than choose a client by timestamp |

## Coverage by area

| Area | Compared implementation and current finding | Limit / next useful check |
|---|---|---|
| Cards, collection, decks, ratings | Both implement search, ownership/exclusions, local decks/import, folders, validation and ratings; see inventory paths | Catalogue versions and copy/reprint resolution can change results. Test identical dated data on both clients |
| Randomizer, draft, play, campaign, versus | Both have feature entry points; campaign state is event-derived; FNE codes and draft/synergy rules have shared contracts | No feature removed for symmetry. Cross-client replay of complete campaign and legacy draft histories remains a stronger check than screen inventory |
| Statistics / progression | Web StatsPage and play statistics helpers; Android PlayStats tally all seats and deduplicate aspect keys per play; achievements now share vectors | Android recovers incomplete legacy rosters without inventing hero/aspect pairs. Verify legacy, timezone and localized-name grouping with shared fixtures before claiming complete statistical equivalence |
| Models / API / sync | Backup format 2 and revision/tombstone sync shared; Web server is the backend for both; new collections require opt-in and both codecs | C1/C2 are addressed above. Server/merge suites exercise protocol; no production account was used |
| Settings / terminology | Both support separate interface/card languages, theme, location and encounter tracking. Browser-local preferences versus DataStore/Room | EN/FR labels and stable wire codes are separate concerns. P2 is resolved; grouped navigation remains an intentional platform preference |
| Validation / error handling | Both have domain validators and parse/import errors; Web additionally handles browser storage/SW failure, Android repository/worker/device failure | Different recovery surfaces are legitimate. No exhaustive runtime matrix for quota, corrupt archives, failed migrations or expired sessions was performed |
| Offline / data freshness | Web cached shell/generated card JSON and service worker; Android Room and opt-in MarvelCDB refresh | Intentional lifecycle difference. Browser eviction and cold install differ from APK installation. Offline-after-install is not a promise of offline first-ever card download |
| Authentication / account | Optional on both, same account/sync service; Web owns verification routes and server; Android supplies native account screens | Backend and sync tests run locally. Email verification, recovery and deletion were not exercised against a live account |
| Notifications | In-app unlock feedback on both; Web install/update guidance, Android background WorkManager | Source search found no general Web Push/Notification or Android POST_NOTIFICATIONS feature. Background work does not imply OS alerts; no OS notification certification |
| Accessibility | Web native dialogs/focus/reduced-motion/safe-area styles; Android Compose semantics and accessible album rows | Web keyboard dismissal/focus return and mobile layout inspected. Token contrast checks are not a WCAG audit. Android TalkBack/large-text/device checks remain explicitly tracked in verification |
| Platform integration | Web browser links/PWA/SEO/social preview; Android SAF, sharing/deep links, adaptive launcher and APK/F-Droid | Preserve these complementary capabilities. Identical asset file formats or identical screens are not required |

## Decisions already resolved

- All seats count for achievement hero/aspect coverage and distinct heroes: owner
  confirmed the latest documented 2026-09-19 decision. Implemented on Android.
- Workspace uses junctions; original repositories and histories stay independent.
- Android visual direction, Web branding coverage and achievement configuration
  are starting references, not blanket authority over unrelated behavior.

## Verification

Initial verification is in `001-IDENTITY-AND-ACHIEVEMENTS.md`; follow-up verification is in `003-PARITY-COMPLETION.md`.
Runtime gaps and failed/retried tooling checks must remain visible there. No push,
release tag, production deployment or remote orchestration was performed.
