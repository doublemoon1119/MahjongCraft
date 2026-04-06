package com.doublemoon1119.mahjongcraft.application.usecase.factory

import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.module.MahjongRuleModule

/**
 * 麻將規則模組註冊中心。
 *
 * 負責管理所有已載入的麻將規則模組。
 * 提供基於規則配置 (Config) 或模組 ID 的查找功能。
 *
 * 每一次 [getModule] 呼叫都會根據傳入的 [config] 返回新的模組實例，
 * 以確保每個麻將桌可以擁有獨立的組件，實現規則配置的獨立性。
 */
class MahjongModuleRegistry {

    /**
     * 工廠函數映射：Config Class 對應工廠函數。
     * 使用 @Suppress("UNCHECKED_CAST") 來處理泛型型別。
     */
    private val factoryMap = mutableMapOf<Class<out MahjongRuleConfig>, (MahjongRuleConfig) -> MahjongRuleModule<*>>()

    /**
     * ID 對應 Config Class (用於存檔識別)。
     */
    private val idMap = mutableMapOf<String, Class<out MahjongRuleConfig>>()

    /**
     * 註冊一個新的規則模組。
     *
     * @param configClass 規則配置類別。
     * @param id 規則模組 ID。
     * @param factory 工廠函數，接收 config 返回新的模組實例。
     * @throws IllegalArgumentException 如果該模組 ID 已經被註冊。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : MahjongRuleConfig> register(
        configClass: Class<T>,
        id: String,
        factory: (T) -> MahjongRuleModule<T>
    ) {
        if (idMap.containsKey(id)) {
            throw IllegalArgumentException("Duplicate Mahjong Module ID: $id")
        }
        factoryMap[configClass] = factory as (MahjongRuleConfig) -> MahjongRuleModule<*>
        idMap[id] = configClass
    }

    /**
     * 根據配置獲取規則模組。
     *
     * 每次呼叫都會返回新的實例，確保每個麻將桌有獨立的模組。
     *
     * @param config 規則配置實例。
     * @return 對應的模組。
     * @throws IllegalStateException 如果找不到對應的模組。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : MahjongRuleConfig> getModule(config: T): MahjongRuleModule<T> {
        val factory = factoryMap[config::class.java]
            ?: throw IllegalStateException("No MahjongRuleModule registered for configuration: ${config::class.simpleName}")
        return factory(config) as MahjongRuleModule<T>
    }

    /**
     * 根據模組 ID 獲取規則配置類別。
     *
     * 用於讀取存檔或網路封包處理。
     *
     * @param id 規則模組 ID。
     * @return 對應的配置類別，若無則返回 null。
     */
    fun getConfigClass(id: String): Class<out MahjongRuleConfig>? {
        return idMap[id]
    }

    /**
     * 獲取所有已註冊的模組 ID。
     *
     * 通常用於 UI 顯示規則列表供玩家選擇。
     *
     * @return 所有已註冊模組 ID 的集合。
     */
    fun getAllModuleIds(): Set<String> {
        return idMap.keys
    }
}