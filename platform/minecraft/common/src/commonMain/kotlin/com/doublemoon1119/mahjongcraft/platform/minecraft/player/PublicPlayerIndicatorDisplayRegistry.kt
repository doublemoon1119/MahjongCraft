package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.PublicPlayerIndicatorDisplayRegistrar

/** 玩家相關公開標記（indicator、option、付款原因）完整 namespaced ID 的凍結式 Minecraft 顯示 registry；擴充只透過 [PublicPlayerIndicatorDisplayRegistrar] 登記。 */
interface PublicPlayerIndicatorDisplayRegistry : PublicPlayerIndicatorDisplayRegistrar {

    /**
     * 查詢指定標記的顯示資料；未登記時回傳 null。
     *
     * @param id 要查詢的公開標記完整 ID。
     * @return 已登記的顯示設定，或未登記時的 null。
     */
    fun find(id: String): PublicPlayerIndicatorDisplay?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
