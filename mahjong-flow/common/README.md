# Mahjong Flow Common

## Purpose

`mahjong-flow-common` defines application state and contracts shared by clients and servers.

## Responsibilities

- Model games, rooms, timers, decisions, rule decision, and pending transitions.
- Track per-game seat control, including AI-controlled seats and forced automatic play.
- Define event, snapshot, synchronization, and presentation request contracts.
- Define history facts and observer snapshots shared by recording, replay, and spectating.
- Project committed game facts into read-only match-start, round-settlement,
  and match-end events; platform adapters own listener registration and
  delivery.
- Provide dependency-injection modules for shared services.

## Boundaries

This module does not execute authoritative commands, persist files, send packets, or render platform UI.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md) and coroutines.

## Testing

Tests cover shared models, timers, snapshots, ranking calculations, rule decision
state, event projection, and win celebration cue resolution. Shared fixtures come from
[testing-mahjong-flow](../../testing/mahjong-flow/README.md).

From the repository root, run
`./gradlew :mahjong-flow:mahjong-flow-common:check "-PmahjongcraftTarget=core"`.
Its declared JVM event API signatures are checked as part of `check`; platform
subscription signatures require the corresponding platform target. See
[CONTRIBUTING.md](../../CONTRIBUTING.md) for verification policy.
