package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.model.DrawFollowUpPresentationStep
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoring
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoringPolicy
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpDecision
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpPolicy
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.NoOpWallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/**
 * 以正式摸牌流程的呈現呼叫順序驗證桌角寬度：每個座位最後擺放牌區用的寬度，都要等於桌上物件同步後的寬度。
 *
 * 測試規則摸到花就自動移出並補牌，移出時把點數放進點數池；點數池擺在莊家桌角，越多越寬。
 */
class TableCornerLayoutFlowTest {
    /** 莊家自己移出時，擺牌區當下就依新寬度讓開，物件同步後不必再移動。 */
    @Test
    fun `the dealer's own set aside lays out with the widened corner`() = runTest {
        val fixture = Fixture(drawerSeat = DEALER_SEAT)

        val result = fixture.drawFlowerAndReplay()

        assertEquals(result.propsWidths, result.laidOutWidths)
        assertEquals(STICK_WIDTH, result.laidOutWidths.getValue(DEALER_SEAT))
        assertEquals(emptyMap(), result.plans.single().slidingSeatWidths)
    }

    /** 別人移出讓莊家桌角變寬時，莊家的牌區一起平移到新寬度，物件等平移結束才擺上。 */
    @Test
    fun `another player's set aside slides the dealer's area before the props appear`() = runTest {
        val fixture = Fixture(drawerSeat = DEALER_SEAT + 1)

        val result = fixture.drawFlowerAndReplay()

        assertEquals(result.propsWidths, result.laidOutWidths)
        assertEquals(mapOf(DEALER_SEAT to STICK_WIDTH), result.plans.single().slidingSeatWidths)
        assertEquals(true, result.plans.single().presentsPropsAfterSlide)
    }

    /**
     * 重播的結果。
     *
     * @property laidOutWidths 各座位最後擺放牌區用的寬度。
     * @property propsWidths 桌上物件同步後的寬度。
     * @property plans 每次物件同步的做法。
     */
    private data class ReplayResult(
        val laidOutWidths: Map<Int, Double>,
        val propsWidths: Map<Int, Double>,
        val plans: List<CornerPropsUpdatePlan>,
    )

    /**
     * 測試用的桌子與正式摸牌用例。
     *
     * @param drawerSeat 摸牌玩家的座位。
     */
    private class Fixture(private val drawerSeat: Int) {
        /** 對局身分。 */
        private val gameId = Uuid.random()

        /** 各座位的玩家。 */
        private val playerIds = List(PLAYER_COUNT) { Uuid.random() }

        /** 權威狀態。 */
        private val repository = GameRepositoryImpl(AuthoritativeStateStore())

        /** 依序記錄的呈現呼叫。 */
        private val calls = mutableListOf<PresentationCall>()

        /** 透過公開 extension bootstrap 登記的規則。 */
        private val modules = bundledCoreExtensionRegistries(additionalExtensions = listOf(PotExtension)).moduleRegistry

        /** 正式摸牌用例。 */
        private val draw = DrawTileUseCase(
            repository,
            modules,
            GameSnapshotSynchronizer(repository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(modules)),
            FakeGameEventPublisher(),
            RecordingPublisher(calls),
        )

        /**
         * 讓 [drawerSeat] 摸到一張花，再依呈現呼叫的順序把擺牌區與物件同步交給 planner。
         *
         * @return 重播的結果。
         */
        suspend fun drawFlowerAndReplay(): ReplayResult {
            val initial = table()
            repository.setTableState(initial)
            val planner = TableCornerLayoutPlanner(TableCornerWidthTracker())
            val laidOut = PotCornerDescriber.cornerWidthsBySeat(initial).toMutableMap()
            planner.recordRound(gameId, initial, laidOut)

            assertIs<Outcome.Success<Unit>>(draw(gameId, playerIds[drawerSeat]))

            val committed = requireNotNull(repository.getTableState(gameId))
            val currentWidths = PotCornerDescriber.cornerWidthsBySeat(committed)
            val plans = mutableListOf<CornerPropsUpdatePlan>()
            calls.forEach { call ->
                when (call) {
                    is PresentationCall.PlayerArea ->
                        laidOut[call.seatIndex] = planner.widthForPlayerArea(gameId, committed, call.seatIndex, currentWidths)
                    PresentationCall.RuleState -> {
                        val plan = planner.planRuleStateUpdate(gameId, committed, currentWidths)
                        laidOut.putAll(plan.slidingSeatWidths)
                        plans += plan
                    }
                }
            }
            return ReplayResult(laidOut, currentWidths, plans)
        }

