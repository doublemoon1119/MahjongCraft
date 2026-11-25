# Fabric 1.21.1 Example

This module builds the example mod for Fabric on Minecraft 1.21.1. Its Gradle project path is
`:example-minecraft_v1.21.1_fabric`. It compiles the [shared Fabric example](../../fabric/README.md), which
describes the entry points, registration and behavior, together with the
[Minecraft 1.21.1 example](../mod/README.md) and its own files, and packages them into one example mod JAR.

This module keeps only what differs per Minecraft version:

- `fabric.mod.json`, whose dependency ranges come from the 1.21.1 version catalog.
- [`ExampleFabricPackaging.kt`](src/test/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/ExampleFabricPackaging.kt):
  what the shared packaging test expects in this version's JARs, such as the `loot_table` folder of the block
  loot tables and the `~1.21.1` Minecraft requirement.
- The [build script](build.gradle.kts), which depends on MahjongCraft's 1.21.1 projects in this repository and
  loads them as a separate mod in the development runs, together with the debug tools for automated tests.
  A separate mod depends on a MahjongCraft release instead and does not need that setup.

Run from the repository root with the example enabled:

```shell
./gradlew :example-minecraft_v1.21.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.21.1-fabric" "-PmahjongcraftExample=true"
```

See the [example guide](../../README.md) for the other commands and development-world isolation.
