package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueProvider

/** 擴充登記規則一覽時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface RuleCatalogueRegistrar {
    /** 目前已登記規則 ID 的快照，供規則選擇與註冊統計使用。 */
    val registrationKeys: Set<String>

    /** 是否禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記來源；拒絕重複規則 ID 與凍結後的變更。
     *
     * @param provider 規則自訂目錄來源。
     */
    fun register(provider: RuleCatalogueProvider)
}
