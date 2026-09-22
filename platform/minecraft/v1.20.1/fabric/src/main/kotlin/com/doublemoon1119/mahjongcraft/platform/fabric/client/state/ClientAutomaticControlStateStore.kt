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
    private var currentSnapshot: AutomaticControlSnapshotDto? = null
    private var currentPendingRequest: AutomaticControlUpdateRequestDto? = null
    private var notificationRevision: Long = 0L

    /** 目前最後一份被接受的個人權威快照。 */
    fun snapshot(): AutomaticControlSnapshotDto? = currentSnapshot

    /** 目前等待伺服器回覆的請求；此 store 同時最多保留一筆。 */
    fun pendingRequest(): AutomaticControlUpdateRequestDto? = currentPendingRequest

    /** 目前狀態的本地通知序號；任何可觀察狀態變更都會遞增，清除後不會回退。 */
    fun notificationRevision(): Long = notificationRevision

    /** 尚無等待中請求時保存 [request]；回傳是否成功，避免新請求取代仍待確認的更新。 */
    fun trySetPendingRequest(request: AutomaticControlUpdateRequestDto): Boolean {
        if (currentPendingRequest != null) return false
        currentPendingRequest = request
        notificationRevision += 1L
        return true
    }

    /** 送出失敗時移除與 [requestId] 相符的 pending request；其他請求不受影響。 */
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
     * 套用更新結果。只有結果的 request ID 與目前 pending request 相同時，才會清除 pending。
     * 若結果附帶快照，仍遵守 [applySnapshot] 的對局與 revision 規則。
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
