# Minecraft 1.20.1 Mod Example

This library compiles the [shared Minecraft example](../../mod/README.md) against Minecraft 1.20.1, together
with the example's own 1.20.1 adapters and the files each Minecraft version keeps its own copy of. Its Gradle
project path is `:example-minecraft_v1.20.1_mod`.

- The `vanilla` package holds the example's 1.20.1 adapters: `resourceId`, `copyBlockProperties`,
  `hasCustomName` and the base classes for blocks, entities, block entities and tooltip items that the shared
  code extends.
- The test `vanilla` package holds `testPackResources`, which builds a resource pack with this version's
  constructor for the shared tests.
- `ExampleEntityTypes` and `ExampleBlockEntityTypes` create the entity types and the table block entity type
  with the 1.20.1 constructors.
- The blocks' loot tables live here, because data pack files change their format and folder names between
  Minecraft versions; models, textures and translations stay in the [Minecraft common](../../common/README.md)
  module.

The module compiles and tests against vanilla Minecraft only, so using a loader API fails its build. Each
loader's example adapter, the [Fabric 1.20.1 example](../fabric/README.md) and the
[Forge 1.20.1 example](../forge/README.md), compiles these sources and the shared example together with its own
and packages them into one mod; this module's own JAR is never distributed.

The module compiles with MahjongCraft's access transformer. The example always runs with MahjongCraft, which
opens the same vanilla classes and members at run time on every loader.
