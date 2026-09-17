package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.SyncRoomSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證房間變更後，所有觀察者都取得並收到最新快照。 */
class RoomObserverSnapshotSyncTest {
    /** 更換 AI 策略後，房間成員與只開啟畫面的玩家都收到含新策略的快照。 */
    @Test
    fun `every observer receives the refreshed snapshot`() = runTest {
        val tableId = Uuid.random()
        val hostId = Uuid.random()
        val aiId = Uuid.random()
        val viewerId = Uuid.random()
        val roomRepository = RoomRepositoryImpl(AuthoritativeStateStore())
        val snapshots = FakeRoomSnapshotRepository()
        val room = Room(
            id = tableId,
            hostId = hostId,
            gameConfig = GameConfig(RiichiRuleConfig()),
            playerIds = listOf(hostId, aiId),
            readyPlayerIds = listOf(aiId),
            aiPlayerStrategyKeys = mapOf(aiId to "random"),
        )
        roomRepository.setRoom(room)
        listOf(hostId, viewerId).forEach { observerId -> snapshots.setSnapshot(observerId, room.toSnapshot(observerId)) }
        roomRepository.setRoom(room.copy(aiPlayerStrategyKeys = mapOf(aiId to "test:scripted")))
        val sentObserverIds = mutableListOf<Uuid>()

        syncRoomToObservers(
            tableId = tableId,
            snapshots = snapshots,
            syncRoom = SyncRoomSnapshotUseCase(roomRepository, snapshots),
        ) { observerId ->
            sentObserverIds += observerId
        }

        assertEquals(setOf(hostId, viewerId), sentObserverIds.toSet())
        listOf(hostId, viewerId).forEach { observerId ->
            assertEquals("test:scripted", snapshots.getSnapshot(tableId, observerId)?.aiPlayerStrategyKeys?.get(aiId))
        }
    }
}
