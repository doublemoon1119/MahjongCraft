# Mahjong Flow Persistence DTO

## Purpose

`mahjong-flow-persistence-dto` defines stable persisted representations of authoritative MahjongCraft state.

## Responsibilities

- Encode games, rooms, tables, reactions, and extension state for storage.
- Restore domain and flow models through explicit codecs.
- Apply registered persistence migrations.

## Boundaries

The module does not select a filesystem or database, schedule saves, or contain platform lifecycle code.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md), [mahjong-flow-common](../common/README.md), Kotlin Serialization, and Koin.

## Testing

Tests cover envelopes, authoritative state round trips, table and room persistence, reactions, migrations, and extension state.
