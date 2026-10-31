package com.doublemoon1119.mahjongcraft.logic.api.registration

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition

/** 擴充登記規則模組時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface MahjongModuleRegistrar {
    /**
     * 註冊一個新的規則模組。
     *
     * @param T 對應的規則配置型別。
     * @param definition 配置型別、規則模組 ID 與延遲工廠。
     * @throws IllegalArgumentException 模組 ID 不符合命名空間格式，或配置類別／模組 ID 已重複註冊。
     * @throws IllegalStateException 註冊中心已凍結時拋出。
     */
    fun <T : MahjongRuleConfig> register(definition: MahjongModuleDefinition<T>)
}
