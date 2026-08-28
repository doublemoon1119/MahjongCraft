# Testing Modules

## Purpose

`testing` groups reusable fixtures shared by production-module test suites.

## Modules

| Module | Responsibility |
| --- | --- |
| [testing-mahjong-logic](mahjong-logic/README.md) | Domain builders, sample rules, and deterministic logic fixtures. |
| [testing-mahjong-flow](mahjong-flow/README.md) | Fake flow services, publishers, stores, and orchestration fixtures. |

## Dependency direction

Production modules may depend on these modules only from test source sets. Testing modules can depend on production APIs to construct fixtures, but production code must not depend on testing artifacts.

## Testing

The fixtures are exercised by the logic, flow, AI, extension, and platform test suites. See [CONTRIBUTING.md](../CONTRIBUTING.md) for verification commands.
