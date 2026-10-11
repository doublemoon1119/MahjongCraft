package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * [DebugWinRoundContinuationResolver] 的執行期開關，由 `/mahjongcraft debug continuing_win` 切換。
 *
 * 之所以需要一個獨立的可變狀態物件，而不是讓指令直接把 resolver 註冊進
 * [WinRoundContinuationResolverRegistry]：該 registry 在 bootstrap 就 `freeze()`，之後禁止再註冊
 * （這是刻意的設計，讓判定順序在整個 server session 內固定不變）。因此 resolver 本身在開發環境固定
 * 註冊、但預設沒有任何桌子開啟時完全 inert，由指令逐桌切換模式。
 *
 * **以桌為範圍**：模式存在以 tableId 為鍵的 map 裡（[TableState.id]
 * 即該桌的 tableId），指令只影響執行者目前所在的那一桌。全伺服器共用的單一開關會讓同一個開發伺服器上
 * 其他桌莫名其妙進入中途胡牌流程，而且開啟後會一直有效到有人記得手動關掉。
 *
 * 條目在該桌的對局結束（`FabricGamePresentationPublisher` 發布 match settlement 時）或該桌被破壞
 * （`FabricTableLifecycleService` 清理孤兒桌時）就會清除，不會累積。
 *
 * 會被遊戲主執行緒（resolver 判定）與指令執行緒同時讀寫，因此用 [ConcurrentHashMap]。
 */
@Single
class DebugWinRoundContinuationState {
    private val modesByTableId = ConcurrentHashMap<Uuid, DebugWinRoundContinuationMode>()

    /** [tableId] 目前的模式；沒有設定過時為 [DebugWinRoundContinuationMode.OFF]。 */
    fun modeFor(tableId: Uuid): DebugWinRoundContinuationMode = modesByTableId[tableId] ?: DebugWinRoundContinuationMode.OFF

    /** 設定 [tableId] 的模式；設為 [DebugWinRoundContinuationMode.OFF] 等同移除條目。 */
    fun setMode(tableId: Uuid, mode: DebugWinRoundContinuationMode) {
        if (mode == DebugWinRoundContinuationMode.OFF) {
            modesByTableId.remove(tableId)
        } else {
            modesByTableId[tableId] = mode
        }
    }

    /** 清除 [tableId] 的設定；該桌對局結束或桌子被破壞時呼叫。 */
    fun clear(tableId: Uuid) {
        modesByTableId.remove(tableId)
    }

    /** 目前仍有設定的桌子，供指令回報影響範圍。 */
    fun activeTableIds(): Set<Uuid> = modesByTableId.keys.toSet()
}
