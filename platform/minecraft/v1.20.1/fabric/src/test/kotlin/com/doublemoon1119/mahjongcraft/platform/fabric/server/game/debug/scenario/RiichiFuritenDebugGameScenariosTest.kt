package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證振聽驗收情境的桌況能重現預定的振聽判定。 */
class RiichiFuritenDebugGameScenariosTest {
    private val registry = DebugGameScenarioRegistry()
    private val moduleRegistry = MahjongModuleRegistryImpl().apply {
        registerBuiltInRuleModules()
        freeze()
    }
    private val scenarioValidator = DebugGameScenarioValidator(moduleRegistry)

    /** 所有振聽情境在各種赤寶牌張數下都通過權威驗證，由呼叫者先捨牌，其他三家改用腳本 AI，王牌區沒有驗收用的和牌張。 */
    @Test
    fun `every furiten scenario is valid and starts with the invoking player`() {
        RiichiFuritenDebugGameScenarios.all.forEach { scenario ->
            RED_DORA_COUNTS.forEach { redDoraCount ->
                assertScenarioIsPlayable(scenario, createContext(config = RiichiRuleConfig(redDoraCount = redDoraCount)))
            }
        }
    }

    /** 驗證 [scenario] 在 [context] 下可以載入並依腳本進行。 */
    private fun assertScenarioIsPlayable(
        scenario: DebugGameScenario,
        context: DebugGameScenarioContext,
    ) {
        val result = scenario.build(context)
        scenarioValidator.validate(context, result)
        val state = result.game.tableState

        assertEquals(context.invokingPlayerId, state.currentPlayer.id, scenario.id)
        assertTrue(state.currentPlayer.hand.lastDrawn != null, "${scenario.id} should start after the invoking player drew")
        val strategyKeys = state.players.filterNot { it.id == context.invokingPlayerId }.map { it.aiStrategyKey }
        assertTrue(
            strategyKeys.all { it == DebugScriptedAiStrategy.TSUMOGIRI_KEY || it == DebugScriptedAiStrategy.KAN_FIRST_KEY },
            "${scenario.id} should hand every opponent to a scripted AI",
        )
        assertTrue(
            state.reservedWallTiles.none { it.tile == ONE_CHARACTER || it.tile == FOUR_CHARACTER },
            "${scenario.id} should keep the waits out of the dead wall",
        )
    }

    /** 呼叫者以外有真人玩家時拒絕載入。 */
    @Test
    fun `scenarios reject a table with a human opponent`() {
        val context = createContext(aiOpponentCount = 2)

        assertFailsWith<IllegalArgumentException> { build("riichi_furiten_discarded_wait", context) }
    }

    /** 捨牌振聽：下家打四萬不能榮和，自己摸到四萬可以自摸。 */
    @Test
    fun `discarded wait scenario blocks ron but allows tsumo`() {
        val state = build("riichi_furiten_discarded_wait").game.tableState

        assertEquals(listOf(FOUR_CHARACTER, Tile.Honor.North, NINE_BAMBOO, FOUR_CHARACTER), state.liveWallFront(4))
        assertFalse(state.canRon(offset = 0, discarderOffset = 1, tile = FOUR_CHARACTER))
        val invoker = state.seat(0)
        val drawnFour = state.liveWallTile(3)
        val ownTurnActions = state.validator().getLegalActions(
            tableState = state,
            player = invoker.copy(hand = invoker.hand.copy(lastDrawn = null)),
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = drawnFour,
        )
        assertTrue(GameAction.Tsumo in ownTurnActions)
    }

