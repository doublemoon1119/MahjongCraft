package com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceDefinition

/** 擴充登記組件對外值的保存格式時取得的登記介面；編碼、解碼與凍結由 MahjongCraft 進行。 */
interface ComponentViewPersistenceRegistrar {
    /** 已登記 ID 的獨立集合。 */
    val registrationKeys: Set<String>

    /**
     * 登記完整轉換，不能重複或在凍結後改動。
     *
     * @param V 投影值型別。
     * @param D 保存 DTO 型別。
     * @param definition 投影及雙向保存轉換。
     */
    fun <V : Any, D : Any> register(definition: ComponentViewPersistenceDefinition<V, D>)
}
