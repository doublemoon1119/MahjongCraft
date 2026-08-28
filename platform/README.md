# Platform Adapters

## Purpose

`platform` contains runtime adapters that connect the platform-independent MahjongCraft modules to a host environment.

## Current adapters

| Platform | Documentation |
| --- | --- |
| Minecraft | [Minecraft adapter](minecraft/README.md) |

## Structure

Each platform owns its integration, presentation, lifecycle, configuration, and packaging concerns. Platform code may compose core modules, while core modules must not depend on platform implementations.

Formal build targets and their module paths are declared in [`gradle/platform-targets.toml`](../gradle/platform-targets.toml). Development and verification instructions are in [CONTRIBUTING.md](../CONTRIBUTING.md).
