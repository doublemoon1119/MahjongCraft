package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.OrphanedTablePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [OrphanedTableCleanupService] 的政策、衍生狀態與 revision 測試。 */
class OrphanedTableCleanupServiceTest {
    /** REMOVE_ALL 應移除 Room、membership、observer snapshot 與位置。 */
    @Test
    fun `test remove all clears room and derived state`() = runTest {
        val fixture = Fixture(OrphanedTablePolicy.REMOVE_ALL)
        val playerId = Uuid.random()
        val observerId = Uuid.random()
        val room = Room(
            id = fixture.tableId,
            hostId = playerId,
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            playerIds = listOf(playerId),
        )
        fixture.roomRepository.setRoom(room)
        fixture.memberships.claim(playerId, fixture.tableId)
        fixture.roomSnapshots.setSnapshot(playerId, room.toSnapshot(playerId))
        fixture.roomSnapshots.setSnapshot(observerId, room.toSnapshot(observerId))

        val result = fixture.service.cleanupMissing(fixture.tableId, fixture.entryRevision)

        assertEquals(OrphanedTableCleanupResult.REMOVED_ROOM, result)
        assertNull(fixture.roomRepository.getRoom(fixture.tableId))
        assertNull(fixture.memberships.getTableId(playerId))
        assertNull(fixture.roomSnapshots.getSnapshot(fixture.tableId, playerId))
        assertNull(fixture.roomSnapshots.getSnapshot(fixture.tableId, observerId))
        assertNull(fixture.locations.get(fixture.tableId))
    }

    /** KEEP_AND_WARN 應保留進行中的 Game 與所有衍生狀態。 */
    @Test
    fun `test keep and warn retains game and location`() = runTest {
        val fixture = Fixture(OrphanedTablePolicy.KEEP_AND_WARN)
        val player = FakeMahjongPlayerFactory.create()
        val game = FakeTableStateFactory.create(id = fixture.tableId, players = listOf(player))
        fixture.gameRepository.setTableState(game)
        fixture.memberships.claim(player.id, fixture.tableId)
        fixture.gameSnapshots.setSnapshot(player.id, game.toSnapshot(setOf(player.id)))

        val result = fixture.service.cleanupMissing(fixture.tableId, fixture.entryRevision)

        assertEquals(OrphanedTableCleanupResult.RETAINED, result)
        assertNotNull(fixture.gameRepository.getTableState(fixture.tableId))
        assertEquals(fixture.tableId, fixture.memberships.getTableId(player.id))
        assertNotNull(fixture.gameSnapshots.getSnapshot(fixture.tableId, player.id))
        assertNotNull(fixture.locations.get(fixture.tableId))
    }

    /** REMOVE_WAITING_ROOM 不得移除進行中的 Game。 */
    @Test
    fun `test remove waiting room retains game`() = runTest {
        val fixture = Fixture(OrphanedTablePolicy.REMOVE_WAITING_ROOM)
        val game = FakeTableStateFactory.create(id = fixture.tableId)
        fixture.gameRepository.setTableState(game)

        val result = fixture.service.cleanupMissing(fixture.tableId, fixture.entryRevision)

        assertEquals(OrphanedTableCleanupResult.RETAINED, result)
        assertNotNull(fixture.gameRepository.getTableState(fixture.tableId))
        assertNotNull(fixture.locations.get(fixture.tableId))
    }

    /** REMOVE_WAITING_ROOM 應移除等待中的 Room。 */
    @Test
    fun `test remove waiting room clears room`() = runTest {
        val fixture = Fixture(OrphanedTablePolicy.REMOVE_WAITING_ROOM)
        val room = Room(fixture.tableId, Uuid.random(), GameConfig(FakeMahjongRuleConfig()))
        fixture.roomRepository.setRoom(room)

        val result = fixture.service.cleanupMissing(fixture.tableId, fixture.entryRevision)

        assertEquals(OrphanedTableCleanupResult.REMOVED_ROOM, result)
        assertNull(fixture.roomRepository.getRoom(fixture.tableId))
        assertNull(fixture.locations.get(fixture.tableId))
    }

