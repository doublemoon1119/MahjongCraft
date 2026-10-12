package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation

import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 保存各桌的開發用中途和牌模式，預設關閉。
 * 判定器在初始化時註冊，指令只切換此處的桌級模式，不改變判定器註冊順序。
 * 讀寫以 [ConcurrentHashMap] 保護；對局結束或桌子清理時由呼叫端移除設定。
 */
@Single
class DebugWinRoundContinuationState {
    /** 各牌桌啟用的非 OFF 模式，支援跨執行緒查詢與更新。 */
    private val modesByTableId = ConcurrentHashMap<Uuid, DebugWinRoundContinuationMode>()

    /**
     * 查詢牌桌目前的模式。
     *
     * @param tableId 牌桌識別碼。
     * @return 已設定的模式；沒有設定時為 [DebugWinRoundContinuationMode.OFF]。
     */
    fun modeFor(tableId: Uuid): DebugWinRoundContinuationMode = modesByTableId[tableId] ?: DebugWinRoundContinuationMode.OFF

    /**
     * 設定牌桌模式；設為 [DebugWinRoundContinuationMode.OFF] 時移除條目。
     *
     * @param tableId 牌桌識別碼。
     * @param mode 要採用的模式。
     */
    fun setMode(tableId: Uuid, mode: DebugWinRoundContinuationMode) {
        if (mode == DebugWinRoundContinuationMode.OFF) {
            modesByTableId.remove(tableId)
        } else {
            modesByTableId[tableId] = mode
        }
    }

    /**
     * 清除指定牌桌的設定。
     *
     * @param tableId 對局結束或牌桌被破壞的牌桌識別碼。
     */
    fun clear(tableId: Uuid) {
        modesByTableId.remove(tableId)
    }

    /**
     * 取得目前啟用中途和牌模式的桌子。
     *
     * @return 呼叫當下取得的識別碼集合副本；後續設定變更不會修改此集合。
     */
    fun activeTableIds(): Set<Uuid> = modesByTableId.keys.toSet()
}
