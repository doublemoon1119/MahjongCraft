package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
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
        val cleaner = ServerSessionStateCleaner(store, roomSnapshots, gameSnapshots, memberships)
        val observerId = Uuid.random()

        val room = Room(
            id = Uuid.random(),
            hostId = observerId,
            config = FakeMahjongRuleConfig(),
            playerIds = setOf(observerId),
        )
        val game = FakeTableStateFactory.create()
        roomRepository.setRoom(room)
        gameRepository.setTableState(game)
        roomSnapshots.setSnapshot(observerId, room.toSnapshot(observerId))
        gameSnapshots.setSnapshot(observerId, game.toSnapshot(observerId))
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
