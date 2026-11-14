# Extensions and Registries

## Registration ID Conventions

- **Format**: Every registration ID, including persistence type keys, uses the `namespace:path` format defined
  by `NamespacedId`: lowercase letters, digits, `_`, `-` and `.`, plus `/` in the path. Do not use prefixes
  such as `builtin:`, dot-separated keys, or IDs without a namespace.
- **Rule-specific IDs**: Place them under the rule's path, for example `mahjongcraft:riichi/declare_riichi`.
  Add a category level when a rule registers a family of IDs, for example `mahjongcraft:riichi/yaku/pinfu`.
- **Rule-neutral IDs**: Place them directly under the namespace, for example `mahjongcraft:pao`. Rule-neutral
  objects must not hold rule-specific IDs.
- **Third-party extensions**: Use their own namespace instead of `mahjongcraft`.
- **Full paths**: Write each ID's complete path where it is defined, for example
  `MahjongCraftMetadata.id("riichi/yaku/pinfu")`. Do not build IDs from slugs, enum names or other IDs. IDs
  created at runtime for numbered instances, such as one per seat, may append that number.
- **Sound events**: Minecraft sound event IDs keep Minecraft's dot-separated style and name the rule as a
  segment, for example `mahjongcraft:voice.riichi.chii`.

## Registry Contracts

- Register one typed definition containing the identity and implementation, factory, or mapping. An existing
  self-identifying provider or template can itself be the definition; do not duplicate its ID in another
  argument. Registration must not invoke a factory or callback to discover its identity.
- Validate registration IDs at the registration boundary. Preserve the complete uniqueness scope, including
  the rule ID when identity is rule-specific, in duplicate checks and diagnostic snapshots. Class keys,
  numeric migration steps, serialized discriminators, translation keys and asset-path tokens are separate
  contracts: do not rewrite them as namespaced IDs merely to resemble registration IDs.
- Higher numeric priority runs first; use stable identity for ties. Compare values rather than subtracting
  them, so minimum and maximum integer priorities remain safe. Keep built-in precedence explicit when changing
  a registry's ordering contract.
- Registration-key collections and registered collection data are detached snapshots, not live map views or
  aliases of caller-owned mutable collections. Freeze forbids later registration; it must not be bypassed by
  mutating a collection supplied before freeze.
- Keep missing-value and failure policies domain-specific. Strategy calculation failure may select a
  documented fallback, but cancellation, fatal errors and authoritative command failures must not become
  successful decisions. Share lifecycle and execution mechanics without merging endpoint authorization or
  data-recovery policies.

## Platform Presentation

