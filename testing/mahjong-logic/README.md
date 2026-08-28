# Testing Mahjong Logic

## Purpose

`testing-mahjong-logic` provides shared domain fixtures for tests.

## Responsibilities

- Build deterministic tiles, hands, players, tables, and rule contexts.
- Reduce duplication across logic, flow, AI, and platform tests.

## Boundaries

This module is test infrastructure, not a public runtime dependency. Its helpers must not become an alternate domain API.

## Dependencies

It depends directly on [mahjong-logic](../../mahjong-logic/README.md).

## Testing

Consumers exercise these fixtures as part of their own test suites. Changes should be verified with the full core build.
