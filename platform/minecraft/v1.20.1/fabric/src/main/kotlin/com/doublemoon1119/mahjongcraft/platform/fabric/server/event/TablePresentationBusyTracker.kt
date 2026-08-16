package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 記錄「哪些桌子目前正在播放呈現動畫（擲骰、建牆…之後可能陸續加入）」的本地狀態，供
 * [FabricGamePresentationPublisher] 標記、供輸入分派入口（`MahjongTableGameActionService`）與自動操作
 * 心跳（`FabricDecisionTimerScheduler`）查詢是否要暫時擋下這桌的操作——動畫播放期間刻意不讓玩家操作
 * 或 AI／強制自動操作搶在畫面之前推進，避免出現牌局狀態跟畫面不一致的情況。
 *
 * 這是純 Minecraft 平台概念，`mahjong-flow` 不需要、也不能知道「這桌現在是不是還在播動畫」；
 * `isBusy` 只影響「Minecraft 端何時把玩家操作／自動操作心跳送進 `GameFlowCoordinator`」，
 * 不影響 `mahjong-flow` 本身的權威決策計時器語意。
 *
 * 用 [clock]（實際綁定為 tick 驅動的實作）而不是真實系統時間計算到期時間：這裡要保護的是「動畫
 * 播完之前不要讓遊戲流程搶跑」，但動畫本身（`MahjongDiceEntity` 的 `world.time` 判斷）在 Minecraft
 * 單機版暫停時會正確凍結——如果這裡改用真實時間，暫停期間忙碌狀態會照樣過期，取消暫停後動畫其實
 * 還沒播完，玩家操作跟 AI 心跳卻已經恢復，等於這個 tracker 想擋的情況自己先破功。
 *
 * 不持久化，伺服器重啟後 [isBusy] 一律回傳 `false`：這個狀態要保護的是「玩家操作／自動操作心跳搶在
 * 呈現動畫播完前推進」，但呈現動畫本身完全不可能撐過一次伺服器重啟（沒有任何 client 在看），重啟後
 * 也不存在「正在播放的動畫」，所以沒有東西需要保護，fallback 成不忙碌是正確行為，不是需要補的漏洞。
 */
@Single
class TablePresentationBusyTracker(private val clock: MonotonicClock) {
    private val busyUntilMillisByTableId = ConcurrentHashMap<Uuid, Long>()

    /** 標記 [tableId] 從現在起忙碌 [durationTicks] 個 tick（依 20 TPS 換算成毫秒）。 */
    fun markBusyFor(tableId: Uuid, durationTicks: Int) {
        busyUntilMillisByTableId[tableId] = clock.nowMillis() + durationTicks * MILLIS_PER_TICK
    }

    /** [tableId] 目前是否仍在忙碌中；已過期的記錄視為不忙碌（不需要另外呼叫清除）。 */
    fun isBusy(tableId: Uuid): Boolean = (busyUntilMillisByTableId[tableId] ?: return false) > clock.nowMillis()

    private companion object {
        /** Minecraft 正常運行時每個 tick 對應的毫秒數（20 TPS）。 */
        const val MILLIS_PER_TICK: Long = 50L
    }
}
