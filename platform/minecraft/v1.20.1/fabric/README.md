# Minecraft 1.20.1 Fabric

## Purpose

`minecraft-v1.20.1-fabric` is the runnable Fabric adapter and packaging module for Minecraft 1.20.1.

## Responsibilities

- Register Fabric blocks, items, entities, commands, networking, and lifecycle hooks.
- Render tables, tiles, animations, settlements, and showcases.
- Connect authoritative flow services to Minecraft persistence and player interaction.
- Package common modules and resources into the distributable mod JAR.

## Boundaries

Fabric-specific APIs remain in this module. Reusable rules and flow behavior belong in core modules; cross-loader Minecraft contracts and assets belong in [minecraft-common](../../common/README.md).

## Dependencies

It composes the Minecraft common layers, all required logic and flow modules, the extension API, Fabric Loader/API, Fabric Language Kotlin, Loom, Koin, and KToml.

## Testing

Tests cover runtime wiring, commands, rendering calculations, persistence, resources, table lifecycle, and server services. The target build also remaps and packages the final mod artifact.
