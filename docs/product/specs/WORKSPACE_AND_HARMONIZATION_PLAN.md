# Workspace and harmonization plan

Status: approved by the owner on 2026-09-19. Repository junctions and platform
instruction consolidation and local application harmonization are implemented.
See specification 001 for verification. This describes the accepted architecture, including the
remote coordination proposal which has not been deployed.

## Recommended local structure

```text
C:/Thwart/                         no .git
  AGENTS.md                       shared product instructions
  specs/                          shared specifications
  thwart-web/                     junction to C:/Thwart Web
  thwart-android/                 junction to C:/MarvelChampionsCompanion
```

Use Windows directory junctions initially. They expose the actual existing
repositories, including their own .git directories, without relocating source,
duplicating checkouts or changing history. This is a logical workspace, not a
monorepo and not a pair of submodules. The current paths remain valid for Android
Studio, Gradle, existing Codex tasks and local cross-repository test defaults.
Do not open competing agents through two aliases of the same checkout and assume
they are isolated. For concurrent tasks, use independent per-repository worktrees.

Before activation, verify C:/Thwart is not inside another Git repository, ensure
the proposed child names are absent, and verify each junction's target and Git
root. Record each child's branch, HEAD and remotes before and after. Never remove
or recursively move a junction target as part of rollback. A later physical move
is optional and would require auditing IDE, local.properties, scripts and external
workspace paths. It is unnecessary for the requested logical workspace.

