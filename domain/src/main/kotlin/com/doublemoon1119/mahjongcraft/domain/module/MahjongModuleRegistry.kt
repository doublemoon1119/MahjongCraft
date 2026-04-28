package com.doublemoon1119.mahjongcraft.domain.module

import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig

/**
 * 麻將規則模組註冊中心介面。
 * 定義了註冊與獲取規則模組的標準行為。
 */
interface MahjongModuleRegistry {

    /**
     * 註冊一個新的規則模組。
     * @param configClass 規則配置類別。
     * @param id 規則模組 ID。
     * @param factory 工廠函數。
     */
    fun <T : MahjongRuleConfig> register(
        configClass: Class<T>,
        id: String,
        factory: (T) -> MahjongRuleModule<T>
    )

    /**
     * 根據配置獲取規則模組實體。
     */
    fun <T : MahjongRuleConfig> getModule(config: T): MahjongRuleModule<T>

    /**
     * 獲取所有已註冊的模組 ID。
     */
    fun getAllModuleIds(): Set<String>

    /**
     * 根據 ID 獲取配置類別。
     */
    fun getConfigClass(id: String): Class<out MahjongRuleConfig>?
}