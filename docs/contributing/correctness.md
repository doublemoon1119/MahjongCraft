# Correctness Principles

These principles apply to every change. They describe failures that recur when code is written only against
the bundled rules or the bundled client. The [review checklist](review-checklist.md) turns them into
questions to ask during review.

Documentation is only one line of defence. When a principle can be checked automatically, add a test or a
build check for it.

## Client input

- The server validates every value a client sends: identity, membership, permissions, the current turn or
  decision window, ranges, enumerated choices, lengths and request rates. Limits enforced by a screen are not
  validation, because a modified client can send anything the protocol accepts.
- Commands typed by a player are client input too.
- Every client-to-server channel has a test that sends an invalid request and checks that it is rejected
  without changing any state. Do not register a channel that no client uses.

## Visibility

- Before sending data, decide who receives it and whether each recipient may know it. Decide visibility in
  one place, where per-viewer data is built, instead of leaving fields out of individual messages.
- Hidden information includes concealed tiles and their order, upcoming wall tiles, private rule state, and
  choices that are not resolved yet. Broadcast a choice only after it takes effect; a choice that is
  superseded or rejected is never broadcast.
- Visibility covers every path: snapshots and events, world objects synced to nearby clients together with
  their positions and stable identities, spectators, reconnection, AI decision contexts, event listeners
  and history.
- Test visibility by building two situations whose hidden information differs while everything public is the
  same, then checking that an observer receives the same information in both across a sequence of actions.
  Values that are expected to differ, such as timestamps, may be ignored. Random IDs may be normalized, but
  only in a way that keeps their relationships: whether two messages refer to the same tile, when an ID
  changes, and which position it is linked to, because a stable ID can itself reveal hidden information. This
  is a regression guard, not proof that nothing leaks.

## Progress and failure

- A match never waits forever. Every state that waits for input has a way forward: a player decision, a
  timeout, an automatic fallback, or an explicit end.
- Automatic fallbacks are chosen from the actions that are legal at that moment and respect every rule
  constraint, such as a required discard.
- Never invent a result to keep a match going. When a rule offers an outcome it cannot settle, or the match
  cannot continue safely, end it with an explicit aborted result and a diagnostic. Do not treat the outcome
  as a pass, a draw or a zero score, and do not retry forever.
- Ending a match after a failure never ends the authoritative state twice, even when an earlier write
  already succeeded. Cleanup of players, timers, AI work and the table is safe to run again. Events and
  history follow their own delivery contracts; this principle does not promise delivery guarantees beyond
  them, for example across crashes, restarts or third-party side effects.

## Contracts

- A contract states what its consumers rely on. When KDoc says a member is optional, may return null, or has a
  safe default, a test exercises that case through the real consumer and shows that the flow still works. When
  a consumer needs more than the KDoc promises, change the contract or the consumer; do not leave
  them disagreeing.
- Document purity, call counts, ordering and threading whenever a consumer depends on them.
- Offering an option and accepting it use the same capability check, so a player is never offered something
  the rules cannot accept. Acceptance still validates against the authoritative state at submission, because
  an offered option can expire through a timeout, another player's action or a change in the game. An expired
  choice is rejected safely and never leaves a flow waiting.

## Identifiers

- Use namespaced IDs whole. Do not build translation keys, asset paths, cache keys or `when` branches from the
  path alone, because two namespaces may use the same path.
- Registries validate namespaces and reject duplicates at registration.

## Extension points

- Design and review an extension point against the variety of real rules, not only the bundled ones: player
  counts, dealer rotation, claim priority, bonus tiles, tile exchanges, dice openings and scoring. That no
  bundled rule needs a capability yet is not a reason to leave it out.
- A third-party extension can do through public API whatever bundled content does, without copying
  internal code.
- When an extension point cannot express a known practice, record the gap and its consequences instead of
  documenting around it.

## Testability

- Keep decisions out of code that holds platform-native objects. Compute decisions from plain data such as
  IDs, positions and snapshots; the platform layer only reads native objects, calls the decision and applies
  its result. Test the decision with fakes instead of mocking platform classes.
- Every fix ships with a regression test for the behaviour it fixes.
