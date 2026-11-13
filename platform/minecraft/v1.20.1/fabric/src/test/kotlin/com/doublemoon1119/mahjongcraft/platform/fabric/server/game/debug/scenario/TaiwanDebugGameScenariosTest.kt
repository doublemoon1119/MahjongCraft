package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanFlowerFlow
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPaoHand
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
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
        val (state) = build("mahjongcraft:taiwan/debug/flower_replacement")
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
        val (state, invokerId) = build("mahjongcraft:taiwan/debug/seven_flowers")

        val pending = assertIs<TaiwanFlowerFlow.Result.AwaitingDecision>(processOpening(state)).pending

        assertEquals(TaiwanTaiType.SevenFlowers, pending.opportunity.taiResult.type)
        assertEquals(invokerId, pending.opportunity.winnerId)
    }

    /** 呼叫者起手八張花，補完花後可以選擇八仙過海。 */
    @Test
    fun `eight flowers scenario offers the invoker an eight-flower win`() {
        val (state, invokerId) = build("mahjongcraft:taiwan/debug/eight_flowers")

        val pending = assertIs<TaiwanFlowerFlow.Result.AwaitingDecision>(processOpening(state)).pending

        assertEquals(TaiwanTaiType.EightFlowers, pending.opportunity.taiResult.type)
        assertEquals(invokerId, pending.opportunity.winnerId)
    }

    /** 呼叫者起手聽牌，沒有任何人起手有花。 */
    @Test
    fun `ready declaration scenario deals the invoker a ready hand without opening flowers`() {
        val (state, invokerId) = build("mahjongcraft:taiwan/debug/ready_declaration")
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
        val (state, invokerId) = build("mahjongcraft:taiwan/debug/last_tile_flower")
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

    /** 上家剛打出白，呼叫者已碰發、中；碰白後上家成為大三元的包牌者，活牌最前端交錯排著一、四萬，花在活牌尾端。 */
    @Test
    fun `pao scenario lets the invoker pon the third dragon from the upstream player`() {
        val (state, invokerId) = build("mahjongcraft:taiwan/debug/before_pao_pon")
        val config = state.config as TaiwanRuleConfig
        val invoker = state.players.single { it.id == invokerId }
        val discarder = state.players[(state.players.indexOf(invoker) + PLAYERS - 1) % PLAYERS]
        val reaction = assertIs<TurnPhase.AwaitingDiscardReaction>(state.turnPhase).reaction
        val calledTile = discarder.discardPile.entries.single().tile

        assertTrue(config.paoLiability)
        assertEquals(listOf(Tile.Honor.Green, Tile.Honor.Red), invoker.hand.melds.map { meld -> meld.tiles.map { it.tile }.distinct().single() })
        assertEquals(2, invoker.hand.tiles.count { it.tile == Tile.Honor.White })
        assertTrue(invoker.hand.melds.all { meld -> meld.sourceTile != null && meld.sourceTile in meld.tiles })
        assertEquals(Tile.Honor.White, calledTile.tile)
        assertEquals(calledTile.id, reaction.tileId)
        assertEquals(discarder.id, reaction.discarderId)
        assertTrue(state.players.filterNot { it.id == invokerId }.all { it.hand.tiles.size == READY_HAND_SIZE })
        assertTrue(state.players.filterNot { it.id == invokerId }.none { TaiwanShantenCalculator().calculate(it.hand) is ShantenResult.Tenpai })
        assertEquals(
            List(PAO_WINNING_DRAWS) { index -> Tile.Numeric(Tile.Suit.Character, if (index % 2 == 0) 4 else 1) },
            state.tileWall.getAllTiles().take(PAO_WINNING_DRAWS).map { it.tile },
        )
        assertTrue(state.tileWall.getAllTiles().takeLast(TaiwanTileTypes.ALL.size).all { TaiwanTileTypes.isFlower(it.tile) })

        val module = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, config)
        val direction = state.relativeDirectionOf(invokerId, discarder.id)
        val liability = module.beforeDiscardClaimed(invoker, MeldType.Pon, calledTile, direction)
            .applyTo(invoker.components, BuiltInComponentOwners.PLAYER)[TaiwanPlayerComponents.PAO_LIABILITY]
        assertEquals(TaiwanPaoLiability(TaiwanPaoHand.BIG_DRAGONS, direction), liability)
    }

    /** 副露擁擠情境：呼叫者有三組明槓、一組碰與八張花，可以碰上家打出的白；全桌不到四個槓，其他人不能碰或胡。 */
    @Test
    fun `crowded melds scenario offers a fifth meld without four kongs`() {
        val (state, invokerId) = build("mahjongcraft:taiwan/debug/crowded_melds")
        val invoker = state.players.single { it.id == invokerId }
        val discarder = state.players[(state.players.indexOf(invoker) + PLAYERS - 1) % PLAYERS]
        val reaction = assertIs<TurnPhase.AwaitingDiscardReaction>(state.turnPhase).reaction
        val calledTile = discarder.discardPile.entries.single().tile

        assertEquals(listOf(MeldType.OpenKan, MeldType.OpenKan, MeldType.OpenKan, MeldType.Pon), invoker.hand.melds.map { it.type })
        assertTrue(invoker.hand.melds.all { meld -> meld.tiles.map { it.tile }.distinct().size == 1 && meld.sourceTile in meld.tiles })
        assertEquals(TaiwanTileTypes.ALL.size, flowerCount(state, state.players.indexOf(invoker)))
        assertEquals(Tile.Honor.White, calledTile.tile)
        assertEquals(calledTile.id, reaction.tileId)
        assertEquals(2, invoker.hand.tiles.count { it.tile == Tile.Honor.White })
        assertEquals(null, TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, state.config as TaiwanRuleConfig).resolveFourKans(state))
        assertTrue(state.players.filterNot { it.id == invokerId }.none { TaiwanShantenCalculator().calculate(it.hand) is ShantenResult.Tenpai })
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
     * 照開局流程讓莊家先摸開門牌，再由莊家開始逐輪補花。
     *
     * @param state 開局桌況。
     * @return 局首補花的結果。
     */
    private fun processOpening(state: TableState): TaiwanFlowerFlow.Result {
        val module = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, state.config as TaiwanRuleConfig)
        val drawn = assertNotNull(state.drawDealerOpeningTile(module))
        return TaiwanFlowerFlow.process(drawn.tableState, openingOrder(state))
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

        /** 包牌情境活牌最前端排放聽牌的張數。 */
        const val PAO_WINNING_DRAWS = 6
    }
}
