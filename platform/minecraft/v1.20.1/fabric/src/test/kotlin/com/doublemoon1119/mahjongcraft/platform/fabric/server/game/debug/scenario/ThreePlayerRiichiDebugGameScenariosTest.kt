package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
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
    private val registry = DebugGameScenarioRegistry()
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

    /** 驗證器清點牌數時把拔出的北算進去，拔過北的桌況照樣通過驗證。 */
    @Test
    fun `validator counts pulled norths`() {
        val context = createThreePlayerRiichiScenarioContext()
        val result = registry.get("mahjongcraft:riichi_three_player_pull_north")!!.build(context)
        val state = result.game.tableState
        val pulled = assertNotNull(state.module().applyTileSetAsideAction(state, state.currentPlayer.id, PULL_NORTH_GAME_ACTION))

        scenarioValidator.validate(context, result.copy(game = result.game.copy(tableState = pulled)))
    }

    private fun build(idPath: String): TableState = registry.get("mahjongcraft:$idPath")!!
        .build(createThreePlayerRiichiScenarioContext())
        .game
        .tableState

    private fun TableState.module(): MahjongRuleModule<*> = scenarioModuleRegistry.getModule(config)

    /** 目前玩家拔北，再把 [replacement] 當成補到的牌放進手上；補牌本身由正式流程負責，這裡只模擬結果。 */
    private fun TableState.pullNorthThenDraw(replacement: IdentifiedTile): TableState {
        val pulled = assertNotNull(module().applyTileSetAsideAction(this, currentPlayer.id, PULL_NORTH_GAME_ACTION))
        val player = pulled.currentPlayer
        return pulled.copy(
            players = pulled.players.map { if (it.id == player.id) player.copy(hand = player.hand.copy(lastDrawn = replacement)) else it },
        )
    }

    /** [offset] 座位能否搶下家拔出的 [north] 榮和。 */
    private fun TableState.canRobPulledNorth(offset: Int, north: IdentifiedTile): Boolean = scenarioValidator().getLegalActions(
        tableState = this,
        player = waitingSeat(offset),
        sourceAction = PULL_NORTH_GAME_ACTION,
        sourceDirection = relativeDirectionOf(seat(offset).id, seat(1).id),
        incomingTile = north,
    ).any { it is GameAction.Ron }

    private companion object {
        /** 三人日麻房間設定可選的赤寶牌張數。 */
        val RED_DORA_COUNTS: List<Int> = listOf(0, 2)

        /** 三人日麻的王牌張數。 */
        const val DEAD_TILE_COUNT: Int = 14

        /** 三人日麻開局配牌後的活牌張數。 */
        const val LIVE_TILES_AFTER_DEAL: Int = 55

        val ONE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 1)
        val NINE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 9)
        val ONE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 1)
        val FOUR_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 4)
    }
}
