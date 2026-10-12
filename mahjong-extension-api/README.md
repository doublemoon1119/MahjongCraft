# Mahjong Extension API

## Purpose

`mahjong-extension-api` is the public registration surface for third-party MahjongCraft extensions.

## Responsibilities

- Expose extension bootstrap and registrar contracts.
- Connect rule modules, actions, commands, DTO codecs, AI strategies, AI
  opponent models, and server handlers through controlled registries.
- Preserve typed boundaries between extension data and authoritative game state.

## Boundaries

The API does not grant arbitrary renderer callbacks or direct mutation of
authoritative state. Platform-specific presentation extensions belong to the
relevant platform API.

Extension registration is separate from game-event subscriptions. Read-only
event types live in [Flow common](../mahjong-flow/common/README.md), and
platform modules provide their subscription/context APIs. The stable
compatibility scope is limited to the designated `api.event` packages; this is
not a compatibility guarantee for every public registry or extension type. See
[Event API Compatibility](../CONTRIBUTING.md#event-api-compatibility) for
signature checks and their target and behavioral limits.

## Dependencies

This facade exposes APIs from [mahjong-logic](../mahjong-logic/README.md),
[mahjong-ai](../mahjong-ai/README.md), and the common, server, network DTO, and
persistence format parts of [mahjong-flow](../mahjong-flow/README.md).

## Testing

Tests verify registrar lifecycle and registry freezing.

From the repository root, run
`./gradlew :mahjong-extension-api:check "-PmahjongcraftTarget=core"`. This
checks the registration module, not every platform event surface. See
[CONTRIBUTING.md](../CONTRIBUTING.md) for the full verification workflow.
