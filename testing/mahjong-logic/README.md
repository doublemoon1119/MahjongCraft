# Testing Mahjong Logic

## Purpose

`testing-mahjong-logic` provides shared domain fixtures for tests.

Its Gradle project path is `:testing:testing-mahjong-logic`.

## Responsibilities

- Build deterministic tiles, hands, players, tables, and rule contexts.
- Reduce duplication across logic, flow, AI, and platform tests.

## Boundaries

This module is test infrastructure, not a public runtime dependency.
Its helpers must not become an alternate domain API.

## Dependencies

It depends directly on [mahjong-logic](../../mahjong-logic/README.md).

## Testing

Consumers exercise these fixtures as part of their own test suites.
A focused domain test run from the repository root is:

```shell
./gradlew :mahjong-logic:jvmTest "-PmahjongcraftTarget=core"
```

This does not cover every Flow, AI, or platform consumer.
Verify shared-fixture changes with the [full core build](../README.md),
and include the Minecraft target when its tests use the changed fixtures.
