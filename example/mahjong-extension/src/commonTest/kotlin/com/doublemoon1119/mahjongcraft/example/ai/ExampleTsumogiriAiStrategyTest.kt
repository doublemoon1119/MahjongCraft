package com.doublemoon1119.mahjongcraft.example.ai

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
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
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** [ExampleTsumogiriAiStrategy] 的最小行為測試。 */
class ExampleTsumogiriAiStrategyTest {
    /** 測試玩家固定使用的識別碼。 */
    private val selfId = Uuid.random()

    /** 受測的摸切 AI 策略。 */
    private val strategy = ExampleTsumogiriAiStrategy()

    /**
     * 建立只有測試玩家可見的 AI 決策情境。
     *
     * @param hand 測試玩家的手牌。
     * @param phase AI 所處的決策階段。
     * @param legalActions 呼叫端提供的合法動作。
     * @param forcedDiscardTileId 規則指定的捨牌識別碼。
     * @return 可供策略使用的決策情境。
     */
    private fun context(
        hand: Hand,
        phase: AiDecisionPhase = AiDecisionPhase.OwnTurn,
        legalActions: List<GameAction> = emptyList(),
        forcedDiscardTileId: Uuid? = null,
    ): AiDecisionContext {
        val player = FakeMahjongPlayerFactory.create(id = selfId, hand = hand)
        val table = FakeTableStateFactory.create(players = listOf(player))
        return AiDecisionContext(
            snapshot = table.toSnapshot(setOf(selfId), setAsideTiles = { emptyList() }),
            selfId = selfId,
            phase = phase,
            legalActions = legalActions,
            forcedDiscardTileId = forcedDiscardTileId,
        )
    }

    /** 驗證自己回合有自摸資格時優先回傳自摸命令。 */
    @Test
    fun `own turn chooses tsumo before discarding`() = runTest {
        val command = strategy.decideGameCommand(context(Hand(), legalActions = listOf(GameAction.Tsumo)))

        assertEquals(GameCommand.Tsumo, command)
    }

    /** 驗證規則指定捨牌時優先遵守指定牌張。 */
    @Test
    fun `own turn obeys forced discard`() = runTest {
        val standing = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val forced = FakeIdentifiedTileFactory.create(Tile.Honor.South)

        val command = strategy.decideGameCommand(
            context(
                hand = Hand(tiles = listOf(standing), lastDrawn = forced),
                forcedDiscardTileId = forced.id,
            ),
        )

        assertEquals(GameCommand.Discard(forced.id), command)
    }

    /** 驗證沒有強制限制時會捨出剛摸入的牌。 */
    @Test
    fun `own turn discards last drawn tile`() = runTest {
        val standing = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.South)

        val command = strategy.decideGameCommand(context(Hand(listOf(standing), lastDrawn = drawn)))

        assertEquals(GameCommand.Discard(drawn.id), command)
    }

    /** 驗證沒有剛摸牌時會依立牌清單的穩定順序捨牌。 */
    @Test
    fun `own turn falls back to first standing tile`() = runTest {
        val first = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val second = FakeIdentifiedTileFactory.create(Tile.Honor.South)

        val command = strategy.decideGameCommand(context(Hand(listOf(first, second))))

        assertEquals(GameCommand.Discard(first.id), command)
    }

    /** 驗證反應階段優先選擇合法過牌。 */
    @Test
    fun `reactions prefer legal pass`() = runTest {
        val command = strategy.decideGameCommand(
            context(
                hand = Hand(),
                phase = AiDecisionPhase.RespondingToDiscard,
                legalActions = listOf(GameAction.Ron(Uuid.random()), GameAction.Pass),
            ),
        )

        assertEquals(GameCommand.RespondToDiscard(GameAction.Pass), command)
    }

    /** 驗證沒有過牌時會選擇清單中的第一個合法反應。 */
    @Test
    fun `reactions choose first legal action when pass is unavailable`() = runTest {
        val action = GameAction.Ron(Uuid.random())
        val command = strategy.decideGameCommand(
            context(hand = Hand(), phase = AiDecisionPhase.RespondingToRobbing, legalActions = listOf(action)),
        )

        assertEquals(GameCommand.RespondToRobbing(action), command)
    }

    /** 驗證合法反應清單為空時明確報錯。 */
    @Test
    fun `reactions reject an empty legal action list`() = runTest {
        assertFailsWith<IllegalStateException> {
            strategy.decideGameCommand(context(hand = Hand(), phase = AiDecisionPhase.RespondingToDiscard))
        }
    }
}
