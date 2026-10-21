# Platform-independent example extension

This Kotlin Multiplatform library shows how an extension can use MahjongCraft's
public callbacks without importing Minecraft, a loader, or dependency injection.
The example currently verifies its JVM target; other runtimes are not claimed.

`ExampleMahjongExtension` is the entry point used by the platform adapter. It
registers the rule, data codecs, action, AI strategy and round-preparation
resolver. It does not copy or delegate to the bundled Riichi rule.

## Pairing Mahjong

This is a deliberately small demonstration rule, not a regional Mahjong rule.

- Two to four players start with 100 points and three tiles.
- Two players use four copies of Characters 1–4; three use 1–6; four use 1–8.
  One Character 1 is replaced by the inverted Character 1. It has a distinct
  identity, but pairing and sorting treat it as Character 1.
- Each turn draws one tile and either declares a two-pair self-draw or discards.
  Four identical tiles count as two pairs. Calls, Ron and Kans are not used.
- The two-player wall uses two opposite faces. Three- and four-player games use
  four faces. The rule owns this layout; it is not a room option.
- The first opening rolls twice: the first roll chooses a temporary dealer, and
  that player's roll chooses the initial dealer and wall opening. Later rounds
  use the normal one-roll opening.
- A self-draw normally makes each other player pay 10 points. Exhausting the
  wall changes no scores; a three-tile hand containing a pair is ready.
  The dealer advances after each round. The default match has four rounds.
- A player may make one public declaration after drawing. It changes a public
  marker and does not change scoring or concealed information.

The rule's settings can change payment, round count and the optional exchange,
with payment from 1–100 points and match length from 1–16 rounds.
The example does not replace server
validation with client controls.

## Optional two-tile exchange

Exchange is disabled by default. Enabling it adds a separate round-preparation
step and its own preparation-time setting. When exchange is disabled, that
setting is not part of the rule's configuration.

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
the legal actions supplied by the decision context, and preparation uses the
public default submission. It is an integration example, not a competitive AI.

## Public capability map

| Capability | Example use |
| --- | --- |
| Rule and wall layout | Pairing rule and fixed two/four-wall layouts |
| Round preparation | Optional simultaneous two-tile exchange |
| Extension action and command | Once-per-round declaration |
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

The declaration action keeps one identity everywhere it is serialized or
presented: `mahjongcraft_example:pairing/declare`. The declaration command is
a separate ID because it is a different serialized type.

The example is not a released compatibility surface. Changing its development
IDs does not add aliases or migrations for old development data. The event and
seating APIs have explicit public baselines; this rule and its AI remain outside
that guarantee.

See the [Minecraft example guide](../platform/minecraft/README.md) for build,
run and isolation instructions.
