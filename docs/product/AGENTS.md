# Thwart product workspace

This workspace coordinates two independent applications. Read `specs/README.md`
first. The owner approved workspace activation and harmonization on 2026-09-19;
see `specs/WORKSPACE_AND_HARMONIZATION_PLAN.md`. Local workspace `C:/Thwart` exposes
`thwart-web` and `thwart-android` through junctions to `C:/Thwart Web` and
`C:/MarvelChampionsCompanion`. Each remains an independent Git repository.

## Product agreements

- Thwart is an unofficial, bilingual English/French, offline-first companion
  for Marvel Champions. Account sync and BGG integration are optional. Preserve
  local use without an account, data portability, and existing capabilities.
- Android is the initial reference for colors and visual direction. Web is the
  initial reference for logos, branding assets and achievement configuration.
  These are scoped preferences, not blanket authority over product behavior.
- Preserve platform-appropriate interaction, accessibility and readable contrast.
  Do not remove capabilities to obtain superficial parity.
- For a meaningful undocumented product difference, present both behaviors,
  their implications and the decision needed. Pause only the affected work until
  the owner decides. Record the answer in the relevant shared specification.
  Proposed or pending decisions are not accepted requirements.
- The owner confirmed on 2026-09-19 that the most recent documented product
  decisions are authoritative. Establish the decision's date and scope; a newer
  code edit or stale comment is not automatically a new product decision. Ask
  only when no clear latest decision resolves a meaningful difference.
- Code, new documentation and commits are in English; UI text is provided in
  English and French together. Use the existing domain vocabulary. No em dashes
  in user-facing prose. Use Conventional Commits with the verified maintainer
  identity `Hasyame <benoit.breul@gmail.com>` (check Git configuration before a
  commit), and no attribution trailers or generated-content notices.
- No tracking or analytics. Do not commit credentials, personal backups, card
  text dumps, card images or campaign book prose. Preserve existing documented
  asset exceptions; they do not authorize additional copied artwork.

## Shared behavior and contracts

- User decisions and accepted specs define behavior. Neither client is master
  of everything. A file's storage repository does not give it product authority.
- Stable card/pack/scenario identifiers are data keys; translated labels are
  presentation. Hero/identity, aspect/affinity, modular set, campaign run and
  play record must retain their domain meanings across platforms.
- Campaign state is folded from an append-only event log. Achievements are
  derived from history, catalogue and definitions, never stored unlocks.
- Preserve backup and sync compatibility, including unknown-field round trips,
  defaults, null/absent distinctions, tombstones, revisions and the existing
  collection-specific merge rules. Do not reduce those rules to generic LWW.
- Achievement definitions are maintained in Web and bundled on Android.
  Compare definitions, derivation vectors, normalization and presentation;
  identical JSON definitions alone do not establish parity.
- All seats count for achievement hero/aspect coverage and heroes played, as
  decided on 2026-09-19 and reconfirmed by the owner. Preserve isOwner and the
  owner/any-seat cell tallies for reading and wire compatibility. See
  specification 001 for implementation and verification status.
- Difficulty uses the shared unknown/standard/expert scale; unrecognized values
  are unknown. Draft mode is distinct from ordinary play. Synergy warnings must
  not become deck-legality failures.
- New sync collections require inspection of the Web backend and both clients,
  including opt-in routing and export/import. Never assume an older client will
  safely defer an unknown collection.

## Cross-platform workflow

1. Read this file, the relevant accepted specs, each affected repository's
   AGENTS.md, Android CLAUDE.md and any deeper instructions. Explicitly read child
   instructions: root launch does not automatically load every descendant file.
2. Record status, branch, commit and remotes independently for both repositories.
   Verify the actual code and tests before relying on dated handover notes.
3. Identify common behavior and the impact on each client and backend. A platform
   may legitimately need no change; explain why rather than changing it for symmetry.
4. Resolve product choices before dependent implementation. Preserve distinct
   implementation architecture, build systems and release procedures.
5. Implement in separate feature branches. Keep shared fixtures and specifications
   synchronized at recorded revisions. Never change expected values just to make
   a failing implementation pass.
6. Run relevant tests, lint/checks and builds independently; UI work also needs
   platform-specific visual and accessibility checks. Report skips and blockers.
7. Report Web, Android and backend outcomes, checks, remaining decisions and
   separate PR links. No push, merge, tag or deployment is implied by local work.

## Git boundaries

The parent is not a Git repository. Never initialize it as a monorepo, merge
histories, remove a child's `.git`, or commit nested repositories as gitlinks.
Use explicit per-repository working directories and stage named files. Preserve
unrelated work. Keep every application independently cloneable and deployable.
Do not create a separate orchestration repository without an established need.

For a standalone clone, shared instructions must be explicitly supplied or
available as a pinned repository-local snapshot. Do not assume this parent exists
in a cloud task. Missing shared context blocks shared product decisions, not
unrelated platform-local work.
