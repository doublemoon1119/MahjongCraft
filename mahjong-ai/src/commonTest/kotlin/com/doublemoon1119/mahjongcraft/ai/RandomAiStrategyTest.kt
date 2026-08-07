package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [RandomAiStrategy] 的單元測試類別。
 *
 * 驗證三種 [AiDecisionPhase] 下的行為，以及「立直永遠不被選擇、預設捨牌」這個 Dummy 策略的
 * 既有限制。含機率性行為的分支（是否選擇 Tsumo/Kan/九種九牌）透過多次試驗驗證兩種結果都可能出現，
 * 不依賴特定亂數種子的具體輸出序列。
 */
class RandomAiStrategyTest {

    private val selfId = Uuid.random()

    private fun contextWithHand(
        hand: Hand,
        phase: AiDecisionPhase,
        legalActions: List<GameAction>,
    ): AiDecisionContext {
        val self = FakeMahjongPlayerFactory.create(id = selfId, hand = hand)
        val table = FakeTableStateFactory.create(players = listOf(self))
        return AiDecisionContext(
            snapshot = table.toSnapshot(selfId),
            selfId = selfId,
            phase = phase,
            legalActions = legalActions,
        )
    }

    /**
     * 驗證回應捨牌反應視窗時，回傳的 [GameCommand.RespondToDiscard] 攜帶的動作確實來自
     * [AiDecisionContext.legalActions]。
     */
    @Test
    fun `test responding to discard picks one of the legal actions`() = runTest {
        val strategy = RandomAiStrategy(Random(1))
        val ronAction = GameAction.Ron(Uuid.random())
        val context = contextWithHand(Hand(), AiDecisionPhase.RespondingToDiscard, listOf(ronAction, GameAction.Pass))

        val result = strategy.decide(context)

        assertTrue(result is GameCommand.RespondToDiscard)
        assertTrue(result.action == ronAction || result.action == GameAction.Pass)
    }

    /**
     * 驗證合法動作清單為空時，回應捨牌反應視窗預設回傳 `Pass`（防呆）。
     */
    @Test
    fun `test responding to discard defaults to pass when legal actions is empty`() = runTest {
        val strategy = RandomAiStrategy(Random(1))
        val context = contextWithHand(Hand(), AiDecisionPhase.RespondingToDiscard, emptyList())

        val result = strategy.decide(context)

        assertEquals(GameCommand.RespondToDiscard(GameAction.Pass), result)
    }

    /**
     * 驗證回應搶槓反應視窗時，回傳的 [GameCommand.RespondToChankan] 攜帶的動作確實來自
     * [AiDecisionContext.legalActions]。
     */
    @Test
    fun `test responding to chankan picks one of the legal actions`() = runTest {
        val strategy = RandomAiStrategy(Random(1))
        val ronAction = GameAction.Ron(Uuid.random())
        val context = contextWithHand(Hand(), AiDecisionPhase.RespondingToChankan, listOf(ronAction, GameAction.Pass))

        val result = strategy.decide(context)

        assertTrue(result is GameCommand.RespondToChankan)
        assertTrue(result.action == ronAction || result.action == GameAction.Pass)
    }

    /**
     * 驗證合法動作清單為空時，回應搶槓反應視窗預設回傳 `Pass`（防呆）。
     */
    @Test
    fun `test responding to chankan defaults to pass when legal actions is empty`() = runTest {
        val strategy = RandomAiStrategy(Random(1))
        val context = contextWithHand(Hand(), AiDecisionPhase.RespondingToChankan, emptyList())

        val result = strategy.decide(context)

        assertEquals(GameCommand.RespondToChankan(GameAction.Pass), result)
    }

    /**
     * 驗證自己回合時，若合法動作清單只有 [GameAction.Riichi]/[GameAction.Pass]（過濾後視同沒有
     * 可選的特殊動作），一律捨牌——這個判斷不涉及機率（`actionableOptions.isEmpty()` 短路），
     * 不需要固定亂數種子即可穩定驗證。
     */
    @Test
    fun `test own turn always discards when only riichi and pass are legal`() = runTest {
        val strategy = RandomAiStrategy(Random(1))
        val onlyTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hand = Hand(tiles = listOf(onlyTile))
        val context = contextWithHand(hand, AiDecisionPhase.OwnTurn, listOf(GameAction.Riichi, GameAction.Pass))

        val result = strategy.decide(context)

        assertEquals(GameCommand.Discard(onlyTile.id), result)
    }

