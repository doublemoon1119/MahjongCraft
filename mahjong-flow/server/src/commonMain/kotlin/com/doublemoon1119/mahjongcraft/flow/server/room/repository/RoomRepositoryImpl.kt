package com.doublemoon1119.mahjongcraft.flow.server.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single(binds = [RoomRepository::class])
class RoomRepositoryImpl : RoomRepository {
    private val rooms = mutableMapOf<Uuid, Room>()
    private val mutex = Mutex()

    override suspend fun getRoom(id: Uuid): Room? = mutex.withLock { rooms[id] }

    override suspend fun setRoom(room: Room) = mutex.withLock { rooms[room.id] = room }

    override suspend fun removeRoom(id: Uuid) = mutex.withLock { rooms.remove(id); Unit }

    override suspend fun <T> update(id: Uuid, block: suspend (Room?) -> Pair<Room?, T>): T = mutex.withLock {
        val (next, result) = block(rooms[id])
        if (next == null) rooms.remove(id) else rooms[id] = next
        result
    }
}
