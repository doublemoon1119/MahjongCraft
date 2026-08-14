package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import kotlin.uuid.Uuid

/**
 * 將開局座位傳送呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端只提供桌子 UUID 與依固定座位 index 排列的玩家 Uuid 清單；桌子位置查詢、entity 解析與座標
 * 計算皆由實作處理。
 */
interface MahjongSeatingPresenter {
    /**
     * 把 [seatedPlayerIds] 依固定座位 index 傳送到 [tableId] 對應桌子的座位。
     *
     * 查無桌子位置、或某個 index 解析不到可移動的實體（例如 AI 座位、玩家目前不在線）時，該筆直接
     * 跳過，不影響其他座位的傳送。
     *
     * @param tableId 桌子的穩定 UUID，與對局 Uuid 相同。
     * @param seatedPlayerIds 依 `TableState.players` 固定座位順序排列的玩家 Uuid 清單。
     */
    fun present(tableId: Uuid, seatedPlayerIds: List<Uuid>)
}
