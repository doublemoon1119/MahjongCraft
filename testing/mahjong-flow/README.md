# Testing Mahjong Flow

## Purpose

`testing-mahjong-flow` provides reusable fakes and builders for application-flow tests.

## Responsibilities

- Supply fake event and presentation publishers, repositories, clocks, and state services.
- Construct deterministic flow scenarios for server and platform integration tests.
- Provide registries filled by the bundled extensions, so tests use the same registrations as the running mod.

## Boundaries

This module is test-only and does not define production behavior or authoritative policy.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md), [mahjong-flow-common](../../mahjong-flow/common/README.md), [mahjong-bundled-extensions](../../mahjong-bundled-extensions/README.md), coroutines, and coroutine test utilities.

## Testing

Its fakes are exercised by flow, AI, and Minecraft adapter test suites.
