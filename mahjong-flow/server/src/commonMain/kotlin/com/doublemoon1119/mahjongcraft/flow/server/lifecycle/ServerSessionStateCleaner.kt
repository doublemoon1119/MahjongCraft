package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import org.koin.core.annotation.Single

/** 清除一個 server session 的所有權威狀態與 observer read-side 快照。 */
@Single
class ServerSessionStateCleaner(
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val roomSnapshots: RoomSnapshotRepository,
    private val gameSnapshots: GameSnapshotRepository,
    private val membershipRepository: PlayerMembershipRepository,
    private val decisionTimerManager: GameDecisionTimerManager,
) {
    /** 清除 session 狀態；持久化完成後應先 flush，再呼叫本方法釋放記憶體且不標記 dirty。 */
    suspend fun clear() {
        decisionTimerManager.clearAll()
        authoritativeStateStore.load(AuthoritativeStateSnapshot())
        roomSnapshots.clearAll()
        gameSnapshots.clearAll()
        membershipRepository.clearAll()
    }
}
