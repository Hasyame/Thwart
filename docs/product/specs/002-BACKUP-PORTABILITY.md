# Preserve opaque backup data

Status: implemented locally on 2026-09-19 after the owner's instruction to continue.

## Shared behavior

Unknown JSON fields in a backup envelope or settings block survive import and
export. Known fields keep their validated values and the writer emits format 2.
Opaque fields are retained as data, never interpreted as settings or instructions.

Web merge retains previously imported unknown keys when absent from the incoming
file; incoming values win on a key collision, including null/false/zero. Replace
discards previous metadata. A failed import rolls back metadata and ordinary rows
together. Erasing local data also erases this metadata. Importing no settings in
merge mode preserves existing settings, as before.

## Platform impact

Web adds an IndexedDB v11 `backupMetadata` store, with separate envelope and
settings extras. It is local only, not a new sync collection. Keeping extras out
of the known settings row preserves them when sync updates that row and avoids
adding opaque backup content to the account protocol. Existing schemas are intact.

Correction from follow-up 003: Android already preserved envelope extras, but
not settings extras. The new local metadata column and settings serializer close
that gap without adding unknown settings to account sync.

The initial photo limitation below is superseded by the owner's approval and
implementation of [003](003-PARITY-COMPLETION.md). Web now imports, displays and
re-exports available photo bytes; missing images are explicitly disclosed.

## Verification

- Actual Dexie import/export with fake IndexedDB: v10 to v11 upgrade, existing
  collection/settings retention, bundled Android fixture, nested unknown fields,
  null/false/zero, internal `id` collisions, close/reopen, repeated merge, replace,
  transaction rollback, known settings updated by sync and complete local erasure.
- Web `test:settings`, `test:backup`, `test:sync-integration`, `test:device` pass.
  `npm run check` has zero errors/warnings; production build passes.
- Initial check, superseded by 003: local browser displayed the photo limitation before import confirmation, using a
  synthetic JSON fixture. Preview cancelled; no user data imported or replaced.
- Android code was unchanged in this initial follow-up; its prior full checks remain the
  relevant validation. No account/production API, push or deployment involved.