    /** 沒被詢問的和牌張：一萬無役不能榮和，四萬有役可以榮和。 */
    @Test
    fun `unoffered wait scenario offers only the tanyao wait`() {
        val state = build("riichi_furiten_unoffered_wait").game.tableState

        assertEquals(listOf(ONE_CHARACTER, FOUR_CHARACTER, Tile.Honor.East, NINE_BAMBOO, FOUR_CHARACTER), state.liveWallFront(5))
        assertFalse(state.canRon(offset = 0, discarderOffset = 1, tile = ONE_CHARACTER))
        assertTrue(state.canRon(offset = 0, discarderOffset = 2, tile = FOUR_CHARACTER))
    }

    /** 放過榮和：下家打一萬時可以榮和。 */
    @Test
    fun `declined ron scenario offers the first wait`() {
        val state = build("riichi_furiten_declined_ron").game.tableState

        assertEquals(listOf(ONE_CHARACTER, FOUR_CHARACTER, NINE_BAMBOO, NINE_CHARACTER, FOUR_CHARACTER), state.liveWallFront(5))
        assertTrue(state.canRon(offset = 0, discarderOffset = 1, tile = ONE_CHARACTER))
    }

    /** 鳴牌解除：呼叫者尚未聽牌，可以碰對家打出的中。 */
    @Test
    fun `cleared by call scenario lets the invoking player pon the red dragon`() {
        val state = build("riichi_furiten_cleared_by_call").game.tableState
        val red = state.liveWallTile(1)

        assertEquals(listOf(ONE_CHARACTER, Tile.Honor.Red, ONE_CHARACTER), state.liveWallFront(3))
        assertFalse(state.canRon(offset = 0, discarderOffset = 1, tile = ONE_CHARACTER))
        assertTrue(state.reactionsOf(offset = 0, discarderOffset = 2, tile = red).any { it is GameAction.Pon })
    }

    /** 立直中放過搶槓：下家摸到一萬可以加槓，呼叫者可以搶槓。 */
    @Test
    fun `declined chankan scenario lets the downstream player add a kan the invoker can rob`() {
        val state = build("riichi_furiten_declined_chankan").game.tableState

        assertTrue((state.seat(0).playerRuleState as RiichiPlayerState).isRiichi)
        assertEquals(DebugScriptedAiStrategy.KAN_FIRST_KEY, state.seat(1).aiStrategyKey)
        assertTrue(state.ownTurnKans(offset = 1).any { it.type == GameAction.KanType.ADDED_KAN })
        assertTrue(state.canRobKan(offset = 0, type = GameAction.KanType.ADDED_KAN))
    }

    /** 暗槓：下家摸到一萬可以暗槓，呼叫者不能搶。 */
    @Test
    fun `closed kan scenario lets the downstream player declare a kan the invoker cannot rob`() {
        val state = build("riichi_furiten_closed_kan_not_offered").game.tableState

        assertTrue(state.ownTurnKans(offset = 1).any { it.type == GameAction.KanType.CLOSED_KAN })
        assertFalse(state.canRobKan(offset = 0, type = GameAction.KanType.CLOSED_KAN))
        assertTrue(state.canRon(offset = 0, discarderOffset = 2, tile = FOUR_CHARACTER))
    }

    /** 頭跳：一炮多響設為頭跳，對家與呼叫者都能搶下家的加槓，其他設定沿用這一桌。 */
    @Test
    fun `head bump scenario overrides only the multi ron policy`() {
        val context = createContext()
        val result = build("riichi_furiten_head_bump_chankan", context)
        val state = result.game.tableState
        val config = state.config as RiichiRuleConfig

        assertEquals(RonResolution.NEAREST_WINNER, config.multiRonPolicy.doubleRonResolution)
        assertEquals(
            (context.currentGame.tableState.config as RiichiRuleConfig).copy(multiRonPolicy = config.multiRonPolicy),
            config,
        )
        assertTrue(state.canRobKan(offset = 2, type = GameAction.KanType.ADDED_KAN))
        assertTrue(state.canRobKan(offset = 0, type = GameAction.KanType.ADDED_KAN))
    }

