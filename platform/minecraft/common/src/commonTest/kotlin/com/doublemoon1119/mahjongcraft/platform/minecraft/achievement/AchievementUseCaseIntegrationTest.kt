package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.bundled.BundledRiichiExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverDefinition
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiGameEventPublicMarkerResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.CommittedFactsListener
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement.RiichiAchievementIds
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證真實自摸用例經事件投影後，取得完整且不重複的成就與統計。 */
class AchievementUseCaseIntegrationTest {
    /** 以大三元役滿測試實際自摸結算，同時涵蓋通用與日麻專屬成果。 */
    @Test
    fun `four player tsumo awards daisangen riichi and ippatsu with win statistics`() = runTest {
        assertRealTsumoAchievements(RiichiRuleConfig())
    }

    /** 三人日麻的真實自摸提交也應保留相同的通用與日麻成果。 */
    @Test
    fun `three player tsumo awards daisangen riichi and ippatsu with win statistics`() = runTest {
        assertRealTsumoAchievements(ThreePlayerRiichiRuleConfig())
    }

    /** 執行真正的自摸結算，再斷言贏家是唯一獲獎者且只累加和牌與自摸統計。 */
    private suspend fun assertRealTsumoAchievements(config: MahjongRuleConfig) {
        val gameId = Uuid.random()
        val winnerId = Uuid.random()
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val snapshotRepository = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(repository, snapshotRepository, GameVisibilityPolicyImpl(modules))
        val detailResolvers = WinSettlementDetailResolverRegistry().apply {
            BundledRiichiExtension.registerWinSettlementDetailResolvers(this)
            freeze()
        }
        val useCase = DeclareTsumoUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            snapshotSynchronizer = snapshotSynchronizer,
            eventPublisher = FakeGameEventPublisher(),
            presentationPublisher = FakeGamePresentationPublisher(),
            winPresentationHandoff = WinPresentationHandoff(),
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = detailResolvers,
        )
        val game = game(gameId, winnerId, config)
        val submitted = mutableListOf<CommittedGameFacts>()
        store.setCommittedFactsListener(object : CommittedFactsListener {
            override fun onCommitted(sequence: Long, facts: CommittedGameFacts) {
                submitted += facts
            }

            override fun onReleased(sequence: Long) = Unit
        })
        repository.updateGame(gameId, history = { _, _, _ -> emptyList() }) { game to Unit }

        val result = useCase(gameId, winnerId)
        assertTrue(result is Outcome.Success, "The real DeclareTsumoUseCase should accept the fixture")
        val facts = assertNotNull(submitted.lastOrNull())
        val registry = GameAchievementResolverRegistryImpl().apply {
            BundledRiichiMinecraftExtension.registerGameAchievementResolvers(this)
        }
        val detector = GameAchievementDetector(registry)
        val markerResolvers = GameEventPublicMarkerResolverRegistry().apply {
            register(GameEventPublicMarkerResolverDefinition(BuiltInRuleModuleIds.RIICHI, RiichiGameEventPublicMarkerResolver))
            register(GameEventPublicMarkerResolverDefinition(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, RiichiGameEventPublicMarkerResolver))
        }
        val batch = assertNotNull(GameEventProjector(modules, markerResolvers = markerResolvers).projectBatch(facts))
        val awards = detector.detect(batch)
        assertEquals(listOf(winnerId), awards.map { it.playerId })
        val actual = awards.single().achievementIds
        assertEquals(
            setOf(
                BuiltInAchievementIds.WIN,
                BuiltInAchievementIds.SELF_DRAW_WIN,
                RiichiAchievementIds.YAKUMAN,
                checkNotNull(RiichiAchievementIds.yakuman(YakuType.Daisangen)),
                RiichiAchievementIds.RIICHI_WIN,
                RiichiAchievementIds.IPPATSU,
            ),
            actual,
        )
        assertEquals(setOf(AchievementStatisticIds.WINS, AchievementStatisticIds.SELF_DRAW_WINS), AchievementStatisticIds.statisticsFor(actual))
    }

    /** 建立莊家持有大三元役滿、自摸且已立直一發的測試對局。 */
    private fun game(gameId: Uuid, winnerId: Uuid, config: MahjongRuleConfig): Game {
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.EAST,
            hand = Hand(
                tiles = listOf(
                    Tile.Honor.Red,
                    Tile.Honor.Red,
                    Tile.Honor.Green,
                    Tile.Honor.Green,
                    Tile.Honor.Green,
                    Tile.Honor.White,
                    Tile.Honor.White,
                    Tile.Honor.White,
                    Tile.Numeric(Tile.Suit.Bamboo, 1),
                    Tile.Numeric(Tile.Suit.Bamboo, 2),
                    Tile.Numeric(Tile.Suit.Bamboo, 3),
                    Tile.Numeric(Tile.Suit.Dot, 5),
                    Tile.Numeric(Tile.Suit.Dot, 5),
                ).map(FakeIdentifiedTileFactory::create),
                lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.Red),
            ),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(
                riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East),
                isIppatsu = true,
            ),
        )
        val winds = if (config is ThreePlayerRiichiRuleConfig) {
            listOf(Wind.EAST, Wind.SOUTH, Wind.WEST)
        } else {
            listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH)
        }
        val players = winds.map { seat ->
            if (seat == Wind.EAST) winner else FakeMahjongPlayerFactory.create(initialSeat = seat)
        }
        return Game(
            tableState = FakeTableStateFactory.create(
                id = gameId,
                players = players,
                config = config,
                currentPlayerIndex = 0,
            ),
            flowConfig = GameFlowConfig(),
        )
    }
}
