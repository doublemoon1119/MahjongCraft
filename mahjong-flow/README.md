# Mahjong Flow

## Purpose

`mahjong-flow` groups the application-layer modules that connect domain rules to clients, servers, transport, and persistence.

## Modules

| Module | Responsibility |
| --- | --- |
| [common](common/README.md) | Shared application state, events, snapshots, timing, and presentation requests. |
| [client](client/README.md) | Client-side state derived from authoritative updates. |
| [server](server/README.md) | Authoritative use cases, orchestration, visibility, AI driving, and lifecycle. |
| [network-dto](network-dto/README.md) | Serializable transport DTOs and domain mappings. |
| [persistence-dto](persistence-dto/README.md) | Serializable persisted state, codecs, and migrations. |

## Dependency direction

The common module builds on `mahjong-logic`. Client and server consume common contracts. DTO modules translate between common/domain state and serialization formats. Platform adapters compose the modules without moving platform code into them.

## Testing

Each implementation module owns focused tests and may reuse fixtures from [testing-mahjong-flow](../testing/mahjong-flow/README.md). See [CONTRIBUTING.md](../CONTRIBUTING.md) for project-wide commands.
