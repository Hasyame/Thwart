# Shared identity and achievement harmonization

Status: accepted and implemented locally on 2026-09-19; see verification below.
The owner approved the proposed workspace and harmonization scope with "Oui".

## Goal

Make Web/PWA and Android recognizable as Thwart while keeping each platform's
capabilities and appropriate interaction patterns.

## Accepted direction

- Android supplies the initial color and comic visual direction.
- Web supplies the initial branding/logo and achievement configuration reference.
- Neither client has blanket authority. Do not discard advanced functionality.
- No repository merger, new production dependency, account requirement or
  deployment follows from this harmonization.

## Proposed design language

Warm paper and warm near-black surfaces, strong ink outlines, restrained panel
shadows, red action accents, warm-surface headers, and the existing gold T over a tilted card.
Gold remains a brand accent. Interactive selection stays neutral where gold could
be confused with Justice. Keep aspect hues semantically distinct from app chrome.
Adapt color roles for contrast rather than copying every hex into every context.

| Role | Android source | Current Web | Proposed handling |
|---|---|---|---|
| Brand red | IronRed `#E30022` | Light accent `#C4001C` | Use red for identity/actions; headers follow the active Android surface role; retain a readable text-action variant |
| Dark primary | `#FF5A49` | `#FF6A57` | Align hue after checking all foreground/background pairings |
| Light paper | `#FFF8F6` | Page `#F4F1EF`, white panels | Move toward warm paper with explicit panel outlines |
| Dark background | `#0D0809` | `#0D0B0C` | Align warm dark ground and coordinate browser/manifest metadata |
| Raised surfaces | `#120C0D`, `#1A1213`, `#241A1A`, `#2F2221` | Four-level CSS scheme | Translate semantic depths without requiring identical component count |
| Brand gold | `#FCC200` | Existing logo `#FCC200` | Preserve logo; avoid using bright gold for small body text |
| Aspect swatches | Aggression/Justice/Leadership/Protection/Pool | Same base swatch values | Keep swatches; Web already has separate accessible text variants |

Do not change default theme choice or navigation as a side effect. Those are
product decisions queued for the later audit. Do not replace keyboard focus,
native dialogs, reduced motion, safe-area handling or large-text support.

## Concrete changes after approval

### Web/PWA

1. Update `web/src/tokens.css`, `app.css`, `controls.css` and relevant shared
   top-bar/panel styles to express the agreed Android-inspired identity.
   Preserve responsive layouts and focus/contrast guarantees. Amend the old
   comment that rejects Android's design so it describes the new scoped decision.
2. Coordinate `web/public/manifest.webmanifest`, theme metadata in `web/index.html`
   and theme-setting code with the approved palette. Regenerate installation icons
   only if their source changes; retain the existing Open Graph asset unless its
   surrounding colors need adjustment and its result is visually verified.
3. Retain the existing SVG mark, PWA/apple icon generation, branded preview and
   accessible Logo component. Their broader asset coverage is useful already.
4. Keep achievement dialogs, completed/remaining target lists, tiers and provenance.
   Preserve definitions and stable identifiers unless an explicit rule change
   requires a versioned contract update.

### Android

1. Preserve the theme's visual direction and adaptive/monochrome launcher support.
   Verify the shared T geometry, background, safe-zone cropping and in-app mark.
   A logo replacement is not justified by the current comparison: the geometry
   already matches Web. Add a meaningful drift check for maintained variants.
2. Bring Web's achievement detail capability into a Compose dialog or sheet:
   explanatory text, completed and remaining heroes/scenarios/aspects, tier
   thresholds, date/difficulty and contributing play where available. Targets are
   derived presentation data, not new stored unlocks.
3. Align counting only after decision D1 below. Update the pure derivation,
   presentation filters if required, and pinned common vectors together. Keep
   owner metadata and both cell tallies for compatibility.
4. Compare art selection and fallback rules. Named badge hero/scenario maps
   already match; preserve Android's offline fallback rather than deleting it.
   Keep FR/EN text complete and explanatory controls accessible to TalkBack.

## Decisions required

| ID | Web | Android | Consequence / pending decision |
|---|---|---|---|
| D1: counting seats | Every seat counts for hero/aspect coverage and heroes played; Web spec records this on 2026-09-19 | Previously owner-only for named predicates; now aligned on all seats | Accepted and implemented 2026-09-19: all seats count. Owner confirmed that the most recent decisions are correct. |
| D2: workspace | Existing Web location | Existing Android location | Accepted 2026-09-19; logical junctions preserve both locations and Git repositories. |
| D3: implementation | Android-inspired styles; retain richer dialogs and assets | Add richer details and apply D1 | Accepted 2026-09-19; implementation authorized on both platforms. |

