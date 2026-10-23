package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugAutoNorthRiichiRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證三人日麻驗收情境的桌況能重現拔北、搶北與連續補牌。 */
class ThreePlayerRiichiDebugGameScenariosTest {
    /** 提供三人日麻情境的登錄表。 */
    private val registry = DebugGameScenarioRegistry()

    /** 檢查三人日麻情境結果的驗證器。 */
    private val scenarioValidator = DebugGameScenarioValidator(scenarioModuleRegistry)

    /** 所有三人情境在兩種赤寶牌設定下都通過權威驗證；呼叫者是莊家且輪到自己，三家的自風依序是東、南、西。 */
    @Test
    fun `every three player scenario is valid at the invoking dealer's turn`() {
        ThreePlayerRiichiDebugGameScenarios.all.forEach { scenario ->
            RED_DORA_COUNTS.forEach { redDoraCount ->
                val context = createThreePlayerRiichiScenarioContext(ThreePlayerRiichiRuleConfig(redDoraCount = redDoraCount))
                val result = scenario.build(context)
                scenarioValidator.validate(context, result)
                val state = result.game.tableState

                assertEquals(context.invokingPlayerId, state.currentPlayer.id, scenario.id)
                assertEquals(context.invokingPlayerId, state.dealerPlayerId, scenario.id)
                assertEquals(listOf(Wind.EAST, Wind.SOUTH, Wind.WEST), (0..2).map { offset -> state.seat(offset).seatWind }, scenario.id)
            }
        }
    }

    /** 三人情境不能在四人日麻的桌子載入，四人情境也不能在三人日麻的桌子載入。 */
    @Test
    fun `seat setup scenarios only load at a table with their player count`() {
        ThreePlayerRiichiDebugGameScenarios.all.forEach { scenario ->
            assertFailsWith<IllegalArgumentException>(scenario.id) { scenario.build(createRiichiScenarioContext()) }
        }
        RiichiIppatsuDebugGameScenarios.all.forEach { scenario ->
            assertFailsWith<IllegalArgumentException>(scenario.id) { scenario.build(createThreePlayerRiichiScenarioContext()) }
        }
    }

    /** 正式開局情境在三人日麻的桌子依三人規則開局：每家 13 張、王牌 14 張，活牌剩 55 張。 */
    @Test
    fun `wall opening scenario deals a three player round`() {
        val context = createThreePlayerRiichiScenarioContext()
        val result = registry.get("mahjongcraft:riichi_wall_opening")!!.build(context)

        scenarioValidator.validate(context, result)
        val state = result.game.tableState
        assertIs<DebugGameScenarioPresentation.InitialRound>(result.presentation)
        assertEquals(3, state.players.size)
        assertTrue(state.players.all { player -> player.hand.tiles.size == INITIAL_HAND_SIZE })
        assertEquals(DEAD_TILE_COUNT, state.reservedWallTiles.size)
        assertEquals(LIVE_TILES_AFTER_DEAL, state.tileWall.getAllTiles().size)
    }

    /** 先拔剛摸到的北、再拔手上的北；第二次補到的四條可以自摸。 */
    @Test
    fun `pull north scenario completes the hand with the second replacement draw`() {
        val state = build("riichi_three_player_pull_north")

        assertTrue(PULL_NORTH_GAME_ACTION in state.currentOwnTurnActions())
        assertEquals(listOf(NINE_CHARACTER, FOUR_BAMBOO), state.reservedWallTiles.take(2).map { it.tile })
        val afterFirstPull = state.pullNorthThenDraw(state.reservedWallTiles[0])
        assertTrue(PULL_NORTH_GAME_ACTION in afterFirstPull.currentOwnTurnActions())
        val afterSecondPull = afterFirstPull.pullNorthThenDraw(state.reservedWallTiles[1])

        val playerState = assertIs<RiichiPlayerState>(afterSecondPull.currentPlayer.playerRuleState)
        assertEquals(2, playerState.nukiDoraTiles.size)
        assertTrue(GameAction.Tsumo in afterSecondPull.currentOwnTurnActions())
    }

