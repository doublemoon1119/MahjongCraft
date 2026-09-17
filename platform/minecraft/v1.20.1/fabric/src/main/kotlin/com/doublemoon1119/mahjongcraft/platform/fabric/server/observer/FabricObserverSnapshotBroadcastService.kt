package com.doublemoon1119.mahjongcraft.platform.fabric.server.observer

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.observer.ObserverSnapshotBroadcaster
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.koin.core.annotation.Single

/**
 * 驅動 [ObserverSnapshotBroadcaster]：server session 期間訂閱權威狀態，並在每次 tick 結束時檢查觀察者
 * 變化。
 *
 * 訂閱負責「內容變了」，tick 檢查負責「觀察者變了」；兩者都進入同一條推送流程，內容與上次相同時不會
 * 送出任何封包。推送在 [CoroutineDispatchers.main]（伺服器 tick 佇列）上執行，觀察者查詢需要讀取世界
 * 狀態，本來就只能在該執行緒進行。[broadcastInProgress] 只是避免 tick 比推送本身更快時堆積重複的
 * 工作，實際的互斥由 [ObserverSnapshotBroadcaster] 自己負責。
 */
@Single
class FabricObserverSnapshotBroadcastService(
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val broadcaster: ObserverSnapshotBroadcaster,
) {
    /** 是否已有一次 tick 觸發的推送尚未結束。 */
    private var broadcastInProgress = false

    /** 註冊每次 tick 結束時的觀察者檢查。 */
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register {
            if (broadcastInProgress) return@register
            broadcastInProgress = true
            scope.launch(dispatchers.main) {
                try {
                    broadcaster.broadcast()
                } finally {
                    broadcastInProgress = false
                }
            }
        }
    }

    /** 開始訂閱權威狀態；協程隨 server session 的作用域一起結束。 */
    fun startSession() {
        scope.launch(dispatchers.main) { broadcaster.observeState() }
    }

    /** 清除送出紀錄，讓下一個 session 重新送出完整內容。 */
    suspend fun stopSession() {
        broadcaster.clearAll()
    }
}
