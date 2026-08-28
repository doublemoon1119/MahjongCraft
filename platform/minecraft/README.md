# Minecraft Platform

## Purpose

The Minecraft adapter turns MahjongCraft flow and domain state into a playable mod.

## Layers

| Layer | Responsibility |
| --- | --- |
| [Cross-version common](common/README.md) | Minecraft-facing contracts, assets, configuration, layout, and presentation models shared across supported versions. |
| [Minecraft 1.20.1 common](v1.20.1/common/README.md) | Version-specific resources shared by loaders for Minecraft 1.20.1. |
| [Minecraft 1.20.1 Fabric](v1.20.1/fabric/README.md) | Fabric runtime integration, rendering, commands, persistence, and final mod packaging. |

## Target

The current catalog-backed target is `minecraft-v1.20.1-fabric`. Cross-version code uses the core Java policy; version and loader modules use the Java policy declared by that target.

## Boundaries

Version-neutral Minecraft behavior belongs in `common`. Version-dependent formats belong in a version-common module, and loader APIs belong only in the corresponding loader module.

See [CONTRIBUTING.md](../../CONTRIBUTING.md) for selecting and building a target.
