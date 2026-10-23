package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import kotlin.uuid.Uuid

/**
 * 將開局與局間換位的座位傳送呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端只提供桌子 UUID 與依固定座位 index 排列的玩家 Uuid 清單；桌子位置查詢、entity 解析與座標
 * 計算皆由實作處理。
 */
interface MahjongSeatingPresenter {
    /**
     * 把 [seatedPlayerIds] 依固定座位 index 傳送到 [tableId] 對應桌子的座位，開局與局間換位都用這一個入口。
     *
     * 正坐在這張桌子其他座位上的玩家先站起來，全部站起後再依座位順序入座，因此玩家開局前隨意坐下或換位時互換座位，
     * 都能坐到自己的座位。已坐在自己座位上的玩家不動；坐在這張桌子座位以外的東西上的玩家不強制拉下來。查無桌子位置、
     * 或某個 index 解析不到可移動的實體（例如 AI 座位、玩家目前不在線）時，該筆直接跳過，不影響其他座位的傳送。
     *
     * @param tableId 桌子的穩定 UUID，與對局 Uuid 相同。
     * @param seatedPlayerIds 依 `TableState.players` 座位順序排列的玩家 Uuid 清單。
     */
    fun present(tableId: Uuid, seatedPlayerIds: List<Uuid>)
}
