# Mahjong Flow Persistence Format

## Purpose

`mahjong-flow-persistence-format` defines explicit persisted representations of
authoritative MahjongCraft state and replay data.

## Responsibilities

- Encode games, rooms, tables, reactions, and extension state for storage.
- Encode pending history events and compact replays.
- Restore domain and flow models through explicit codecs.
- Apply registered persistence migrations.

## Boundaries

The module does not select a filesystem or database, schedule saves, or contain platform lifecycle code.

Persistence DTOs describe stored data; they are not database access objects.
Versioned codecs and registered migrations belong here, while backend schemas,
SQL operations, storage paths, and retention jobs belong to the storage adapter.
Follow [Persistence Evolution](../../CONTRIBUTING.md#persistence-evolution)
when changing a format rather than treating every persisted surface as one
version.

## Stored encoding

Every format in this module is stored as JSON, and `persistenceJson` is the only definition of its settings.
The JSON choice stays inside this module and the storage adapter: domain, Flow use cases,
the extension API, and bundled extensions describe stored data only through DTOs and
serializers. Code that works with JSON structure directly, and would change with the
encoding, is limited to:

- The typed payload wrapper for extension DTOs, which stores each payload as a JSON object
  under a stable type key resolved by the persistence registries.
- The compact replay format, its readers, and the bounded replay parser that limits input
  size before parsing. Replays store discard piles and set-aside tiles in a rule-neutral
  form, so rules register nothing for replay.
- Persistence migrations, which rewrite older stored shapes as JSON.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md),
[mahjong-flow-common](../common/README.md), and Kotlin Serialization.

## Dependency injection

The module does not depend on a DI framework. Platform composition assembles the persistence
registry aggregate and codec with their existing defaults; see [Minecraft common](../../platform/minecraft/common/README.md).
Persistence DTOs are not dependency-injected services.

## Testing

Tests cover envelopes, authoritative state round trips, table and room
persistence, reactions, migrations, history state, and extension state.

From the repository root, run
`./gradlew :mahjong-flow:mahjong-flow-persistence-format:check "-PmahjongcraftTarget=core"`.
This does not test a platform's filesystem or database backend. See
[CONTRIBUTING.md](../../CONTRIBUTING.md) for full verification.
