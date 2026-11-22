# Gradle Configuration

This folder holds the version catalogs, the platform targets and the Gradle wrapper.

- `libs.versions.toml`: the `libs` catalog, with the versions shared by every platform version, such as
  Kotlin, the core libraries and build tooling.
- `platform/<platform>/<version>/`: one folder per platform version.
  - `targets.toml` declares the platform targets of that version and the library versions their runtime
    provides.
  - `versions.toml`, when present, is that version's own catalog of dependencies and runtime requirements.
- `wrapper/`: the Gradle wrapper.

Naming and grouping rules for catalog entries, and how targets are selected, are in
[Build](../docs/contributing/build.md). Each platform's build guide describes how its version catalogs are
named and used.
