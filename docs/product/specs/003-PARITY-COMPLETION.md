# Complete the approved client capabilities

Status: implemented and verified locally on 2026-09-19.
Owner explicitly answered "Oui, appliquer cette direction" to the combined choices.

1. Add starred plays on Android, using Web's existing `favourite_plays` contract,
   including backup, sync, tombstones, history controls and filtering.
2. Preserve imported photo files on Web and display them with recorded plays.
   Export photo-bearing backups as Android-compatible ZIP archives. No camera
   capture or photo account sync is implied. Retain offline/local privacy.
3. Add Web's owner-only album reading option. Default remains all seats and named
   achievements/completion always count all seats. Filters do not rewrite unlocks.
4. Keep the platforms' current navigation. No further owner decision is needed.
5. Use system theme for new Android installations, as Web already does. Preserve
   explicit choices and the old implicit dark theme on existing installations.

Storage and contract changes precede UI work. Test migrations, complete backup
round trips, conflict/deletion behavior and bilingual interactions. Preserve
separate repositories and release gates; this approval is not a release request.

Remote coordination remains a separate operational setup: prepare a usable shared
request workflow without claiming cloud access or multi-repository PR delivery
until the required host/environment and authentication have been established.

## Implementation

Android Room v28 adds `favourite_plays` and a defaulted `settingsExtras` column.
The auto-migration materializes stars previously carried in backup envelope extras
and marks them dirty for the existing opt-in sync collection. Old source schemas
remain unchanged. Settings extras belong to the backup dataset; account snapshots
continue to carry only known preferences. The initial theme is a separate local
fallback, not an explicit preference: a fresh device still adopts an existing
account theme on first merge, while choosing a theme manually remains authoritative.

Web IndexedDB v12 adds local photo bytes. Import writes rows and photos in one
transaction; incoming same-name files win in merge mode, replacement and erasure
clear the old files. Unknown settings/envelope metadata from v11 remains intact.
The ZIP reader bounds sizes, rejects duplicate/unsafe names and validates checksums;
the writer uses stored entries compatible with Android. Export lists only available
files; play references survive when an image is unavailable. No server change.

## Verification record

- Web type/Svelte check: zero errors and warnings. Production build passes.
- Web backup archive/settings, achievements, sync-integration and device-data tests
  pass. Database upgrade coverage includes v10 and v11 to v12, preserving existing
  metadata. Tests cover byte round-trip, ZIP corruption/traversal/duplicates, database
  reopen, merge collision, replacement rollback, unavailable files and sync exclusion.
- Local browser: synthetic photo import displays 1 of 2 files present; image decodes
  at its original 400 x 240 size. English/dark desktop and French/light mobile
  (390 px viewport) inspected without horizontal overflow. Missing-photo text is
  visible. Owner-only filter changes a non-owner hero from 1/8 to 0/8 while global
  achievements (2/31) and completion (2/56) stay unchanged. No browser errors;
  community ratings API intentionally unavailable at the local proxy.
- Android: 812 unit tests pass, zero failures/errors/skips. The optional restore
  test runs with a Web-generated synthetic photo ZIP and compares photo bytes,
  stars and metadata after Android re-export. Theme migration and first-merge
  absence semantics pass. Final full command `:app:testDebugUnitTest :app:lintDebug
  :app:assembleDebug` succeeds (7m49s); lint reports zero errors/warnings and two
  pre-existing informational hints.
  Initial compile issues (icon name, mixed SQL argument array types and an invalid
  test assumption about settings extras) were fixed, not suppressed. The first
  full unit pass found six outdated/isolation assertions: the new DAO/collection
  counts, known versus unknown stars, allowed sync defaults, and a previously
  skipped fixture leaking dismissed-pack preferences between tests. These were
  corrected against the actual contracts. Interrupted
  intermediate builds are not counted as passes.

- Emulator PhoneGoogle: updated the existing debug installation without clearing
  storage; previous history and dark appearance retained. Starred Rhino, filtered
  history to that one game, restarted the process and verified persistence, then
  unstarred it and verified the empty filtered view. Restored unfiltered history.
  French labels and empty state inspected, including accessible action descriptions;
  original locale restored. No application crash recorded. Test star removed.
- Shared definitions, vectors and logo geometry agree. Product documentation is
  distributed to Android with checked SHA-256 provenance. Both Git repositories
  retain their original locations, histories, remotes and separate feature branches.

Seed-dependent Android campaign tests are a separate suite and were not run in
this follow-up. The exported Room schema and synthetic migration tests are included.

These are focused checks, not exhaustive account recovery, screen-reader or physical
device certification. No release, production access, push or deployment is implied.

## Publication

The owner explicitly requested production publication on 2026-09-19. Web uses its
CI-tested release pointer and host deployment timer. Android 1.57.0 (code 91)
retains the maintainer-device check before the stable tag because Room changes.
