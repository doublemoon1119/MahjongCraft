package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry

/**
 * 開放註冊的牌種顯示名稱對照表。
 *
 * 比照 [AiStrategyDisplayNameRegistry] 的既有設計：
 * 可擴充識別碼（這裡是 [TileTypeId]，例如日麻赤五）需要顯示名稱對照時，複製這個檔案改型別，
 * 不把所有概念塞進同一個通用 registry。
 */
interface TileDisplayNameRegistry {
    /** 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /** 為 [typeId] 註冊顯示名稱的 translation key。 */
    fun register(typeId: TileTypeId, translationKey: String)

    /** 凍結 registry；凍結後不得新增顯示名稱。 */
    fun freeze()

    /** 查詢 [typeId] 的顯示名稱 translation key；未登記時回傳 null。 */
    fun find(typeId: TileTypeId): String?
}
