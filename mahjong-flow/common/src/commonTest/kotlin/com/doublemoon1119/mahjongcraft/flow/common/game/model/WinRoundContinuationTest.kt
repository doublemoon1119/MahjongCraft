package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** [WinRoundDirective.ContinueRound.applyTo] 的驗證與套用行為測試。 */
class WinRoundContinuationTest {
    private val fourPlayers = List(4) { FakeMahjongPlayerFactory.create() }

    /** 正常套用時應把新標記的玩家併入 `finishedPlayerIds`，並將回合交給 `nextPlayerId`。 */
    @Test
    fun `applyTo marks newly finished players and advances turn to nextPlayerId`() {
        val state = FakeTableStateFactory.create(players = fourPlayers, currentPlayerIndex = 0)
        val directive = WinRoundDirective.ContinueRound(
            newlyFinishedPlayerIds = setOf(fourPlayers[0].id),
            nextPlayerId = fourPlayers[1].id,
            presentationMode = ContinuingWinPresentationMode.FULL,
        )

        val updated = directive.applyTo(state)

        assertEquals(setOf(fourPlayers[0].id), updated.finishedPlayerIds)
        assertEquals(1, updated.currentPlayerIndex)
    }

    /** 已經標記過 finished 的玩家再次出現在 `newlyFinishedPlayerIds` 應拋出例外。 */
    @Test
    fun `applyTo rejects a player already finished`() {
        val state = FakeTableStateFactory.create(
            players = fourPlayers,
            currentPlayerIndex = 1,
            finishedPlayerIds = setOf(fourPlayers[0].id),
        )
        val directive = WinRoundDirective.ContinueRound(
            newlyFinishedPlayerIds = setOf(fourPlayers[0].id),
            nextPlayerId = fourPlayers[1].id,
            presentationMode = ContinuingWinPresentationMode.NONE,
        )

        assertFailsWith<IllegalArgumentException> { directive.applyTo(state) }
    }

    /** `newlyFinishedPlayerIds` 內含不屬於本桌的玩家應拋出例外。 */
    @Test
    fun `applyTo rejects a player not on the table`() {
        val state = FakeTableStateFactory.create(players = fourPlayers, currentPlayerIndex = 0)
        val directive = WinRoundDirective.ContinueRound(
            newlyFinishedPlayerIds = setOf(Uuid.random()),
            nextPlayerId = fourPlayers[1].id,
            presentationMode = ContinuingWinPresentationMode.NONE,
        )

        assertFailsWith<IllegalArgumentException> { directive.applyTo(state) }
    }

    /** 套用後若導致所有玩家皆 finished，應拋出例外——這種終止條件 resolver 應改回傳 EndRound。 */
    @Test
    fun `applyTo rejects marking every player as finished`() {
        val state = FakeTableStateFactory.create(
            players = fourPlayers,
            currentPlayerIndex = 0,
            finishedPlayerIds = setOf(fourPlayers[1].id, fourPlayers[2].id, fourPlayers[3].id),
        )
        val directive = WinRoundDirective.ContinueRound(
            newlyFinishedPlayerIds = setOf(fourPlayers[0].id),
            nextPlayerId = fourPlayers[1].id,
            presentationMode = ContinuingWinPresentationMode.NONE,
        )

        assertFailsWith<IllegalArgumentException> { directive.applyTo(state) }
    }

    /** `nextPlayerId` 若套用後仍是 finished（例如剛好是這次新標記的玩家）應拋出例外。 */
    @Test
    fun `applyTo rejects a nextPlayerId that would itself be finished`() {
        val state = FakeTableStateFactory.create(players = fourPlayers, currentPlayerIndex = 0)
        val directive = WinRoundDirective.ContinueRound(
            newlyFinishedPlayerIds = setOf(fourPlayers[1].id),
            nextPlayerId = fourPlayers[1].id,
            presentationMode = ContinuingWinPresentationMode.NONE,
        )

        assertFailsWith<IllegalArgumentException> { directive.applyTo(state) }
    }

    /** `nextPlayerId` 若不屬於本桌應拋出例外。 */
    @Test
    fun `applyTo rejects a nextPlayerId not on the table`() {
        val state = FakeTableStateFactory.create(players = fourPlayers, currentPlayerIndex = 0)
        val directive = WinRoundDirective.ContinueRound(
            newlyFinishedPlayerIds = setOf(fourPlayers[0].id),
            nextPlayerId = Uuid.random(),
            presentationMode = ContinuingWinPresentationMode.NONE,
        )

        assertFailsWith<IllegalArgumentException> { directive.applyTo(state) }
    }
}
