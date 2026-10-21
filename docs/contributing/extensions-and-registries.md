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
- Only add settings the rule can use. A rule without tile selection or exchange before a round has no
  round-preparation time setting.
- Preparation settings are absent by default. A preparation resolver decides from the current rule
  configuration whether players need to choose before a round, and supplies a default
  `RoundPreparationConfig` only when needed. Do not cache that answer solely by rule ID: an option can
  enable or disable preparation within the same rule. The room settings use that decision to add the
  preparation-time field; do not declare a second capability flag in the presentation definition.
- Creating defaults or changing rules adds that rule's preparation configuration, or removes it when the
  new rule has none. Other shared flow settings are preserved. Preparation time does not consume the
  ordinary turn's reserve time. A player-choice preparation step without its configuration is an error,
  not an instruction to silently supply a timer or wait forever.
- Changing a rule option uses the same decision. Enabling preparation supplies its default time when
  none exists; keeping it enabled preserves the chosen time; disabling it removes both the setting and
  its field. Rebuild submitted rule options before deciding which shared fields the server may accept.
- MahjongCraft owns the shared fields' IDs, allowed values, update functions and translations. A rule that
  reuses one of those IDs is rejected with an error; neither definition silently replaces the other.
- Screens, summaries, history and server checks use the same registered fields. Test that a third-party
  rule can actually submit and save a change, not just that a bundled rule shows a control.
- For an optional setting, test both sides: rules that need it can change and save it; rules that do not
  need it have no field and the server rejects changes to that value.

## Standard Table Layouts

- Player seats and wall sides are separate. Use the complete match roster to place players; do not use
  online players, winners, a partial tile update, or the wall's side count as the player count.
- Wall creation and layout also receive the actual complete roster's player count. Use that same count
  when opening a round, starting the next one and reconstructing presentation. A rule's minimum or
  maximum allowed players is a constraint, not the number playing this match; do not add another room
  setting or mutable module state merely to pass that number around.
- A rule's `TileWallLayout.sideArrangement` specifies which standard sides hold its logical wall faces
  and whether those sides stay fixed or rotate with the dealer. This is a rule design, not a room option.
  It must return the same arrangement for the same rule configuration and dealer seat, including after
  a restart. Do not use randomness or mutable external state in this function.
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
  fixed; establish the initial ranking order once, relative to that dealer. Later rounds use the dealer
  chosen by match progression, even when another player rolls.
- Keep the bundled single-roll behaviour through the default procedure. Test custom multi-roll procedures
  through actual start and next-round use cases, including invalid requests and interrupted presentation.

## Observer State Projection

- Rule modules define what each observer may know through `projectPlayerRuleState` and
  `projectDynamicRuleState`. The visibility policy applies these hooks to both network snapshots and AI
  contexts. Do not send a raw `TableState.toSnapshot` result directly to either consumer.
- The default player projection exposes state only to its owner; the default dynamic projection exposes
  nothing. A spectator's permission to see tile faces does not grant access to private rule state.
- Return immutable, explicitly public projections for other observers. Represent hidden fields as unknown,
  not as false or another invented value. Register a network DTO for any new projection type; never write
  projected state back into the authoritative game or its persistence format.
- Projection hooks must not modify state or make game decisions. Authoritative validation and wall reveal
  rules still read the original state. Test that changing hidden data leaves other observers' output unchanged.

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

- Registrars in the core and platform adapters follow the same workflow: register built-ins through extension
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

- ABI protection is opt-in and exact. Modules list the packages or individual entrypoint types they promise to
  keep stable; public classes outside those lists are not covered automatically.
- Modules use `mahjongcraft.public-api-abi`, which uses Kotlin's built-in ABI validation. Reference signatures
  live in each module's `api/` directory and must be committed. `check` (and therefore `build`) checks them
  automatically; do not regenerate the references as part of normal builds.
- The current stable surfaces are `api.event` in Flow common, Minecraft common and each loader adapter, plus
  `api.seating` in Minecraft common and its loader adapters. The current Fabric adapter also protects the exact
  `FabricMahjongExtension` entrypoint because third-party mods use it to register seats. This does not make every
  table-prop type referenced by that interface stable as a separate API surface. In particular,
  `registerTablePropKinds` is recorded as a method signature, but the methods of its
  `FabricTablePropKindRegistry` parameter are not covered by this compatibility promise.
- Seat constructors and internal request factories keep their existing visibility. A third-party provider creates
  the public identity and geometry definitions; MahjongCraft creates its own internal request objects. This change
  records the existing shape and does not redesign the seating API.
- Run `./gradlew checkKotlinAbi` with the intended platform target to inspect signature changes. The core
  target checks Flow; a Minecraft target also checks its common and loader public surfaces. Verify every
  supported target when changing shared APIs. New loader adapters exposing protected public APIs must apply the
  convention and add their own reference signatures. KMP validation follows the module's declared targets;
  checking JVM does not validate a Native binary, and unsupported-target inference is not a substitute for
  validation on a suitable host.
- Only after reviewing a deliberate API change, run `./gradlew updateKotlinAbi` for the intended target,
  inspect the signature diff, and commit it with the implementation. Updating the reference acknowledges a
  change; it does not prove compatibility. Preserve existing names, types and listener methods; prefer
  additive read-only properties, and retain deprecated declarations when removing a released API would
  break consumers.
- Signature checks do not guarantee event or seating behavior, constant values, nullability, or generic type
  contracts erased by the JVM. Keep behavior, visibility and contract tests alongside the ABI baselines. They
  also do not guarantee compatibility across Minecraft versions or loader adapters whose own APIs differ. For
  example, a Fabric 1.20.1 baseline does not promise that another loader can use Fabric-native types.
