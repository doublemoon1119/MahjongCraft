# Game Event Hooks

Use `MahjongCraftEvents` to observe committed match activity. Listeners receive read-only snapshots,
not a `Game`, a repository, or permission to change authoritative state.

## Delivery and Subscription

Register listeners during extension initialization, before the first server session starts. All
subscription points lock when that session starts; a new session does not reopen them.

Events are projected after a transaction commits. MahjongCraft releases the transaction lock before
delivering them on the platform's main thread. Listeners must return promptly; schedule expensive
work separately. Events are not saved or replayed after a restart or reconnection. Ending a session
discards its pending notifications instead of delivering them to the next world.

Each transaction with public activity produces one `CommittedMatchEventBatch`. Individual event
listeners run first, followed by batch listeners. Use the batch to handle related notifications
together, for example to count a winner only once when a transaction contains multiple results.
Do not count both an individual notification and its copy in the batch: they refer to the same event.

## Subscription Points

| Subscription | Meaning |
| --- | --- |
| `MATCH_STARTED` | A new match has started. Loading a saved match does not send it again. |
| `ACTIONS_PERFORMED` | Public actions that actually took effect in one transaction. |
| `ROUND_SETTLED` | A win, draw or special result has been settled. The round may still continue. |
| `RULE_RESULTS_RESOLVED` | Rule effects that took effect in this transaction, with their public details and optional settlement summaries. |
| `ROUND_COMPLETED` | The round has finished, including round-end decisions. Its context describes the completed round, not the next one. |
| `MATCH_ENDED` | The match completed normally or was aborted. Check `completion` before counting a completed match. |
| `EVENT_BATCH_COMMITTED` | The transaction's events have been delivered to their individual listeners. |

The original start, settlement and end notifications keep their relative order. Ordinary draws,
pending choices, passes and rejected commands produce no action notification. Transactions without
public activity produce no empty batch.

## Public Actions and Tile Faces

`ActionsPerformedEvent.actions` preserves the order of actions that took effect. A declaration and
its discard remain two entries. Continuous replacement draws are grouped by transaction rather than
sending a separate event for each hidden tile.

- Every action supplies its complete namespaced action ID, actor and neutral action kind.
- Discards and completed melds include public tile faces. Concealed meld positions remain `null`
  according to the rule's existing visibility policy.
- Replacement draws supply only their count and the public triggering action ID, never the drawn
  faces, physical tile UUIDs or concealed tile order.
- Other actions supply only the common fields. Read settlement details for winners and payments;
  do not count a win again from its action entry.

`PublicTileFace.typeId` identifies a face, not a physical tile. Extension faces retain their complete
registered tile type ID. Standard numeric faces use `mahjongcraft:tile/numeric/<suit>/<value>`, where
the suit is `character`, `dot` or `bamboo`. Standard honors use `mahjongcraft:tile/honor/<name>`, where
the name is `east`, `south`, `west`, `north`, `red`, `green` or `white`. These are event face identifiers,
not platform model or texture paths.

## Win Details and Rule Markers

`RoundSettledEvent.winGroups` keeps each result's winners and responsible players together. Win methods
are `SELF_DRAW`, `DISCARD` or `UNKNOWN`. Unknown means the committed source did not establish the method;
do not guess from an extension's outcome ID. A responsible player is not necessarily a discarder.
Use `discarderPlayerIds` only when the source establishes a discard win.

`RoundWinGroup.source` distinguishes an ordinary win settlement from a rule result. Do not infer
that distinction from the outcome ID. A rule result may also appear in `RuleResultsResolvedEvent`;
these are two views of the same committed activity, not two wins to count.

Each rule result keeps its reason ID, detail winners and optional summary beneficiaries separate.
Some results have details without a summary. Others have only a summary. Decide which list your
feature needs rather than combining them automatically. Rule-result events retain submission order
and include only public entries and quantities, not tile lists or physical tile IDs.

For a completed match, `completedScores` preserves the scores explicitly recorded at completion.
Its keys correspond to `completedPlayerIds`; neither is filled from the complete standings. An
aborted match has no completed scores. Standings continue to describe the final ranking snapshot.

