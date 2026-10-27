# MahjongCraft examples

This directory contains small extensions that show how to use MahjongCraft's
public extension points. Examples are optional: normal builds and the main mod
do not load them.

Start with the [platform-independent extension](mahjong-extension/README.md),
then read the [Minecraft adapter guide](platform/minecraft/README.md) when you
want to run the example in a game.

## What the examples cover

| Capability | Example | Evidence | Status |
| --- | --- | --- | --- |
| Rule, tile wall and tile interpretation | Pairing Mahjong and inverted 1-man | [pairing tests](mahjong-extension/src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/pairing) | Automated |
| Multi-choice room setting | Selected ranks build the actual wall | [submission and save test](platform/minecraft/common/src/jvmTest/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/ExamplePairingMultiChoiceSubmissionTest.kt) | Automated checks; game check pending |
| Multi-roll opening | Temporary dealer, final dealer and wall opening | [opening tests](mahjong-extension/src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/pairing/ExamplePairingRoundOpeningProcedureTest.kt) | Automated |
| Optional rule decision | Two-tile exchange | [rule decision tests](mahjong-extension/src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/decision) | Automated and game-tested |
| Extension action and command | Once-per-round public declaration | [action tests](mahjong-extension/src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/action) | Automated |
| AI strategy | Tsumogiri strategy | [AI test](mahjong-extension/src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/ai/ExampleTsumogiriAiStrategyTest.kt) | Automated |
| Network and persistence registration | Example-owned DTOs and mappings | [round-trip test](mahjong-extension/src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/ExampleFormatRoundTripTest.kt) | Automated |
| Minecraft presentation | Names, settings, history text and tile assets | [common adapter](platform/minecraft/common/README.md) | Automated and game-tested |
| Public table props | Saved bonus tiles and custom declaration tokens | [prop checks](platform/minecraft/README.md#bonus-tiles-and-table-props) | Automated; game check pending |
| Native seating | Block chair and persistent cushion | [Fabric adapter](platform/minecraft/v1.20.1/fabric/README.md) | Game-tested; ABI baseline established |
| Public game events | Administrator chat observer | [Fabric adapter](platform/minecraft/v1.20.1/fabric/README.md) | Game-tested |
| Missing-extension recovery | Keep authoritative data and restore it after reinstall | [recovery procedure](platform/minecraft/README.md#safe-recovery-check) | Manual check pending |

Minecraft game checks in this table currently refer to the Fabric 1.20.1
adapter. They do not establish coverage for another version or loader.
The table is deliberately limited to capabilities this example actually uses.
A successful compile is not evidence that every extension point works.

The pairing example also demonstrates rule-owned public table props. At the start of each
round it selects one fewer bonus kind than the number of kinds in the configured wall. The
selection is saved in the round's dynamic rule state, so the centre display and self-draw
settlement use the same values. Repeated copies of one kind count once; the inverted 1-man
is treated as the 1-man kind for this example. Each opponent pays the configured base plus that
number of matching kinds. Declared players also add custom gold tokens to the same central layout.

## Compatibility boundaries

The event and seating APIs have explicit binary-compatibility baselines. Rule, strategy, appearance
and other example APIs currently have no new binary-compatibility promise.

The examples do not claim to implement the following future work:

- third-party table implementations or AI world characters;
- extra claim, flower and other flow capabilities not needed by this example;
- new progress, sound, opponent-model or special-continuation integrations.

These limits describe the example, not a restriction on the public API.

## Build commands

Build from the repository root with the example switch and the desired target.
For example, the currently available Fabric 1.20.1 target uses:

```shell
./gradlew build "-PmahjongcraftTarget=minecraft-v1.20.1-fabric" -PmahjongcraftExample=true
```

The [Minecraft guide](platform/minecraft/README.md) contains isolated client
and server run commands, packaging checks and the safe missing-mod procedure.
