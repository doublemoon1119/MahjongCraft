package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import org.koin.core.annotation.Single

/**
 * 清除一個伺服器工作階段的所有權威狀態與觀察者讀取快照。
 *
 * @property authoritativeStateStore 權威狀態儲存。
 * @property roomSnapshots 房間觀察者快照倉庫。
 * @property gameSnapshots 對局觀察者快照倉庫。
 * @property membershipRepository 玩家場地歸屬倉庫。
 * @property decisionTimerManager 對局決策計時器管理器。
 * @property decisionTimerSynchronizationService 決策計時器同步服務。
 */
@Single
class ServerSessionStateCleaner(
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val roomSnapshots: RoomSnapshotRepository,
    private val gameSnapshots: GameSnapshotRepository,
    private val membershipRepository: PlayerMembershipRepository,
    private val decisionTimerManager: GameDecisionTimerManager,
    private val decisionTimerSynchronizationService: DecisionTimerSynchronizationService,
) {
    /** 清除 session 狀態；持久化完成後應先 flush，再呼叫本方法釋放記憶體且不標記 dirty。 */
    suspend fun clear() {
        decisionTimerManager.clearAll()
        decisionTimerSynchronizationService.clear()
        authoritativeStateStore.load(AuthoritativeStateSnapshot())
        roomSnapshots.clearAll()
        gameSnapshots.clearAll()
        membershipRepository.clearAll()
    }
}
