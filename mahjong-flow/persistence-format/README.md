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

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md),
[mahjong-flow-common](../common/README.md), Kotlin Serialization, and Koin.

## Testing

Tests cover envelopes, authoritative state round trips, table and room
persistence, reactions, migrations, history state, and extension state.

From the repository root, run
`./gradlew :mahjong-flow:mahjong-flow-persistence-format:check "-PmahjongcraftTarget=core"`.
This does not test a platform's filesystem or database backend. See
[CONTRIBUTING.md](../../CONTRIBUTING.md) for full verification.
