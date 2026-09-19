# Thwart Android

Read the shared product instructions and relevant specifications
first: from the product workspace use its AGENTS.md/specs; in a standalone clone
use the pinned `docs/product/AGENTS.md` and `docs/product/specs` snapshot.
Verify snapshot provenance before deciding a cross-client rule. Read any deeper
instructions in the files being changed. CLAUDE.md points here. Read docs/ANDROID_DEVELOPMENT.md for Android-specific
pack/scenario/campaign procedures; its historical roadmap is not a task gate.

## Architecture and toolchain

Single `:app` module, Kotlin/Compose, Hilt ViewModels, StateFlow and repositories.
UI calls data/repository; domain stays Android-free. Preserve five nested top-level
navigation graphs with independent back stacks. Match existing Kotlin formatting.
Use JDK 21 and the wrapper/catalog versions in gradle files, not dated handover
versions. AGP 9 includes Kotlin support; do not apply org.jetbrains.kotlin.android.
Use androidResources.localeFilters. Robolectric stays pinned to its tested SDK;
configuration-cache tasks must resolve Project-dependent inputs at configuration time.

## Data and resources

Room changes need versioned exported schemas, auto-migrations and SQL defaults
for new non-null columns. Test both fresh installation and upgrade when storage
changes. Campaign templates are declarative and mechanics-only; keep validation
strict and justify any bespoke handler. Pack/scenario metadata follows DATA_SOURCES
and campaign-template guides. Do not bundle card seed in a release APK.

Use strings.xml and values-fr/strings.xml together; escape apostrophes for Android
resource compilation and use proper plurals. Keep adaptive/monochrome launcher icons,
TalkBack semantics, large-text support and device-specific storage integrations.
Pin bundled achievement definitions and shared vectors to the agreed Web revision;
use THWART_WEB_DIR when the sibling checkout is in a non-default location.

## Verification

Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` after code
changes. Lint warnings are errors; fix them without a baseline. For UI changes,
install the debug app on a device/emulator and exercise affected flows. A storage
change also needs upgrade/backup verification. Seed-dependent campaign tests are
separate; report skipped prerequisites explicitly. Use the debug application ID
suffix to keep release user data separate.

## Git and release

Work on feat/ or fix/ branches from dev. Feature PRs target dev in Hasyame/Thwart-dev.
Hasyame/Thwart public main is a release mirror. Never merge stacked PRs through
the GitHub UI. Preserve package ID, minSdk, signing key, F-Droid checks and release
artifact verification. Version code rises for every release tag; changelogs are
bilingual. Tags/publication require an explicit release request. Stored-data
releases retain the maintainer device-check gate. Detailed operations belong in
RELEASING.md, fdroid instructions and docs/MAINTAINER_GUIDE.md, not this file.
