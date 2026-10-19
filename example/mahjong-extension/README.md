# Common Extension Example

This Kotlin Multiplatform library depends on the public extension API.
It contains no Minecraft, loader or dependency-injection code.
Only the existing JVM target is configured; other runtimes are not verified.

`ExampleMahjongExtension` registers the example through the public callbacks.
It does not register, delegate to or copy a bundled Riichi rule.
The platform example packages this library rather than publishing it as another mod.

## Pairing Mahjong

This small game demonstrates extension contracts, not a traditional regional rule.

- Two to four players start with 100 points and three tiles each.
  Each turn draws one tile, then either declares a two-pair self-draw or discards.
  Four identical tiles also count as two pairs. There are no calls, Ron or Kans.
- Two players use four copies of Characters 1–4; three use 1–6; four use 1–8.
  One Character 1 is replaced by the example's inverted Character 1.
  Its identity remains distinct, but pairing and sorting treat it as Character 1.
- Two players use two opposite wall faces. Three and four use four faces.
  Each stack contains two tiles; no tiles are reserved as a dead wall.
- The first round rolls two dice twice: the first roll selects a temporary
  dealer, and that player's roll selects the initial dealer and wall opening.
  Later rounds use one roll and keep the dealer selected by match progression.
- Each other player pays 10 points for a self-draw by default.
  Exhausting the wall changes no scores; a three-tile hand containing a pair
  is ready. The dealer advances after each round, with four rounds by default.
  Payment accepts 1–100 points; match length accepts 1–16 rounds.
- A player may make one public example declaration per round after drawing.
  The declaration changes a public marker, not scores or concealed information.

## Optional Two-Tile Exchange

Exchange is disabled by default. Enabling it adds a round-preparation step
and its separate preparation-time configuration.
There is no preparation-time setting while exchange is disabled.

Each player chooses two of their three starting tiles.
After everyone submits, the choices pass simultaneously to the next player
in the fixed roster order. Two players therefore exchange with each other.
The exchange preserves physical tile IDs and does not reveal other players'
choices before resolution. Automatic submissions use the resolver's default.

## Tsumogiri Strategy

The example strategy chooses a legal self-draw first, then a required discard,
then the drawn tile. With no drawn tile it chooses the first standing tile.
Reaction decisions use the supplied legal actions; preparation uses the
public default submission. It is an API example, not a competitive strategy.

## Integration Coverage

| Public capability | This library |
| --- | --- |
| Rule module, wall layout and tile interpretation | Pairing rule and inverted Character 1 |
| Multi-roll opening | Initial dealer selection and wall opening |
| Round preparation | Optional simultaneous two-tile exchange |
| Extension action and command | Once-per-round public declaration |
| AI strategy | Tsumogiri using the public decision context |
| Network and persistence registries | Example-owned DTOs and two-way mappings |
| Match flow and history | Small automated integration checks |
| Names, controls, assets and world presentation | Supplied by the platform example; not verified here |
| Furniture and event chat output | Subsequent platform integration |

Usable extension points do not imply a binary-compatibility promise.
This library does not establish new ABI guarantees for rules or strategies.
Automated checks are intentionally small and do not certify every extension path.

See the [Minecraft example guide](../platform/minecraft/README.md)
for build and run commands.
