# Forge 1.20.1 Example

This is the Forge build of the independently packaged example mod, with ID `mahjongcraft_example`.
It depends on MahjongCraft and combines the two example libraries into its own JAR.
It does not bundle MahjongCraft, its dependencies or another copy of Kotlin; Kotlin for Forge
provides Kotlin at run time.

What the example registers is listed in the
[shared Minecraft 1.20.1 example code](../mod/README.md); this module only decides how to register
it on Forge. The behavior is the same as the [shared Fabric example](../../fabric/README.md).

## Registration

[`ExampleMod.kt`](src/main/kotlin/com/doublemoon1119/mahjongcraft/example/forge/ExampleMod.kt) is the
`@Mod` entry point. Forge writes content only during its registration events, so
[`ForgeExampleRegistrar.kt`](src/main/kotlin/com/doublemoon1119/mahjongcraft/example/forge/ForgeExampleRegistrar.kt)
keeps one `DeferredRegister` for each registry. Each object is created when Forge reaches its
registry and is handed back to the shared code after it is written.

The entry point also adds the actor attributes in `EntityAttributeCreationEvent` and the
actor-on-cushion interaction in `PlayerInteractEvent.EntityInteract`. On the client,
[`ExampleModClient.kt`](src/main/kotlin/com/doublemoon1119/mahjongcraft/example/forge/ExampleModClient.kt)
registers the furniture, actor and hand-reveal chip renderers in
`EntityRenderersEvent.RegisterRenderers`. A dedicated server does not load it.

## Extension Declaration

Forge has no entrypoints, so the extension classes are declared in
[`mods.toml`](src/main/resources/META-INF/mods.toml) under `modproperties`, using the same names as
the Fabric entrypoints:

```toml
[modproperties.mahjongcraft_example]
"mahjongcraft:extension" = "com.doublemoon1119.mahjongcraft.example.minecraft.mod.ExampleNativeMahjongExtension"
"mahjongcraft:client_extension" = "com.doublemoon1119.mahjongcraft.example.minecraft.mod.ExampleNativeMahjongClientExtension"
```

Each value is one class name or an array of class names. The client extension is read only on the
client. The example requires the same MahjongCraft version and loads after it.

## Development Runs

The client and server runs load MahjongCraft and the example as two mods, each from its own build
output. MahjongCraft is loaded as in its own development run, with its libraries unpacked into the
same mod so that they can see Kotlin; the example libraries are unpacked into the example mod for the
same reason. MahjongCraft's JAR is not on the run classpath, so it is not loaded twice.

This setup exists because the example and MahjongCraft are built in the same repository. A separate
mod depends on a MahjongCraft release instead, and does not need the project dependencies or the
development-run setup in this module's [build script](build.gradle.kts).

## Packaging

The example JAR is converted to Forge's run-time names like the main mod. It merges both example
libraries into the mod itself, without jar-in-jar.
[`ExampleModPackagingTest.kt`](src/test/kotlin/com/doublemoon1119/mahjongcraft/example/forge/ExampleModPackagingTest.kt)
reads the two converted JARs. It checks that the example JAR contains every layer and none of the main
mod or its libraries, that its `mods.toml` declares the extensions and the MahjongCraft dependency,
and that the main JAR contains nothing from the example.

See the [example guide](../../README.md) for commands and development-world isolation.