    /**
     * 驗證自己回合捨牌時，候選牌同時包含立牌與剛摸到的牌。
     */
    @Test
    fun `test own turn discard picks from standing tiles and last drawn`() = runTest {
        val strategy = RandomAiStrategy(Random(1))
        val standingTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val hand = Hand(tiles = listOf(standingTile), lastDrawn = lastDrawn)
        val context = contextWithHand(hand, AiDecisionPhase.OwnTurn, emptyList())

        val result = strategy.decide(context)

        assertTrue(result is GameCommand.Discard)
        assertTrue(result.tileId == standingTile.id || result.tileId == lastDrawn.id)
    }

    /**
     * 驗證自己回合有自摸資格時，多次試驗下「選擇自摸」與「捨牌」兩種結果皆可能出現——證明
     * `GameAction.Tsumo` 選項確實有機會被選中，不是永遠被忽略。
     */
    @Test
    fun `test own turn can select tsumo when legal across many trials`() = runTest {
        val strategy = RandomAiStrategy(Random(42))
        val tile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hand = Hand(tiles = listOf(tile))
        val results = List(200) {
            val context = contextWithHand(hand, AiDecisionPhase.OwnTurn, listOf(GameAction.Tsumo))
            strategy.decide(context)
        }

        assertTrue(results.any { it == GameCommand.Tsumo }, "Tsumo should be selected at least once across many trials.")
        assertTrue(results.any { it is GameCommand.Discard }, "Discard should still occur at least once across many trials.")
    }

    /**
     * 驗證自己回合有暗槓資格時，多次試驗下會選到正確的 [GameCommand.Kan]（`type`/`tileId` 對應）。
     */
    @Test
    fun `test own turn can select kan when legal across many trials`() = runTest {
        val strategy = RandomAiStrategy(Random(42))
        val tile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hand = Hand(tiles = listOf(tile))
        val kanAction = GameAction.Kan(GameAction.KanType.CLOSED_KAN, tile.id, emptyList())
        val results = List(200) {
            val context = contextWithHand(hand, AiDecisionPhase.OwnTurn, listOf(kanAction))
            strategy.decide(context)
        }

        assertTrue(
            results.any { it == GameCommand.Kan(GameAction.KanType.CLOSED_KAN, tile.id) },
            "Kan should be selected at least once across many trials.",
        )
        assertTrue(results.any { it is GameCommand.Discard }, "Discard should still occur at least once across many trials.")
    }

    /**
     * 驗證自己回合有九種九牌資格時，多次試驗下會選到 [GameCommand.KyuushuKyuuhai]。
     */
    @Test
    fun `test own turn can select kyuushu kyuuhai when legal across many trials`() = runTest {
        val strategy = RandomAiStrategy(Random(42))
        val tile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hand = Hand(tiles = listOf(tile))
        val exhaustiveDrawAction = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai)
        val results = List(200) {
            val context = contextWithHand(hand, AiDecisionPhase.OwnTurn, listOf(exhaustiveDrawAction))
            strategy.decide(context)
        }

        assertTrue(results.any { it == GameCommand.KyuushuKyuuhai }, "KyuushuKyuuhai should be selected at least once across many trials.")
        assertTrue(results.any { it is GameCommand.Discard }, "Discard should still occur at least once across many trials.")
    }

    /**
     * 驗證自己回合即使 [GameAction.Riichi] 出現在合法動作清單裡，也永遠不會被選擇——這個 Dummy
     * 策略刻意略過立直（見 [RandomAiStrategy] 的 KDoc 說明）。
     */
    @Test
    fun `test own turn never selects riichi even when legal`() = runTest {
        val strategy = RandomAiStrategy(Random(42))
        val tile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hand = Hand(tiles = listOf(tile))
        val results = List(200) {
            val context = contextWithHand(hand, AiDecisionPhase.OwnTurn, listOf(GameAction.Riichi, GameAction.Tsumo))
            strategy.decide(context)
        }

        assertTrue(results.none { it is GameCommand.Riichi }, "Riichi should never be selected by this Dummy strategy.")
    }
}
