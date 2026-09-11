package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiSupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** Development-only 權威對局情境的 registry、fixture 與驗證測試。 */
class DebugGameScenarioTest {
    /** Registry 應以穩定順序列出並解析首批五個情境。 */
    @Test
    fun `test registry lists and resolves built in scenarios`() {
        val registry = DebugGameScenarioRegistry()

        assertEquals(EXPECTED_IDS, registry.getAll().map { it.id })
        EXPECTED_IDS.forEach { id -> assertNotNull(registry.get(id)) }
        assertEquals(null, registry.get("mahjongcraft:unknown"))
    }

    /** 重複建立同一情境時應保留語意並換用全新的牌 UUID。 */
    @Test
    fun `test repeated scenario builds preserve semantics with fresh tile ids`() {
        val fixture = createFixture()
        val scenario = DebugGameScenarioRegistry().get("mahjongcraft:riichi_before_kan_4") ?: error("Missing scenario")

        val first = scenario.build(fixture.context)
        val second = scenario.build(fixture.context)

        fixture.validator.validate(fixture.context, first)
        fixture.validator.validate(fixture.context, second)
        assertEquals(first.game.tableState.players.map { it.id }, second.game.tableState.players.map { it.id })
        assertEquals(3, (first.game.tableState.dynamicRuleState as RiichiDynamicState).completedSupplementalDrawCount)
        assertEquals(
            3,
            first.game.tableState.players.sumOf { player ->
                player.hand.melds.count { it.type == MeldType.CLOSED_KAN }
            },
        )
        assertNotEquals(allTileIds(first), allTileIds(second))
    }

    /** 四個槓牌情境都應停在呼叫者恰好只能宣告一次預定暗槓、且不能意外自摸的位置。 */
    @Test
    fun `test kan scenarios expose the intended legal closed kan`() {
        val fixture = createFixture()
        val registry = DebugGameScenarioRegistry()

        (1..4).forEach { number ->
            val result = registry.get("mahjongcraft:riichi_before_kan_$number")!!.build(fixture.context)
            fixture.validator.validate(fixture.context, result)
            val state = result.game.tableState
            val module = fixture.moduleRegistry.getModule(state.config)
            val legalActions = module.createLegalActionValidator().getLegalActions(
                tableState = state,
                player = state.currentPlayer,
                sourceAction = GameAction.Draw,
                sourceDirection = RelativeDirection.Self,
            )

            val closedKans = legalActions.filterIsInstance<GameAction.Kan>().filter {
                it.type == GameAction.KanType.CLOSED_KAN
            }
            assertEquals(number - 1, (state.dynamicRuleState as RiichiDynamicState).completedSupplementalDrawCount)
            assertEquals(1, closedKans.size, "Scenario $number must expose exactly one closed kan")
            assertFalse(GameAction.Tsumo in legalActions, "Scenario $number must not expose an accidental tsumo")
            assertEquals(71 - number * 2, state.tileWall.remainingCount)
            assertEquals(14, state.reservedWallTiles.size)
            assertEquals((number - 1) * 2, state.players.sumOf { it.discardPile.entries.size })
        }
    }

    /** Debug 情境中的對手不應因填充手牌意外取得明槓選項。 */
    @Test
    fun `test kan scenarios do not give opponents accidental open kans`() {
        val fixture = createFixture()
        val registry = DebugGameScenarioRegistry()

        (1..4).forEach { number ->
            val state = registry.get("mahjongcraft:riichi_before_kan_$number")!!.build(fixture.context).game.tableState
            val module = fixture.moduleRegistry.getModule(state.config)
            val validator = module.createLegalActionValidator()
            val actor = state.currentPlayer
            val discardCandidates = actor.hand.standingTiles + listOfNotNull(actor.hand.lastDrawn)

            state.players.filterNot { it.id == actor.id }.forEach { opponent ->
                discardCandidates.forEach { discardedTile ->
                    val reactions = validator.getLegalActions(
                        tableState = state,
                        player = opponent,
                        sourceAction = GameAction.Discard(discardedTile.id),
                        sourceDirection = state.relativeDirectionOf(opponent.id, actor.id),
                        incomingTile = discardedTile,
                    )
                    assertFalse(
                        reactions.any { it is GameAction.Kan && it.type == GameAction.KanType.OPEN_KAN },
                        "Scenario $number gave an opponent an accidental open kan for ${discardedTile.tile}",
                    )
                }
            }
        }
    }

    /** 情境的剩餘活牌格位應是正式發牌前綴與既有補牌尾端消耗後的連續區段。 */
    @Test
    fun `test kan scenarios preserve physical live wall history`() {
        val fixture = createFixture()
        val registry = DebugGameScenarioRegistry()
        val templateModule = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())
        val templateTiles = templateModule.createWallFactory().create().getAllTiles()
        val template = templateModule.createWallLayout().resolve(templateTiles, WallOpening(0, 8))
        val templateLivePositions = template.drawOrder.map { template.structure.getValue(it.id) }
        val templateReservedPositions = template.reservedWallTiles.map { template.structure.getValue(it.id) }

