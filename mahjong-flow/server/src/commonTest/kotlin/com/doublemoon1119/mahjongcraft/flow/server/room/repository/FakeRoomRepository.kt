package com.doublemoon1119.mahjongcraft.flow.server.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [RoomRepository] 簡易實作。
 */
class FakeRoomRepository : RoomRepository {
    private val rooms = mutableMapOf<Uuid, Room>()
    override suspend fun getRoom(id: Uuid): Room? = rooms[id]
    override suspend fun setRoom(room: Room) { rooms[room.id] = room }
    override suspend fun removeRoom(id: Uuid) { rooms.remove(id) }
}