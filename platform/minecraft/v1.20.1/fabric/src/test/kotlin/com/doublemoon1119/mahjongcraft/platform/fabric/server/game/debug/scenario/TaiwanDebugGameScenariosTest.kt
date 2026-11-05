package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanFlowerFlow
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.drawDealerOpeningTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證台灣麻將驗收情境的花牌與手牌排列，以及通過權威情境驗證。 */
class TaiwanDebugGameScenariosTest {
    private val registry = DebugGameScenarioRegistry()
    private val validator = DebugGameScenarioValidator(scenarioModuleRegistry)

    /** 莊家先摸開門牌，再逐輪補花：莊家兩輪共三張、下家與上家各一張；之後下家摸到的花補到的又是花。 */
    @Test
    fun `flower replacement scenario chains opening and mid-game replacements`() {
        val (state) = build("mahjongcraft:taiwan_flower_replacement")
        val dealer = state.dealerIndex
        val module = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, state.config as TaiwanRuleConfig)
        val drawn = assertNotNull(state.drawDealerOpeningTile(module))

        val opened = assertIs<TaiwanFlowerFlow.Result.Completed>(TaiwanFlowerFlow.process(drawn.tableState, openingOrder(state)))

        assertEquals(listOf(3, 1, 0, 1), (0 until PLAYERS).map { offset -> flowerCount(opened.tableState, dealer + offset) })
        assertTrue(!TaiwanTileTypes.isFlower(drawn.tile.tile))
        assertTrue(TaiwanTileTypes.isFlower(opened.tableState.tileWall.getAllTiles().first().tile))
        assertTrue(TaiwanTileTypes.isFlower(opened.tableState.reservedWallTiles.first().tile))
    }

    /** 呼叫者起手七張花，下家亮出第八張時，呼叫者可以選擇七搶一。 */
    @Test
    fun `seven flowers scenario offers the invoker a seven-flower win`() {
        val (state, invokerId) = build("mahjongcraft:taiwan_seven_flowers")

        val pending = assertIs<TaiwanFlowerFlow.Result.AwaitingDecision>(TaiwanFlowerFlow.process(state, openingOrder(state))).pending

        assertEquals(TaiwanTaiType.SEVEN_FLOWERS, pending.opportunity.type)
        assertEquals(invokerId, pending.opportunity.winnerId)
    }

    /** 呼叫者起手八張花，補完花後可以選擇八仙過海。 */
    @Test
    fun `eight flowers scenario offers the invoker an eight-flower win`() {
        val (state, invokerId) = build("mahjongcraft:taiwan_eight_flowers")

        val pending = assertIs<TaiwanFlowerFlow.Result.AwaitingDecision>(TaiwanFlowerFlow.process(state, openingOrder(state))).pending

        assertEquals(TaiwanTaiType.EIGHT_FLOWERS, pending.opportunity.type)
        assertEquals(invokerId, pending.opportunity.winnerId)
    }

    /** 呼叫者起手聽牌，沒有任何人起手有花。 */
    @Test
    fun `ready declaration scenario deals the invoker a ready hand without opening flowers`() {
        val (state, invokerId) = build("mahjongcraft:taiwan_ready_declaration")
        val invoker = state.players.single { it.id == invokerId }
        val sixteen = invoker.hand.copy(tiles = invoker.hand.standingTiles.filterNot { it.tile == Tile.Honor.North }, lastDrawn = null)

        assertEquals(READY_HAND_SIZE, sixteen.tiles.size)
        assertIs<ShantenResult.Tenpai>(TaiwanShantenCalculator().calculate(sixteen))
        assertTrue(state.players.none { player -> player.hand.allTiles.any { TaiwanTileTypes.isFlower(it.tile) } })
        assertIs<TaiwanFlowerFlow.Result.Completed>(TaiwanFlowerFlow.process(state, openingOrder(state)))
    }

    /** 呼叫者剛摸到牌；活牌只剩一張花，其餘七張花已在花區；下家摸到那張花後沒有牌可以補。 */
    @Test
    fun `last tile flower scenario leaves one flower and no replacement`() {
        val (state, invokerId) = build("mahjongcraft:taiwan_last_tile_flower")
        val drawer = state.players[(state.players.indexOfFirst { it.id == invokerId } + 1) % PLAYERS]

        assertEquals(invokerId, state.currentPlayer.id)
        assertEquals(17, state.currentPlayer.hand.standingTiles.size)
        val lastTile = state.tileWall.getAllTiles().single()
        val drawn = state.copy(
            players = state.players.map { if (it.id == drawer.id) it.copy(hand = it.hand.copy(lastDrawn = lastTile)) else it },
            tileWall = state.tileWall.draw().wall,
            physicalWallLayout = state.physicalWallLayout?.retainOnly(state.reservedWallTiles.mapTo(mutableSetOf()) { it.id }),
        )

        assertTrue(TaiwanTileTypes.isFlower(lastTile.tile))
        assertEquals(SEVEN, (0 until PLAYERS).sumOf { flowerCount(state, it) })
        assertIs<TaiwanFlowerFlow.Result.Exhausted>(TaiwanFlowerFlow.process(drawn, listOf(drawer.id)))
    }

    /**
     * 以四人日麻桌建立情境，確認改用台灣麻將並通過權威情境驗證。
     *
     * @param id 情境識別碼。
     * @return 情境的桌況與呼叫者識別碼。
     */
    private fun build(id: String): Pair<TableState, Uuid> {
        val context = createRiichiScenarioContext()
        val result = registry.get(id)!!.build(context)
        validator.validate(context, result)
        assertIs<TaiwanRuleConfig>(result.game.tableState.config)
        return result.game.tableState to context.invokingPlayerId
    }

    /**
     * 局首補花的順序：由莊家開始。
     *
     * @param state 桌況。
     * @return 依補花順序排列的玩家識別碼。
     */
    private fun openingOrder(state: TableState): List<Uuid> = (0 until PLAYERS).map { offset -> state.players[(state.dealerIndex + offset) % PLAYERS].id }

    /**
     * 座位花區的花牌張數。
     *
     * @param state 桌況。
     * @param seatIndex 座位索引；超過人數時繞回。
     * @return 花牌張數。
     */
    private fun flowerCount(state: TableState, seatIndex: Int): Int = state.players[seatIndex % PLAYERS].tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles?.size ?: 0

    /** 測試常數。 */
    private companion object {
        /** 四人桌。 */
        const val PLAYERS = 4

        /** 七張花。 */
        const val SEVEN = 7

        /** 聽牌手牌張數。 */
        const val READY_HAND_SIZE = 16
    }
}
