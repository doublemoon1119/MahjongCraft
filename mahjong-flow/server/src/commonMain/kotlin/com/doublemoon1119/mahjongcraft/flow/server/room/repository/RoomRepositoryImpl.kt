package com.doublemoon1119.mahjongcraft.flow.server.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single(binds = [RoomRepository::class])
class RoomRepositoryImpl(
    private val store: AuthoritativeStateStore,
) : RoomRepository {
    override suspend fun getRoom(id: Uuid): Room? = store.getRoom(id)

    override suspend fun setRoom(room: Room) = store.update { state ->
        AuthoritativeStateUpdate(state.copy(rooms = state.rooms + (room.id to room)), Unit)
    }

    override suspend fun removeRoom(id: Uuid) = store.update { state ->
        AuthoritativeStateUpdate(state.copy(rooms = state.rooms - id), Unit)
    }

    override suspend fun clearAll() = store.update { state ->
        AuthoritativeStateUpdate(state.copy(rooms = emptyMap()), Unit)
    }

    override suspend fun <T> update(id: Uuid, block: suspend (Room?) -> Pair<Room?, T>): T = store.update { state ->
        val (next, result) = block(state.rooms[id])
        val rooms = if (next == null) state.rooms - id else state.rooms + (id to next)
        AuthoritativeStateUpdate(state.copy(rooms = rooms), result)
    }
}
