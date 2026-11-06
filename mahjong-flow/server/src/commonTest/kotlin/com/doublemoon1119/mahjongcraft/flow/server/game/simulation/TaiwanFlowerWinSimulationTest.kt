package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.component.initializeRuleComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBuiltInAiStrategies
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/** 以正式流程驗證內建 AI 遇到特殊花胡時會接受。 */
class TaiwanFlowerWinSimulationTest {
    /** 開局時莊家下家起手七張花、再下家起手一張花：每個內建策略都接受七搶一，只有被搶花者付款，之後流程接著結束整場。 */
    @Test
    fun `every built-in strategy accepts a seven-flower win`() = runTest(timeout = 5.minutes) {
        BUILT_IN_STRATEGIES.forEach { key ->
            val (runtime, dealt) = setUp(key)
            val dealerIndex = dealt.players.indexOfFirst { it.id == dealt.dealerPlayerId }
            val winner = dealt.players[(dealerIndex + 1) % 4]
            val payer = dealt.players[(dealerIndex + 2) % 4]

            val deltas = firstRoundScoreChanges(runtime, key, dealt.withFlowersIn(winner.hand.tiles.take(SEVEN) + payer.hand.tiles.take(1)))

            assertTrue(deltas.getValue(winner.id) > 0, "$key did not accept the seven-flower win: $deltas; abort: ${runtime.abortDiagnostic}")
            assertEquals(-deltas.getValue(winner.id), deltas.getValue(payer.id), "$key: only the robbed player pays")
            assertTrue(deltas.filterKeys { it != winner.id && it != payer.id }.values.all { it == 0 }, "$key: $deltas")
            assertMatchContinued(runtime, dealt.id, key)
        }
    }

    /** 開局時莊家下家起手八張花：每個內建策略都接受八仙過海，由其他三家付款，之後流程接著結束整場。 */
    @Test
    fun `every built-in strategy accepts an eight-flower win`() = runTest(timeout = 5.minutes) {
        BUILT_IN_STRATEGIES.forEach { key ->
            val (runtime, dealt) = setUp(key)
            val dealerIndex = dealt.players.indexOfFirst { it.id == dealt.dealerPlayerId }
            val winner = dealt.players[(dealerIndex + 1) % 4]

            val deltas = firstRoundScoreChanges(runtime, key, dealt.withFlowersIn(winner.hand.tiles.take(EIGHT)))

            assertTrue(deltas.getValue(winner.id) > 0, "$key did not accept the eight-flower win: $deltas; abort: ${runtime.abortDiagnostic}")
            assertTrue(deltas.filterKeys { it != winner.id }.values.all { it < 0 }, "$key: every other player pays: $deltas")
            assertEquals(0, deltas.values.sum(), "$key: $deltas")
            assertMatchContinued(runtime, dealt.id, key)
        }
    }

    /**
     * 建立所有座位都使用 [key] 的對局環境與一般發牌後的台灣麻將桌況。
     *
     * @param key 所有座位使用的策略 key。
     * @return 對局環境與發牌後的桌況。
     */
    private fun setUp(key: String): Pair<SimulationRuntime, TableState> {
        val runtime = SimulationRuntime(defaultStrategyKey = key)
        runtime.aiStrategyRegistry.registerBuiltInAiStrategies(
            moduleRegistry = runtime.moduleRegistry,
            extensionActionRegistry = runtime.extensionActionRegistry,
            opponentModelRegistry = runtime.opponentModelRegistry,
        )
        val module = runtime.moduleRegistry.getModule(TaiwanRuleConfig())
        val dealt = GameInitializer.initialize(id = Uuid.random(), playerIds = List(4) { Uuid.random() }, module = module)
            .initializeRuleComponents(module.id, bundledCoreExtensionRegistries().componentRegistry)
            .tableState
        return runtime to dealt
    }

    /**
     * 以 [state] 開局並推進到整場結束，回傳第一局各玩家的分數變化。
     *
     * @param runtime 對局環境。
     * @param key 所有座位使用的策略 key。
     * @param state 開局桌況。
     * @return 以玩家識別碼索引的第一局分數變化。
     */
    private suspend fun firstRoundScoreChanges(
        runtime: SimulationRuntime,
        key: String,
        state: TableState,
    ): Map<Uuid, Int> {
        runtime.start(state, state.players.associate { it.id to key })
        runtime.driveLikeServer(state.id)
        val firstRound = runtime.gameRepository.rounds(state.id).first().final
        return firstRound.players.associate { player -> player.id to player.score - state.players.first { it.id == player.id }.score }
    }

    /**
     * 花胡之後流程接著換局並結束整場，等待返回房間，沒有停在排定換局的狀態。
     *
     * @param runtime 對局環境。
     * @param gameId 對局識別碼。
     * @param key 所有座位使用的策略 key。
     */
    private suspend fun assertMatchContinued(
        runtime: SimulationRuntime,
        gameId: Uuid,
        key: String,
    ) {
        val game = runtime.gameRepository.getGame(gameId)
        assertEquals(PendingGameTransition.ReturnToRoom, game?.pendingTransition, "$key: the match must finish; abort: ${runtime.abortDiagnostic}")
    }

    /**
     * 交換牌面，讓全部花牌恰好落在 [targets]，其餘位置都是一般牌；牌張識別碼與牌牆布局不變。
     *
     * @param targets 要放花牌的牌張，張數必須等於花牌總數。
     * @return 交換牌面後的桌況。
     */
    private fun TableState.withFlowersIn(targets: List<IdentifiedTile>): TableState {
        val all = players.flatMap { it.hand.standingTiles } + tileWall.getAllTiles() + reservedWallTiles
        val flowers = all.filter { TaiwanTileTypes.isFlower(it.tile) }
        require(flowers.size == targets.size) { "Every flower must have a target" }
        val targetIds = targets.mapTo(mutableSetOf()) { it.id }
        val misplacedFlowers = flowers.filterNot { it.id in targetIds }
        val ordinaryTargets = targets.filterNot { TaiwanTileTypes.isFlower(it.tile) }
        val faceById = (misplacedFlowers.zip(ordinaryTargets) + ordinaryTargets.zip(misplacedFlowers))
            .associate { (tile, other) -> tile.id to other.tile }

        fun IdentifiedTile.swapped(): IdentifiedTile = faceById[id]?.let { copy(tile = it) } ?: this
        return copy(
            players = players.map { player ->
                player.copy(hand = player.hand.copy(tiles = player.hand.tiles.map { it.swapped() }, lastDrawn = player.hand.lastDrawn?.swapped()))
            },
            tileWall = TileWall(tileWall.getAllTiles().map { it.swapped() }),
            reservedWallTiles = reservedWallTiles.map { it.swapped() },
        )
    }

    /** 測試常數。 */
    private companion object {
        /** 七搶一需要的起手花牌張數。 */
        const val SEVEN = 7

        /** 八仙過海需要的起手花牌張數。 */
        const val EIGHT = 8

        /** 受測的內建策略。 */
        val BUILT_IN_STRATEGIES = listOf(BuiltInAiStrategyKeys.BEGINNER, BuiltInAiStrategyKeys.INTERMEDIATE, BuiltInAiStrategyKeys.ADVANCED, RandomAiStrategy.KEY)
    }
}
