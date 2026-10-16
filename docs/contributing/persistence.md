# Persistence

## Persistence Evolution

Every persisted format is versioned on its own. A single schema version must not be stretched to cover
unrelated formats: the authoritative Room/Game state, each platform storage surface, the configuration files,
and any future format each evolve separately.

Classify a format before changing it, and follow that class's compatibility strategy:

- **Authoritative and not rebuildable**: carries an explicit schema version and evolves through consecutive
  migrations, one per version step. Data from an unknown newer version, or a gap in the migration chain, is
  rejected loudly: the world still loads, the features that depend on the data are disabled for that session,
  every write to the empty state is refused, the original file is left untouched, and administrators are told
  which file failed, the likely cause and how to recover. Never treat state you cannot understand as
  empty state.
- **Owned by the player and must survive**: keep field names stable and keep reading the previous shape until
  a migration exists. Add a version field at the first change that alters the meaning of an existing field
  rather than in advance.
- **Rebuildable from authoritative state**: unreadable data is skipped and logged, never fatal. State that
  self heals must say in its code how it heals, so the choice to discard can be reviewed.
- **Transient presentation**: no long term migrations. Unknown or stale content is discarded safely and must
  never prevent the platform from starting up.

Before the first release, every format keeps its initial version: a structural change neither raises it nor
adds a migration. Development data that no longer matches the current format is still handled by its class's
strategy above, whether that is rejecting it, skipping it or rebuilding it. The versioning and migration rules
take effect from the first release onward.

Rules that apply to all of them:

- **Stored JSON never uses the network format.** Formats owned by the persistence format module encode and
  decode with its `persistenceJson`; platform-owned stored JSON, such as entity data, keeps its own fixed
  settings beside its codec. Network messages use `NetworkJson`, the only JSON format in the dependency
  container, and its settings must not shape stored data. Changing the settings of a stored format changes the
  data it writes.
- **Enums are persisted by name.** An ordinal silently changes meaning when the enum is reordered, which turns
  stored data into wrong data instead of missing data. Add a legacy reader only when compatibility with a
  released format is required.
- **Raise a version only for a real incompatibility**: a renamed, split, retyped, or re-meaning field. Adding
  a field that older defaults already cover is not a reason to raise it. Configuration files stay version free
  while field names and defaults keep them compatible.
- **Migrations stay consecutive.** Each migration converts exactly one version to the next; do not collapse
  several steps into one function, and do not skip versions.
- **Document the recovery path** where the format is loaded: what is rejected, what is skipped, and what
  restores the skipped data.

Platform storage details belong with the platform code that owns them, not here. Each platform decides how a
class is stored; this policy only fixes which class a format belongs to and how it may evolve.
