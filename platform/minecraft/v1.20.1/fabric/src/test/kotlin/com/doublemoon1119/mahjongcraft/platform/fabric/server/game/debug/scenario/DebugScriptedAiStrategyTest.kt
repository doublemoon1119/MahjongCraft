package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 驗證 debug 情境腳本 AI 的固定行為。 */
class DebugScriptedAiStrategyTest {
    private val selfId = Uuid.random()
    private val standingTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
    private val drawnTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)

    /** 他家捨牌與宣告槓時，即使可以榮和也一律跳過。 */
    @Test
    fun `always passes reactions`() = runTest {
        val strategy = DebugScriptedAiStrategy(declaresKanFirst = true)
        val ron = GameAction.Ron(Uuid.random())

        assertEquals(
            GameCommand.RespondToDiscard(GameAction.Pass),
            strategy.decideGameCommand(context(AiDecisionPhase.RespondingToDiscard, listOf(ron, GameAction.Pass))),
        )
        assertEquals(
            GameCommand.RespondToKan(GameAction.Pass),
            strategy.decideGameCommand(context(AiDecisionPhase.RespondingToKan, listOf(ron, GameAction.Pass))),
        )
    }

    /** 自己回合不和牌、不槓，打出剛摸到的牌。 */
    @Test
    fun `tsumogiri strategy discards the drawn tile even when it could win or kan`() = runTest {
        val strategy = DebugScriptedAiStrategy(declaresKanFirst = false)
        val legalActions = listOf(GameAction.Tsumo, GameAction.Kan(GameAction.KanType.CLOSED_KAN, drawnTile.id, emptyList()))

        assertEquals(GameCommand.Discard(drawnTile.id), strategy.decideGameCommand(context(AiDecisionPhase.OwnTurn, legalActions)))
    }

    /** 能槓時先宣告槓。 */
    @Test
    fun `kan first strategy declares an available kan`() = runTest {
        val strategy = DebugScriptedAiStrategy(declaresKanFirst = true)
        val kan = GameAction.Kan(GameAction.KanType.ADDED_KAN, drawnTile.id, emptyList())

        assertEquals(
            GameCommand.Kan(GameAction.KanType.ADDED_KAN, drawnTile.id),
            strategy.decideGameCommand(context(AiDecisionPhase.OwnTurn, listOf(GameAction.Tsumo, kan))),
        )
    }

    /** 沒有摸牌時打出第一張立牌。 */
    @Test
    fun `discards the first standing tile without a drawn tile`() = runTest {
        val strategy = DebugScriptedAiStrategy(declaresKanFirst = true)

        assertEquals(
            GameCommand.Discard(standingTile.id),
            strategy.decideGameCommand(context(AiDecisionPhase.OwnTurn, emptyList(), hand = Hand(tiles = listOf(standingTile)))),
        )
    }

    /** 兩個腳本 AI 皆可由 registry 解析。 */
    @Test
    fun `registration exposes both scripted strategies`() {
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = DebugScriptedAiStrategy.TSUMOGIRI_KEY)
        registry.registerDebugScriptedAiStrategies()

        assertEquals(
            setOf(DebugScriptedAiStrategy.TSUMOGIRI_KEY, DebugScriptedAiStrategy.KAN_FIRST_KEY),
            registry.getAllStrategyKeys(),
        )
        assertIs<DebugScriptedAiStrategy>(registry.resolve(DebugScriptedAiStrategy.KAN_FIRST_KEY))
    }

    private fun context(
        phase: AiDecisionPhase,
        legalActions: List<GameAction>,
        hand: Hand = Hand(tiles = listOf(standingTile), lastDrawn = drawnTile),
    ): AiDecisionContext {
        val table = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create(id = selfId, hand = hand)))
        return AiDecisionContext(
            snapshot = table.toSnapshot(setOf(selfId)),
            selfId = selfId,
            phase = phase,
            legalActions = legalActions,
        )
    }
}
