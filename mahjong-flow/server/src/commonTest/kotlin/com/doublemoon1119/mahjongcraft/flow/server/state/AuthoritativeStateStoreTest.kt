package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 Room 與 Game 共用狀態儲存的交易與 dirty tracking。 */
class AuthoritativeStateStoreTest {
    /** 驗證兩個 repository 透過同一個 store 完成基本新增、讀取與刪除。 */
    @Test
    fun `room and game repositories share one store`() = runTest {
        val store = AuthoritativeStateStore()
        val roomRepository = RoomRepositoryImpl(store)
        val gameRepository = GameRepositoryImpl(store)
        val room = createRoom()
        val game = FakeTableStateFactory.create()

        roomRepository.setRoom(room)
        gameRepository.setTableState(game)

        assertEquals(room, roomRepository.getRoom(room.id))
        assertEquals(game, gameRepository.getTableState(game.id))
        assertEquals(setOf(room.id), store.snapshot().rooms.keys)
        assertEquals(setOf(game.id), store.snapshot().games.keys)

        roomRepository.removeRoom(room.id)
        gameRepository.removeTableState(game.id)

        assertNull(roomRepository.getRoom(room.id))
        assertNull(gameRepository.getTableState(game.id))
    }

    /** 驗證純讀取與無實際變更的交易不會標記 dirty 或通知 listener。 */
    @Test
    fun `reads and unchanged updates remain clean`() = runTest {
        val store = AuthoritativeStateStore()
        var notifications = 0
        store.setDirtyListener { notifications++ }

        store.getRoom(Uuid.random())
        store.snapshot()
        store.update { state -> AuthoritativeStateUpdate(state, Unit) }

        assertFalse(store.isDirty())
        assertEquals(0, notifications)
    }

    /** 驗證每次實際變更都標記 dirty 並通知平台 listener。 */
    @Test
    fun `mutations mark dirty and notify listener`() = runTest {
        val store = AuthoritativeStateStore()
        val repository = RoomRepositoryImpl(store)
        var notifications = 0
        store.setDirtyListener { notifications++ }

        repository.setRoom(createRoom())

        assertTrue(store.isDirty())
        assertEquals(1, notifications)

        store.markClean()

        assertFalse(store.isDirty())
    }

    /** 驗證載入存檔會整批替換狀態且維持 clean。 */
    @Test
    fun `load replaces all state without marking dirty`() = runTest {
        val store = AuthoritativeStateStore()
        val oldRoom = createRoom()
        RoomRepositoryImpl(store).setRoom(oldRoom)
        val loadedRoom = createRoom()
        val loadedGame = FakeTableStateFactory.create()

        store.load(
            AuthoritativeStateSnapshot(
                rooms = mapOf(loadedRoom.id to loadedRoom),
                games = mapOf(loadedGame.id to loadedGame),
            ),
        )

        assertFalse(store.isDirty())
        assertNull(store.getRoom(oldRoom.id))
        assertEquals(loadedRoom, store.getRoom(loadedRoom.id))
        assertEquals(loadedGame, store.getGame(loadedGame.id))
    }

    /** 驗證 Room → Game 能在單次交易中完成且只通知一次。 */
    @Test
    fun `room to game transition commits atomically`() = runTest {
        val store = AuthoritativeStateStore()
        val room = createRoom()
        store.load(AuthoritativeStateSnapshot(rooms = mapOf(room.id to room)))
        val game = FakeTableStateFactory.create(id = room.id)
        var notifications = 0
        store.setDirtyListener { notifications++ }

        store.update { state ->
            AuthoritativeStateUpdate(
                state.copy(
                    rooms = state.rooms - room.id,
                    games = state.games + (game.id to game),
                ),
                Unit,
            )
        }

        val snapshot = store.snapshot()
        assertTrue(snapshot.rooms.isEmpty())
        assertEquals(game, snapshot.games[room.id])
        assertEquals(1, notifications)
    }

    /** 驗證相同桌子 ID 不可透過不同 repository 同時保存為 Room 與 Game。 */
    @Test
    fun `same table ID cannot be stored as room and game`() = runTest {
        val store = AuthoritativeStateStore()
        val room = createRoom()
        RoomRepositoryImpl(store).setRoom(room)

        assertFailsWith<IllegalArgumentException> {
            GameRepositoryImpl(store).setTableState(FakeTableStateFactory.create(id = room.id))
        }
    }

    /** 建立最小等待階段 Room。 */
    private fun createRoom(): Room {
        val hostId = Uuid.random()
        return Room(
            id = Uuid.random(),
            hostId = hostId,
            config = FakeMahjongRuleConfig(),
            playerIds = setOf(hostId),
        )
    }
}