    /** 立直中拔的是剛摸到的北；手上那對北在之後摸到別的牌時不能拔，補到的四條可以自摸。 */
    @Test
    fun `pull north in riichi only sets aside the drawn north`() {
        val state = build("riichi_three_player_pull_north_in_riichi")
        val invoker = state.currentPlayer

        assertNotNull(assertIs<RiichiPlayerState>(invoker.playerRuleState).riichiTile)
        assertTrue(PULL_NORTH_GAME_ACTION in state.currentOwnTurnActions())
        assertEquals(invoker.hand.lastDrawn, state.module().tileSetAsideBy(invoker, PULL_NORTH_GAME_ACTION))
        assertEquals(FOUR_BAMBOO, state.reservedWallTiles[0].tile)

        val afterPull = state.pullNorthThenDraw(state.reservedWallTiles[0])
        assertEquals(2, afterPull.currentPlayer.hand.standingTiles.count { it.tile == Tile.Honor.North })
        assertTrue(GameAction.Tsumo in afterPull.currentOwnTurnActions())
        val laterDraw = IdentifiedTile(Uuid.random(), NINE_CHARACTER)
        assertFalse(PULL_NORTH_GAME_ACTION in afterPull.ownTurnActions(offset = 0, drawnTile = laterDraw))
    }

    /** 下家摸到北後會先拔北；單騎聽北的呼叫者可以搶這張北榮和，上家不能。 */
    @Test
    fun `rob north scenario lets the invoker ron the pulled north`() {
        val result = registry.get("mahjongcraft:riichi_three_player_rob_north")!!.build(createThreePlayerRiichiScenarioContext())
        val state = result.game.tableState
        val north = state.liveWallTile(0)

        assertEquals(Tile.Honor.North, north.tile)
        assertEquals(DebugScriptedAiStrategy.PULL_NORTH_FIRST_KEY, result.game.aiPlayerStrategyKeys[state.seat(1).id])
        assertTrue(PULL_NORTH_GAME_ACTION in state.ownTurnActions(offset = 1, drawnTile = north))
        assertTrue(state.canRobPulledNorth(offset = 0, north = north))
        assertFalse(state.canRobPulledNorth(offset = 2, north = north))
    }

    /** 剛摸到北時可以拔北，也可以暗槓一筒、九筒、一條；王牌前端依序是九條、北、九條、九條，湊得出第四槓。 */
    @Test
    fun `kan and pull north scenario offers every declaration and arranges the replacement draws`() {
        val state = build("riichi_three_player_kan_and_pull_north")
        val actions = state.currentOwnTurnActions()
        val handTiles = state.currentPlayer.hand.allTiles.associateBy { it.id }

        assertTrue(PULL_NORTH_GAME_ACTION in actions)
        assertEquals(
            setOf(ONE_DOT, NINE_DOT, ONE_BAMBOO),
            actions.filterIsInstance<GameAction.Kan>().mapTo(mutableSetOf()) { kan -> handTiles.getValue(kan.tileId).tile },
        )
        assertEquals(
            listOf(NINE_BAMBOO, Tile.Honor.North, NINE_BAMBOO, NINE_BAMBOO),
            state.reservedWallTiles.take(4).map { it.tile },
        )
        assertEquals(1, state.currentPlayer.hand.standingTiles.count { it.tile == NINE_BAMBOO })
    }

    /**
     * 白從上家、發從下家碰出；連拔三次北依序補到七筒、八筒、四筒，第三次補牌可以自摸。活牌只剩排定的四張，
     * 其餘的牌依序分進三家牌河。
     */
    @Test
    fun `pull north with melds scenario chains three pulls into a tsumo`() {
        val state = build("riichi_three_player_pull_north_with_melds")

        assertEquals(
            listOf(Tile.Honor.White to RelativeDirection.Left, Tile.Honor.Green to RelativeDirection.Right),
            state.currentPlayer.hand.melds.map { meld -> meld.tiles.first().tile to meld.sourceDirection },
        )
        assertEquals(
            listOf(Tile.Honor.West, Tile.Honor.East, Tile.Honor.West, Tile.Honor.East),
            state.tileWall.getAllTiles().map { it.tile },
        )
        val riverSizes = (0..2).map { offset -> state.seat(offset).discardPile.entries.size }
        assertTrue(riverSizes.max() - riverSizes.min() <= 1, "rivers must be filled evenly: $riverSizes")
        (0..2).forEach { offset ->
            assertEquals(riverSizes[offset] * 2, state.seat(offset).actionHistory.size)
        }

        val (seven, eight, four) = state.reservedWallTiles.take(3)
        assertEquals(listOf(SEVEN_DOT, EIGHT_DOT, FOUR_DOT), listOf(seven.tile, eight.tile, four.tile))
        assertTrue(PULL_NORTH_GAME_ACTION in state.currentOwnTurnActions())
        val afterFirstPull = state.pullNorthThenDraw(seven)
        assertTrue(PULL_NORTH_GAME_ACTION in afterFirstPull.currentOwnTurnActions())
        val afterSecondPull = afterFirstPull.pullNorthThenDraw(eight)
        assertTrue(PULL_NORTH_GAME_ACTION in afterSecondPull.currentOwnTurnActions())
        val afterThirdPull = afterSecondPull.pullNorthThenDraw(four)

        assertEquals(3, assertIs<RiichiPlayerState>(afterThirdPull.currentPlayer.playerRuleState).nukiDoraTiles.size)
        assertTrue(GameAction.Tsumo in afterThirdPull.currentOwnTurnActions())
    }

