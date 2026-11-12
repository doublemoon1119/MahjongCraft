package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileAreaDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileReplacementDisplayDefinition

/** 擴充登記牌譜上移出牌區標題與換牌用語時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface HistoryTileSetAsideDisplayRegistrar {
    /** 目前已登記項目的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記牌區標題；凍結後或牌區重複時拒絕。
     *
     * @param definition 牌區與標題。
     */
    fun register(definition: HistoryTileAreaDisplayDefinition)

    /**
     * 登記規則的換牌用語；凍結後或規則重複時拒絕。
     *
     * @param definition 規則與用語。
     */
    fun register(definition: HistoryTileReplacementDisplayDefinition)
}
