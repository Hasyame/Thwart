# AGENTS.md — Thwart (Android)

Handover notes for a coding agent taking over this repository without the
previous conversation history. Read `CLAUDE.md` too: it holds the standing
rules (git identity, no AI attribution, Conventional Commits, toolchain
traps) and is still authoritative; this file adds what an agent needs to
operate the repository end to end. Where the two disagree, `CLAUDE.md` wins.

Everything below was checked against the repository on 2026-09-19 (branch
`feat/achievements`, `dev` at `10544b1`, version 1.55.0 / versionCode 88).
Statements marked **Unknown / requires confirmation** could not be verified
from the repository alone.

Current implementation update: Room is version 27 with a 26→27 auto-migration
for local backup metadata. Achievements use an accessible hero/scenario album
instead of the canvas grid, and catalogue changes invalidate derived inputs.
The dependency catalog is authoritative for current versions. The dated
release and F-Droid notes below are historical, not publication instructions.

---

## 1. Architecture

### What the product is

Thwart is an unofficial, offline-first Android companion for *Marvel
Champions: The Card Game*: card database and search, collection, deck
builder (MarvelCDB import and local decks), randomiser, campaign tracker
(event-sourced), draft mode, play history and statistics, achievements,
optional account sync with the web app (thwart.app), optional BoardGameGeek
play logging. Bilingual French / English, with the app language and the
card language set independently.

