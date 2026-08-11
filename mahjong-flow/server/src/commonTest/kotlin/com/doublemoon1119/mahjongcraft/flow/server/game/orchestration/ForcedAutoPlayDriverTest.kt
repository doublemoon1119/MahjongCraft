package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [ForcedAutoPlayDriver] 的固定自動命令測試。 */
class ForcedAutoPlayDriverTest {
    /** 驗證捨牌反應視窗中的強制自動操作玩家固定選擇跳過。 */
    @Test
    fun `test forced discard reaction passes`() = runTest {
        val repository = FakeGameRepository()
        val playerId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
                pendingReaction = PendingReaction(
                    discarderId = Uuid.random(),
                    tileId = Uuid.random(),
                    eligiblePlayerIds = setOf(playerId),
                ),
            ),
            flowConfig = GameFlowConfig(),
            forcedAutoPlayPlayerIds = setOf(playerId),
        )
        repository.setGame(game)

        assertEquals(
            playerId to GameCommand.RespondToDiscard(GameAction.Pass),
            ForcedAutoPlayDriver(repository).resolveNextAction(game.id),
        )
    }

    /** 驗證自己回合固定捨出剛摸入的牌。 */
    @Test
    fun `test forced own turn discards last drawn tile`() = runTest {
        val repository = FakeGameRepository()
        val playerId = Uuid.random()
        val lastDrawn = IdentifiedTile(Uuid.random(), Tile.Honor.Red)
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    FakeMahjongPlayerFactory.create(
                        id = playerId,
                        hand = Hand(lastDrawn = lastDrawn),
                    ),
                ),
            ),
            flowConfig = GameFlowConfig(),
            forcedAutoPlayPlayerIds = setOf(playerId),
        )
        repository.setGame(game)

        assertEquals(
            playerId to GameCommand.Discard(lastDrawn.id),
            ForcedAutoPlayDriver(repository).resolveNextAction(game.id),
        )
    }
}
