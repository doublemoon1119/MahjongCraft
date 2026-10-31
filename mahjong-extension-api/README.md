# Mahjong Extension API

## Purpose

`mahjong-extension-api` is the public registration surface for third-party MahjongCraft extensions.

## Responsibilities

- Expose the `MahjongExtension` entrypoint and its bootstrap.
- Connect rule modules, actions, commands, DTO codecs, AI strategies, AI
  opponent models, and server handlers. Each callback receives a register-only
  Registrar from the module that owns the feature, in that module's
  `api.registration` package; freezing and lookups stay with MahjongCraft.
- Preserve typed boundaries between extension data and authoritative game state.

## Boundaries

The API does not grant arbitrary renderer callbacks or direct mutation of
authoritative state. Platform-specific presentation extensions belong to the
relevant platform API.

Extension registration is separate from game-event subscriptions. Read-only
event types live in [Flow common](../mahjong-flow/common/README.md), and
platform modules provide their subscription/context APIs. The stable
compatibility scope covers the `MahjongExtension` entrypoint and the
registrars it passes, plus the designated event, seating and table surfaces;
the rule types an extension registers are not covered. See
[Public API Compatibility](../docs/contributing/extensions-and-registries.md#public-api-compatibility) for
signature checks and their target and behavioral limits.

## Dependencies

This facade exposes APIs from [mahjong-logic](../mahjong-logic/README.md),
[mahjong-ai](../mahjong-ai/README.md), and the common, server, network DTO, and
persistence format parts of [mahjong-flow](../mahjong-flow/README.md).

## Testing

Core and platform bootstraps follow the same workflow: built-in callbacks,
per-extension ID validation and addition reports, then freezing after that layer succeeds.
The core bootstrap accepts a caller-supplied built-in extension to avoid depending on
the bundled implementation. Failed initialization is not rolled back or retried.

Tests verify the bootstrap lifecycle and registry freezing.

From the repository root, run
`./gradlew :mahjong-extension-api:check "-PmahjongcraftTarget=core"`. This
checks the registration module, not every platform event surface. See
[CONTRIBUTING.md](../CONTRIBUTING.md) for the full verification workflow.
