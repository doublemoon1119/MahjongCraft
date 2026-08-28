# Minecraft 1.20.1 Common

## Purpose

`minecraft-v1.20.1-common` contains Minecraft 1.20.1 resources and code shared by loaders targeting that game version.

## Responsibilities

- Store version-specific data formats, currently including Minecraft 1.20.1 recipe JSON.
- Provide a version boundary for code or resources that cannot remain cross-version compatible.

## Boundaries

Loader APIs do not belong here. Assets that remain compatible across Minecraft versions stay in [minecraft-common](../../common/README.md).

## Dependencies

The module uses the target-specific Java toolchain and release configured by the Minecraft version-common convention.

## Testing

Version-specific resources are included in the relevant Minecraft resource-verification tests and the complete target build.
