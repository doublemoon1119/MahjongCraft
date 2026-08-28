# Mahjong Extension API

## Purpose

`mahjong-extension-api` is the public registration surface for third-party MahjongCraft extensions.

## Responsibilities

- Expose extension bootstrap and registrar contracts.
- Connect rule modules, actions, commands, DTO codecs, AI strategies, and server handlers through controlled registries.
- Preserve typed boundaries between extension data and authoritative game state.

## Boundaries

The API does not grant arbitrary renderer callbacks or direct mutation of authoritative state. Platform-specific presentation extensions belong to the relevant platform API.

## Dependencies

This facade exposes APIs from [mahjong-logic](../mahjong-logic/README.md), [mahjong-ai](../mahjong-ai/README.md), and the common, server, network DTO, and persistence DTO parts of [mahjong-flow](../mahjong-flow/README.md).

## Testing

Tests verify registrar lifecycle and registry freezing. See [CONTRIBUTING.md](../CONTRIBUTING.md) for the full verification workflow.
