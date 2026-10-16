package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryRetentionCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.PlayerIdentitySender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.ServerPlayerIdentityStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableStateDelivery
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FakeTableStateDeliveryEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledNetworkJson
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [GameEventPublisherImpl] 的對局結束證據測試。 */
class GameEventPublisherImplTest {
    /** 桌況交付被拒絕時，對局結束的參與者證據仍依權威狀態記下。 */
    @Test
    fun `ended match evidence is remembered even when delivery is rejected`() = runTest {
        val store = AuthoritativeStateStore()
        val writer = historyWriter(store)
        val publisher = publisher(store = store, historyWriter = writer)
        val hostId = Uuid.random()
        val otherId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    FakeMahjongPlayerFactory.create(seatWind = Wind.EAST, id = hostId),
                    FakeMahjongPlayerFactory.create(seatWind = Wind.SOUTH, id = otherId),
                ),
                config = FakeMahjongRuleConfig(),
            ),
            flowConfig = GameConfig(FakeMahjongRuleConfig()).flowConfig,
            hostId = hostId,
        )
        store.load(AuthoritativeStateSnapshot(games = mapOf(game.id to game)))

        publisher.publishToAllObservers(
            gameId = game.id,
            seatedPlayerIds = listOf(hostId, otherId),
            actorId = hostId,
            action = GameAction.MatchEnded,
        )

        assertEquals(setOf(hostId, otherId), writer.recentEndedMatch(game.matchId)?.participantIds)
    }

    /**
     * 建立沒有有效 server session 的發布器，所有桌況交付都會被拒絕。
     *
     * @param store 權威狀態來源。
     * @param historyWriter 接收對局結束證據的 writer。
     * @return 受測發布器。
     */
    private fun publisher(
        store: AuthoritativeStateStore,
        historyWriter: FabricHistoryOutboxWriter,
    ): GameEventPublisherImpl {
        val dispatchers = TestCoroutineDispatchers()
        val serverHolder = FabricServerHolder()
        return GameEventPublisherImpl(
            gameSnapshotRepository = GameSnapshotRepositoryImpl(),
            tableStateDelivery = FabricTableStateDelivery(
                dispatchers = dispatchers,
                environment = FakeTableStateDeliveryEnvironment(sessionId = null),
            ),
            authoritativeStateStore = store,
            serverHolder = serverHolder,
            playerIdentities = PlayerIdentitySender(
                names = ServerPlayerIdentityStore(serverHolder),
                serverHolder = serverHolder,
                dispatchers = dispatchers,
                json = bundledNetworkJson(),
            ),
            historyWriter = historyWriter,
            json = bundledNetworkJson(),
            networkRegistries = DefaultNetworkDtoRegistries(),
        )
    }

    /**
     * 建立未附加資料庫的歷史 writer。
     *
     * @param store 權威歷史來源。
     * @return 受測 writer。
     */
    private fun historyWriter(store: AuthoritativeStateStore): FabricHistoryOutboxWriter = FabricHistoryOutboxWriter(
        store = store,
        registries = bundledPersistenceRegistries(),
        moduleRegistry = MahjongModuleRegistryImpl(),
        locations = TableLocationRegistry(),
        configState = MinecraftServerConfigState(),
        retentionCoordinator = HistoryRetentionCoordinator(),
        dispatchers = TestCoroutineDispatchers(),
    )
}
