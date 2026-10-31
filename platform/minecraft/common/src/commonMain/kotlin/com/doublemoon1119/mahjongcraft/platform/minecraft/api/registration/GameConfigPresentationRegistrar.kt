package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition

/** 擴充登記房間設定呈現時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface GameConfigPresentationRegistrar {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記設定呈現定義。
     *
     * @param definition 要登記的規則設定呈現定義。
     * @throws IllegalArgumentException 規則重複登記，或提供了保留的共用流程分類、欄位 ID。
     */
    fun register(definition: GameConfigPresentationDefinition)
}
