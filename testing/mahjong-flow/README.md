# Testing Mahjong Flow

## Purpose

`testing-mahjong-flow` provides reusable fakes and builders for application-flow tests.

Its Gradle project path is `:testing:testing-mahjong-flow`.

## Responsibilities

- Supply fake event and presentation publishers, repositories, clocks, and state services.
- Construct deterministic flow scenarios for server and platform integration tests.
- Provide registries filled by the bundled extensions, so tests use the same registrations as the running mod.

## Boundaries

This module is test-only and does not define production behavior or authoritative policy.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md),
[mahjong-flow-common](../../mahjong-flow/common/README.md),
[mahjong-bundled-extensions](../../mahjong-bundled-extensions/README.md),
coroutines, and coroutine test utilities.

## Testing

Its fakes are exercised by Flow, AI, and Minecraft adapter test suites.
A focused Flow server test run from the repository root is:

```shell
./gradlew :mahjong-flow:mahjong-flow-server:jvmTest "-PmahjongcraftTarget=core"
```

Run the [full core build](../README.md) for shared-fixture changes,
and include the Minecraft target when platform consumers are affected.
Fixture-library compilation alone does not verify the behavior of its consumers.
