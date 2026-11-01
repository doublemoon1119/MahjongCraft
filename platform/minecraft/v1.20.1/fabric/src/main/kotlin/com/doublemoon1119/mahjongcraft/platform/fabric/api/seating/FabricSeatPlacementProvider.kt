package com.doublemoon1119.mahjongcraft.platform.fabric.api.seating

import net.minecraft.item.ItemStack

/** 提供座位物品的辨識與放置，不取代已存在座位的解析能力。 */
interface FabricSeatPlacementProvider {
    /**
     * 判斷是否支援此物品；可在客戶端及伺服器呼叫，不得改動物品或世界。
     *
     * @param stack 目前手持的物品，不可保留其可變參照。
     * @return 此提供者是否負責放置。
     */
    fun supports(stack: ItemStack): Boolean

    /**
     * 在固定席位放置座位，只在伺服器主執行緒呼叫。
     *
     * 不自行讓角色入座或修改牌局。失敗不扣物品；成功使用物品原有的權限、放置及耗材規則。
     * 必須檢查完整放置範圍，不載入區塊。若已改變世界，不可回報未放置；後續入座失敗仍保留座位。
     *
     * @param context 本次互動，不可保留至回呼結束後使用。
     * @return 座位是否已成功放置。
     */
    fun place(context: FabricSeatPlacementContext): Boolean
}
