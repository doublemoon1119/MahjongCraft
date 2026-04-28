package com.doublemoon1119.mahjongcraft.domain.module

import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig

/**
 * 麻將規則模組註冊中心。
 *
 * 負責管理所有已載入的麻將規則模組。
 * 提供基於規則配置 (Config) 或模組 ID 的查找功能。
 *
 * 每一次 [getModule] 呼叫都會根據傳入的 [MahjongRuleConfig] 返回新的模組實例，
 * 以確保每個麻將桌可以擁有獨立的組件，實現規則配置的獨立性。
 */
class MahjongModuleRegistryImpl: MahjongModuleRegistry {

    /**
     * 配置類別與註冊資訊的映射表。
     *
     * Key 為規則配置的類別類型 ([MahjongRuleConfig])，Value 為包含規則 ID 與工廠函式的註冊物件。
     * 用於根據傳入的配置實例快速檢索對應的模組生產工廠。
     */
    private val registrationMap = mutableMapOf<Class<out MahjongRuleConfig>, Registration<*>>()

    /**
     * 規則 ID 與配置類別的映射表。
     *
     * Key 為唯一的規則識別字串 (例如 "mahjongcraft:riichi")，Value 為該規則對應的配置類別類型。
     * 用於確保規則 ID 的唯一性，並提供根據 ID 查詢配置類別的功能。
     */
    private val idToConfigClassMap = mutableMapOf<String, Class<out MahjongRuleConfig>>()

    /**
     * 內部的資料結構，用來綁定 ID 與 Factory
     */
    private data class Registration<T : MahjongRuleConfig>(
        val id: String,
        val factory: (T, String) -> MahjongRuleModule<T>
    )

    /**
     * 註冊一個新的規則模組。
     *
     * @param configClass 規則配置類別。
     * @param id 規則模組 ID。
     * @param factory 工廠函數，接收 config 返回新的模組實例。
     * @throws IllegalArgumentException 如果該模組 ID 已經被註冊。
     */
    override fun <T : MahjongRuleConfig> register(
        configClass: Class<T>,
        id: String,
        factory: (T, id: String) -> MahjongRuleModule<T>
    ) {
        if (registrationMap.containsKey(configClass)) {
            throw IllegalArgumentException("Config class ${configClass.simpleName} already registered.")
        }
        if (idToConfigClassMap.containsKey(id)) {
            throw IllegalArgumentException("ID $id already registered.")
        }

        registrationMap[configClass] = Registration(id, factory)
        idToConfigClassMap[id] = configClass
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
    override fun <T : MahjongRuleConfig> getModule(config: T): MahjongRuleModule<T> {
        val registration = registrationMap[config::class.java] as? Registration<T>
            ?: throw IllegalStateException("No registration for ${config::class.java.simpleName}")
        return registration.factory(config, registration.id)
    }

    /**
     * 根據模組 ID 獲取規則配置類別。
     *
     * 用於讀取存檔或網路封包處理。
     *
     * @param id 規則模組 ID。
     * @return 對應的配置類別，若無則返回 null。
     */
    override fun getConfigClass(id: String): Class<out MahjongRuleConfig>? {
        return idToConfigClassMap[id]
    }

    /**
     * 獲取所有已註冊的模組 ID。
     *
     * 通常用於 UI 顯示規則列表供玩家選擇。
     *
     * @return 所有已註冊模組 ID 的集合。
     */
    override fun getAllModuleIds(): Set<String> {
        return idToConfigClassMap.keys
    }
}