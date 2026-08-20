package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeGameLength
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeScoreConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 測試用的模擬動態規則狀態實作。
 */
private class MockDynamicState : DynamicRuleState

/**
 * 針對 [TableState] 的基礎通用邏輯進行單元測試。
 *
 * 驗證包含玩家分數初始化、下家邏輯判定以及動態狀態持有等核心功能。
 */
class TableStateTest {

    /**
     * 驗證當 [TableState] 初始化時，是否能正確根據規則配置設定所有玩家的初始分數。
     */
    @Test
    fun `test player score initialization from config`() {
        val initialScoreValue = 30000
        // 透過傳入具備特定分數的 FakeScoreConfig 來達成測試需求
        val config = FakeMahjongRuleConfig(
            scoreConfig = FakeScoreConfig(initialScore = initialScoreValue),
        )
        val players = listOf(
            FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST),
            FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH),
        )

        val tableState = FakeTableStateFactory.create(
            players = players,
            config = config,
        )
        val initializedTableState = tableState.init()

        for (player in initializedTableState.players) {
            assertEquals(
                initialScoreValue,
                player.score,
                "Player score should be initialized to the value defined in config.",
            )
        }
    }

    /**
     * 驗證下家獲取邏輯是否正確，並確保支援動態人數（如三人麻將）。
     */
    @Test
    fun `test next player logic supports dynamic player count`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)

        val table = FakeTableStateFactory.create(
            players = listOf(p1, p2, p3),
        )

        assertEquals(3, table.playerCount, "Table should correctly reflect the number of joined players.")
        assertEquals(p2, table.getNextPlayer(p1), "The next player of P1 (East) should be P2 (South).")
        assertEquals(
            p1,
            table.getNextPlayer(p3),
            "The next player of the last person (P3) should wrap back to the first person (P1).",
        )
    }

    /**
     * 驗證 TableState 能正確持有並透過介面存取動態規則狀態。
     */
    @Test
    fun `test dynamic rule state assignment`() {
        val dynamicState = MockDynamicState()
        val table = FakeTableStateFactory.create(
            players = emptyList(),
            dynamicRuleState = dynamicState,
        )

        assertEquals(dynamicState, table.dynamicRuleState, "TableState should hold the assigned dynamic rule state.")
    }

    /**
     * 驗證 [TableState.relativeDirectionOf] 能依座位順序正確判斷上家/對家/下家/自己。
     */
    @Test
    fun `test relativeDirectionOf resolves directions based on seating order`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)

        val table = FakeTableStateFactory.create(players = listOf(p1, p2, p3, p4))

        assertEquals(
            RelativeDirection.Self,
            table.relativeDirectionOf(p1.id, p1.id),
            "A player relative to themselves should be Self.",
        )
        assertEquals(
            RelativeDirection.Right,
            table.relativeDirectionOf(p1.id, p2.id),
            "P2 comes right after P1 in seating order, so P2 is P1's shimocha (Right).",
        )
        assertEquals(
            RelativeDirection.Across,
            table.relativeDirectionOf(p1.id, p3.id),
            "P3 sits directly across from P1.",
        )
        assertEquals(
            RelativeDirection.Left,
            table.relativeDirectionOf(p1.id, p4.id),
            "P4 comes right before P1 in seating order, so P4 is P1's kamicha (Left) — the only valid Chi source.",
        )
    }

    /**
     * 驗證三人桌（無對家概念）時，[TableState.relativeDirectionOf] 仍能正確判斷上家/下家，
     * 不會誤判為對家（三人桌中差值 2 同時等於 playerCount - 1，必須優先判定為上家）。
     */
    @Test
    fun `test relativeDirectionOf on a three-player table has no across`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)

        val table = FakeTableStateFactory.create(players = listOf(p1, p2, p3))

        assertEquals(RelativeDirection.Right, table.relativeDirectionOf(p1.id, p2.id))
        assertEquals(
            RelativeDirection.Left,
            table.relativeDirectionOf(p1.id, p3.id),
            "In a three-player table, the only other seat besides your shimocha is your kamicha.",
        )
    }

    /**
     * 驗證 [TableState.nearestPlayerInTurnOrder] 在候選人本身就是下家時，直接回傳該候選人。
     */
    @Test
    fun `test nearestPlayerInTurnOrder returns the immediate next player when eligible`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)

        val table = FakeTableStateFactory.create(players = listOf(p1, p2, p3, p4))

        assertEquals(p2.id, table.nearestPlayerInTurnOrder(p1.id, setOf(p2.id, p3.id, p4.id)))
    }

    /**
     * 驗證 [TableState.nearestPlayerInTurnOrder] 會跳過不在候選名單中的下家，找出順位次接近的候選人。
     */
    @Test
    fun `test nearestPlayerInTurnOrder skips ineligible players in turn order`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)

        val table = FakeTableStateFactory.create(players = listOf(p1, p2, p3, p4))

        assertEquals(
            p3.id,
            table.nearestPlayerInTurnOrder(p1.id, setOf(p3.id, p4.id)),
            "P2 is not a candidate, so the nearest eligible candidate should be P3.",
        )
    }

    /**
     * 驗證 [TableState.nearestPlayerInTurnOrder] 在三人桌時仍能正確依回合順序 wraparound 判斷。
     */
    @Test
    fun `test nearestPlayerInTurnOrder wraps around on a three-player table`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)

        val table = FakeTableStateFactory.create(players = listOf(p1, p2, p3))

        assertEquals(
            p1.id,
            table.nearestPlayerInTurnOrder(p3.id, setOf(p1.id, p2.id)),
            "From P3, the turn order wraps back around to P1 first.",
        )
    }

    /**
     * 驗證 [TableState.advanceRound] 連莊時只推進本場數，其餘欄位（局數、場風、各玩家方位）
     * 皆維持不變；未打到 `totalRounds` 前，連莊不會讓整場對局結束。
     */
    @Test
    fun `test advanceRound with dealer repeats only increments combo count`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val table = FakeTableStateFactory.create(
            players = listOf(p1, p2, p3, p4),
            roundNumber = 2,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            config = FakeMahjongRuleConfig(gameLength = FakeGameLength(totalRounds = 4)),
        )

        val result = table.advanceRound(dealerRepeats = true)

        assertEquals(1, result.comboCount)
        assertEquals(2, result.roundNumber, "Round number should not advance on a repeat.")
        assertEquals(Wind.EAST, result.prevalentWind)
        assertEquals(listOf(p1, p2, p3, p4), result.players, "No player's currentWind should change on a repeat.")
        assertFalse(result.isMatchOver, "The match should still be ongoing before the final round is reached.")
    }

    /**
     * 驗證 [TableState.advanceRound] 連莊發生在已經是最後一局（`roundNumber >= totalRounds`，例如
     * 一局戰打完唯一一局、莊家自摸或榮和）時，即使是連莊分支，整場對局也應該結束——先前版本這個分支
     * 固定回傳 `isMatchOver = false`，導致設定一局戰時只要贏家剛好是莊家，對局就會無限連莊下去。
     */
    @Test
    fun `test advanceRound with dealer repeats ends the match when already at the final round`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val table = FakeTableStateFactory.create(
            players = listOf(p1, p2, p3, p4),
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            config = FakeMahjongRuleConfig(gameLength = FakeGameLength(totalRounds = 1)),
        )

        val result = table.advanceRound(dealerRepeats = true)

        assertTrue(result.isMatchOver, "OneGame (totalRounds = 1) should end the match once the only round's dealer repeats.")
    }

    /**
     * 驗證 [TableState.advanceRound] 過莊時（同一場風內）正確輪動各玩家的 `currentWind`、
     * 本場數歸零、局數推進，且場風維持不變。
     */
    @Test
    fun `test advanceRound without repeat rotates dealer within the same prevalent wind`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val table = FakeTableStateFactory.create(
            players = listOf(p1, p2, p3, p4),
            roundNumber = 1,
            comboCount = 2,
            prevalentWind = Wind.EAST,
            config = FakeMahjongRuleConfig(gameLength = FakeGameLength(totalRounds = 8)),
        )

        val result = table.advanceRound(dealerRepeats = false)

        assertEquals(0, result.comboCount)
        assertEquals(2, result.roundNumber)
        assertEquals(Wind.EAST, result.prevalentWind, "Still within the East wind after just one rotation.")
        assertFalse(result.isMatchOver)
        assertEquals(Wind.EAST, result.players.first { it.id == p2.id }.currentWind, "P2 (previously South) becomes the new dealer.")
        assertEquals(Wind.SOUTH, result.players.first { it.id == p3.id }.currentWind)
        assertEquals(Wind.WEST, result.players.first { it.id == p4.id }.currentWind)
        assertEquals(Wind.NORTH, result.players.first { it.id == p1.id }.currentWind, "The old dealer rotates all the way to North.")
    }

    /**
     * 驗證 [TableState.advanceRound] 過莊且輪完一輪莊家後，場風正確從東進位到南。
     */
    @Test
    fun `test advanceRound crosses into the next prevalent wind after every seat has dealt`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH).copy(currentWind = Wind.EAST)
        // p4 是本局莊家（第 4 局，東風的最後一位莊家）
        val table = FakeTableStateFactory.create(
            players = listOf(
                p1.copy(currentWind = Wind.SOUTH),
                p2.copy(currentWind = Wind.WEST),
                p3.copy(currentWind = Wind.NORTH),
                p4,
            ),
            roundNumber = 4,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            config = FakeMahjongRuleConfig(gameLength = FakeGameLength(totalRounds = 8)),
        )

        val result = table.advanceRound(dealerRepeats = false)

        assertEquals(5, result.roundNumber)
        assertEquals(Wind.SOUTH, result.prevalentWind, "Every seat has now dealt once, so the prevalent wind advances to South.")
        assertFalse(result.isMatchOver)
    }

    /**
     * 驗證 [TableState.advanceRound] 過莊且局數已達 `GameLength.totalRounds` 上限時，
     * 正確標記整場對局已結束。
     */
    @Test
    fun `test advanceRound flags match over when exceeding total rounds`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val table = FakeTableStateFactory.create(
            players = listOf(p1, p2, p3, p4),
            roundNumber = 4,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            config = FakeMahjongRuleConfig(gameLength = FakeGameLength(totalRounds = 4)),
        )

        val result = table.advanceRound(dealerRepeats = false)

        assertEquals(5, result.roundNumber)
        assertTrue(result.isMatchOver)
    }

    /**
     * 驗證 [TableState.advanceRound] 在三人桌時，莊家輪動能正確 wraparound（從最後一位輪回第一位）。
     */
    @Test
    fun `test advanceRound wraps dealer rotation on a three-player table`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST).copy(currentWind = Wind.EAST)
        // p3 是本局莊家；p1、p2 的 currentWind 需要跟著調整成非莊家的方位，避免同時有兩位「東」
        val table = FakeTableStateFactory.create(
            players = listOf(p1.copy(currentWind = Wind.SOUTH), p2.copy(currentWind = Wind.WEST), p3),
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            config = FakeMahjongRuleConfig(gameLength = FakeGameLength(totalRounds = 8)),
        )

        val result = table.advanceRound(dealerRepeats = false)

        assertEquals(
            Wind.EAST,
            result.players.first { it.id == p1.id }.currentWind,
            "The dealer wraps back around to P1 after P3.",
        )
    }
}
