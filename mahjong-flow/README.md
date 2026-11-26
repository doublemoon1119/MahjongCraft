# Mahjong Flow

## Purpose

`mahjong-flow` groups the application-layer modules that connect domain rules to
clients, servers, transport, and persistence.

## Modules

| Module | Responsibility |
| --- | --- |
| [common](common/README.md) | Shared application state, events, snapshots, timing, and presentation requests. |
| [client](client/README.md) | Client-side state derived from authoritative updates. |
| [server](server/README.md) | Authoritative use cases, orchestration, visibility, AI driving, and lifecycle. |
| [network-dto](network-dto/README.md) | Serializable transport DTOs and domain mappings. |
| [persistence-format](persistence-format/README.md) | Serializable persisted state, codecs, and migrations. |

## Dependency direction

The common module builds on `mahjong-logic`. Client and server consume common
contracts. DTO modules translate between common/domain state and serialization
formats. Platform adapters compose the modules without moving platform code into
them.

The `core` selection includes these modules; it selects a project graph, not a
Kotlin compilation target. The implementation modules currently declare JVM
targets in their KMP builds. Their platform-neutral code is not a claim that
Native, iOS, or every other Kotlin target is already configured or validated.

## Testing

Each implementation module owns focused tests and may reuse fixtures from
[testing-mahjong-flow](../testing/mahjong-flow/README.md).

From the repository root, run `./gradlew build "-PmahjongcraftTarget=core"` to
verify all core modules, or use the leaf-module `check` commands documented
above. `:mahjong-flow` is a grouping project: its qualified `check` task does
not aggregate the child test suites. Source directories such as `common` map to
prefixed Gradle project names such as `:mahjong-flow:mahjong-flow-common`.

See [CONTRIBUTING.md](../CONTRIBUTING.md) for project-wide commands.
