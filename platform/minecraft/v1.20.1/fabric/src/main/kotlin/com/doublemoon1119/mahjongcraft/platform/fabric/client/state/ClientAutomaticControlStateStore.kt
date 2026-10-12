package com.doublemoon1119.mahjongcraft.platform.fabric.client.state

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import org.koin.core.annotation.Single

/**
 * 保存本地玩家本局自動操作的暫存權威資料。
 *
 * 這份資料只存在目前 client 執行期間，不會寫入設定檔；伺服器重新提供快照時，呼叫端應以
 * [applySnapshot] 套用。這裡不執行任何遊戲動作，也不判斷畫面或輸入狀態。
 */
@Single
class ClientAutomaticControlStateStore {
    /** 最後接受的權威快照；尚未同步或已清除時為 null。 */
    private var currentSnapshot: AutomaticControlSnapshotDto? = null

    /** 等待伺服器回覆的單一更新請求。 */
    private var currentPendingRequest: AutomaticControlUpdateRequestDto? = null

    /** 本地狀態變更通知序號，清除資料時不歸零。 */
    private var notificationRevision: Long = 0L

    /** 目前最後一份被接受的個人權威快照。 */
    fun snapshot(): AutomaticControlSnapshotDto? = currentSnapshot

    /** 目前等待伺服器回覆的請求；此 store 同時最多保留一筆。 */
    fun pendingRequest(): AutomaticControlUpdateRequestDto? = currentPendingRequest

    /** 目前狀態的本地通知序號；任何可觀察狀態變更都會遞增，清除後不會回退。 */
    fun notificationRevision(): Long = notificationRevision

    /**
     * 尚無等待中請求時保存新請求，避免取代仍待確認的更新。
     *
     * @param request 要等待伺服器確認的請求。
     * @return 成功保存時為 true；已有等待中請求時為 false。
     */
    fun trySetPendingRequest(request: AutomaticControlUpdateRequestDto): Boolean {
        if (currentPendingRequest != null) return false
        currentPendingRequest = request
        notificationRevision += 1L
        return true
    }

    /**
     * 送出失敗時移除識別碼相符的等待中請求；其他請求不受影響。
     *
     * @param requestId 送出失敗的請求識別碼。
     * @return 找到並移除相符請求時為 true。
     */
    fun discardPendingRequest(requestId: String): Boolean {
        if (currentPendingRequest?.requestId != requestId) return false
        currentPendingRequest = null
        notificationRevision += 1L
        return true
    }

    /**
     * 套用伺服器提供的權威快照。
     *
     * 同一局只接受較新的 revision；不同局代表新的生命週期，可以取代舊局，即使 revision 較小也一樣。
     * 回傳值表示快照是否真的改變了 store。
     *
     * @param snapshot 新收到的權威快照。
     * @return 快照被接受並更新暫存狀態時為 true。
     */
    fun applySnapshot(snapshot: AutomaticControlSnapshotDto): Boolean {
        val previous = currentSnapshot
        if (previous != null && previous.gameId == snapshot.gameId && snapshot.revision <= previous.revision) {
            return false
        }
        if (currentPendingRequest?.gameId != snapshot.gameId) currentPendingRequest = null
        currentSnapshot = snapshot
        notificationRevision += 1L
        return true
    }

    /**
     * 套用更新結果。只有結果的請求識別碼與對局識別碼都符合等待中請求時，才會清除該請求。
     * 若結果附帶快照，仍遵守 [applySnapshot] 的對局與 revision 規則。
     *
     * @param result 伺服器回覆的更新結果。
     * @return 結果與等待中請求相符並完成處理時為 true。
     */
    fun applyResult(result: AutomaticControlUpdateResultDto): Boolean {
        val pending = currentPendingRequest
        if (pending?.requestId != result.requestId || pending.gameId != result.gameId) return false
        currentPendingRequest = null
        result.snapshot?.let(::applySnapshot)
        notificationRevision += 1L
        return true
    }

    /** 清除本局快照與未完成請求；本地通知序號刻意保留，以維持單調性。 */
    fun clear() {
        if (currentSnapshot == null && currentPendingRequest == null) return
        currentSnapshot = null
        currentPendingRequest = null
        notificationRevision += 1L
    }
}
