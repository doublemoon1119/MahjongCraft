package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileStyleDisplayNameDefinition

/** 擴充登記牌名時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface TileDisplayNameRegistrar {
    /** 目前已登記牌種 ID 的快照。 */
    val registrationKeys: Set<String>

    /** 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: TileDisplayNameDefinition)

    /**
     * 登記外觀樣式套在指定基本牌面時的顯示名稱；凍結後或同一組樣式與牌面重複時拒絕。
     *
     * @param definition 外觀樣式、基本牌面與顯示名稱的對應。
     */
    fun register(definition: TileStyleDisplayNameDefinition)
}