Win detail fields retain their namespaced IDs and grouping. Scoring entries and quantities are separate
from public state markers, such as a declared status still relevant on a winning hand. Match-end events
also carry rule-provided markers. Rule-specific information, such as a bust threshold, belongs in
those markers rather than fixed common event fields.

Rules provide additional markers through `GameEventPublicMarkerResolver`, registered with
`MahjongExtension.registerGameEventPublicMarkerResolvers`. The resolver receives committed before and
after states and an explicit winner or match-end context. It must be synchronous, read-only, reentrant
and fast: projection runs inside the authoritative lock. Do not perform I/O, query repositories, send
messages or grant achievements. Missing resolvers produce empty markers. An isolatable failure discards
that call's markers and reports a diagnostic without discarding the core event. Cancellation and fatal
failures are not converted to empty markers.

The resolver also supplies `resolvePrevalentWindId` when a rule uses a public prevalent wind. Without
an explicit value, `roundContext.prevalentWindId` is `null`; a default wind in internal table state
does not mean that every rule uses winds. The round number still identifies the completed round.

Marker and quantity unit IDs keep their full namespaces. Player-specific markers must name participants;
win markers must name their winner. Duplicate marker ID and player pairs are rejected. Unknown valid IDs
remain available for consumers that understand them. Additional state and match-end markers exist only
in live event snapshots: they do not add history fields or cause replay to call the resolver again.

## Visibility, Debug Matches and Failures

Events are for trusted server-side extensions. When forwarding information to chat or clients, check
that the recipient may see it. Hooks do not expose pending choices, concealed faces, physical tile
UUIDs or hand-sort positions.

`GameEventContext.tableLocation` is copied at projection time. A missing location is reported but does
not suppress the event: the property is `null`. Check it before interacting with a physical table.
Use the loader's context adapter only while the source session is valid.

Development debug exclusions suppress subsequent notifications, so a listener may receive a start
without an end. Existing valid notifications are not retroactively revoked. Listener failures follow
the extension failure policy and do not cancel matches or block other listeners. Delivery does not
promise durable or exactly-once side effects across crashes.

Listener failure diagnostics identify the actual event ID for individual notifications and the batch
ID for transaction batch notifications, together with the listener, match and venue.

## Built-in Achievements and Statistics

Generic achievements consume the same committed event batch delivered to extensions. Outcomes from
the transaction are combined per human player before one grant updates the seven built-in statistics
and evaluates advancement conditions. Statistics are incremented before conditions read their values.
Use `ROUND_COMPLETED`, rather than draw settlement, to count a completed exhaustive draw.

Rule-specific achievement resolvers receive that same batch through
`GameAchievementResolver.resolve(batch)`. Register them through the platform adapter's extension
entrypoint; for example, the Minecraft adapter provides
`MinecraftMahjongExtension.registerGameAchievementResolvers`. The registry selects the batch's rule.
Resolvers return achievement IDs and must not query a game repository, recalculate payments, send
messages or grant outcomes themselves. Riichi uses public scoring fields and rule-provided state
markers, including riichi and ippatsu on natural yakuman wins.

Generic and rule-specific outcomes are combined before granting. A rule-specific resolver failure
suppresses all built-in achievements for that transaction while public delivery continues. A marker
or public round-context projection failure also suppresses the transaction's built-in achievements,
so missing data cannot silently produce partial awards. Core events still reach external listeners.
An absent optional marker resolver is not a projection failure. There is no facts-based achievement
bridge or separate private delivery for transactions without public events.

AI status comes from the batch's player snapshot, including after loading a match without a new start
event. Grants check the source session and look up online players when executed. Offline players are
skipped without retaining work for login. Debug exclusions apply at submission; they do not revoke
work already accepted before the exclusion.

## Compatibility

MahjongCraft creates event data; consumers do not construct or implement it. Collections are detached,
unmodifiable snapshots. Existing listener methods and properties remain available when new events
or read-only properties are added.

`MatchEvent` is sealed. Adding a subtype can require another branch when recompiling an exhaustive
`when`. ABI signature checks alone do not establish behavioral or source compatibility.
