package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [ForcedAutoPlayDriver] 的固定自動命令測試。 */
class ForcedAutoPlayDriverTest {
    /** 讓測試經過正式日麻的合法動作查詢。 */
    private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }

    /** 建立與正式代操作共用合法命令解析器的驅動器。 */
    private fun driver(repository: FakeGameRepository): ForcedAutoPlayDriver = ForcedAutoPlayDriver(
        repository,
        AutomaticGameCommandResolver(moduleRegistry),
    )

    /** 驗證捨牌反應視窗中的強制自動操作玩家固定選擇跳過。 */
    @Test
    fun `test forced discard reaction passes`() = runTest {
        val repository = FakeGameRepository()
        val playerId = Uuid.random()
        val discarderId = Uuid.random()
        val discardedTile = IdentifiedTile(Uuid.random(), Tile.Honor.Red)
        val game = Game(
            tableState = FakeTableStateFactory.create(
                config = RiichiRuleConfig(),
                players = listOf(
                    FakeMahjongPlayerFactory.create(
                        id = discarderId,
                        initialSeat = Wind.EAST,
                        discardPile = FakeDiscardPile().discardTile(discardedTile),
                    ),
                    FakeMahjongPlayerFactory.create(
                        id = playerId,
                        initialSeat = Wind.SOUTH,
                        hand = Hand(tiles = List(2) { IdentifiedTile(Uuid.random(), Tile.Honor.Red) }),
                        playerRuleState = RiichiPlayerState(),
                    ),
                ),
                pendingReaction = PendingReaction(
                    discarderId = discarderId,
                    tileId = discardedTile.id,
                    eligiblePlayerIds = setOf(playerId),
                    ronPlayerIds = setOf(playerId),
                    id = Uuid.random(),
                ),
            ),
            flowConfig = GameFlowConfig(),
            forcedAutoPlayPlayerIds = setOf(playerId),
        )
        repository.setGame(game)

        assertEquals(
            playerId to GameCommand.RespondToDiscard(GameAction.Pass, checkNotNull(game.tableState.pendingReaction).id),
            driver(repository).resolveNextAction(game.id),
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
                config = RiichiRuleConfig(),
                players = listOf(
                    FakeMahjongPlayerFactory.create(
                        id = playerId,
                        hand = Hand(lastDrawn = lastDrawn),
                        playerRuleState = RiichiPlayerState(),
                    ),
                ),
            ),
            flowConfig = GameFlowConfig(),
            forcedAutoPlayPlayerIds = setOf(playerId),
        )
        repository.setGame(game)

        assertEquals(
            playerId to GameCommand.Discard(lastDrawn.id),
            driver(repository).resolveNextAction(game.id),
        )
    }

    /** 驗證開局準備尚未完成時，不會把逾時玩家誤判成一般摸打代操作。 */
    @Test
    fun `test pending round preparation suppresses forced action`() = runTest {
        val repository = FakeGameRepository()
        val playerId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                config = RiichiRuleConfig(),
                players = listOf(
                    FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST),
                ),
            ),
            flowConfig = GameFlowConfig(),
            forcedAutoPlayPlayerIds = setOf(playerId),
            pendingRoundPreparation = PendingRoundPreparation(
                stepId = "test:confirmation",
                stepIndex = 0,
                inputSpecsByPlayerId = mapOf(playerId to RoundPreparationInputSpec.Confirmation),
            ),
        )
        repository.setGame(game)

        assertEquals(null, driver(repository).resolveNextAction(game.id))
    }

    /** 驗證共用代操作解析器對自己回合、反應視窗與旁觀玩家的結果。 */
    @Test
    fun `test fixed command matches the forced driver for each player`() = runTest {
        val reactorId = Uuid.random()
        val currentId = Uuid.random()
        val bystanderId = Uuid.random()
        val lastDrawn = IdentifiedTile(Uuid.random(), Tile.Honor.Red)
        val players = listOf(
            FakeMahjongPlayerFactory.create(
                id = currentId,
                initialSeat = Wind.EAST,
                hand = Hand(lastDrawn = lastDrawn),
                discardPile = FakeDiscardPile().discardTile(
                    IdentifiedTile(Uuid.random(), Tile.Honor.South),
                ),
                playerRuleState = RiichiPlayerState(),
            ),
            FakeMahjongPlayerFactory.create(
                id = reactorId,
                initialSeat = Wind.SOUTH,
                hand = Hand(tiles = List(2) { IdentifiedTile(Uuid.random(), Tile.Honor.South) }),
                playerRuleState = RiichiPlayerState(),
            ),
            FakeMahjongPlayerFactory.create(id = bystanderId, initialSeat = Wind.WEST),
        )
        val ownTurn = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            players = players,
            currentPlayerIndex = 0,
        )
        val discardedTile = players.first().discardPile.entries.single().tile
        val reaction = ownTurn.copy(
            pendingReaction = PendingReaction(
                discarderId = currentId,
                tileId = discardedTile.id,
                eligiblePlayerIds = setOf(reactorId),
                ronPlayerIds = setOf(reactorId),
                id = Uuid.random(),
            ),
        )
        listOf(ownTurn, reaction).forEach { state ->
            players.forEach { player ->
                val repository = FakeGameRepository()
                val game = Game(tableState = state, flowConfig = GameFlowConfig(), forcedAutoPlayPlayerIds = setOf(player.id))
                repository.setGame(game)

                val forced = driver(repository).resolveNextAction(game.id)

                assertEquals(forced?.second, AutomaticGameCommandResolver(moduleRegistry).resolve(state, player.id))
            }
        }
        assertEquals(GameCommand.Discard(lastDrawn.id), AutomaticGameCommandResolver(moduleRegistry).resolve(ownTurn, currentId))
        assertEquals(
            GameCommand.RespondToDiscard(GameAction.Pass, checkNotNull(reaction.pendingReaction).id),
            AutomaticGameCommandResolver(moduleRegistry).resolve(reaction, reactorId),
        )
        assertEquals(null, AutomaticGameCommandResolver(moduleRegistry).resolve(reaction, bystanderId))
    }
}