    /** 自動拔北情境改用開發用規則，設定沿用這一桌；之後在同一桌載入其他三人情境時恢復一般三人日麻。 */
    @Test
    fun `auto north scenarios switch the rule and other scenarios switch back`() {
        val config = ThreePlayerRiichiRuleConfig(redDoraCount = 0)
        val context = createThreePlayerRiichiScenarioContext(config)

        val autoNorth = registry.get("mahjongcraft:auto_north_riichi_chain")!!.build(context)
        assertEquals(DebugAutoNorthRiichiRuleConfig(config), autoNorth.game.tableState.config)

        val plain = registry.get("mahjongcraft:riichi_three_player_pull_north")!!.build(context.copy(currentGame = autoNorth.game))
        assertEquals(config, plain.game.tableState.config)
    }

    /** 連續自動拔北：兩家對手先摸東，呼叫者摸到北後，王牌前端依序補到北、北、四條；呼叫者聽一、四條。 */
    @Test
    fun `auto north chain scenario arranges three consecutive pulls`() {
        val state = build("auto_north_riichi_chain")

        assertEquals(listOf(Tile.Honor.East, Tile.Honor.East, Tile.Honor.North), state.liveWallFront(3))
        assertEquals(listOf(Tile.Honor.North, Tile.Honor.North, FOUR_BAMBOO), state.reservedWallTiles.take(3).map { it.tile })
        assertTrue(state.canRon(offset = 0, discarderOffset = 1, tile = FOUR_BAMBOO))
    }

    /** 自動拔北被搶：呼叫者碰出中與白，下家照常摸切，摸到的北由規則自動拔出；單騎聽北的呼叫者可以搶，上家不能。 */
    @Test
    fun `auto north rob scenario lets the invoker ron the pulled north`() {
        val result = registry.get("mahjongcraft:auto_north_riichi_rob")!!.build(createThreePlayerRiichiScenarioContext())
        val state = result.game.tableState
        val north = state.liveWallTile(0)

        assertEquals(Tile.Honor.North, north.tile)
        assertEquals(DebugScriptedAiStrategy.TSUMOGIRI_KEY, result.game.aiPlayerStrategyKeys[state.seat(1).id])
        assertEquals(
            listOf(Tile.Honor.Red to RelativeDirection.Left, Tile.Honor.White to RelativeDirection.Right),
            state.seat(0).hand.melds.map { meld -> meld.tiles.first().tile to meld.sourceDirection },
        )
        assertTrue(state.canRobPulledNorth(offset = 0, north = north))
        assertFalse(state.canRobPulledNorth(offset = 2, north = north))
    }

    /** 沒有牌可補：活牌只剩東、東、北三張，呼叫者摸到的最後一張是北。 */
    @Test
    fun `auto north last tile scenario leaves a north as the last live tile`() {
        val state = build("auto_north_riichi_last_tile")

        assertEquals(listOf(Tile.Honor.East, Tile.Honor.East, Tile.Honor.North), state.tileWall.getAllTiles().map { it.tile })
    }

    /** 槓牌收付：現在可以暗槓一筒，九筒從上家碰出、嶺上補到九筒可以加槓；下家會摸切一條，王牌前端依序是九筒、南、東。 */
    @Test
    fun `auto north kan payments scenario arranges every kan`() {
        val state = build("auto_north_riichi_kan_payments")
        val handTiles = state.currentPlayer.hand.allTiles.associateBy { it.id }

        assertEquals(
            setOf(ONE_DOT),
            state.currentOwnTurnActions().filterIsInstance<GameAction.Kan>().mapTo(mutableSetOf()) { kan -> handTiles.getValue(kan.tileId).tile },
        )
        assertEquals(
            listOf(NINE_DOT to RelativeDirection.Left),
            state.currentPlayer.hand.melds.map { meld -> meld.tiles.first().tile to meld.sourceDirection },
        )
        assertEquals(listOf(NINE_DOT, Tile.Honor.South, Tile.Honor.East), state.reservedWallTiles.take(3).map { it.tile })
        assertEquals(ONE_BAMBOO, state.liveWallTile(0).tile)
        assertEquals(3, state.currentPlayer.hand.standingTiles.count { it.tile == ONE_BAMBOO })
    }