A sister project, **Thwart Web** (Svelte PWA + Go server, repository
`C:\Thwart Web` locally, deployed at https://thwart.app), shares the data
contracts with this app: the backup document, the sync protocol, the
achievement definitions, the synergy rule, the draft rules, the Fear No Evil
one-off codes. **The web repository is not this repository**; its server,
Docker/nginx/systemd deployment and CI are documented there. This app talks
to that server only for the optional account sync.

### Layout

Single Gradle module `:app`, package `com.hasyame.marvelchampions`:

| Directory | Responsibility |
|---|---|
| `core/designsystem`, `core/ui` | Theme (comic style: halftone background, `ComicPanel`, `comicTopBarColors`), reusable composables. |
| `data/db` | Room database (`MarvelChampionsDatabase`, **version 26**), entities, DAOs, FTS4 card search, exported schemas in `app/schemas/`. |
| `data/marvelcdb` | Retrofit client and DTOs for the MarvelCDB public API (EN and FR hosts). |
| `data/seed` | First-run seeding from `assets/seed/*.json` (gitignored, fetched by `./gradlew fetchCardSeed`) and curated assets (`pack_metadata.json`, `scenario_rules.json`, `set_name_overrides.json`, `rules_reference.json`). |
| `data/repository` | The only layer `ui/` may call. One repository per concern (cards, collection, decks, campaigns, plays, ratings, draft, achievements, FNE catalogue…). |
| `data/sync` | Account sync with thwart.app: `SyncEngine` (pull/push per collection with revisions), `SyncRecordCodec` (row ⇄ JSON body), `SyncMerge`, `SyncStream` (SSE), `AutoSync`/`AutoSyncWorker`, `CardSyncManager`/`CardSyncWorker` (MarvelCDB refresh through WorkManager, staged tables swapped in one transaction). |
| `data/backup` | The versioned JSON backup document (`Backup`, **formatVersion 2**), SAF export/import, optional photo archive. |
| `data/bgg` | BoardGameGeek login and play reporting (`BggClient`). |
| `data/settings` | DataStore preferences (`AppPreferences`). |
| `data/photos` | Table photographs in private storage. |
| `domain/*` | Pure Kotlin, no Android types: `campaign/` (append-only event log + `CampaignEngine.fold`, `TemplateValidator`, template schema), `randomizer/`, `deckbuilder/` (`DeckValidator`, hero rules), `draft/` (pack-based draft engine), `achievements/` (derivation), `play/` (Fear No Evil one-off codes), `ratings/`, `search/`, `deeplink/`, `model/`. |
| `ui/*` | Compose screens + Hilt ViewModels per feature (`home`, `cards`, `decks`, `play`, `plays` (game session, stats), `campaign`, `randomizer`, `draft`, `history`, `achievements`, `versus`, `collection`, `rules`, `settings`, `photos`, `ratings`). `ui/navigation` holds the type-safe routes and the five-tab nested graphs. |
| `di/` | Hilt modules (database, network, `Json`, dispatchers). |

Other top-level directories:

| Path | Purpose |
|---|---|
| `app/src/main/assets/campaigns/*.json` | Bundled campaign templates (mechanics only, committed). |
| `app/src/main/assets/achievements.json` | Byte-identical snapshot of the web's achievement definitions. |
| `app/schemas/` | Room exported schemas, one per version; auto-migrations are generated from them. **Commit new ones.** |
| `docs/` | `ARCHITECTURE.md`, `DATA_SOURCES.md`, `REPO_SETUP.md` (GitHub settings to apply by hand), `RELEASING.md` (root), `BETA.md`, `REVIEWING.md`, `SCENARIO_COVERAGE.md`, `FEAR_NO_EVIL_DESIGN.md`, `campaign-templates/` (blank template + questionnaire), `spec/` (French design notes: `synergie-et-draft.md`, `fear-no-evil-one-off.md`, `achievements.md`). |
| `fastlane/metadata/android/{en-US,fr-FR}/` | Store listing and per-versionCode changelogs. The changelogs are also bundled into the APK at build time (`bundleChangelogs` task) and shown on the home page. |
| `fdroid/com.hasyame.marvelchampions.yml` | Copy of the F-Droid recipe kept in step with each release (see §5). |
| `tools/` | `generate-scenario-rules.mjs` (regenerates `scenario_rules.json` from the card seed), `build_*.py` (campaign template builders), `build_rules_reference.py`, `drive-device.sh` (uiautomator-based device driving), `ci/` (shell checks used by the workflows). |
| `web/data/` | Gitignored leftover of an earlier GitHub Pages web build. No workflow uses it any more. **Unknown / requires confirmation** whether it can be deleted; nothing in the repository references it. |
| `*.apk` at the root | Old local builds, gitignored. Ignore. |

### Main data flows

- **Cards**: first launch seeds Room from `assets/seed` when present, otherwise asks the user before downloading from MarvelCDB (`FirstRunInitializer`, `CardSyncManager`). Everything about scenarios, heroes, modular sets is derived from the card table at runtime; only pack type/wave (`pack_metadata.json`) and scenario→modular relationships (`scenario_rules.json`) are curated.
- **User state**: owned packs, exclusions, favourites, decks, folders, campaign runs + events, plays, randomiser history, ratings, draft session. Exported as one JSON `Backup` (photos optionally in a zip). The card cache is never exported.
- **Campaigns**: a run stores its template JSON and an append-only `CampaignEvent` log; `CampaignEngine.fold(template, events)` derives all state. Nothing derived is stored. Finishing a scenario also records a `PlayEntity` with `campaignRunId`.
- **Plays**: every finished game (own setup, randomiser, campaign) goes through `PlayRepository.record`, which stamps location/updatedAt and optionally reports to BGG. Statistics, history and achievements are all derived from `plays`.
- **Achievements** (new, 2026-09-19): `AchievementRepository` builds a `DeriveInput` (definitions, catalogue from card data, owned packs, play facts, run facts) and `AchievementDerivation.derive` produces the state. Never stored; recomputed on change. Shared spec lives in the web repository (`docs/spec/achievements/`).
- **Sync**: each syncable row carries `updatedAt`/`deletedAt` (tombstones) plus a `sync_state` row (dirty flag, revision). `SyncEngine` pulls per collection and pushes dirty rows; bodies are the entities serialised exactly as in the backup, because the server's `/account/export` produces a backup file. Merge is last-write-wins per record (`SyncMerge` has a few field-level exceptions, e.g. `reportedToBgg`). Server: thwart.app (`SyncEndpoints.DEFAULT_BASE_URL = https://thwart.app/api`).

### Important dependencies

Kotlin 2.4.10, AGP 9.4.0 (version catalog says 9.4.0; `CLAUDE.md` says 9.3.1 — the catalog is the truth), Compose BOM 2026.08.00, Material 3 + adaptive navigation suite, Navigation Compose 2.10.0 (type-safe routes), Hilt 2.60.1, Room 2.8.4 (KSP), WorkManager, DataStore, kotlinx.serialization 1.11.0, Retrofit 3 + OkHttp 5 (+ SSE), Coil 3. Tests: JUnit 4, Robolectric 4.16.1 (pinned to SDK 36 via `app/src/test/resources/robolectric.properties`), Turbine, coroutines-test, navigation-testing, room-testing. All versions live in `gradle/libs.versions.toml` only.

### Architectural decisions (and why)

- **Offline first, no account required, nothing collected.** Every network call is user-initiated or opt-in (card sync, account sync, BGG). This is a product promise and an F-Droid requirement.
- **`domain/` has no Android dependency** so the engines are unit-testable in plain JUnit.
- **Event-sourced campaigns** (`docs/ARCHITECTURE.md`): undo, history and two-device merge come for free; derived state is never persisted so it cannot disagree with the log.
- **Derived, never stored** is the recurring rule: synergy traits are the one exception (written at insert time as a cache column, re-derived when `SYNERGY_RULE_VERSION` changes), achievements are derived on the fly, statistics are queries.
- **Contracts shared with the web are ports, not reimplementations**: the synergy rule was ported function for function from the web's `synergy.mjs`; the achievements derivation reproduces the web's 31 test vectors exactly; the backup format and sync bodies are one shape on both sides.
- **Room schema changes are auto-migrations** from exported schemas (see `MarvelChampionsDatabase.autoMigrations`), with a spec class only when data must be seeded (`SyncMigration17To18`).
- **Debug build uses `applicationIdSuffix = ".debug"`** so it installs beside the release build with its own database.
- **Card data never enters the repository or the APK** (legal: Fantasy Flight's text). CI verifies the release APK carries no `assets/seed/`.

---

## 2. Development environment

- **Languages/frameworks**: Kotlin, Jetpack Compose, Gradle Kotlin DSL. Helper scripts in Python 3 (`tools/build_*.py`) and Node (`tools/generate-scenario-rules.mjs`).
- **Required**: JDK 21, Android SDK with platform API 37 and build-tools, Gradle wrapper 9.7.1 (`gradlew`), Android device/emulator API 28+. `local.properties` with `sdk.dir`, or `ANDROID_HOME`.
- **Dependency management**: Gradle version catalog `gradle/libs.versions.toml`; Dependabot (monthly, grouped, targets `dev`).
- **Configuration cache is on** (`gradle.properties`); tasks must resolve inputs at configuration time (see `checkStringEscaping`, `fetchCardSeed`).

Commands (run from the repository root; PowerShell users use `.\gradlew`):

| Task | Command |
|---|---|
| Debug APK | `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` |
| Unit tests (JUnit + Robolectric) | `./gradlew :app:testDebugUnitTest` (~785 tests, ~5–8 min cold) |
| One test class | `./gradlew :app:testDebugUnitTest --tests "com.hasyame.marvelchampions.domain.achievements.*"` |
| Lint (warnings are errors) | `./gradlew :app:lintDebug` — report in `app/build/reports/lint-results-debug.xml` |
| Everything CI runs | `./gradlew lintDebug testDebugUnitTest assembleDebug` |
| Release APK (needs keystore, else debug-signed with a loud warning) | `./gradlew :app:assembleRelease` |
| Card seed for offline first launch (optional, ~15 MB, gitignored) | `./gradlew fetchCardSeed` |
| Regenerate scenario rules | `node tools/generate-scenario-rules.mjs` (needs the seed) |
| Install on a device | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Drive the app by on-screen text | `tools/drive-device.sh text|tap|field|scroll|shot` |

There is no formatter configured (no ktlint/spotless). Match the surrounding style: 4 spaces, trailing commas, KDoc that explains *why*. Lint runs with `warningsAsErrors = true` and `checkDependencies = true`; `GradleDependency` and `AndroidGradlePluginVersion` are disabled on purpose. Common lint failures and their fixes: `PluralsCandidate` (reword so a number is not followed by a noun, or use `<plurals>`; French plurals need `one`, `many`, `other`), `UnusedResources` (delete the string), `MissingTranslation` (add the French half). A bare apostrophe in `strings.xml` fails `checkStringEscaping` before aapt2: write `\'`.

Emulator notes (local experience, not enforced): the `PhoneGoogle` AVD is more stable headless (`emulator -avd PhoneGoogle -no-window -gpu swiftshader_indirect -no-snapshot`), SystemUI "isn't responding" dialogs appear after cold boot and are dismissed with a tap + `am force-stop com.android.systemui`, and two concurrent `uiautomator dump` calls crash uiautomator.

---

## 3. CI/CD (GitHub Actions)

Three workflows in `.github/workflows/`, all with `permissions: contents: read` at the top, every action pinned to a commit SHA (a tag can be repointed), and Gradle cache **read-only on pull requests** so a fork cannot poison the shared cache. `pull_request` is used, never `pull_request_target` (that would run contributor code with a writable token). Workflows run on both repositories (see §6): the private `Thwart-dev` (where work happens) and the public `Thwart` (where releases and F-Droid live).

### `ci.yml` — "CI"

Triggers: `pull_request` and `push` on `dev` and `main`, `workflow_dispatch`. Concurrency group per branch, cancel-in-progress.

Three independent jobs (no dependencies between them), each: checkout → JDK 21 (Temurin) → `gradle/actions/setup-gradle`:

1. **Build and test**: `./gradlew --no-daemon testDebugUnitTest`, then `assembleDebug`; uploads `app/build/reports/tests/**` as artifact `test-reports` (7 days). No card seed: the app must build and test without it.
2. **Android Lint**: `./gradlew --no-daemon lintDebug`; uploads `lint-report`.
3. **F-Droid compliance** (`fetch-depth: 0`): `tools/ci/check-no-binaries.sh` (no tracked `.jar/.aar/.so/.dex/.apk/.aab` except the Gradle wrapper), `tools/ci/check-dependencies.sh` (resolves `releaseRuntimeClasspath` and fails on any coordinate in `tools/ci/proprietary-coordinates.txt`; the script also honours an optional `tools/ci/proprietary-allowed.txt`, absent today), and on pull requests only `tools/ci/check-version-code.sh origin/<base>` (fails only when the PR touched the version and `versionCode` did not increase).

Why three jobs: the branch ruleset (§6) is meant to require only "Build and test" and "F-Droid compliance". Lint can go red because a library moved; Campaign data depends on MarvelCDB being up. Neither should block a contributor.

### `data.yml` — "Campaign data"

Triggers: `pull_request` to `dev`/`main` only when campaign assets, `tools/build_*.py`, `domain/campaign/**` or the workflow itself change; weekly `cron: "17 6 * * 1"`; `workflow_dispatch`.

One job: fetches the card seed from MarvelCDB (`./gradlew fetchCardSeed`) and runs only `BundledCampaignsTest`, `CardPlaceholdersResolveTest`, `TemplateValidatorTest`, `TranslationCoverageTest`. These tests skip themselves without the seed, so they cannot run in `ci.yml` without making every PR depend on a third-party API. The schedule exists so a change at MarvelCDB (a renumbered card) is noticed here, not by a player mid-campaign.

### `release.yml` — "Release"

Trigger: push of a tag `v*`. This is **the only workflow that sees the signing key**, and a tag can only be pushed by someone with write access, so a pull request can never reach it. Concurrency `release-<ref>`, no cancellation.

Single job `release`, `environment: release`, `permissions: contents: write` (to create the release). Steps, in order:

1. Decide `prerelease` from the tag: any `-` suffix (`v1.30.0-beta.1`) is a beta.
2. `tools/ci/check-tag-matches-version.sh <tag>`: the tag (suffix stripped) must equal `versionName` in `app/build.gradle.kts`.
3. `testDebugUnitTest`, `lintDebug`, the two F-Droid scripts: a tag is not an excuse to skip checks.
4. **Restore the signing key** from environment secrets into `$RUNNER_TEMP/signing/` (outside the workspace), writing a `keystore.properties`. Fails if `KEYSTORE_BASE64` is empty rather than publishing a debug-signed build.
5. `assembleRelease` with `MCC_KEYSTORE_PROPERTIES` pointing at that file (the build script's first lookup location).
6. Artifact checks on the APK: no `assets/seed/` inside; no `networkSecurityConfig`/`usesCleartextTraffic` in the manifest and no `network_security` file (the cleartext exemption lives in `src/debug` only; `CleartextIsDebugOnlyTest` checks the same from the source side); `apksigner verify --print-certs` must not show `CN=Android Debug`. The APK is renamed `Thwart-<version>.apk`.
7. Beta: upload the APK as a workflow artifact `Thwart-<tag>` (60 days), nothing public. Stable: `gh release create <tag> Thwart-X.Y.Z.apk --title "Thwart X.Y.Z" --notes-file fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (falls back to generated notes). Idempotent: an existing release is left alone, or gets the asset attached if missing.
8. `always()`: remove the signing directory and `certs.txt`.

**Secrets (names only)**, configured as *environment* secrets on the `release` environment of the public repository: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The environment should have the maintainer as required reviewer and a deployment-tag policy `v*` (`docs/REPO_SETUP.md` §7). Whether the reviewer gate is actually enabled: **Unknown / requires confirmation** (recent releases ran to completion without a visible manual step in this session's logs, which suggests no reviewer is required today).

Environment variables used by the build: `MCC_KEYSTORE_PROPERTIES` (optional path to the keystore properties), `ANDROID_HOME` (build tools in the release job), `MCC_BACKUP_FILE` (optional, lets `BackupRoundTripTest` run against a real export), `THWART_WEB_DIR` (optional, lets `AchievementsAssetTest` compare the bundled definitions with a web checkout; defaults to `C:/Thwart Web`).

External services: GitHub (releases, artifacts), MarvelCDB (`data.yml` only), nothing else. No deployment to any server happens from this repository.

Failure handling: there is no rollback. A failed release run publishes nothing (the release is created last). If a tag was pushed with a wrong `versionName`, fix the build, delete the tag locally and remotely, retag (F-Droid reads tags, so never leave a wrong tag). Re-running a successful stable release is safe (idempotent publish step).

Caching: `gradle/actions/setup-gradle` cache, read-only on PRs and in the release job, writable on pushes to `dev`/`main`.

Signed APKs produced here are signed with the maintainer's key: certificate `CN=BREUL Benoit`, SHA-256 `335e95961dc79468969e7ba3aa9ab23e418ff5012336ad70b4d1731533f7e5c4`. Verify every published APK against this before announcing a release.

---

## 4. Docker and infrastructure

None in this repository. There is no Dockerfile, no Compose file, no server code. The backend this app optionally talks to (account sync at thwart.app, BGG relay) is the Go server in the **Thwart Web** repository, deployed on a VPS with nginx and systemd units (`deploy/` there). Do not add server concerns here.

---

## 5. Deployment and operations

"Deployment" for this app means a signed APK on GitHub Releases plus the F-Droid build. There is no staging environment; the debug build (`.debug` suffix, separate data) is the test bed, and betas are `-beta.N` tags handed out privately.

### Release procedure (as practised for 1.54.x–1.55.0)

1. Finish work on a feature branch, open a PR against `dev` on `Hasyame/Thwart-dev`, get CI green.
2. **Merge locally** into `dev` with `git merge --no-ff origin/<branch>`. Do not merge stacked PRs from the GitHub UI: the 1.54.0 stack got half-closed and a tag landed on the wrong commit that way.
3. Bump `versionCode` (+1, every release) and `versionName` in `app/build.gradle.kts`; minor for features, patch for fixes.
4. Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and `fr-FR/…` (under ~500 characters; these become the release notes and the home-page "what's new").
5. Commit `chore(release): X.Y.Z`. Run `./gradlew :app:lintDebug` at least (CI reruns everything on the tag).
6. `git push origin dev`, then `git push public dev:main` (the public repository's `main` is a mirror of `dev` at release points), then `git tag -a vX.Y.Z -m "…"` and `git push public vX.Y.Z`. The tag on the **public** repository triggers `release.yml` there.
7. Watch the run (`gh run list -R Hasyame/Thwart`), download the APK from the release and verify the signer certificate and `versionCode`/`versionName` (`apksigner verify --print-certs`, `aapt dump badging`).
8. F-Droid: update the build entry in `fdroid/com.hasyame.marvelchampions.yml` (versionName, versionCode, the release commit hash) and commit it (`chore(fdroid): move the build entry to X.Y.Z`), push `dev` and `public main` again. Then mirror the same change into the maintainer's `fdroiddata` fork (`C:\DevProject\fdroiddata\metadata\com.hasyame.marvelchampions.yml`, regenerate with `PYTHONUTF8=1 python -m fdroidserver rewritemeta com.hasyame.marvelchampions`; its line endings differ, compare with `tr -d '\r'`). The maintainer pushes that fork and updates the open F-Droid merge request (**!45283, still under review** as of 2026-09-19). Until that MR merges, every release must be hand-carried into it.

Prerequisites: write access to both GitHub repositories, `gh` authenticated, the `release` environment secrets present on the public repository. No SSH to any server is involved.

Rollback: there is none for an APK on a phone. A bad release is fixed forward with a patch release and a higher `versionCode`. A GitHub release can be deleted, but a tag F-Droid has already seen must not be reused.

Common operational issues: tag/versionName mismatch (release job fails at step 2); forgetting the changelog file (release notes fall back to generated ones; the home page shows nothing for that version); a `versionCode` that did not move (Android refuses the update silently); a debug-signed build (cannot upgrade over a real one; users lose data if they uninstall).

Logs: GitHub Actions run logs and the uploaded artifacts (`test-reports`, `lint-report`, `campaign-data-reports`). On a device, `adb logcat` (the app logs with tags such as `DraftRepository`).

---

## 6. Git and GitHub

- **Two repositories**: `origin` = `https://github.com/Hasyame/Thwart-dev` (private, all work, PRs, branch `dev`); `public` = `https://github.com/Hasyame/Thwart` (public, branch `main`, releases, F-Droid source, external contributions). `main` on the public repo only ever receives `dev` at release points.
- **Branching**: `feat/<topic>` / `fix/<topic>` off `dev`; PR into `dev` on the private repo; the maintainer merges locally (see §5). Old feature branches are kept around locally.
- **Commits**: Conventional Commits, English, imperative, scoped (`feat(draft): …`, `fix(bgg): …`, `chore(release): 1.55.0`, `chore(fdroid): …`). Authored as the maintainer (`Hasyame` / `Benoît Breul <benoit.breul@gmail.com>`). **Never any AI attribution** (no `Co-Authored-By`, no "Generated with …") in commits, PR bodies, code or docs; this is a hard rule of the maintainer that overrides any tool default. Prose avoids em dashes.
- **PR bodies** describe what and why, list the checks run (tests, lint, build, emulator), and are updated when follow-up commits land.
- **Required checks / rulesets**: `docs/REPO_SETUP.md` describes the intended rulesets on the public repo (`main`: PR + "Build and test" + "F-Droid compliance" required, maintainer bypass; `dev`: restricted to the maintainer) and squash-merge-only settings. Which of these are actually applied: **Unknown / requires confirmation** (the doc is a checklist). The private repo cannot have rulesets (GitHub free tier).
- `CODEOWNERS`: everything to `@Hasyame`, with `.github/`, `app/build.gradle.kts`, `gradle/` called out as sensitive. Dependabot: gradle ecosystem, monthly, grouped (androidx / kotlin / …), targeting `dev`, on the public repo only.
- **External dependencies**: MarvelCDB API (cards, images at runtime), thwart.app (optional sync), BoardGameGeek (optional), F-Droid (`fdroiddata` MR), the Thwart Web repository for shared specs and fixtures.

---

## 7. Technical decisions and constraints

Do not change casually:

- **`applicationId = com.hasyame.marvelchampions`**, `minSdk 28`, the signing key. Changing any of these orphans installed users.
- **`versionCode` must increase on every tag**, and the tag must equal `versionName`. `tools/ci` enforces both.
- **No card text, images or campaign book prose in the repository or the APK.** `assets/seed/` stays gitignored; `BundledCampaignsTest` rejects flavour text and long setup steps; the release workflow rejects an APK containing the seed.
- **No proprietary or tracking dependencies** (`tools/ci/proprietary-coordinates.txt`), no analytics, no crash reporting. F-Droid and the privacy promise both depend on it.
- **No cleartext networking in release**; the only `network_security_config` lives in `src/debug`.
- **Backup / sync contract** (`data/backup/BackupModels.kt`, `data/sync/SyncRecordCodec.kt`): the JSON shape of every entity is shared with the web app and the server export. Renaming a field, changing a default or dropping nulls changes what other clients read. Format is 2 (read 1 and 2, write 2, keep unknown keys via `extra`). A new synced collection must be added on the server (opt-in) and in **both** clients' collection lists; never assume the other client will defer it.
- **Play record semantics** (achievements spec): the first seat is the owner's; `difficulty` is read by prefix (`standard*`, `expert*`) and anything else is `unknown`, never Standard by default; `mode` is written only when set.
- **`domain/` stays Android-free.** Tests for engines are plain JUnit.
- **Campaign templates are declarative**; the `ScenarioHandler` registry is a last resort, and `TemplateValidator` must stay strict.
- **Room migrations are auto-generated**; always commit the new `app/schemas/<n>.json`. New non-null columns need `@ColumnInfo(defaultValue = …)`.
- **The web repository is the master of shared files**: `achievements.json` (copy byte-identical, then update the pinned SHA-256 and web commit in `AchievementsAssetTest`), synergy rule fixture, draft constants (`DEFAULT_OFFER_SIZE`, stride semantics) — change both sides together.
- **AGP 9 quirks**: do not apply `org.jetbrains.kotlin.android`; use `androidResources.localeFilters`; Robolectric pinned to SDK 36.
- **`dependenciesInfo.includeInApk = false`**: F-Droid rejects APKs with the Play dependency block.
- **Timer state is wall-clock based** on purpose (`elapsedRealtime` resets on reboot).

Considered and rejected: Navigation 3 (ecosystem too thin at the time); FTS5 (Room's FTS4 is first-class and FTS5 is unreliable on older Android); bundling the card seed in the release APK (tried once; stale content and a build dependent on a third party); precomputed achievement state in backups (two sources of truth); hidden achievements and yearly windows (v1 scope); fetching achievement definitions at runtime (breaks offline first).

---

## 8. Known issues and technical debt

- **F-Droid inclusion is not merged** (MR !45283). Every release needs the manual recipe update described in §5. Once merged, `AutoUpdateMode: Version` + `UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` take over (betas excluded by the regex).
- **Stored-data changes were meant to wait for the maintainer's device check** before release (Room 26, backup format 2 on branch `feat/achievements` are in that category). Confirm with the maintainer before tagging a release that migrates the database.
- **Achievements page performance**: first load builds the catalogue (parses all campaign templates, ~4 s on a software-rendered emulator) and composes a 60×75 grid; the grid is drawn on a canvas and catalogue/names are cached per process, but the first open still takes several seconds on the emulator. Real-device timing: **Unknown / requires confirmation**.
- **Campaign scenario keys for achievements** resolve through the run's template and the card database (`CampaignRepository.scenarioSetCode`); a run whose template cannot be read yields `campaign:<id>` cells that count for volume but not for coverage. Same behaviour as the web, by spec.
- **Backup restore merges dismissed packs** with the device's own rather than replacing them (existing behaviour, relied on by tests).
- **`web/data/`** leftover directory (see §1).
- **`CLAUDE.md` toolchain line says AGP 9.3.1 / Gradle 9.6.1**; the catalog and wrapper say AGP 9.4.0 / Gradle 9.7.1. Trust the files.
- **Draft decks created before 1.55.0+achievements carry no `draft` tag**, so games played from them never record `mode: draft`. Accepted.
- **Emulator harness**: `uiautomator dump` is flaky (SystemUI ANRs, concurrent dumps); `tools/drive-device.sh` mitigates by tapping on text.
- **Lint `warningsAsErrors`** means library bumps can turn CI red without a code change; fix the warning, never add a baseline.
- TODO markers in code: none tracked centrally; grep `TODO` before assuming.

---

## 9. Recent development history (August–September 2026)

- **1.54.0** (mid-September): new home page (news, account panel, history/stats tiles), deck visuals, campaign shelf with box art and three tabs, synergy rule ported from the web (`SYNERGY_RULE_VERSION = 2`, `TraitKey` normalisation), security review pass. Lesson: merging a stack of PRs from the GitHub UI closed PRs out of order and tagged the wrong commit; since then stacks are merged locally with `--no-ff` and the tag is retagged after verification.
- **1.54.1**: synergy contract fix, BGG login 400-vs-401 handling.
- **1.54.2**: "Sign in to Thwart" panel on the home page; "Ready to play, <name>" when signed in.
- **1.54.3**: History screen with villain art tiles and play details; Stats moved from the bottom bar to the home page; Fear No Evil box cover asset.
- **1.54.4**: Fear No Evil scenarios playable outside the campaign with a random or chosen subordinate villain (`fne_<scenario>__<villain>` codes; the environment card step is campaign-only). Mirrored on the web.
- **1.55.0** (2026-09-18): draft rework — packs built in advance from the collection, copies owned respected, "max 1 per deck" unique across packs, packs always full and rebuilt from returned cards; reprint folding fix (a title owned only as a reprint is filed under the owned printing, and the deck page counts any owned printing as owned).
- **Unreleased, PR #14 `feat/achievements`** (2026-09-19): achievements ported from the shared spec (derivation + 31 vectors, definitions snapshot pinned by hash, backup format 2 with `isOwner`/`mode`/unknown-key preservation, Room 26, achievements page, home strip, play-hub entry, stats link, result-page unlocks). `app/build/fixtures/backup-android-v2.json` is generated by the tests for the web repository to keep as its round-trip fixture.

CI itself has been stable through this period: the workflows above are the "complex CI/CD" of this repository, and their shape (three PR jobs, a seed-dependent data job, a tag-only signing job with artifact checks) was designed for F-Droid compliance and for keeping the signing key out of reach of pull requests. Recent releases (1.54.0–1.55.0) all went through `release.yml` on the public repository.

---

## 10. Remaining work

Known requirements:

- Release the achievements branch (PR #14) after the maintainer's device check of the Room 26 migration and format 2 backup; suggested version 1.56.0 (minor). Update F-Droid recipes afterwards.
- Hand the generated `backup-android-v2.json` to the web repository as its fixture, and keep `assets/achievements.json` in step whenever the web bumps `definitionsVersion` (update the pinned hash in `AchievementsAssetTest`).
- Keep carrying releases into the F-Droid MR until it merges.
- Google Play listing was being prepared (package name, "Thwart" title without the Marvel trademark, privacy policy page on thwart.app, AAB build, Play App Signing with the existing key). Nothing in the repository yet: the release workflow builds an APK only. **Unknown / requires confirmation** whether Play publication will proceed.

Potential improvements / ideas (not committed to):

- `bundleRelease` job or task for Play (AAB), still signed with the same key.
- Optional runtime override of the achievement definitions from thwart.app on top of the bundled snapshot (explicitly deferred by the spec).
- Achievement families for sealed / daily / shared-table modes once those modes exist (`mode` values reserved).
- Year filter on achievements (timestamps already exist).
- Splitting `:app` into Gradle modules along the existing package boundaries.
- Deleting `web/data/`.

---

## 11. Agent instructions

Rules:

1. Read `CLAUDE.md` first and obey it: maintainer identity on commits, **no AI attribution anywhere**, Conventional Commits in English, bilingual strings written at the same time, no card/book text committed.
2. Work on a `feat/…` or `fix/…` branch off `dev`; open PRs against `dev` on `Hasyame/Thwart-dev`. Do not push to `main` or tag without an explicit release request. Never merge stacked PRs from the GitHub UI.
3. Before any change: run `git status`, check `gradle/libs.versions.toml` for versions (never inline one), and read the relevant `docs/` and `docs/spec/` file.
4. After any change: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` must be green. For UI work, install the debug APK on a device/emulator and exercise the screen; for stored-data work, test a fresh install and an upgrade from the previous schema.
5. Schema changes: bump the Room version, add the `AutoMigration`, commit `app/schemas/<n>.json`, give new non-null columns a SQL default, and tell the maintainer the release touches stored data.
6. Contract changes (backup fields, sync bodies, achievement definitions, synergy/draft rules): change the web side too (or write the prompt for the web session), keep round-trip tests green (`BackupRoundTripTest`, `BackupWebFixtureTest`, `SyncWireFormatTest`, `AchievementVectorsTest`), and never adjust expected vector values to fit an implementation.
7. Assets: adding a pack means `pack_metadata.json` + `PackMetadataAssetTest` count; scenarios come from the card data; `scenario_rules.json` is regenerated, never hand-edited; campaign templates hold mechanics only.
8. Security: never commit keys, keystores, `keystore.properties`, tokens or a real backup file (they hold personal play history). Never put secrets in workflows outside the `release` environment. Keep every action pinned to a SHA. Keep `permissions: contents: read` as the default. Do not weaken the release artifact checks.
9. CI: if "Android Lint" fails after a dependency bump, fix the code; if "Campaign data" fails on schedule, suspect MarvelCDB data changes (a renumbered card) before suspecting the engine; if "F-Droid compliance" fails, read the script's message, it names the offending file or coordinate.
10. Release safety: only the maintainer decides to tag. Verify tag = `versionName`, `versionCode` moved, both changelogs exist, then after the run verify the APK's signer certificate and version, then update the F-Droid recipes.

Files needing extra care: `app/build.gradle.kts` (signing lookup, lint config, custom tasks), `.github/workflows/*`, `tools/ci/*`, `data/db/MarvelChampionsDatabase.kt` and `app/schemas/`, `data/backup/BackupModels.kt`, `data/sync/SyncRecordCodec.kt` and `SyncMerge.kt`, `data/db/entity/PlayEntity.kt`/`PlayHero.kt`/`Extras.kt` (shared record shape), `domain/campaign/engine/*` (event log semantics), `domain/achievements/AchievementDerivation.kt` (vector contract), `app/src/main/assets/achievements.json` and `scenario_rules.json` (generated/copied, not edited), `fdroid/com.hasyame.marvelchampions.yml`.
