# Shared Thwart specifications

Status: workspace and harmonization implemented locally on 2026-09-19; verification
and its limitations are recorded in the feature specification.
The local workspace is active. Each application has its own feature branch.

- [Workspace and harmonization plan](WORKSPACE_AND_HARMONIZATION_PLAN.md): accepted
  architecture, remote-work limitations and rationale.
- [Parity report](PARITY_REPORT.md): post-change findings, product decisions and
  contract defects, with explicit runtime coverage limits.
- [Backup portability](002-BACKUP-PORTABILITY.md): preserve unknown fields and
  retain local opaque metadata.
- [Approved capability completion](003-PARITY-COMPLETION.md): starred Android plays,
  Web photos and owner filtering, new-install theme and retained navigation.
- [Limited formats and game preparation](004-LIMITED-MODES-AND-GAME-SETUP.md):
  temporary collections, unplayed pairings, achievement setup, sealed boosters
  and the hand-off from saved limited decks into games.
- [Implementation inventory](IMPLEMENTATION_INVENTORY.md): code-backed baseline,
  instruction conflicts and the scope of the later parity audit.
- [Visual identity and achievements](001-IDENTITY-AND-ACHIEVEMENTS.md): first feature
  specification; accepted direction is separated from pending decisions.
- [Feature template](FEATURE_TEMPLATE.md): copy only for work that needs shared
  behavior or a lasting decision.
- Platform instructions live in each repository's AGENTS.md. Historical operational
  notes are retained in each repository's docs/MAINTAINER_GUIDE.md.

Keep each specification small. Give it a status (proposed, accepted, implemented),
record owner decisions with date and rationale, and distinguish accepted behavior
from implementation coverage. Link to detailed domain specifications and fixtures
rather than copying large technical documents. Never infer acceptance from silence.

At completion record separate Web/Android commit or PR references and test results.
If a future request changes an accepted decision, record the replacement explicitly.
An accepted product rule does not mean both clients already implement it.

The broader source-backed parity audit follows approved harmonization. The
inventory remains a historical baseline, not a screen-by-screen runtime audit.
The canonical authoring location is Thwart-Web/docs/product. Android carries a
content-pinned snapshot in docs/product; it is a distribution copy, not a separate
authoring location. Shared documentation is included in both repositories' changes.

From the Web repository, distribute after a reviewed edit:
`node tools/sync-product.mjs --android <android-checkout> --workspace <local-root> --write`.
Omit `--write` to check for drift. The script refuses to overwrite independently
edited destination files. The optional workspace argument updates its AGENTS.md;
the specs junction already points at the canonical directory. Standalone clones
read their local docs/product files without requiring the sibling repository.

Check definitions, vectors and logo geometry with
`node tools/check-client-assets.mjs <android-checkout>` from the Web repository.
Snapshot SHA-256 hashes identify exact contents; sourceBaseCommit records the source
checkout base and does not falsely claim that uncommitted documents are in that commit.
