# Testing Mahjong Flow

## Purpose

`testing-mahjong-flow` provides reusable fakes and builders for application-flow tests.

## Responsibilities

- Supply fake event and presentation publishers, repositories, clocks, and state services.
- Construct deterministic flow scenarios for server and platform integration tests.

## Boundaries

This module is test-only and does not define production behavior or authoritative policy.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md), [mahjong-flow-common](../../mahjong-flow/common/README.md), coroutines, and coroutine test utilities.

## Testing

Its fakes are exercised by flow, AI, and Minecraft adapter test suites.
