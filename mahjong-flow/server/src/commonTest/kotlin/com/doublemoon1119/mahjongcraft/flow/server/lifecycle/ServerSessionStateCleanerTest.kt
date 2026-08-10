package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ServerSessionStateCleaner] 清除權威狀態與 observer snapshot 的整合測試。 */
class ServerSessionStateCleanerTest {
    /** 切換 server session 後四個 repository 都不得殘留前一個世界的資料。 */
    @Test
    fun `test clear removes room game and observer snapshots from the previous server session`() = runTest {
        val roomRepository = RoomRepositoryImpl()
        val gameRepository = GameRepositoryImpl()
        val roomSnapshots = RoomSnapshotRepositoryImpl()
        val gameSnapshots = GameSnapshotRepositoryImpl()
        val cleaner = ServerSessionStateCleaner(roomRepository, gameRepository, roomSnapshots, gameSnapshots)
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

        cleaner.clear()

        assertNull(roomRepository.getRoom(room.id))
        assertNull(gameRepository.getTableState(game.id))
        assertNull(roomSnapshots.getSnapshot(room.id, observerId))
        assertNull(gameSnapshots.getSnapshot(game.id, observerId))
        assertTrue(roomSnapshots.getAllObservers(room.id).isEmpty())
        assertTrue(gameSnapshots.getAllObservers(game.id).isEmpty())
    }
}
