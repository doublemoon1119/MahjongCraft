# Minecraft 1.21.1 NeoForge

## Purpose

`minecraft-v1.21.1-neoforge` is the NeoForge 1.21.1 adapter. It compiles the
[Minecraft 1.21.1 mod](../mod/README.md) sources together with its own and packages them as a NeoForge mod
that runs with [Kotlin for Forge](https://github.com/thedarkcolour/KotlinForForge).

Its Gradle project path is `:minecraft_v1.21.1_neoforge`, its code uses the
`com.doublemoon1119.mahjongcraft.platform.neoforge` package, and it belongs to the
`minecraft-v1.21.1-neoforge` target.

## Overview

The server side is implemented: registration, server events, networking and the blocks a player uses, and
development runs load the [debug tools](../neoforge-debug/README.md). The client side is not implemented
yet, so the mod runs only on a dedicated server.

NeoForge runs mods with Mojang names in production as well, so the distributable JAR is the `jar` output;
there is no separate conversion step as on Forge 1.20.1.

## Networking

Each channel is a NeoForge custom payload type whose content is the raw channel bytes. NeoForge accepts
payload types only while it fires its payload registration event, so declared channels are kept and
registered together then; receivers may be registered before or after the event. The channels are optional,
so a client or server without the mod can still connect.

NeoForge refuses to send a payload to a connection that did not negotiate its channel, such as a player
without the mod. The adapter skips such connections, which has the same effect as on the other loaders,
where the other side ignores a payload it does not know.

## Dependencies

The same rules as the [Forge 1.20.1 adapter](../../v1.20.1/forge/README.md#dependencies) apply: Kotlin for
Forge provides the Kotlin libraries at run time, the other bundled libraries are embedded with jar-in-jar
without their Java module descriptors, and development runs load them from the unpacked `devLibraries`
source set. NeoForge ships MixinExtras, so the mod does not embed it.

## Mixins

`mahjongcraft.neoforge.mixins.json` lists the NeoForge-only mixins for the server events NeoForge does not
have: after vanilla starts tracking an entity added to a world, after a chunk records a block entity, and
after a block a player breaks is actually removed.

## Deprecation Warnings

NeoForge, like Forge, marks several vanilla APIs as deprecated. The code shared with Fabric must use the
vanilla APIs, so this module disables the deprecation warnings as the
[Forge 1.20.1 adapter](../../v1.20.1/forge/README.md#deprecation-warnings) does.

## Running

```shell
./gradlew :minecraft_v1.21.1_neoforge:runServer "-PmahjongcraftTarget=minecraft-v1.21.1-neoforge"
```

Dedicated-server runs use `run/server` under this module.