    /** 驗證器清點牌數時把拔出的北算進去，拔過北的桌況照樣通過驗證。 */
    @Test
    fun `validator counts pulled norths`() {
        val context = createThreePlayerRiichiScenarioContext()
        val result = registry.get("mahjongcraft:riichi_three_player_pull_north")!!.build(context)
        val state = result.game.tableState
        val pulled = assertNotNull(state.module().applyTileSetAsideAction(state, state.currentPlayer.id, PULL_NORTH_GAME_ACTION))

        scenarioValidator.validate(context, result.copy(game = result.game.copy(tableState = pulled)))
    }

    /**
     * 由 registry 建立指定三人日麻情境的桌況。
     *
     * @param idPath 情境識別碼中 namespace 後的路徑。
     * @return 建立後的對局桌況。
     */
    private fun build(idPath: String): TableState = registry.get("mahjongcraft:$idPath")!!
        .build(createThreePlayerRiichiScenarioContext())
        .game
        .tableState

    /** @return 目前桌況設定對應的規則模組。 */
    private fun TableState.module(): MahjongRuleModule<*> = scenarioModuleRegistry.getModule(config)

    /**
     * 目前玩家拔北後，直接設定其補牌以模擬結果；不執行正式補牌流程。
     *
     * @param replacement 模擬補到的牌。
     * @return 更新拔北狀態與目前玩家摸牌位後的桌況。
     */
    private fun TableState.pullNorthThenDraw(replacement: IdentifiedTile): TableState {
        val pulled = assertNotNull(module().applyTileSetAsideAction(this, currentPlayer.id, PULL_NORTH_GAME_ACTION))
        val player = pulled.currentPlayer
        return pulled.copy(
            players = pulled.players.map { if (it.id == player.id) player.copy(hand = player.hand.copy(lastDrawn = replacement)) else it },
        )
    }

    /**
     * 查詢指定座位能否搶座位偏移 1 的玩家拔出的北榮和。
     *
     * @param offset 回應玩家相對於目前玩家的座位偏移。
     * @param north 被拔出的北牌。
     * @return 合法反應包含榮和時為 true。
     */
    private fun TableState.canRobPulledNorth(offset: Int, north: IdentifiedTile): Boolean {
        val player = seat(offset)
        val declarer = seat(1)
        val contextState = copy(
            players = players.map {
                if (it.id == player.id) it.copy(hand = it.hand.copy(lastDrawn = null)) else it
            },
        )
        return contextState.scenarioValidator().getLegalActions(
            LegalActionContext.declarationReaction(
                contextState,
                player.id,
                declarer.id,
                PULL_NORTH_GAME_ACTION,
                north,
            ),
        ).any { it is GameAction.Ron }
    }

    /** 三人日麻情境共用的規則數值與牌面。 */
    private companion object {
        /** 三人日麻房間設定可選的赤寶牌張數。 */
        val RED_DORA_COUNTS: List<Int> = listOf(0, 2)

        /** 三人日麻的王牌張數。 */
        const val DEAD_TILE_COUNT: Int = 14

        /** 三人日麻開局配牌後的活牌張數。 */
        const val LIVE_TILES_AFTER_DEAL: Int = 55

        /** 一筒，暗槓候選牌面。 */
        val ONE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 1)

        /** 九筒，暗槓候選牌面。 */
        val NINE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 9)

        /** 一索，暗槓候選牌面。 */
        val ONE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 1)

        /** 四索，拔北補牌後的和牌張。 */
        val FOUR_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 4)

        /** 四筒，有副露的拔北情境第三次補牌的和牌張。 */
        val FOUR_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 4)

        /** 七筒，有副露的拔北情境第一次補牌。 */
        val SEVEN_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 7)

        /** 八筒，有副露的拔北情境第二次補牌。 */
        val EIGHT_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 8)
    }
}
