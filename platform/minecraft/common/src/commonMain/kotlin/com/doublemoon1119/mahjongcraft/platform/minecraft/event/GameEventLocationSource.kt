package com.doublemoon1119.mahjongcraft.platform.minecraft.event

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 提供事件產生當下的麻將桌位置。
 *
 * 查詢可能在背景提交執行緒且仍持有權威交易鎖時執行，實作必須執行緒安全且非阻塞，
 * 只能讀取已發布的記憶體索引，不得存取 Minecraft 世界或等待主執行緒。
 */
fun interface GameEventLocationSource {
    /**
     * 依場地 UUID 取得位置；找不到時回傳 `null`。
     *
     * @param venueId 要查詢位置的場地 UUID。
     * @return 場地目前的位置，或找不到時的 `null`。
     */
    fun find(venueId: Uuid): TableLocation?
}