Example for D1: win as Spider-Man plus Captain Marvel, with Spider-Man marked as
owner. Web can credit both heroes and both seats' aspects. Android's owner-based
predicates credit Spider-Man's hero/aspects. This is a behavior change, not a logo
or configuration-file copy. It needs no new Room field to derive from existing data.

Owner clarification, 2026-09-19: "Les décisions les plus récentes sont les bonnes."
This resolves D1 using the explicit dated decision in Web's AGENTS.md and
achievement algorithm. Apply the same precedence to other traceable product
decisions; do not equate a newer implementation timestamp with product approval.

## Verification and acceptance

- The same definitions and vectors are used by both clients, with semantic output
  comparison on multi-seat, multi-aspect, unknown difficulty and campaign cases.
  Compare input normalization too; vectors alone start from already-normalized facts.
- Named achievements and detail checklists agree on targets, current progress,
  unlock provenance and tiers, including after edits, deletion, sync and catalogue
  refresh. No expected vector is changed merely to accommodate implementation.
- Both themes, FR/EN, phone/desktop Web and phone/tablet Android are inspected.
  Test missing images, offline use, large text, keyboard/dialog focus, TalkBack,
  reduced motion and launcher masks. Do not claim a WCAG audit from token tests alone.
- Web: `npm run check`, `npm run build`, affected `test:*` including contrast,
  achievements, nav, head, safe-area, device, sw, backup and settings; all scripts
  before push, with required card fixtures available. Backend checks independently
  if changed; full repository gates before publishing.
- Android: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, including
  achievement vectors/facts/assets and backup/sync regressions; device UI check.
- No schema migration is planned. If implementation reveals one is necessary,
  revise this scope and follow each repository's migration and release rules.

## Baseline checks

2026-09-19: Web `test:achievements` and `test:contrast` pass. Definitions are
byte-identical at SHA-256 `2fac996e5c36133219fd7c2c8460b291d4b831ddbbb095259033924a2b88dfd0`.
Both vector files have 31 cases, but differ in content. Android tests and full
builds have not been run in this analysis phase. No runtime visual parity claim
is made; the current comparison uses actual source, assets and focused tests.

## Implementation and verification record

Delivered locally on independent `feat/thwart-identity` branches, based on Web
`9413601` and Android `fb9ffd8`. No commit, push, tag or deployment was performed.
See [PARITY_REPORT.md](PARITY_REPORT.md) for the post-change audit and decisions.

- Web: `npm run check` (zero errors/warnings), production build and all 36 `test:*`
  scripts passed. The release-script test initially timed out under concurrent
  machine load; its complete rerun passed without changing the test or deployment
  code. Contrast was rerun after the final surface-header adjustment.
- Backend: `go vet`, formatting check and `go test ./...` passed. No server changes;
  the race detector was not run on this Windows toolchain.
- Android: the first full tests/lint/debug-build pass succeeded. A subsequent lint
  run crashed inside Compose's ModifierParameterDetector while sources were being
  finalized. No detector was disabled. The final stable-source run passed all three
  tasks in 11m56s: 807 tests, zero failures/errors, one optional real-backup skip.
- Shared assets: byte-identical definitions and all 31 vectors, plus SVG/Android
  logo geometry, verified by `tools/check-client-assets.mjs`.
- Browser inspection: local Web in light/dark themes, English/French, desktop and
  390px phone width; completed/remaining achievement dialog, keyboard dismissal
  and return of focus. Settings restored after testing. Final neutral headers were
  re-inspected in both themes. No live account or production API used for tests.
- Android runtime: debug APK installed on PhoneGoogle; achievement screen, recent
  unlock detail, all four completed aspects with multi-seat evidence, Core Set
  completed/remaining checklist, close and history navigation verified. Dialog
  screenshots were inspected. The emulator's System UI ANR was cleared before
  testing; no application crash was found in the crash log. English/dark phone
  rendering was exercised; French/light/tablet/TalkBack remain unverified at runtime.
- Coverage limits: these checks are not a full WCAG/TalkBack audit, tablet/launcher
  mask matrix or end-to-end account verification. The optional Android test using a
  real personal backup is skipped without `MCC_BACKUP_FILE`; synthetic backup tests
  run. No schema migration was introduced.