Keep platform-specific rendering contracts in the platform's presentation guide rather than defining
core extension behavior through one adapter. For example, see the Minecraft guide for
[round information parameters](minecraft-presentation.md#round-info-parameters) and
[tile corner labels](minecraft-presentation.md#tile-corner-labels).

## Extension Action Identity

- Give each extension action one stable namespaced ID. Use that same constant for the action's `id`,
  its persistence registration's `typeKey`, and its player-facing vocabulary registration. Different
  selected tiles or action parameters do not create a new action ID.
- The action persistence mapper rejects a saved or restored action when its ID differs from the registered
  persistence key. Registration alone cannot check the ID of an instance that does not exist yet; do not
  call conversion functions or construct sample actions during registration to guess it.
- Test both saving and reading a mismatched ID, and test the saved action through replay and the vocabulary
  lookup. Changing only an example's string leaves the same mistake possible in another extension.
- This rule applies to extension actions, not to every persistence type or network discriminator. Keep
  command IDs and unrelated format IDs separate. It does not require a DTO serializer's own name to match.

## Shared Room Settings

- Rule authors add settings for their own game, such as the red-tile count in Riichi. MahjongCraft adds
  the shared turn-time and spectating settings when the rule is registered. Do not add another copy.
- Only add settings the rule can use. A rule that never asks players to choose, such as for a tile
  exchange before a round, has no rule-decision time setting.
- Rule-decision settings are absent by default. A rule decision resolver decides from the current rule
  configuration whether players may need to choose at any timing, and supplies a default
  `RuleDecisionConfig` only when needed. One setting covers every timing. Do not cache that answer
  solely by rule ID: an option can enable or disable the choice within the same rule. The room settings
  use that decision to add the rule-decision time field; do not declare a second capability flag in the
  presentation definition.
- Creating defaults or changing rules adds that rule's rule-decision configuration, or removes it when
  the new rule has none. Other shared flow settings are preserved. Rule-decision time does not consume
  the ordinary turn's reserve time. A step that asks players to choose without its configuration is an
  error, not an instruction to silently supply a timer or wait forever.
- Changing a rule option uses the same decision. Enabling the choice supplies its default time when
  none exists; keeping it enabled preserves the chosen time; disabling it removes both the setting and
  its field. Rebuild submitted rule options before deciding which shared fields the server may accept.
- MahjongCraft owns the shared fields' IDs, allowed values, update functions and translations. A rule that
  reuses one of those IDs is rejected with an error; neither definition silently replaces the other.
- Screens, summaries, history and server checks use the same registered fields. Test that a third-party
  rule can actually submit and save a change, not just that a bundled rule shows a control.
- For an optional setting, test both sides: rules that need it can change and save it; rules that do not
  need it have no field and the server rejects changes to that value.

## Multi-choice Room Settings

- Use `GameConfigEditorSpec.MultiChoice` when a rule setting selects a set of options.
  Register the full option IDs, their display order, explicit translation keys and
  minimum/maximum selection counts. Use `optionDescriptionTranslationKeys` for
  optional hover explanations; its keys must also be registered option IDs.
  `MultiChoiceValue.optionIds` is a set: changing
  its iteration order does not change the setting. An empty set is allowed only
  when the minimum is zero.
- The rule owns the actual setting and its network and persistence mappings.
  The editor is a way to read and update that setting, not another wire format.
  The server uses the editor's validation and rebuilds the submitted configuration;
  screen controls never replace server checks.
- Keep option collections and translations stable after registration. Display names
  follow the registered option order, with the complete ID as the missing-name
  fallback. Never infer a translation key from an option ID.
- A selection screen edits a local selection first. Applying updates the room draft
  once; cancelling leaves it alone. Temporary selections may be below the minimum
  while replacing one option with another, but cannot be applied until valid.
- A valid selection may still be unsuitable for the current match roster. Express
  that restriction through the rule's player-count contract and test the real room
  update and start paths. The pairing example uses this to keep enough tiles for
  its initial hands and at least one draw.

## Rule Decision Windows

- A rule decision resolver is asked at four timings, and returns a step only when players should choose:
  - `ROUND_START`: after the deal; play starts once the window closes.
  - `BEFORE_EXHAUSTIVE_DRAW`: the wall has run out and before the ordinary draw is settled; the draw is
    settled once the window closes.
  - `WIN_CONTINUES`: after a win that does not end the round; the remaining players continue once the
    window closes.
  - `ROUND_END`: after the round is settled; the next round, or the end of the match, follows once the
    window closes.
- Return `null` for every timing the rule does not use. A resolver that only checks its own options and
  ignores the timing opens the same choice at all four.
- A window holds every step from opening until the last step resolves. Each step gets its own window ID;
  a submission naming another window is rejected, even when the step ID repeats. Do not rely on step IDs
  to tell one round's choice from the next.
- Write the result into table state in `resolve`, for example by recording an extension action in the
  player's action history, and let the rule read it at the timing that needs it. The draw settlement,
  for example, reads the recorded choice; nothing else carries a choice from the window to later code.
- At `ROUND_END` the round is already settled, and `resolve` must not change any player's score;
  doing so stops the match. A rule whose choice changes payments opens it before the settlement, such
  as at `BEFORE_EXHAUSTIVE_DRAW`.
- Each viewer sees only their own part of a window: whether they are asked, their input and their
  submission. Who else is asked or has answered is not sent to anyone, as with reaction windows.
- Opening a window and scheduling what follows are one authoritative write, and so are closing a window
  and scheduling what follows. Do not add a second write that a crash could separate from the first.
- Choices that need player input require the rule-decision setting above. Timeout and AI fallbacks use
  the input's default submission unless the resolver supplies its own.

## Standard Table Layouts

- Player seats and wall sides are separate. Use the complete match roster to place players; do not use
  online players, winners, a partial tile update, or the wall's side count as the player count.
- A wall factory builds its wall, including every tile ID, only from the `Random` it receives. The same
  source must produce the same wall, and other randomness would let players predict hidden tiles.
- A component lifecycle that needs a random initial value uses `ComponentLifecycleContext.random`, which
  exists only while a round opens or transitions. Records created during play get no randomness.
- Wall creation and layout also receive the actual complete roster's player count. Use that same count
  when opening a round, starting the next one and reconstructing presentation. A rule's minimum or
  maximum allowed players is a constraint, not the number playing this match; do not add another room
  setting or mutable module state merely to pass that number around.
- A rule's `TileWallLayout.sideArrangement` specifies which standard sides hold its logical wall faces
  and whether those sides stay fixed or rotate with the dealer. This is a rule design, not a room option.
  It must return the same arrangement for the same rule configuration and dealer seat, including after
  a restart. Do not use randomness or mutable external state in this function.
- `TileWallLayout.sideStackCounts` reports how many stacks each wall face has, in face order. It must
  match the structure `resolve` lays out and depend only on the rule configuration, because the platform
  reads the wall size from it after a restart instead of keeping the opening structure.
- `PhysicalWallLayoutPolicy.roundReach` reports every position the wall's tiles may occupy for the rest
  of the round, including where they are now. Table rows make room for exactly these positions, so a later
  state of the same round must never report a position outside an earlier report. A policy whose tiles only
  leave the wall returns their current positions; one that moves tiles, such as a reserved section that
  slides as it is replenished, also returns every position those tiles can still reach.
- Keep draw order and opening indices separate from physical placement. The platform adapter maps the
  arrangement to its fixed table geometry; rules do not choose arbitrary coordinates or tile dimensions.
- Test two-player games with four walls, adjacent and opposite two-wall arrangements, both dealer seats,
  and every table facing. Keep three-player and four-player geometry covered by regression tests.

## Round Opening Procedures

- A rule's opening procedure decides who rolls, how many dice they roll, and when the recorded results
  determine the first dealer and wall opening. It reads completed rolls and returns the next request;
  it does not generate dice, keep mutable progress, or wait for presentation.
- Use the shared executor as the only source of dice results. The current round's saved transcript,
  live events, history and presentation all use those same results. Reconnection and loading never reroll.
- Selecting the initial dealer does not move players to different physical seats. Keep the roster order
  as it is; establish the initial ranking order once, relative to that dealer. Later rounds use the dealer
  chosen when the previous round ended, even when another player rolls. Players change seats only through
  the seat order described in Dealer, Seats and Pot Payouts.
- Keep the bundled single-roll behaviour through the default procedure. Test custom multi-roll procedures
  through actual start and next-round use cases, including invalid requests and interrupted presentation.

## Dealer, Seats and Pot Payouts

- `resolveRoundTransition` decides the next dealer once, when a round ends: `RepeatDealer`, `PassToNextSeat`
  or `PassTo` a named player, for example the winner. The result is saved with the round summary and reused
  after loading; nothing recomputes it. `PassTo` must name another player at the table. The next seat is
  counted in the seating of the round that just ended.
- A match progression policy that changes seats between rounds returns `ContinueMatch.seatOrder`, the
  complete turn order for the next round. It must list every player exactly once. Flow reorders the players
  and keeps their IDs, scores and `initialSeatIndex`; relative directions and seat winds follow the new order.
  Do not reorder `players` anywhere else.
- When seats change, Flow calls `publishSeatOrderChanged` before the next round is shown. The platform seats
  players the same way as at the start of a match: anyone sitting on another seat of the same table stands up
  first, so two players can swap and a player who sat down early reaches their own seat. Players riding
  something that is not one of the table's seats are not pulled off.
- `collectStickPot` only says how much the pot holds. Who receives it, and who collects combo bonus points,
  comes from `allocateSettlementExtras`. Candidates arrive in priority order: from the discarder's next seat
  for a win by discard, the winner alone for a self-draw, from the dealer for draws, and by final ranking at
  the end of the match. Shares must go to candidates, must not be negative and must add up to the collected
  pot; only at the end of the match may the rule return no shares, which removes the leftover pot. No combo
  bonus is paid at the end of the match. An invalid allocation stops the match through the contract-failure
  path.

## Observer State Projection

- Player rule data lives in typed components. Register a separate view and projection for each value
  that a reader may receive. Unregistered projections expose nothing; permission to see someone's
  tile faces does not grant access to their private rule data.
- Describe tiles in any current area through `describeTileComponents(tableState)` or registered pure
  component effects. Each description identifies one tile UUID. A property of one tile does not
  apply to every tile with the same face.
- `resolveTileComponents` combines area defaults, the tile's saved components and rule effects.
  It rejects duplicate descriptions, references outside current areas, conflicting values and access
  grants to players outside the match. All consumers use this resolver, including platform adapters.
- Keep access separate from pose. A readable tile may stay face down or upright; a face-up tile must
  be public. Neither a texture nor a pose grants permission to observe a match.
- The visibility policy uses an authenticated player, spectator or public context before building
  network snapshots and AI input. Do not send a raw `TableState.toSnapshot` result to either
  consumer. Unknown faces carry no face-specific appearance or component views.
- Derive effects from the same saved state without changing it. For example, a player's reveal
  component can affect their current hand and future draws without storing another list of tile IDs.
  Use the registered round lifecycle to reset, retain or transform each component.
- Views must be immutable and contain only explicitly authorized data. Register their network
  codecs; never write views back into authoritative state or use them as the persistence format.
- Projections do not make game decisions. Test that changing private values leaves unauthorized
  observers' output unchanged, including spectators and public events.

## Component Lifecycles and Changes

- Register a lifecycle for every player, table, discard-record and meld component. Its `appliesTo`
  states which rules and configurations use the component; a saved component outside that scope is
  rejected before any write. Tile components may come from the tile set itself and do not need a
  lifecycle.
- To reuse another extension's component in your rule, register a `ComponentLifecycleScope` for it
  instead of editing the original registration. The original lifecycle's initial value, required
  check and round transition then also apply to your rule. Register your own projection if your rule
  should expose it.
- A discard record or meld created by an action receives its registered initial values right after
  the action. Values the rule already wrote take precedence. An added kan keeps the meld's ID and
  components.
- Every set or removed component in a `ComponentPatch` carries its expected previous value. Build
  the patch with `ComponentPatch.basedOn` from the state the rule read; a stale value stops the
  match through the contract-failure path instead of overwriting another committed result.
- Flow checks components returned by hooks, effects and patches as soon as it receives them, using
  the same registry checks as the final write.

## Legal Action Input

- Build a `LegalActionContext` from the current table and the assessed player's ID. Use `ownTurn`
  for their own turn, `discardReaction` for another player's discard, or `declarationReaction`
  for a kan or extension declaration. The latter two also work before a reaction window is opened.
- Do not copy a player and remove their drawn tile before calling the validator. The context reads the
  player from the same table and provides both `handBeforeIncoming` and `handWithIncoming`.
  The first excludes this query's source tile; the second includes it exactly once.
- Use those named hands for tile calculations. `context.player` and `context.tableState` remain
  the original state, for scores, rule state, wall state and action history. Do not write a calculated
  hand back to the repository or add `incomingTile` to `handWithIncoming` again.
- Preserve the source kind, action, player and physical tile ID. Do not infer a discarded tile or a
  robbing declaration from whether a nullable tile is present. Equal tile faces are not equal tile IDs.
- Offering actions and checking a submitted command use the same context factories. At submission,
  rebuild the context inside the authoritative transaction; an earlier offer is not permission to act.
  Keep turn, membership and reaction-window checks in Flow.
- A single own-turn query returns all applicable actions, including riichi alongside self-draw or kan.
  Do not recursively call the validator with a different representation of the same drawn tile.
- `tileSelectionRequirement` still receives the original player with the complete hand. It describes
  which tiles must be selected after choosing an action, not where that action's source tile is stored.
- Test both the offer and the real command. A test that only sees a self-draw option or calls the rule's
  settlement method directly does not show that the player can successfully submit self-draw.

## Claims and Declaration Discards

- A rule's `createDiscardReactionPolicy` supplies executable reactions from the validator's legal actions.
  Each offered candidate includes a valid selection and, for a claim, its `MeldClaimEffect`. A rule may
  override `resolveSelection` to validate other selections for that same action. Neither method changes
  state or publishes anything; the same input must produce the same result.
- Use the shared discard-reaction command for reactions, even when that extension action also has an
  own-turn command factory. Keep the reaction window ID and selected physical tile IDs with the command.
  The server checks them again inside the transaction; an old window is not permission to act.
- Higher reaction priority wins. Higher tie-break priority wins within that priority, followed by the
  nearest seat after the discarder. Do not compare priorities by subtraction. Winning reactions still use
  the shared multi-winner settlement path; a discard can belong to only one claimed meld.
- A declaration that also discards a tile uses
  `ExtensionGameCommandContext.declareAndDiscardUseCase`. Its handler supplies the action and
  discard ID, not a second repository write or its own publications. `declarationDiscardPolicy`
  returns a player-component patch, the new discard record's components, a table-component patch and
  the declared cost. An empty patch keeps the current components; removals must be listed
  explicitly, and every change carries its expected previous value.
- Flow owns the discard, score deduction, action history, reaction window and post-commit
  notifications. A rule effect describes component changes only, so it cannot touch hands, seats,
  identities, scores or existing discard entries. Invalid rule effects stop the match through the
  contract-failure path instead of becoming a pass or a normal discard.
- Keep a meld's information visibility separate from its physical pose. `publiclyRevealedTileIds` says
  which faces other observers may know; `faceDownTileIds` says which tiles lie face down. The called discard
  is already public. Save both sets and the stable display order; do not sort hidden tiles by their faces
  while building presentation. Test owner, opponent, spectator, AI and replay views.

## Draw Follow-ups and Action Scoring

- A rule that sets tiles aside automatically after a draw, such as replacing flower tiles, returns a
  decision from `createDrawFollowUpPolicy`. Flow asks after every live-wall draw and every replacement
  draw, and asks again after each replacement until the policy returns `None` or `EndRound`. When a
  draw brings in several tiles, Flow asks about each one in draw order and first moves the tile it asks
  about to the drawn slot; `tileSetAsideBy` must then pick that tile.
- An automatic set-aside reuses the rule's own `tileSetAsideBy`, `applyTileSetAsideAction` and
  supplemental draw policy, and the same robbing check as a declared set-aside: when the legal action
  validator offers a win on the tile, Flow opens the robbing window and pauses the chain. Do not add a
  second replacement source for automatic steps.
- Return `EndRound` when nothing is left to draw. Asking for a set-aside that cannot draw a replacement,
  or one that does not consume a wall or reserved tile, stops the match through the contract-failure
  path. A set-aside result may only move the named tile from the actor's standing tiles to the end of
  `setAsideTiles`, record the action and change rule state.
- Points that change hands when an action stands come from `createActionScoringPolicy`. Flow asks once
  for each claim, kan and set-aside that stands, before the replacement draw, and not for actions that
  are robbed or cancelled. Player changes plus the pot deposit must add up to zero; keep the pot balance
  in a table component and pay it out from the rule's settlement.
- `onPlayerDrew`, `onPlayerDiscarded`, `onPlayerDeclinedWin`, `beforeDiscardClaimed` and
  `onMeldClaimed` return player-component patches; `onMeldClaimed` keys them by player ID. A hook
  cannot change scores, hands or discards; use the scoring policy or a declaration cost instead.

## Koin Registration

- Declare every Koin definition in the owning module's DSL `module { }` value. Do not annotate classes with
  Koin annotations or rely on component scanning.
- Register directly constructible services with the compiler plugin's `single<T>()` or `factory<T>()`. Use a
  definition lambda for third-party types, configured builders, factory methods, bootstrap aggregates,
  runtime-owned instances, and classes intentionally kept framework-neutral, and state the reason in the
  module's KDoc or beside the definition.
- `single<T>()` registers only `T`. Add `bind` for every supertype consumers resolve. Runtime lookups such as
  `getOrNull<T>()` are not compile-checked, so a missing binding there fails without a build error.
- An unregistered parameter with a default value, nullable or not, fails the compile check instead of falling
  back to its default. When the production graph intentionally leaves such a parameter at its default,
  construct the class in a definition lambda that omits it. Inside a lambda, give `get<T>()` an explicit type
  whenever the parameter type is broader than the intended binding.
- Preserve lifetime: `factory` creates a new instance per resolution; `single` shares one within the Koin
  container. A single is not automatically a runtime-session scope. Preserve explicit session reset,
  cancellation, resource release, qualifiers, parameters and eager/lazy initialization behavior.
- Do not introduce Koin into Logic, AI, or Flow, or register DTOs and value objects merely because their
  module uses Koin elsewhere. Platform composition declares the definitions for Flow services.
- Keep each module within its intended graph. A module `includes` the modules its own definitions depend on,
  and application entry points start Koin with their top-level modules. Do not move environment-specific
  services into a shared module, relocate public types, or widen visibility just to simplify registration.
- The Koin compiler plugin validates the complete graph at each `startKoin` call, but only in a compilation
  that declares at least one definition of its own. Keep application entry points in such a module.
- A module that declares definitions without starting Koin reports `compile-safety validation skipped`; its
  definitions are validated at the entry points that load them. Do not disable `compileSafety` there to
  silence the warning: that also hides its definitions from the entry-point check.

## Extension Bootstrap

- Bootstraps in the core and platform adapters follow the same workflow: register built-ins through extension
  callbacks, capture their snapshot, register each extension in order, report its additions, and freeze that
  layer only after all callbacks succeed. Validate namespaced extension IDs and duplicates with
  source context.
- Registry aggregates hold existing runtime instances; they own snapshot creation and `freezeAll`, not
  registration callbacks. Built-in extensions own built-in registrations. Keep report category IDs separate
  from the feature IDs being registered, and centralize both under their respective owners.
- Snapshots own filtering of empty categories and sorting of registration IDs. Keep source and category order
  deterministic. Discovery code materializes the discovered extensions once, composes the layers, and logs
  their combined report; it must not maintain a second registration implementation for tests.
- Preserve dependency boundaries rather than adding cross-layer dependencies merely to share report classes.
  Cross-module bootstrap entry points remain public when callers require them; module-local implementation
  details stay internal.
- Bootstrap is one-shot. Failure aborts startup; earlier writes or previously completed layers are not rolled
  back, and partially initialized registries must not be reused for retry. Tests cover invalid and duplicate
  IDs, callback order, no-op sources, snapshot differences, shared instances, and successful or failed
  freeze paths.

## Public API Compatibility

### What is protected

ABI protection is opt-in and exact. Each module lists the packages or individual types it promises to
keep stable in `publicApiAbi.includedNames`. Public declarations outside those lists are not protected
automatically. A type referenced by a protected signature needs its own matching entry to be protected
as a separate API surface.

The core modules protect `api.event` in Flow common, `api.registration` in the modules that provide
registrars, and the exact `MahjongExtension` entrypoint. Platform adapters document their additional
contracts in their own guides. For example, see the
[Minecraft API scope](minecraft-presentation.md#public-api-compatibility). A new platform records its scope
in its own guide rather than extending this shared policy with platform-specific type lists.

### Registration and internal access

- Extension callbacks receive a `Registrar` in `api.registration`, never the full registry. It provides
  registration methods and read-only state such as `registrationKeys` and `isFrozen`. MahjongCraft
  provides it; extensions do not implement it. Internal registries add lookups and freezing.
- A module adding a registry also adds its Registrar to `api.registration` and passes that type to the
  extension callback.
- Mappers for nested extensible types receive a read-only converter when MahjongCraft calls them, in
  both directions. Examples are `NetworkDtoConverter` for `DtoDefinition` and `PersistenceConverter`
  for `PersistenceDtoDefinition`. Do not capture a Registrar or registry for later conversion.
  Definitions without nested conversions use the value-only constructor.
- A platform may need public declarations for calls between its own modules. Mark such declarations
  with an error-level opt-in annotation, and opt in at the calling file rather than for a whole module.
  Their signatures may be recorded without promising their internal behavior to extensions. Example
  integrations use public extension contracts instead of opting into internal ones.

### Checking a change

- Modules use `mahjongcraft.public-api-abi`, backed by Kotlin's built-in ABI validation. Commit the
  reference signatures in each module's `api/` directory. `check`, and therefore `build`, checks them
  automatically; normal builds must not regenerate them.
- Run `./gradlew checkKotlinAbi` with the intended platform target. The core target checks the loaded
  core modules; a platform target also checks its adapter's protected surfaces. New adapters exposing
  protected APIs must apply the convention and add reference signatures.
- Verify every supported target affected by shared API changes. KMP checks follow the module's declared
  targets: a JVM check does not validate a Native binary, and target inference does not replace
  validation on a suitable host.
- After reviewing an intentional API change, run `./gradlew updateKotlinAbi` for the intended target,
  inspect the diff, and commit it with the implementation. Updating the reference acknowledges a change;
  it does not prove compatibility.
- Preserve existing names, types and listener methods. Prefer additive read-only properties, and retain
  deprecated declarations when removing a released API would break consumers.
- Signature checks do not guarantee runtime behavior, constant values, nullability or generic contracts
  erased by the runtime. Keep behavior, visibility and contract tests alongside the baselines. Protection
  for one platform, version or adapter does not imply compatibility with another adapter's native types.
