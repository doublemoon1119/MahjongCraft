package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileDisplayNameRegistrar

/**
 * 開放註冊的牌種顯示名稱對照表；擴充只透過 [TileDisplayNameRegistrar] 登記。
 *
 * 比照 [AiStrategyDisplayNameRegistry] 的既有設計：
 * 可擴充識別碼（這裡是 [TileTypeId]，例如日麻赤五）需要顯示名稱對照時，複製這個檔案改型別，
 * 不把所有概念塞進同一個通用 registry。
 */
interface TileDisplayNameRegistry : TileDisplayNameRegistrar {

    /** 凍結 registry；凍結後不得新增顯示名稱。 */
    fun freeze()

    /**
     * 查詢 [typeId] 的顯示名稱 translation key；未登記時回傳 null。
     *
     * @param typeId 牌種穩定識別碼。
     * @return 顯示名稱翻譯 key；未登記時為 `null`。
     */
    fun find(typeId: TileTypeId): String?

    /**
     * 查詢外觀樣式套在 [tile] 上的顯示名稱 translation key；未登記時回傳 null，由呼叫端改用基本牌面名稱。
     *
     * @param styleId 牌張外觀樣式的完整 ID。
     * @param tile 基本牌面。
     * @return 顯示名稱翻譯 key；未登記時為 `null`。
     */
    fun findStyle(styleId: String, tile: Tile): String?
}
