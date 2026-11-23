# Minecraft Platform

## Purpose

The Minecraft adapter turns MahjongCraft flow and domain state into a playable mod.

## Layers

| Layer | Responsibility |
| --- | --- |
| [Cross-version common](common/README.md) | Minecraft-facing contracts, assets, configuration, layout, and presentation models shared across supported versions. |
| [Minecraft 1.20.1 mod](v1.20.1/mod/README.md) | Code and resources for Minecraft 1.20.1 shared by its loaders, compiled against vanilla Minecraft. |
| [Minecraft 1.20.1 Fabric](v1.20.1/fabric/README.md) | Fabric runtime integration, rendering, commands, persistence, and final mod packaging. |
| [Minecraft 1.20.1 Forge](v1.20.1/forge/README.md) | Forge runtime integration, rendering, commands, persistence, and final mod packaging. |
| [Cross-version common debug tools](common-debug/README.md) | Development-only debug code and resources that do not call Minecraft, such as debug rules, scenario data and stress test statistics. |
| [Minecraft 1.20.1 mod debug tools](v1.20.1/mod-debug/README.md) | Development-only debug commands, scenarios and stress tests for Minecraft 1.20.1, compiled against vanilla Minecraft. |
| [Minecraft 1.20.1 Fabric debug tools](v1.20.1/fabric-debug/README.md) | Fabric entrypoints for the debug tools, loaded into development runs and never packaged. |
| [Minecraft 1.20.1 Forge debug tools](v1.20.1/forge-debug/README.md) | Forge entry points for the debug tools, loaded into development runs and never packaged. |

## Target

The catalog-backed targets are `minecraft-v1.20.1-fabric` and `minecraft-v1.20.1-forge`.
Cross-version code uses the core Java policy; version and loader modules use the Java policy
declared by the selected target.

Run commands from the repository root; quote the target property, including on PowerShell:

```shell
./gradlew build "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
./gradlew detektAll "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

Each target loads the cross-version common layer, the 1.20.1 mod module, one loader adapter and
their debug tools. It does not validate other Minecraft versions or loaders.

## Boundaries

Version-neutral Minecraft behavior belongs in `common`.
Code that calls a specific Minecraft version belongs in that version's mod module, and loader APIs
belong only in the corresponding loader module. Rules, command validation, and authoritative changes
remain in the core logic and Flow layers.

Minecraft presentation extensions and game-event subscriptions are separate surfaces:
extensions register platform mappings during initialization, while event listeners observe
committed game results. Event and seating API compatibility is limited to the scope in
[Public API Compatibility](../../docs/contributing/extensions-and-registries.md#public-api-compatibility), not by every public class in the Minecraft modules.

See [Build](../../docs/contributing/build.md) for selecting and building a target, and
[Minecraft Build](../../docs/contributing/minecraft-build.md) for build rules specific to these modules.
