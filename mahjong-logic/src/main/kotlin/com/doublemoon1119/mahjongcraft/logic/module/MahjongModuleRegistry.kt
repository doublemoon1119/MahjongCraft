package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 麻將規則模組註冊中心介面。
 * 負責管理不同麻將規則配置 (Config) 與其對應模組 (Module) 的映射關係。
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
        factory: (T, id: String) -> MahjongRuleModule<T>
    )

    /**
     * 根據傳入的配置獲取已綁定該配置的規則模組實例。
     */
    fun <T : MahjongRuleConfig> getModule(config: T): MahjongRuleModule<T>

    /**
     * 獲取所有已註冊模組的唯一識別碼集合。
     */
    fun getAllModuleIds(): Set<String>

    /**
     * 根據 ID 獲取對應的配置類別。
     */
    fun getConfigClass(id: String): Class<out MahjongRuleConfig>?
}