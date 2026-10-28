# Platform-independent example extension

This Kotlin Multiplatform library shows how an extension can use MahjongCraft's
public callbacks without importing Minecraft, a loader, or dependency injection.
The example currently verifies its JVM target; other runtimes are not claimed.

`ExampleMahjongExtension` is the entry point used by the platform adapter. It
registers the rule, data codecs, action, AI strategy and rule decision
resolver. It does not copy or delegate to the bundled Riichi rule.

## Pairing Mahjong

This is a deliberately small demonstration rule, not a regional Mahjong rule.

- Two to four players start with 100 points and three tiles.
- The room chooses which ranks of Characters 1–8 go into the wall, four copies
  of each selected rank. All eight ranks are selected by default. Selecting
  Character 1 replaces one copy with the inverted Character 1; pairing and
  sorting still treat it as Character 1.
- Choose at least two ranks for two players, three for three players, or four
  for four players. There must be enough tiles to deal three to each player
  and leave at least one draw. Reducing the selection also reduces the room's
  player limit; the server rejects a change below its current player count.
- Each turn draws one tile and either wins with two pairs or discards.
  Four identical tiles count as two pairs. Calls, Ron and Kans are not used.
- The two-player wall uses two opposite faces. Three- and four-player games use
  four faces. The rule owns this layout; it is not a room option.
- The first opening rolls twice: the first roll chooses a temporary dealer, and
  that player's roll chooses the initial dealer and wall opening. Later rounds
  use the normal one-roll opening.
- A self-draw normally makes each other player pay 10 points. Exhausting the
  wall changes no scores; a three-tile hand containing a pair is ready.
  The dealer advances after each round. The default match has four rounds.
- At the start of each round, the rule randomly selects one fewer bonus rank
  than the number of ranks in the wall. The selected ranks are saved in the
  round state. Each opponent pays one extra point per selected rank represented
  in the winning hand; repeated copies of one rank count once. The Minecraft adapter displays
  the same saved ranks in the centre of the table.
- A player may reveal their hand once after drawing. It changes a public
  marker and does not change scoring or concealed information.

The rule's settings can change the wall's ranks, payment, round count and the
optional exchange, with payment from 1–100 points and match length from 1–16 rounds.
The selected ranks change wall contents, not which otherwise complete hands may win.
The example does not replace server
validation with client controls.

## Optional two-tile exchange

Exchange is disabled by default. Enabling it adds a rule decision step at the
start of each round and its own rule-decision time setting. The resolver opens
no step at any other timing. When exchange is disabled, that setting is not
part of the rule's configuration.

Each player chooses two of the three starting tiles. After every player submits,
the choices pass together to the next player in the fixed roster order. Two
players therefore exchange with each other. Choices stay private until the
exchange is resolved, and the resolver supplies a legal automatic submission
when a player times out.

## Tsumogiri strategy

Tsumogiri means discarding the tile just drawn. The sample strategy is
intentionally simple. It chooses a legal self-draw
first, then a required discard, and otherwise discards the drawn tile. If there
is no drawn tile, it chooses the first standing tile. Reaction decisions use
the legal actions supplied by the decision context, and rule decisions use the
public default submission. It is an integration example, not a competitive AI.

## Hand disclosure and pose

The example saves one `handRevealed` flag per player. Its rule module implements
`describeHandTiles(tableState)` and describes every tile currently held by that player as public
and face up. It reads the current hand each time, so later draws follow the same rule.
No second list of permanently revealed tile IDs is stored.

A description identifies one physical tile, not a tile face. Visibility and pose are independent:
public tiles may remain upright, and only public tiles may be placed face up. This example uses
the full-hand case. The query describes standing and drawn hand tiles only, not tiles in other areas.

Flow and platform consumers use the same `resolveHandTileDescriptions` validator. Duplicate tile
IDs, tiles outside current hands and nonpublic face-up descriptions are rejected.
The default empty description preserves normal play. Player state is reset for the next round.
See [`ExamplePairingRuleModule.kt`](src/commonMain/kotlin/com/doublemoon1119/mahjongcraft/example/pairing/ExamplePairingRuleModule.kt)
for the query and
[`HandTileDescription.kt`](../../mahjong-logic/src/commonMain/kotlin/com/doublemoon1119/mahjongcraft/logic/module/HandTileDescription.kt)
for the data contract.

## Public capability map

| Capability | Example use |
| --- | --- |
| Rule and wall layout | Pairing rule and fixed two/four-wall layouts |
| Rule decision | Optional simultaneous two-tile exchange |
| Extension action and command | Once-per-round hand reveal |
| Per-tile hand description | Current and future held tiles become public and face up |
| AI strategy | Tsumogiri decision context |
| Network and persistence codecs | Example configuration, state, action and tile data |
| Settlement and history presentation | Supplied by the Minecraft adapter |

Start with
[`ExampleMahjongExtension`](src/commonMain/kotlin/com/doublemoon1119/mahjongcraft/example/ExampleMahjongExtension.kt)
to see the public registration callbacks. Follow its registered definitions
for rule, strategy and codec implementations.

[`ExamplePairingPublicApiIntegrationTest`](src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/integration/ExamplePairingPublicApiIntegrationTest.kt)
exercises the rule through Flow, rather than only calling rule methods.
[`ExampleFormatRoundTripTest`](src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/ExampleFormatRoundTripTest.kt)
checks network and persistence mappings, including rejection without the
extension and reading the same encoded data after registration is restored.
That codec test does not prove that a world or a disk file is recovered.

## Identifiers and compatibility

The hand-reveal action keeps one identity everywhere it is serialized or
presented: `mahjongcraft_example:pairing/reveal_hand`. The hand-reveal command is
a separate ID because it is a different serialized type.

The example is not a released compatibility surface. Changing its development
IDs does not add aliases or migrations for old development data. The event and
seating APIs have explicit public baselines; this rule and its AI remain outside
that guarantee.

See the [Minecraft example guide](../platform/minecraft/README.md) for build,
run and isolation instructions.
