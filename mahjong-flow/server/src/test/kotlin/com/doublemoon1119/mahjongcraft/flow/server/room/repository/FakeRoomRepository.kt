package com.doublemoon1119.mahjongcraft.flow.server.room.repository

import com.doublemoon1119.mahjongcraft.logic.room.Room
import java.util.*

/**
 * 供測試使用的 [RoomRepository] 簡易實作。
 */
class FakeRoomRepository : RoomRepository {
    private val rooms = mutableMapOf<UUID, Room>()
    override suspend fun getRoom(id: UUID): Room? = rooms[id]
    override suspend fun setRoom(room: Room) { rooms[room.id] = room }
    override suspend fun removeRoom(id: UUID) { rooms.remove(id) }
}