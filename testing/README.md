# Testing Modules

## Purpose

`testing` groups reusable fixtures shared by production-module test suites.

## Modules

| Module | Responsibility |
| --- | --- |
| [testing-mahjong-logic](mahjong-logic/README.md) | Domain builders, sample rules, and deterministic logic fixtures. |
| [testing-mahjong-flow](mahjong-flow/README.md) | Fake flow services, publishers, stores, and orchestration fixtures. |

## Dependency direction

Production modules may depend on these modules only from test source sets.
Testing modules can depend on production APIs to construct fixtures,
but production code must not depend on testing artifacts.

The Gradle project paths are `:testing:testing-mahjong-logic` and `:testing:testing-mahjong-flow`,
despite their shorter source directory names.
These are ordinary KMP fixture libraries, not JVM `testFixtures` variants.

## Testing

The fixtures are exercised by the logic, flow, AI, extension, and platform test suites.
Run the core consumers from the repository root:

```shell
./gradlew build "-PmahjongcraftTarget=core"
```

Fixture-library checks alone do not run all consumer tests.
If a fixture affects Minecraft behavior, also run the relevant
[Minecraft target checks](../platform/minecraft/README.md).
See [CONTRIBUTING.md](../CONTRIBUTING.md) for the complete verification workflow.
