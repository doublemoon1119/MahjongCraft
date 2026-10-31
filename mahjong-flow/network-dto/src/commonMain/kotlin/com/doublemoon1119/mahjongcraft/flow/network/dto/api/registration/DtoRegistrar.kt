package com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration

import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoDefinition

/**
 * 擴充登記一類網路 DTO 時取得的登記介面；轉換、序列化查詢與凍結由 MahjongCraft 進行。
 *
 * @param Domain 這一類的領域型別。
 * @param Dto 這一類的網路 DTO 型別。
 */
interface DtoRegistrar<Domain : Any, Dto : Any> {
    /** 目前已登記 DTO 的穩定序列化名稱快照。 */
    val registrationKeys: Set<String>

    /**
     * 註冊一組領域型別 ↔ DTO 的對應關係。
     *
     * @param D 要登記的具體領域型別。
     * @param T 對應的 DTO 型別。
     * @param definition 領域型別、DTO 型別、serializer 與雙向 mapper 的完整定義。
     */
    fun <D : Domain, T : Dto> register(definition: DtoDefinition<D, T>)
}
