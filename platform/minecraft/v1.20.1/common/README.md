# Minecraft 1.20.1 Common

## Purpose

`minecraft-v1.20.1-common` contains Minecraft 1.20.1 resources and code shared by loaders
targeting that game version.

Its Gradle project path is `:minecraft_v1.20.1_common`.

## Responsibilities

- Store version-specific data formats, currently including Minecraft 1.20.1 recipe JSON.
- Provide a version boundary for code or resources that cannot remain cross-version compatible.

## Boundaries

Loader APIs do not belong here. Assets that remain compatible across Minecraft versions stay in
[minecraft-common](../../common/README.md).

## Dependencies

The module uses the target-specific Java toolchain and release configured by the Minecraft
version-common convention. It currently has no declared production library dependencies;
its recipe resources are consumed by the Minecraft common tests and Fabric packaging.

## Testing

Version-specific resources are included in the relevant Minecraft resource-verification tests
and the complete target build.

Run the shared resource tests from the repository root:

```shell
./gradlew :minecraft_common:check "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

Checking this resource module alone is not a substitute for those consumer tests or the
[complete target build](../../README.md).
