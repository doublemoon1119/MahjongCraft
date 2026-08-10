package com.doublemoon1119.mahjongcraft.flow.server.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/**
 * [RoomRepositoryImpl] 的單元測試類別。
 *
 * 著重驗證 [RoomRepositoryImpl.update] 在並發存取下的原子性，
 * 這是 [FakeRoomRepository]（單執行緒、無鎖）無法驗證的部分。
 */
class RoomRepositoryImplTest {

    /**
     * 測試多個並發的 [RoomRepositoryImpl.update] 呼叫，對同一房間各自新增一位玩家，
     * 最終應完整反映所有變更，不應有任何一次寫入因競態條件而遺失。
     */
    @Test
    fun `test update serializes concurrent mutations without losing updates`() = runTest {
        val repository = RoomRepositoryImpl(AuthoritativeStateStore())
        val roomId = Uuid.random()
        val hostId = Uuid.random()
        val config = FakeMahjongRuleConfig()
        repository.setRoom(Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId)))

        val concurrency = 200

        // 使用 Dispatchers.Default（真實多執行緒）並發呼叫，才能實際觸發競態條件；
        // 若在單一虛擬時間排程的測試調度器下執行，即使沒有鎖也無法暴露此問題。
        coroutineScope {
            repeat(concurrency) {
                launch(Dispatchers.Default) {
                    repository.update(roomId) { room ->
                        val updatedRoom = room!!.copy(playerIds = room.playerIds + Uuid.random())
                        updatedRoom to Unit
                    }
                }
            }
        }

        val finalRoom = repository.getRoom(roomId)
        assertNotNull(finalRoom)
        assertEquals(
            expected = concurrency + 1, // + 1 為初始房主
            actual = finalRoom.playerIds.size,
            message = "All concurrent updates should be reflected; none should be lost to a race condition.",
        )
    }
}
