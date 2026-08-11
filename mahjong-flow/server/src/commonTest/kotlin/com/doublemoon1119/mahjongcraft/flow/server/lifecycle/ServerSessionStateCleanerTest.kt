package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ServerSessionStateCleaner] 清除權威狀態、membership 與 observer snapshot 的整合測試。 */
class ServerSessionStateCleanerTest {
    /** 切換 server session 後所有 repository 都不得殘留前一個世界的資料。 */
    @Test
    fun `test clear removes room game and observer snapshots from the previous server session`() = runTest {
        val store = AuthoritativeStateStore()
        val roomRepository = RoomRepositoryImpl(store)
        val gameRepository = GameRepositoryImpl(store)
        val roomSnapshots = RoomSnapshotRepositoryImpl()
        val gameSnapshots = GameSnapshotRepositoryImpl()
        val memberships = PlayerMembershipRepositoryImpl()
        val clock = FixedMonotonicClock()
        val decisionTimerManager = GameDecisionTimerManager(
            gameRepository = gameRepository,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(clock),
            clock = clock,
        )
        val cleaner = ServerSessionStateCleaner(
            store,
            roomSnapshots,
            gameSnapshots,
            memberships,
            decisionTimerManager,
        )
        val observerId = Uuid.random()

        val room = Room(
            id = Uuid.random(),
            hostId = observerId,
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            playerIds = setOf(observerId),
        )
        val game = FakeTableStateFactory.create()
        store.load(
            com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot(
                rooms = mapOf(room.id to room),
                games = mapOf(game.id to Game(game, GameFlowConfig())),
            ),
        )
        roomSnapshots.setSnapshot(observerId, room.toSnapshot(observerId))
        gameSnapshots.setSnapshot(observerId, game.toSnapshot(setOf(observerId)))
        memberships.claim(observerId, room.id)

        cleaner.clear()

        assertNull(roomRepository.getRoom(room.id))
        assertNull(gameRepository.getTableState(game.id))
        assertNull(roomSnapshots.getSnapshot(room.id, observerId))
        assertNull(gameSnapshots.getSnapshot(game.id, observerId))
        assertTrue(roomSnapshots.getAllObservers(room.id).isEmpty())
        assertTrue(gameSnapshots.getAllObservers(game.id).isEmpty())
        assertNull(memberships.getTableId(observerId))
        assertFalse(store.isDirty())
    }
}

/** session cleanup 測試使用的固定單調時間來源。 */
private class FixedMonotonicClock : MonotonicClock {
    /** 固定回傳 session 起點。 */
    override fun nowMillis(): Long = 0L
}