        /** @return 輪到 [drawerSeat] 摸牌、牌山最前面是花的桌況。 */
        private fun table(): TableState = FakeTableStateFactory.create(
            id = gameId,
            players = playerIds.mapIndexed { seat, id ->
                FakeMahjongPlayerFactory.create(initialSeat = Wind.entries[seat], id = id, hand = Hand(tiles = fillers(HAND_SIZE)))
            },
            dealerPlayerId = playerIds[DEALER_SEAT],
            config = PotRuleConfig(),
            tileWall = TileWall(listOf(tile(FLOWER)) + List(WALL_SIZE) { tile(ORDINARY) }),
            currentPlayerIndex = drawerSeat,
            dynamicRuleState = Pot(0),
        )
    }

    /** 測試只關心的兩種呈現呼叫。 */
    private sealed interface PresentationCall {
        /**
         * 擺放一個座位的牌區。
         *
         * @property seatIndex 座位。
         */
        data class PlayerArea(val seatIndex: Int) : PresentationCall

        /** 規則狀態更新、同步桌上物件。 */
        data object RuleState : PresentationCall
    }

    /**
     * 依序記錄牌區與物件同步呼叫的呈現替身，其餘呼叫交給一般替身。
     *
     * @property calls 記錄呼叫的清單。
     */
    private class RecordingPublisher(
        private val calls: MutableList<PresentationCall>,
    ) : GamePresentationPublisher by FakeGamePresentationPublisher() {
        override fun publishPlayerTilesUpdated(
            gameId: Uuid,
            seatIndex: Int,
            standingTileIds: List<Uuid>,
            drawnTileId: Uuid?,
            melds: List<MeldPresentation>,
            setAsideTileIds: List<Uuid>,
            isNewlyDrawn: Boolean,
            newlyClaimedMeldTileIds: Set<Uuid>,
            newlySetAsideTileIds: Set<Uuid>,
            followUpSteps: List<DrawFollowUpPresentationStep>,
        ) {
            calls += PresentationCall.PlayerArea(seatIndex)
        }

        override fun publishRuleStateUpdated(gameId: Uuid) {
            calls += PresentationCall.RuleState
        }
    }

    /** 莊家桌角依點數池餘額擺點棒，每 1000 點一根。 */
    private object PotCornerDescriber : TablePropDescriber {
        override fun describe(tableState: TableState): List<TablePropPlacement> = emptyList()

        override fun cornerWidth(tableState: TableState, seatIndex: Int): Double {
            if (seatIndex != tableState.dealerIndex) return 0.0
            return ((tableState.dynamicRuleState as? Pot)?.balance ?: 0) / POT_UNIT * STICK_WIDTH
        }
    }

    /**
     * 點數池。
     *
     * @property balance 餘額。
     */
    private data class Pot(val balance: Int) : DynamicRuleState

    /**
     * 已移出的花。
     *
     * @property flowers 依移出順序排列的花。
     */
    private data class Flowers(val flowers: List<IdentifiedTile>) : PlayerRuleState

    /**
     * 測試規則的配置，委派日麻設定。
     *
     * @property delegate 提供與本測試無關的共用設定。
     */
    private data class PotRuleConfig(val delegate: RiichiRuleConfig = RiichiRuleConfig()) : RiichiFamilyRuleConfig by delegate

    /** 登記測試規則。 */
    private object PotExtension : MahjongExtension {
        override val id: String = "test:pot"

        override fun registerRuleModules(registry: MahjongModuleRegistry) {
            registry.register(MahjongModuleDefinition(PotRuleConfig::class, id) { config, moduleId -> PotRule(RiichiRuleModule(moduleId, config.delegate)) })
        }

        override fun registerNetworkDtos(registries: NetworkDtoRegistries) = Unit

        override fun registerPersistenceDtos(registries: PersistenceRegistries) = Unit
    }