Launch the product task from C:/Thwart. Explicitly load each child AGENTS.md before
editing it. Opening a child alone does not reliably discover the non-Git parent:
Codex normally discovers instructions from the Git root down to its working
directory. The documentation also gives a default 32 KiB combined instruction
limit, another reason to move historical handover prose out of AGENTS.md.
[Official instruction discovery](https://learn.chatgpt.com/docs/agent-configuration/agents-md).

## Instruction split and persistence

The supplied root AGENTS.md contains only shared product rules and coordination.
The installed child AGENTS.md documents contain platform architecture, commands
and release boundaries. Historical material is retained with explicit status.

Implemented after approval:

1. Extract current architecture/operations material from each long AGENTS.md into
   its own `docs/MAINTAINER_GUIDE.md`. Preserve useful history as explicitly dated
   history, correct verified stale facts, and do not carry obsolete commands as
   standing rules. Do not turn an archive into a second competing instruction source.
2. Install the concise platform instructions. Remove blanket master-client and
   cross-repository prohibitions, as explicitly superseded by this request. Preserve
   independent release gates, owner identity and no-attribution policy.
3. Refactor Android CLAUDE.md too: it currently declares precedence over AGENTS.md.
   Keep a short entry point to AGENTS.md and Android-specific guidance rather than
   leaving contradictory shared rules active. This is required for a real cleanup,
   not just renaming the old duplication.
4. Version shared documentation in an existing repository, proposed location
   `Thwart-Web/docs/product/`, with `AGENTS.md` and `specs/`. This chooses a storage
   home, not Web authority over behavior. No third orchestration repository is needed.
5. Expose canonical specs at C:/Thwart/specs through a junction after moving the
   approved draft documents into that documentation directory. Publish the root
   AGENTS.md from the canonical product file with a small explicit bootstrap/check
   operation. Do not silently overwrite local edits; fail on drift.
6. Keep a pinned product-doc snapshot in Android `docs/product/` for independent
   clones and remote tasks. Record source repository, commit and hashes. This is
   generated distribution, not a second authoring location. A shared-rule change
   updates the canonical document and the Android snapshot in coordinated PRs.
   For an Android-only cloud task, propose shared-rule edits explicitly and refresh
   the canonical document before considering the shared change complete.

This keeps both repositories usable alone while making the root the local entry
point. Local-only parent files would otherwise disappear from GitHub tasks and
lose all version history. Shared documentation replication is justified here;
duplicated independently edited rules are not.

## Repository independence and delivery

| Client | Working repository | Base | Release boundary |
|---|---|---|---|
| Web/PWA and backend | Hasyame/Thwart-Web | main | Main CI advances release; API promotion is separate |
| Android | Hasyame/Thwart-dev | dev | Public Hasyame/Thwart is a release mirror, not the feature PR target |

Use a separate branch and PR in each working repository; `feat/thwart-identity`
is compatible with the existing Android convention. Documentation consolidation
can be its own reviewable commit per repository. Do not create a parent commit,
reuse a Git index across children or assume a parent diff contains both changes.
No push, merge, production update or release is included in current local approval.

## Conflicts to resolve in the instruction cleanup

| Topic | Existing conflict | Resolution / treatment |
|---|---|---|
| Cross-repository edits | Web says never edit Android; Android asks for contract changes on both sides | Current owner request explicitly authorizes coordinated local work |
| Master implementation | Web declares Android master of all contracts; Android delegates achievements/synergy to Web | Scoped sources plus accepted product specifications; never blanket ownership |
| Achievement seats | Android uses owner; latest Web decision uses all seats | Owner's follow-up confirms latest decisions; all seats is accepted |
| Web styling | tokens.css records rejection of direct Android transcription over contrast and visual density | Owner now prefers Android direction; adapt color roles and preserve contrast instead of repeating exact transcription |
| Workflow pauses | Android CLAUDE milestones are historical; Web asks for approval before pushes | Follow current staged request and retain distinct publication gates |
| Merge semantics | Achievement sync prose says updatedAt whole-record merge and no field merge; base sync spec uses server revisions and refinements | Inspect actual server/client rules. Preserve reportedToBgg, timer, ratings and deck-fork handling; do not choose by prose shortcut |
| New collections | Old protocol says no server deployment needed; actual backend has collection registry/opt-in | Current implementation requires server and both clients to be inspected |
| Card data | Web says never re-host data but serves generated JSON; Android forbids seed in release APK | Distinguish platform delivery mechanisms from common prohibition on committing card dumps; do not expand asset permissions |
| Campaign content | CLAUDE's final paragraph says user-supplied only; its campaign section and committed assets bundle mechanics | Document current mechanics-only templates, without licensing new book text |
| Current versions | Android handover says 1.55.0/Room 26 and unreleased achievements | Checkout is 1.56.1/code 90, Room 27. Catalog/build/schema files win over dated notes |

## Remote Codex and one request from another device

Official cloud documentation describes selecting one repository, checking out its
chosen revision, running environment setup and returning a diff/PR. A Windows
junction or local parent AGENTS.md does not transfer to that environment.
[Official cloud environment lifecycle](https://learn.chatgpt.com/docs/environments/cloud-environment).

The documentation reviewed does not establish a native single-task, two-repository,
two-PR workflow. Do not promise it on this account without checking the actual
environment and repository permissions. A setup script can prepare additional
files, but this alone does not establish second-repository push permissions,
persistent review diffs or coordinated PR delivery. Setup-only secrets are removed
before the agent phase; do not work around that by writing long-lived tokens into
the checkout. [Environment variables and secrets](https://learn.chatgpt.com/docs/environments/cloud-environment).

Recommended progression:

1. Use the local root workspace now. Keep shared docs versioned in an existing repo
   and distributed to Android as above. No new orchestration service is necessary.
2. For an entirely hosted workflow, start with one shared feature specification and
   two repository-specific cloud tasks. This is safe and independently reviewable,
   but it does not by itself automate a single request from a phone.
3. If one-request dispatch is essential, add a thin coordinator in an existing
   repository or on an authenticated development host. It accepts one request,
   assigns a feature/spec revision, starts two tasks/checkouts and collects two PR
   links and check results. It must stop for unresolved product choices and preserve
   separate CI and publication gates. A custom multi-repository worker is another
   possible implementation, but requires explicit authentication, durable results
   and separate PR handling to be validated first.

This is an architecture proposal, not a configured integration. Mobile access,
cloud feature availability, private-repository authorization and a coordination
mechanism have not been inspected on the user's account. Do not create a third
repository merely to hold the coordinator; the existing Web repository already
offers a place for lightweight tooling if it later becomes necessary.

## Approval and delivery

The owner approved the logical workspace, instruction plan and specification 001
on 2026-09-19. The workspace is active and both independent feature branches contain
the local implementation. See specification 001 for checks and runtime limits,
and PARITY_REPORT.md for the post-change audit. Specification 003 records the
subsequent explicit approval of all five remaining product choices.
The all-seat achievement rule is resolved; do not ask again.