        (1..4).forEach { number ->
            val result = registry.get("mahjongcraft:riichi_before_kan_$number")!!.build(fixture.context)
            val state = result.game.tableState
            val completedKanCount = number - 1
            val consumedFrontCount = INITIAL_DEAL_TILE_COUNT + completedKanCount + CURRENT_DRAW_TILE_COUNT
            val expectedLivePositions = templateLivePositions
                .drop(consumedFrontCount)
                .dropLast(completedKanCount)
            val actualLivePositions = state.tileWall.getAllTiles().map { result.wallStructure.getValue(it.id) }
            val actualReservedPositions = state.reservedWallTiles.map { result.wallStructure.getValue(it.id) }

            assertEquals(expectedLivePositions, actualLivePositions, "Scenario $number has non-contiguous live wall slots")
            val expectedReservedPositions = templateReservedPositions.drop(completedKanCount) +
                (0 until completedKanCount).map { offset ->
                    templateLivePositions[templateLivePositions.lastIndex - offset]
                }
            assertEquals(expectedReservedPositions, actualReservedPositions, "Scenario $number has incorrect dead wall slots")
        }
    }

    /** 下一次補牌應取出情境編號對應的正式嶺上槽位，並只推進一次補牌計數。 */
    @Test
    fun `test kan scenarios draw from the intended supplemental slot`() {
        val fixture = createFixture()
        val registry = DebugGameScenarioRegistry()

        (1..4).forEach { number ->
            val result = registry.get("mahjongcraft:riichi_before_kan_$number")!!.build(fixture.context)
            val state = result.game.tableState
            val kanAction = fixture.moduleRegistry.getModule(state.config)
                .createLegalActionValidator()
                .getLegalActions(state, state.currentPlayer, GameAction.Draw, RelativeDirection.Self)
                .filterIsInstance<GameAction.Kan>()
                .single { it.type == GameAction.KanType.CLOSED_KAN }
            val decision = RiichiSupplementalDrawPolicy.resolve(
                SupplementalDrawContext(state, state, state.currentPlayer.id, kanAction),
            ) as SupplementalDrawDecision.Completed

            assertEquals(state.reservedWallTiles.first().id, decision.drawnTiles.single().id)
            assertEquals(number, (decision.dynamicRuleState as RiichiDynamicState).completedSupplementalDrawCount)
            assertEquals(state.tileWall.remainingCount - 1, decision.tileWall.remainingCount)
        }
    }

    /** 建立測試所需的正式日麻遊戲、module registry 與 validator。 */
    private fun createFixture(): Fixture {
        val playerIds = List(4) { Uuid.random() }
        val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())
        val initialized = GameInitializer.initialize(Uuid.random(), playerIds, module)
        val game = Game(
            tableState = initialized.tableState,
            flowConfig = GameFlowConfig(),
            hostId = playerIds.first(),
            roomPlayerIds = playerIds,
        )
        val moduleRegistry = MahjongModuleRegistryImpl().apply {
            registerBuiltInRuleModules()
            freeze()
        }
        val context = DebugGameScenarioContext(game, playerIds.first())
        return Fixture(context, moduleRegistry, DebugGameScenarioValidator(moduleRegistry))
    }

    /** 收集結果內所有權威牌 UUID。 */
    private fun allTileIds(result: DebugGameScenarioResult): Set<Uuid> = result.game.tableState.let { state ->
        (
            state.players.flatMap { it.hand.allTiles + it.discardPile.entries.map { entry -> entry.tile } } +
                state.tileWall.getAllTiles() + state.reservedWallTiles
            ).mapTo(mutableSetOf()) { it.id }
    }

    /** 測試共用的遊戲 context 與規則依賴。 */
    private data class Fixture(
        /** 情境建構輸入。 */
        val context: DebugGameScenarioContext,
        /** 已註冊內建規則的 module registry。 */
        val moduleRegistry: MahjongModuleRegistryImpl,
        /** 權威情境 validator。 */
        val validator: DebugGameScenarioValidator,
    )

    private companion object {
        /** 四人日麻完成開局發牌後離開牌牆的牌張數。 */
        const val INITIAL_DEAL_TILE_COUNT: Int = 52

        /** 情境停點前，當前玩家已完成的一次一般摸牌。 */
        const val CURRENT_DRAW_TILE_COUNT: Int = 1

        /** 首批情境的固定排序。 */
        val EXPECTED_IDS: List<String> = listOf(
            "mahjongcraft:riichi_before_kan_1",
            "mahjongcraft:riichi_before_kan_2",
            "mahjongcraft:riichi_before_kan_3",
            "mahjongcraft:riichi_before_kan_4",
            "mahjongcraft:riichi_wall_initial",
        )
    }
}
