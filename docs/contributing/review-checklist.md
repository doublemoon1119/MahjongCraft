# Review Checklist

Use this checklist when reviewing a change, in addition to the conventions in the other guides. Each section
turns one of the [correctness principles](correctness.md) into questions, followed by examples of the kind of
failure the questions are meant to catch. Not every question applies to every change; skip the sections the
change does not touch, but do not skip a section only because the change is small.

A finding should name the concrete scenario: who does what, and what goes wrong.

## Client input

- Which values in this change come from a client, including typed commands?
- For each value, does the server check identity, membership and permission, the current turn or decision
  window, ranges and enumerated choices, and lengths?
- Is any limit enforced only by a screen, an editor or a suggestion list?
- Can a request arrive at a moment the screen would never send it, such as during another player's
  reaction window?
- Is the request rate limited per player, with a global limit on work where one is needed? Are rejection
  replies and log messages throttled as well, so rejected requests cannot flood players or the log?
- Is there a test that sends an invalid request and checks that nothing changes?
- Does the change add a channel or handler that no client uses?

Examples of failures:

- A handler that forwards raw commands to the game flow skips the turn-window checks of the normal entry
  points, so a client can draw while others can still claim the discard.
- Room settings that only the room screen range-checks let a client set values that overflow scores or make
  the turn timer effectively infinite.
- A strategy key stored without checking that it is registered lets a blank key stop the table from ever
  starting a game.

## Visibility

- Who receives each piece of data this change sends: the actor, every seated player, spectators, AI
  strategies, event listeners, history viewers?
- May each of them know it at that moment?
- Does anything reveal hidden information indirectly, such as the order or position of concealed tiles, the
  identity of a tile that can be tracked, or the fact that a player had an option?
- Are choices broadcast before they take effect?
- Does visibility hold for reconnection, spectators and history as well as live clients?
- Is there a test comparing two situations that differ only in hidden information? If it normalizes random
  IDs, does it keep which messages refer to the same tile instead of dropping the IDs?

Examples of failures:

- Broadcasting each reaction response before the window closes, including passes and calls that never take
  effect, reveals who could call or win and which tiles they hold.
- Sending or placing opponents' concealed tiles in face order lets every discard narrow down what the rest of
  the hand can be.
- Removing private rule state from network messages alone still leaves it visible to server-side
  AI strategies.

## Progress and failure

- For every state this change can wait in, what moves the match forward, and what happens if that
  never arrives?
- If an automatic decision is rejected, what happens next? Is the fallback legal under every rule constraint?
- Can a rule return a value that the flow cannot act on? Does the flow then stall, invent a result, or end
  the match explicitly?
- If a match is ended after a failure, can the authoritative state be ended twice? Is cleanup of players,
  timers, AI work and the table safe to run again? Do events and history follow their own delivery contracts?

Examples of failures:

- Deciding whether the dealer repeats with one rule's conventions, then rejecting a rule whose progression
  policy decides differently, retries the same rejected step forever.
- A settlement that the KDoc allows to be null, but that the flow cannot act on, leaves the match waiting on
  an empty wall.
- A fallback that always discards the drawn tile is itself rejected when the rule requires a
  different discard.

## Contracts and documentation

- Does each KDoc statement that consumers rely on match what the consumers do?
- For every "optional", "may return null" or default value, is there a test that exercises it through the
  real consumer?
- Does a consumer rely on purity, call count, ordering or threading that the contract does not state?
- Do offering an option and accepting it use the same capability check?
- Is a submitted choice still validated against the authoritative state? Is an option that expired after it
  was offered, through a timeout, another player's action or a change in the game, rejected safely without
  leaving the flow waiting?
- Do layers describe the same contract the same way?
- Does the change make possible something other code relied on never happening, such as a value changing in
  the middle of a round or more than one item where there was always one? Which consumers relied on it?
- Does a consumer read a cached copy of derived state? Who updates the copy, and is the consumer correct no
  matter which of the two runs first?

Examples of failures:

- A wall layout documented as optional in one layer, while the platform cannot show any tiles without it.
- A settlement computed twice, once for scoring and once for presentation, without the contract requiring
  both results to be the same.
- A reaction that a rule can offer but the flow then treats as a pass.
- An automatic follow-up that asks only about the last tile of a replacement draw, after replacement draws
  were allowed to bring in several tiles.
- A hand laid out with a corner width cached before the rule state that widened the corner was presented.

## Identifiers

- Is every namespaced ID used whole, including in translation keys, asset paths, caches and `when` branches?
- Does every registry that accepts IDs validate their namespace and reject duplicates?
- Could two extensions, or an extension and bundled content, end up with the same key?

Examples of failures:

- Building choice translation keys from the path only makes `example:disabled` and `mahjongcraft:disabled`
  share one translation.
- Matching status IDs on their path only makes an extension status with the same path show the bundled label.

## Extension points

- Can a third-party rule do this through public API, without copying internal code?
- Does the design hold for real variants beyond the bundled rules, such as other player counts, dealer
  rotation, claim priority, bonus tiles, tile exchanges and dice openings?
- Does a capability exist only because a bundled rule needed it, or is it described in rule-neutral terms?
- Is there a known practice the extension point still cannot express, and is that gap recorded?

Examples of failures:

- A decoder contract that hands extensions raw stored JSON, while bundled rules use decoders that only the
  persistence module can provide.
- An opening contract that supports a single roll by the dealer only, so rules that roll more than once
  cannot be expressed.

## Testability

- Is the decision logic separate from code that holds platform-native objects?
- Does the fix come with a regression test that fails without it?
- If a path is left without an automated test, is the reason stated and is the untested part as small
  as possible?