    /** 玩家已獲准破壞時應略過 KEEP_AND_WARN 並移除 Game。 */
    @Test
    fun `test player break overrides orphan retention policy`() = runTest {
        val fixture = Fixture(OrphanedTablePolicy.KEEP_AND_WARN)
        fixture.gameRepository.setTableState(FakeTableStateFactory.create(id = fixture.tableId))

        val result = fixture.service.cleanupPlayerBroken(fixture.tableId, fixture.entryRevision)

        assertEquals(OrphanedTableCleanupResult.REMOVED_GAME, result)
        assertNull(fixture.gameRepository.getTableState(fixture.tableId))
        assertNull(fixture.locations.get(fixture.tableId))
    }

    /** 過期 revision 不得清除已移動桌子的狀態。 */
    @Test
    fun `test stale revision does not remove moved table`() = runTest {
        val fixture = Fixture(OrphanedTablePolicy.REMOVE_ALL)
        val oldRevision = fixture.entryRevision
        fixture.locations.put(fixture.tableId, TableLocation("minecraft:overworld", 32, 64, 0))
        val room = Room(fixture.tableId, Uuid.random(), GameConfig(FakeMahjongRuleConfig()))
        fixture.roomRepository.setRoom(room)

        val result = fixture.service.cleanupMissing(fixture.tableId, oldRevision)

        assertEquals(OrphanedTableCleanupResult.STALE_REQUEST, result)
        assertNotNull(fixture.roomRepository.getRoom(fixture.tableId))
        assertNotNull(fixture.locations.get(fixture.tableId))
    }

    /** 後續缺失桌子清理應讀取熱重載後的政策。 */
    @Test
    fun `test cleanup uses replaced config`() = runTest {
        val fixture = Fixture(OrphanedTablePolicy.KEEP_AND_WARN)
        fixture.roomRepository.setRoom(
            Room(fixture.tableId, Uuid.random(), GameConfig(FakeMahjongRuleConfig())),
        )
        fixture.configState.replace(MinecraftServerConfig(orphanedTablePolicy = OrphanedTablePolicy.REMOVE_ALL))

        val result = fixture.service.cleanupMissing(fixture.tableId, fixture.entryRevision)

        assertEquals(OrphanedTableCleanupResult.REMOVED_ROOM, result)
    }

    /** 建立單一政策測試所需的共用 repository 與位置。 */
    private class Fixture(policy: OrphanedTablePolicy) {
        /** 測試桌子 UUID。 */
        val tableId: Uuid = Uuid.random()

        /** 共用權威狀態。 */
        val store = AuthoritativeStateStore()

        /** Room repository。 */
        val roomRepository = RoomRepositoryImpl(store)

        /** Game repository。 */
        val gameRepository = GameRepositoryImpl(store)

        /** 玩家 membership repository。 */
        val memberships = PlayerMembershipRepositoryImpl()

        /** Room observer snapshot repository。 */
        val roomSnapshots = RoomSnapshotRepositoryImpl()

        /** Game observer snapshot repository。 */
        val gameSnapshots = GameSnapshotRepositoryImpl()

        /** 桌子位置索引。 */
        val locations = TableLocationRegistry()

        /** 可在測試中模擬熱重載的設定 state。 */
        val configState = MinecraftServerConfigState(MinecraftServerConfig(orphanedTablePolicy = policy))

        /** 初始位置 revision。 */
        val entryRevision: Long = locations.put(
            tableId,
            TableLocation("minecraft:overworld", 0, 64, 0),
        ).revision

        /** 受測清理服務。 */
        val service = OrphanedTableCleanupService(
            store,
            memberships,
            roomSnapshots,
            gameSnapshots,
            locations,
            configState,
        )
    }
}
