# Minecraft 1.20.1 Fabric

## Purpose

`minecraft-v1.20.1-fabric` is the runnable Fabric adapter and packaging module for Minecraft 1.20.1.

Its Gradle project path is `:minecraft_v1.20.1_fabric`.

## Responsibilities

- Register Fabric blocks, items, entities, commands, networking, and lifecycle hooks.
- Render tables, tiles, animations, settlements, and showcases.
- Connect authoritative flow services to Minecraft persistence and player interaction.
- Provide history storage and queries, player-facing screens, administrator tools,
  and Fabric context access for game-event listeners.
- Package common modules and resources into the distributable mod JAR.

## Boundaries

Fabric-specific APIs remain in this module. Reusable rules and flow behavior belong in core modules;
cross-loader Minecraft contracts and assets belong in [minecraft-common](../../common/README.md).

## Dependencies

It composes the Minecraft common layers, all required logic and flow modules, the extension API
and bundled extensions, Fabric Loader/API, Fabric Language Kotlin, Loom, Koin, and KToml.
History storage uses Exposed's SQL APIs and SQLite JDBC;
this backend is not a dependency of the platform-neutral Flow modules.

## Development runs

Run from the repository root, selecting the target explicitly:

```shell
./gradlew :minecraft_v1.20.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
./gradlew :minecraft_v1.20.1_fabric:runServer "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

Client and dedicated-server runs use separate directories,
`platform/minecraft/v1.20.1/fabric/run/client` and `run/server`.
Server startup requires the usual Minecraft server setup, including EULA acceptance;
these tasks are interactive development launches, not automated tests.

## Dependency injection

The common graph scans metadata only; server and client adapters keep separate scans.
Application includes used by Koin compiler validation are intentional, even when runtime module
inclusion de-duplicates them. Formal graph tests reject duplicate bindings and verify registry
identity and factory lifetimes. The Minecraft environment provider remains explicit because
its existing implementation package belongs to the server scan, while both graphs need it.

## Testing

Tests cover runtime wiring, commands, rendering calculations, persistence, resources, table lifecycle,
and server services. The target build also remaps and packages the final mod artifact.

For a focused test run from the repository root:

```shell
./gradlew :minecraft_v1.20.1_fabric:test "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

Use the [complete Minecraft target checks](../../README.md) before committing.
The event API signature check covers this module's `api.event` surface;
it does not guarantee compatibility with a different Minecraft version or loader.

## Seat providers

Fabric integrations can register a fixed-seat provider through
`FabricSeatProviderRegistry`. Register an ID in the integration's namespace during extension
initialization, then let the registry freeze before gameplay starts:

```kotlin
registry.register("example:furniture", provider, priority = 0)
```

The provider resolves only the requested table seat. It may recognize a block, an entity, or a
particular slot on a multi-seat object, but it must not scan for a replacement chair or claim a
foreign passenger. The returned `SeatTarget` must expose a stable `SeatIdentity`, finite
geometry, occupancy inspection, and a revalidated `trySit` operation.

Seat providers run on the server thread and own the Minecraft-specific work: checking loaded
chunks and collision space, moving the rider, and creating or removing only resources owned by
the provider. A normal refusal must leave the rider unchanged. If another passenger occupies
the target, return `OCCUPIED`; if space is blocked, return `BLOCKED` or `FAILED` as appropriate.

The registry is a version-specific API. An integration targeting another Minecraft version or
loader must use that platform's corresponding registry and adapter. MahjongCraft does not
maintain compatibility adapters for every furniture mod, and registering a provider does not
grant permission to remove or control entities owned by another mod.

Opening a game currently resolves online players only; world AI representatives are a separate
integration. Built-in stools accept riders already in their world. Cross-world players may use
the checked standing fallback, but are not first teleported into a stool to attempt mounting.
Non-player cross-world transfer remains the responsibility of the entity's lifecycle adapter.

Provider exceptions stop automatic seating for that rider because occupancy could not be
confirmed. Other riders are still processed. Recovery is deliberately limited: MahjongCraft
does not forcibly remove an unknown vehicle or search the world for another safe position.