    private fun build(
        idPath: String,
        context: DebugGameScenarioContext = createContext(),
    ): DebugGameScenarioResult = registry.get("mahjongcraft:$idPath")!!.build(context)

    /** 建立呼叫者以外有 [aiOpponentCount] 位 AI 的正式日麻對局。 */
    private fun createContext(
        aiOpponentCount: Int = 3,
        config: RiichiRuleConfig = RiichiRuleConfig(),
    ): DebugGameScenarioContext {
        val playerIds = List(4) { Uuid.random() }
        val initialized = GameInitializer.initialize(
            id = Uuid.random(),
            playerIds = playerIds,
            module = RiichiRuleModule("mahjongcraft:riichi", config),
            aiPlayerStrategyKeys = playerIds.drop(1).take(aiOpponentCount).associateWith { "random" },
        )
        val game = Game(
            tableState = initialized.tableState,
            flowConfig = GameFlowConfig(),
            hostId = playerIds.first(),
            roomPlayerIds = playerIds,
        )
        return DebugGameScenarioContext(game, playerIds.first())
    }

    /** 以呼叫者為 0，依打牌順序取得座位。 */
    private fun TableState.seat(offset: Int): MahjongPlayer = players[(currentPlayerIndex + offset) % players.size]

    /** 呼叫者打出剛摸到的牌後、他家行動時的手牌狀態。 */
    private fun TableState.waitingSeat(offset: Int): MahjongPlayer = seat(offset).let { player ->
        player.copy(hand = player.hand.copy(lastDrawn = null))
    }

    private fun TableState.validator(): LegalActionValidator = moduleRegistry.getModule(config).createLegalActionValidator()

    private fun TableState.liveWallTile(index: Int): IdentifiedTile = tileWall.getAllTiles()[index]

    private fun TableState.liveWallFront(count: Int): List<Tile> = tileWall.getAllTiles().take(count).map { it.tile }

    /** [offset] 座位對 [discarderOffset] 座位打出的一張 [tile] 可做的反應。 */
    private fun TableState.reactionsOf(
        offset: Int,
        discarderOffset: Int,
        tile: IdentifiedTile,
    ): List<GameAction> = validator().getLegalActions(
        tableState = this,
        player = waitingSeat(offset),
        sourceAction = GameAction.Discard(tile.id),
        sourceDirection = relativeDirectionOf(seat(offset).id, seat(discarderOffset).id),
        incomingTile = tile,
    )

    private fun TableState.canRon(
        offset: Int,
        discarderOffset: Int,
        tile: Tile,
    ): Boolean = reactionsOf(offset, discarderOffset, IdentifiedTile(Uuid.random(), tile)).any { it is GameAction.Ron }

    /** [offset] 座位摸到活牌第一張後，自己回合可宣告的槓。 */
    private fun TableState.ownTurnKans(offset: Int): List<GameAction.Kan> {
        val player = seat(offset).let { it.copy(hand = it.hand.copy(lastDrawn = liveWallTile(0))) }
        return validator().getLegalActions(
            tableState = this,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
        ).filterIsInstance<GameAction.Kan>()
    }

    /** [offset] 座位能否搶下家以活牌第一張宣告的槓。 */
    private fun TableState.canRobKan(offset: Int, type: GameAction.KanType): Boolean {
        val kanTile = liveWallTile(0)
        return validator().getLegalActions(
            tableState = this,
            player = waitingSeat(offset),
            sourceAction = GameAction.Kan(type, kanTile.id, emptyList()),
            sourceDirection = relativeDirectionOf(seat(offset).id, seat(1).id),
            incomingTile = kanTile,
        ).any { it is GameAction.Ron }
    }

    private companion object {
        /** 房間設定可選的赤寶牌張數。 */
        val RED_DORA_COUNTS: List<Int> = listOf(0, 3, 4)

        val ONE_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 1)
        val FOUR_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 4)
        val NINE_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 9)
        val NINE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 9)
    }
}
