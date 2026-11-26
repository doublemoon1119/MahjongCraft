# Minecraft 1.21.1 NeoForge

## Purpose

`minecraft-v1.21.1-neoforge` is the NeoForge 1.21.1 adapter. It compiles the
[Minecraft 1.21.1 mod](../mod/README.md) sources together with its own and packages them as a NeoForge mod
that runs with [Kotlin for Forge](https://github.com/thedarkcolour/KotlinForForge).

Its Gradle project path is `:minecraft_v1.21.1_neoforge`, its code uses the
`com.doublemoon1119.mahjongcraft.platform.neoforge` package, and it belongs to the
`minecraft-v1.21.1-neoforge` target.

## Overview

The server and client sides are implemented, including the config screen opened from NeoForge's mod list,
and development runs load the [debug tools](../neoforge-debug/README.md).

NeoForge runs mods with Mojang names in production as well, so the distributable JAR is the `jar` output;
there is no separate conversion step as on Forge 1.20.1.

The shared client startup reads registered items and entities, which NeoForge writes only after the mod is
constructed. The client therefore starts at the first client registration event, as on Forge.

Differences from the Forge 1.20.1 adapter on the client:

- The Mahjong tile item gets its programmatic renderer through NeoForge's client item extensions, so no
  mixin is needed for it.
- The HUD drawn before the chat, such as the compact countdown and the hand analysis, is a GUI layer
  registered below vanilla's chat layer, so this module does not load the shared `GuiMixin`.
- NeoForge already sends commands run by clicking a chat message to its client command handler first.
- Client listeners for entity use run when the use key is pressed, as on Forge and Fabric, because vanilla
  sends a right-click on an entity to the server before NeoForge fires its client entity interaction event.

## Networking

Each channel is a NeoForge custom payload type whose content is the raw channel bytes. NeoForge accepts
payload types only while it fires its payload registration event, so declared channels are kept and
registered together then; receivers may be registered before or after the event. The channels are optional,
so a client or server without the mod can still connect.

NeoForge refuses to send a payload to a connection that did not negotiate its channel, such as a player
without the mod or a server without it. The adapter skips such connections in both directions, which has
the same effect as on the other loaders, where the other side ignores a payload it does not know.

## Dependencies

The same rules as the [Forge 1.20.1 adapter](../../v1.20.1/forge/README.md#dependencies) apply: Kotlin for
Forge provides the Kotlin libraries at run time, the other bundled libraries are embedded with jar-in-jar
without their Java module descriptors, and development runs load them from the unpacked `devLibraries`
source set. NeoForge ships MixinExtras, so the mod does not embed it.

## Mixins

`mahjongcraft.mixins.json` lists the mixins shared with Fabric, which live in the
[Minecraft 1.21.1 mod](../mod/README.md) module. `mahjongcraft.neoforge.mixins.json` lists the NeoForge-only
mixins:

- The server events NeoForge does not have: after vanilla starts tracking an entity added to a world, after a
  chunk records a block entity, and after a block a player breaks is actually removed.
- Model loading puts the models provided by the registered model loadings into vanilla's cache of unbaked
  models before vanilla reads a model file, so tile variants without a model file still load. Their extra
  models are registered as NeoForge standalone models.

NeoForge reads the mixin configurations from `neoforge.mods.toml`.

## Deprecation Warnings

NeoForge, like Forge, marks several vanilla APIs as deprecated. The code shared with Fabric must use the
vanilla APIs, so this module disables the deprecation warnings as the
[Forge 1.20.1 adapter](../../v1.20.1/forge/README.md#deprecation-warnings) does.

## Running

```shell
./gradlew :minecraft_v1.21.1_neoforge:runClient "-PmahjongcraftTarget=minecraft-v1.21.1-neoforge"
./gradlew :minecraft_v1.21.1_neoforge:runServer "-PmahjongcraftTarget=minecraft-v1.21.1-neoforge"
```

Development runs also load the [debug tools module](../neoforge-debug/README.md), which provides the
`/mahjongcraft debug` commands; the packaged mod does not contain it.

Client and dedicated-server runs use `run/client` and `run/server` under this module.
