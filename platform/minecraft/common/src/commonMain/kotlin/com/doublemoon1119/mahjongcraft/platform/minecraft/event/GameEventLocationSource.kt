package com.doublemoon1119.mahjongcraft.platform.minecraft.event

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/** 提供事件產生當下的麻將桌位置。 */
fun interface GameEventLocationSource {
    /**
     * 依場地 UUID 取得位置；找不到時回傳 `null`。
     *
     * @param venueId 要查詢位置的場地 UUID。
     * @return 場地目前的位置，或找不到時的 `null`。
     */
    fun find(venueId: Uuid): TableLocation?
}
