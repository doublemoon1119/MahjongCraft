package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import org.koin.core.annotation.Single

/** 清除一個 server session 的所有權威狀態與 observer read-side 快照。 */
@Single
class ServerSessionStateCleaner(
    private val roomRepository: RoomRepository,
    private val gameRepository: GameRepository,
    private val roomSnapshots: RoomSnapshotRepository,
    private val gameSnapshots: GameSnapshotRepository,
) {
    /** 清除四個 repository；持久化完成後應先 flush 權威狀態，再呼叫本方法釋放記憶體。 */
    suspend fun clear() {
        roomRepository.clearAll()
        gameRepository.clearAll()
        roomSnapshots.clearAll()
        gameSnapshots.clearAll()
    }
}