    /**
     * 摸到花自動移出、從牌山尾端補牌，移出時付 1000 點進點數池的測試規則；其他行為委派給日麻。
     *
     * @property delegate 提供測試未覆寫的行為；型別參數只用於已覆寫的 [config]。
     */
    @Suppress("UNCHECKED_CAST")
    private class PotRule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
    ) : MahjongRuleModule<PotRuleConfig> by (delegate as MahjongRuleModule<PotRuleConfig>) {
        override val config: PotRuleConfig = PotRuleConfig(delegate.config)

        override fun createWallRevealPolicy(): WallRevealPolicy = NoOpWallRevealPolicy

        override fun createDrawFollowUpPolicy(): DrawFollowUpPolicy = DrawFollowUpPolicy { context ->
            if (context.drawnTile.tile == FLOWER) DrawFollowUpDecision.SetAside(FLOWER_ACTION) else DrawFollowUpDecision.None
        }

        override fun createSupplementalDrawPolicy(): SupplementalDrawPolicy = SupplementalDrawPolicy { context ->
            val candidate = context.tableStateAfterAction
            val (replacement, wall) = candidate.tileWall.drawLast()
            if (replacement == null) {
                SupplementalDrawDecision.Rejected(SupplementalDrawReasonIds.WALL_EXHAUSTED)
            } else {
                SupplementalDrawDecision.Completed(listOf(replacement), wall, candidate.reservedWallTiles, candidate.dynamicRuleState)
            }
        }

        override fun createActionScoringPolicy(): ActionScoringPolicy = ActionScoringPolicy { context ->
            val balance = (context.tableStateAfterAction.dynamicRuleState as Pot).balance
            ActionScoring(mapOf(context.actorPlayerId to -POT_UNIT), potDeposit = POT_UNIT, dynamicRuleState = Pot(balance + POT_UNIT))
                .takeIf { context.action == FLOWER_ACTION }
        }

        override fun tileSetAsideBy(player: MahjongPlayer, action: GameAction.Extension): IdentifiedTile? = player.hand.lastDrawn?.takeIf { action == FLOWER_ACTION && it.tile == FLOWER }

        override fun applyTileSetAsideAction(
            tableState: TableState,
            actorPlayerId: Uuid,
            action: GameAction.Extension,
        ): TableState? {
            val actor = tableState.players.first { it.id == actorPlayerId }
            val flower = tileSetAsideBy(actor, action) ?: return null
            val updated = actor.copy(
                hand = actor.hand.copy(lastDrawn = null),
                playerRuleState = Flowers((actor.playerRuleState as? Flowers)?.flowers.orEmpty() + flower),
            ).recordAction(action)
            return tableState.copy(players = tableState.players.map { if (it.id == actorPlayerId) updated else it })
        }

        override fun setAsideTiles(player: MahjongPlayer): List<IdentifiedTile> = (player.playerRuleState as? Flowers)?.flowers.orEmpty()
    }

    /** 測試規則自動移出花的動作。 */
    private data object FlowerAction : ExtensionGameAction {
        override val id: String = "test:flower"
    }

    private companion object {
        /** 測試的人數。 */
        const val PLAYER_COUNT: Int = 4

        /** 莊家的座位。 */
        const val DEALER_SEAT: Int = 0

        /** 每位玩家的立牌張數。 */
        const val HAND_SIZE: Int = 13

        /** 花之後的一般牌張數。 */
        const val WALL_SIZE: Int = 8

        /** 一根點棒代表的點數。 */
        const val POT_UNIT: Int = 1000

        /** 一根點棒佔用的寬度。 */
        const val STICK_WIDTH: Double = 0.065

        /** 測試規則當作花的牌面。 */
        val FLOWER: Tile = Tile.Honor.White

        /** 一般牌。 */
        val ORDINARY: Tile = Tile.Numeric(Tile.Suit.Character, 5)

        /** 自動移出花的完整動作。 */
        val FLOWER_ACTION = GameAction.Extension(FlowerAction)

        /**
         * 建立一張實體牌。
         *
         * @param face 牌面。
         * @return 新的實體牌。
         */
        fun tile(face: Tile): IdentifiedTile = FakeIdentifiedTileFactory.create(face)

        /**
         * 建立不會成組的填充牌。
         *
         * @param count 張數。
         * @return 依序排列的填充牌。
         */
        fun fillers(count: Int): List<IdentifiedTile> = List(count) { index ->
            tile(Tile.Numeric(Tile.Suit.entries[index % Tile.Suit.entries.size], 2 + index / Tile.Suit.entries.size % 4 * 2))
        }
    }
}
