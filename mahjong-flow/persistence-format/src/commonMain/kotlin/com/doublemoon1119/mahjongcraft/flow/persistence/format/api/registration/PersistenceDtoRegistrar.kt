package com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoDefinition

/**
 * 擴充登記一類保存格式時取得的登記介面；編碼、解碼與凍結由 MahjongCraft 進行。
 *
 * @param Domain 這一類的領域型別。
 */
interface PersistenceDtoRegistrar<Domain : Any> {
    /** 目前已登記 persistence mapper 的穩定 type key 快照。 */
    val registrationKeys: Set<String>

    /**
     * 註冊一組具體領域型別與 persistence DTO 的雙向轉換。
     *
     * @param D 要登記的具體領域型別。
     * @param T 對應的存檔 DTO 型別。
     * @param definition 穩定 type key、領域型別、serializer 與雙向 mapper 的完整定義。
     * @throws IllegalArgumentException 若 definition 的 type key 不是 namespaced ID，或領域類別／type key 已被註冊。
     * @throws IllegalStateException 註冊表已凍結時拋出。
     */
    fun <D : Domain, T : Any> register(definition: PersistenceDtoDefinition<D, T>)
}
