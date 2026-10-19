# Minecraft Extension Example

The example is opt-in and is not included in normal builds or normal MahjongCraft runs.
It uses the same compiler, Java and Minecraft version policies as the main project.

## Structure

- [Platform-independent extension](../../../example/mahjong-extension/README.md): common Kotlin code.
- [Minecraft common](common/README.md): shared presentation and resources.
- [Fabric 1.20.1](v1.20.1/fabric/README.md): the example mod and native entrypoints.

The common extension registers the pairing rule, custom tile and Tsumogiri strategy.
Platform names, controls and assets are not connected yet, so this is not a playable
in-game example at this stage. Furniture and event chat output are also pending.

## Build and Run

Run these commands from the repository root. The flag accepts only `true` or `false`.
Omitting it is equivalent to `false`; it is not saved by `switchTarget`.

```shell
./gradlew build -PmahjongcraftTarget=minecraft-v1.20.1-fabric -PmahjongcraftExample=true
./gradlew :minecraft_example_v1.20.1_fabric:runClient -PmahjongcraftTarget=minecraft-v1.20.1-fabric -PmahjongcraftExample=true
./gradlew :minecraft_example_v1.20.1_fabric:runServer -PmahjongcraftTarget=minecraft-v1.20.1-fabric -PmahjongcraftExample=true
```

Both example runs load MahjongCraft and the example as separate mods.
Their worlds, configuration and logs stay under this example loader's `run/client` and `run/server`.
They never reuse the main loader's development worlds.
The main loader's own run tasks do not gain the example when the flag is enabled.

Use disposable example worlds. Do not use worlds containing example data without the matching mod.
Later integration tests will verify missing-extension handling; this skeleton does not establish that coverage.

## Packaging

The example JAR contains its loader entrypoints, both example libraries and its own resources.
The main mod remains a separate required JAR. Neither mod includes the other.
The example uses its own metadata and archive name; the icon currently reuses the project logo
under the example's own resource namespace.

No example module starts Koin. Extension registration goes through MahjongCraft's public entrypoint.
Successful compilation alone is not evidence that a rule or another extension capability works.
