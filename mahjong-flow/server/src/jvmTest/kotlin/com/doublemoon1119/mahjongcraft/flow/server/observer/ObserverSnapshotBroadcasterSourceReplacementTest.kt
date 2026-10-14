package com.doublemoon1119.mahjongcraft.flow.server.observer

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.observer.model.ObserverSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverAudienceSource
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.HandReadinessVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 驗證權威來源在產生快照期間被替換時，略過的內容不會被當成已送出。 */
class ObserverSnapshotBroadcasterSourceReplacementTest {
    /** 測試場地識別碼。 */
    private val id = Uuid.random()

    /** 測試房主識別碼。 */
    private val hostId = Uuid.random()

    /** 測試觀察者識別碼。 */
    private val observerId = Uuid.random()

    /** 記錄每次送出的快照。 */
    private class RecordingSender : ObserverSnapshotSender {
        /** 依送出順序保存的快照。 */
        val sent = mutableListOf<ObserverSnapshot?>()

        override suspend fun send(id: Uuid, observerId: Uuid, snapshot: ObserverSnapshot?) {
            sent += snapshot
        }
    }

    /**
     * 在指定對局產生快照時，先以觀察者看不見差異的新實例取代 store 中的對局。
     *
     * @property store 要被替換對局的權威狀態。
     * @property delegate 實際產生快照的政策。
     */
    private class ReplacingVisibilityPolicy(
        private val store: AuthoritativeStateStore,
        private val delegate: GameVisibilityPolicy,
    ) : GameVisibilityPolicy {
        /** 下一次產生快照時要被替換的對局；替換一次後清除。 */
        var replaceOnSnapshot: Game? = null

        override fun snapshotFor(game: Game, observerId: Uuid): TableStateSnapshot {
            if (game === replaceOnSnapshot) {
                replaceOnSnapshot = null
                runBlocking { store.putGame(game.copy(automaticControlRevision = game.automaticControlRevision + 1)) }
            }
            return delegate.snapshotFor(game, observerId)
        }

        override fun roundPreparationSnapshotFor(game: Game, observerId: Uuid): RoundPreparationSnapshot? = delegate.roundPreparationSnapshotFor(game, observerId)
    }

    /** 來源在記錄前被替換時不記錄該次內容；下一輪即使內容相同也會送出。 */
    @Test
    fun `a snapshot skipped for a replaced source is sent on the next broadcast`() = runTest {
        val store = AuthoritativeStateStore()
        val registry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val visibilityPolicy = ReplacingVisibilityPolicy(store = store, delegate = GameVisibilityPolicyImpl(registry))
        val sender = RecordingSender()
        val broadcaster = ObserverSnapshotBroadcaster(
            store = store,
            visibilityPolicy = visibilityPolicy,
            handReadinessVisibilityPolicy = HandReadinessVisibilityPolicy(moduleRegistry = registry, visibilityPolicy = visibilityPolicy),
            audienceSource = object : ObserverAudienceSource {
                override suspend fun observersById(): Map<Uuid, Set<Uuid>> = mapOf(id to setOf(observerId))
            },
            sender = sender,
        )
        store.putGame(game(secondPlayerId = Uuid.random()))
        broadcaster.broadcast()
        val changedGame = game(secondPlayerId = Uuid.random())
        store.putGame(changedGame)
        visibilityPolicy.replaceOnSnapshot = store.state.value.games.getValue(id)

        broadcaster.broadcast()

        assertEquals(1, sender.sent.size)

        broadcaster.broadcast()

        assertEquals(2, sender.sent.size)
        val delivered = assertIs<ObserverSnapshot.OfGame>(sender.sent.last())
        assertEquals(GameVisibilityPolicyImpl(registry).snapshotFor(changedGame, observerId), delivered.game)
    }

    /**
     * 建立由房主控制、含兩名玩家的測試對局。
     *
     * @param secondPlayerId 第二名玩家的識別碼；不同的值讓觀察者看到不同內容。
     * @return 以測試場地 ID 建立的對局。
     */
    private fun game(secondPlayerId: Uuid) = Game(
        tableState = FakeTableStateFactory.create(
            id = id,
            players = listOf(
                FakeMahjongPlayerFactory.create(seatWind = Wind.EAST, id = hostId),
                FakeMahjongPlayerFactory.create(seatWind = Wind.SOUTH, id = secondPlayerId),
            ),
            config = RiichiRuleConfig(),
        ),
        flowConfig = GameConfig(RiichiRuleConfig()).flowConfig,
        hostId = hostId,
    )
}

/**
 * 以指定對局取代 store 中同 ID 的房間或對局。
 *
 * @param game 要保存的對局。
 */
private suspend fun AuthoritativeStateStore.putGame(game: Game) = update { state ->
    AuthoritativeStateUpdate(
        state = state.copy(rooms = state.rooms - game.id, games = state.games + (game.id to game)),
        result = Unit,
    )
}
