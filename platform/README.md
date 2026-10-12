# Platform Adapters

## Purpose

`platform` contains runtime adapters that connect the platform-independent MahjongCraft modules
to a host environment.

## Current adapters

| Platform | Documentation |
| --- | --- |
| Minecraft | [Minecraft adapter](minecraft/README.md) |

## Structure

Each platform owns its integration, presentation, lifecycle, configuration, and packaging concerns.
Platform code may compose core modules, while core modules must not depend on platform implementations.

Adapters submit authoritative changes through Flow use cases and coordinators.
They may map immutable domain data to platform presentation, but do not own rule decisions
or bypass authoritative validation.

Formal build targets and their module paths are declared in
[`gradle/platform-targets.toml`](../gradle/platform-targets.toml).
Development and verification instructions are in [CONTRIBUTING.md](../CONTRIBUTING.md).

Platform modules are not loaded by the `core` target.
Select a catalog-backed platform target when building or testing its adapters;
creating a directory does not register a new target.
